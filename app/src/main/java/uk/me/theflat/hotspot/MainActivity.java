package uk.me.theflat.hotspot;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
  private LinearLayout root, deviceList;
  private EditText minutes, cap;
  private Switch restrict, watch;
  private TextView status;
  private Button apply;
  private JSONObject latest = new JSONObject();
  private final Map<String, CheckBox> boxes = new LinkedHashMap<>();
  private final Handler handler = new Handler();
  private boolean resumed = false, busy = false, changingWatch = false;
  private final Runnable refresh =
      () ->
          ControllerClient.get(this)
              .request(
                  1,
                  new JSONObject(),
                  o -> {
                    latest = o;
                    renderStatus();
                    if (resumed) handler.postDelayed(this.refresh, 3000);
                  });

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    ScrollView scroll = new ScrollView(this);
    root = new LinearLayout(this);
    root.setOrientation(1);
    root.setPadding(32, 24, 32, 32);
    root.setBackgroundColor(0xff102531);
    scroll.addView(root);
    setContentView(scroll);
    scroll.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    label("Hotspot Keeper", 28);
    label("Shizuku edition · 0.2.2", 14);
    status = label("Connecting…", 17);
    button(
        "Connect / authorise Shizuku",
        v -> {
          try {
            toast(ControllerClient.get(this).authorize());
          } catch (RuntimeException e) {
            toast(e.getMessage());
            Intent launch =
                getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (launch != null) startActivity(launch);
          }
        });
    watch = new Switch(this);
    watch.setText("Prompt whenever hotspot is enabled");
    watch.setTextColor(-1);
    watch.setChecked(prefs(this).getBoolean("watch", false));
    root.addView(watch);
    watch.setOnCheckedChangeListener(
        (v, on) -> {
          if (!changingWatch) {
            if (on) enableWatch();
            else {
              prefs(this).edit().putBoolean("watch", false).apply();
              stopService(new Intent(this, SessionService.class));
              toast("Prompts off. Any active controller session still enforces its rules.");
            }
          }
        });
    label("Session rules", 22);
    minutes = field("Duration in minutes · blank = normal idle shutdown");
    cap = field("Data allowance in MB · blank = unlimited");
    minutes.setText(prefs(this).getString("minutes", ""));
    cap.setText(prefs(this).getString("cap", ""));
    restrict = new Switch(this);
    restrict.setText("Only selected devices");
    restrict.setTextColor(-1);
    restrict.setChecked(prefs(this).getBoolean("restricted", false));
    root.addView(restrict);
    label(
        "When off, anyone with your existing hotspot password can connect. Timers start when"
            + " applied; edited data caps use the session's original baseline.",
        14);
    deviceList = new LinearLayout(this);
    deviceList.setOrientation(1);
    root.addView(deviceList);
    buildDevices();
    button("Remember connected devices", v -> remember());
    button("Add device by Wi-Fi address", v -> addDevice());
    apply = button("Start hotspot with these rules", v -> configure());
    button("Stop hotspot now", v -> command(4, new JSONObject()));
    button("Android hotspot settings", v -> settings(this));
    button(
        "Capabilities / diagnostic details",
        v ->
            new AlertDialog.Builder(this)
                .setTitle("Controller diagnostics")
                .setMessage(latest.toString())
                .setPositiveButton("Close", null)
                .show());
    label(
        "Device addresses may change with private/randomised Wi-Fi addresses. Long-press a saved"
            + " device to rename or remove it. Hidden SSID is not used as an access restriction.",
        14);
    label(
        "Data counters may lag and include other tethering. A cap stops the hotspot after reported"
            + " usage reaches the allowance; overshoot is possible. If counters fail during a"
            + " capped session, Keeper requests shutdown.",
        14);
    label(
        "Automatic prompts need this watcher running and Shizuku started. After reboot restart"
            + " Shizuku; after Android force-stop, open Keeper again. No analytics or internet"
            + " permission.",
        14);
    if (prefs(this).getBoolean("watch", false)) enableWatch();
  }

  private boolean notifications() {
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != getPackageManager().PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1);
      return false;
    }
    return getSystemService(NotificationManager.class).areNotificationsEnabled();
  }

  private void enableWatch() {
    if (!notifications()) {
      changingWatch = true;
      watch.setChecked(false);
      changingWatch = false;
      toast("Allow notifications to enable automatic session prompts");
      return;
    }
    prefs(this).edit().putBoolean("watch", true).apply();
    changingWatch = true;
    watch.setChecked(true);
    changingWatch = false;
    startForegroundService(new Intent(this, SessionService.class));
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == 1 && results.length > 0 && results[0] == getPackageManager().PERMISSION_GRANTED)
      enableWatch();
  }

  private void configure() {
    if (busy) return;
    if (!notifications()) {
      toast("Allow notifications before starting a managed session");
      return;
    }
    try {
      long m = SessionPolicy.parse(minutes.getText().toString(), 10080),
          mb = SessionPolicy.parse(cap.getText().toString(), 1048576);
      List<String> selected = new ArrayList<>();
      for (Map.Entry<String, CheckBox> e : boxes.entrySet())
        if (e.getValue().isChecked()) selected.add(e.getKey());
      new SessionRules(m, mb, restrict.isChecked(), selected);
      JSONObject p =
          new JSONObject()
              .put("minutes", m)
              .put("mb", mb)
              .put("restricted", restrict.isChecked())
              .put("allowed", new JSONArray(selected));
      prefs(this)
          .edit()
          .putString("minutes", minutes.getText().toString())
          .putString("cap", cap.getText().toString())
          .putBoolean("restricted", restrict.isChecked())
          .putString("selected", new JSONArray(selected).toString())
          .apply();
      enableWatch();
      command(latest.optBoolean("enabled") ? 3 : 2, p);
    } catch (Exception e) {
      toast(e.getMessage());
    }
  }

  private void command(int command, JSONObject p) {
    if (busy) return;
    busy = true;
    apply.setEnabled(false);
    ControllerClient.get(this)
        .request(
            command,
            p,
            o -> {
              busy = false;
              apply.setEnabled(true);
              if (o.optBoolean("ok")) {
                latest = o;
                renderStatus();
                getSystemService(NotificationManager.class).cancel(2);
                toast(
                    command == 4
                        ? "Stop requested—watch for hotspot off"
                        : "Rules accepted; checking hotspot state");
              } else {
                new AlertDialog.Builder(this)
                    .setTitle("Rules were not confirmed")
                    .setMessage(
                        o.optString("error")
                            + "\n"
                            + "Existing rules, if any, remain. Check diagnostics before retrying.")
                    .setPositiveButton("OK", null)
                    .show();
              }
            });
  }

  private void renderStatus() {
    boolean ready = latest.optBoolean("ready") && latest.optBoolean("ok");
    if (!ready) {
      status.setText(ControllerClient.get(this).availability() + "\n" + latest.optString("error"));
      return;
    }
    boolean enabled = latest.optBoolean("enabled"), managed = latest.optBoolean("managed");
    long deadline = latest.optLong("deadline"), used = latest.optLong("used", -1);
    String state =
        enabled
            ? "Hotspot on"
            : latest.optInt("state") == 12 || latest.optBoolean("awaitingStart")
                ? "Hotspot starting"
                : "Hotspot off";
    status.setText(
        ControllerClient.get(this).availability()
            + "\n"
            + state
            + " · "
            + latest.optString("ssid")
            + "\n"
            + (managed ? "Session rules active" : "No Keeper session rules active")
            + "\n"
            + (managed
                ? (deadline == 0
                        ? "Normal idle shutdown"
                        : (SessionRules.remaining(deadline, SystemClock.elapsedRealtime()) + 59999)
                                / 60000
                            + " min remaining")
                    + " · "
                    + (used < 0
                        ? "Usage unavailable"
                        : String.format(Locale.UK, "%.1f MB reported", used / 1048576.0))
                    + "\n"
                : "")
            + "Connected: "
            + latest.optJSONArray("clients")
            + "\nDevice allowlist: "
            + (latest.optBoolean("allowSupported") ? "supported" : "not yet supported/confirmed")
            + (latest.optString("error").isEmpty() ? "" : "\n" + latest.optString("error")));
    apply.setText(enabled ? "Apply rules to running hotspot" : "Start hotspot with these rules");
  }

  private JSONObject known() {
    try {
      return new JSONObject(prefs(this).getString("known", "{}"));
    } catch (JSONException e) {
      return new JSONObject();
    }
  }

  private void buildDevices() {
    deviceList.removeAllViews();
    boxes.clear();
    JSONObject names = known();
    Set<String> selected = new HashSet<>();
    try {
      JSONArray a = new JSONArray(prefs(this).getString("selected", "[]"));
      for (int i = 0; i < a.length(); i++) selected.add(a.getString(i));
    } catch (JSONException ignored) {
    }
    Iterator<String> it = names.keys();
    while (it.hasNext()) {
      String mac = it.next();
      CheckBox b = new CheckBox(this);
      b.setText(names.optString(mac) + " · " + mac);
      b.setTextColor(-1);
      b.setChecked(selected.contains(mac));
      b.setOnLongClickListener(
          v -> {
            editDevice(mac);
            return true;
          });
      deviceList.addView(b);
      boxes.put(mac, b);
    }
    if (boxes.isEmpty()) {
      TextView empty = new TextView(this);
      empty.setText(
          "No known devices yet. Connect once using password access, then remember them.");
      empty.setTextColor(0xffb8ccd6);
      deviceList.addView(empty);
    }
  }

  private void saveSelections() {
    JSONArray selected = new JSONArray();
    for (Map.Entry<String, CheckBox> e : boxes.entrySet())
      if (e.getValue().isChecked()) selected.put(e.getKey());
    prefs(this).edit().putString("selected", selected.toString()).apply();
  }

  private void remember() {
    JSONArray clients = latest.optJSONArray("clients");
    if (clients == null || clients.length() == 0) {
      toast("No connected devices reported yet");
      return;
    }
    saveSelections();
    JSONObject names = known();
    try {
      for (int i = 0; i < clients.length(); i++) {
        String mac = clients.getString(i);
        if (!names.has(mac))
          names.put(mac, "Device " + mac.substring(Math.max(0, mac.length() - 5)));
      }
      prefs(this).edit().putString("known", names.toString()).apply();
      buildDevices();
      toast("Devices saved. Long-press to give them names.");
    } catch (JSONException e) {
      toast("Device list unavailable");
    }
  }

  private void addDevice() {
    LinearLayout fields = new LinearLayout(this);
    fields.setOrientation(1);
    EditText name = new EditText(this), mac = new EditText(this);
    name.setHint("Friendly name");
    mac.setHint("Wi-Fi address: aa:bb:cc:dd:ee:ff");
    fields.addView(name);
    fields.addView(mac);
    new AlertDialog.Builder(this)
        .setTitle("Add known device")
        .setView(fields)
        .setPositiveButton(
            "Save",
            (d, w) -> {
              try {
                String address = mac.getText().toString().trim().toLowerCase(Locale.ROOT);
                new SessionRules(0, 0, true, Collections.singletonList(address));
                JSONObject names = known();
                names.put(
                    address,
                    name.getText().toString().trim().isEmpty()
                        ? address
                        : name.getText().toString().trim());
                saveSelections();
                prefs(this).edit().putString("known", names.toString()).apply();
                buildDevices();
              } catch (Exception e) {
                toast("Enter a valid Wi-Fi MAC address");
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void editDevice(String mac) {
    EditText name = new EditText(this);
    name.setText(known().optString(mac));
    new AlertDialog.Builder(this)
        .setTitle(mac)
        .setView(name)
        .setPositiveButton(
            "Rename",
            (d, w) -> {
              try {
                JSONObject names = known();
                names.put(mac, name.getText().toString());
                saveSelections();
                prefs(this).edit().putString("known", names.toString()).apply();
                buildDevices();
              } catch (JSONException ignored) {
              }
            })
        .setNeutralButton(
            "Remove",
            (d, w) -> {
              JSONObject names = known();
              names.remove(mac);
              saveSelections();
              prefs(this).edit().putString("known", names.toString()).apply();
              buildDevices();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private EditText field(String hint) {
    EditText e = new EditText(this);
    e.setTextColor(-1);
    e.setHintTextColor(0xffa9bec8);
    e.setHint(hint);
    e.setInputType(2);
    root.addView(e, new LinearLayout.LayoutParams(-1, -2));
    return e;
  }

  private TextView label(String s, int size) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(0xffe6f0f4);
    t.setPadding(0, 12, 0, 12);
    root.addView(t);
    return t;
  }

  private Button button(String s, View.OnClickListener l) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setOnClickListener(l);
    root.addView(b, new LinearLayout.LayoutParams(-1, -2));
    return b;
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("session", 0);
  }

  static void settings(Context c) {
    try {
      c.startActivity(
          new Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (ActivityNotFoundException e) {
      c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    handler.removeCallbacks(refresh);
    handler.post(refresh);
  }

  @Override
  protected void onPause() {
    resumed = false;
    handler.removeCallbacks(refresh);
    super.onPause();
  }
}
