package uk.me.theflat.hotspot;

import static uk.me.theflat.hotspot.KeeperUi.*;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/**
 * Native dashboard, settings and contextual session editor. Controller operations stay separate.
 */
public final class MainActivity extends Activity {
  private LinearLayout root;
  private JSONObject latest = new JSONObject();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private String page = "home";
  private boolean resumed, busy, pendingWatch;
  private Runnable afterPermission;
  private TextView connection,
      state,
      network,
      countdown,
      elapsed,
      times,
      rulesValue,
      hotspotValue,
      ruleCaption,
      errorLabel;
  private String renderedClients = "";
  private TextView connectionInfo;
  private long editorSession;
  private Button primary;
  private ProgressBar progress;
  private LinearLayout connectedCard;
  private LinearLayout connectedSection;
  private Dialog editor;
  private HotspotSettingsSheet hotspotEditor;
  private SeekBar slider;
  private TextView preview, limitLabel;
  private int editingMinutes;
  private final Map<String, CheckBox> choices = new LinkedHashMap<>();
  private final Runnable refresh =
      new Runnable() {
        public void run() {
          ControllerClient.get(MainActivity.this)
              .request(
                  1,
                  new JSONObject(),
                  o -> {
                    latest = o;
                    if (page.equals("home")) updateHome();
                    if (connectionInfo != null
                        && (page.equals("connection") || page.equals("settings")))
                      connectionInfo.setText(
                          ready()
                              ? "● Connected"
                              : ControllerClient.get(MainActivity.this).availability());
                    if (editor != null && editor.isShowing()) updatePreview();
                    if (resumed) handler.postDelayed(this, 3000);
                  });
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    if (saved != null) page = saved.getString("page", "home");
    show(page);
    if (prefs(this).getBoolean("watch", false) && notificationAllowed())
      startForegroundService(new Intent(this, SessionService.class));
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putString("page", page);
  }

  private int d(float x) {
    return dp(this, x);
  }

  private void pad(View v, int x, int y) {
    v.setPadding(d(x), d(y), d(x), d(y));
  }

  private void space(LinearLayout parent, int h) {
    View v = new View(this);
    parent.addView(v, new LinearLayout.LayoutParams(1, d(h)));
  }

  private TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
    TextView t = KeeperUi.text(this, value, size, color, bold);
    parent.addView(t);
    return t;
  }

  private LinearLayout card(LinearLayout parent) {
    LinearLayout c = column(this);
    pad(c, 16, 14);
    c.setBackground(shape(this, CARD, 22, true));
    parent.addView(c, new LinearLayout.LayoutParams(-1, -2));
    return c;
  }

  private void divider(LinearLayout p) {
    View v = new View(this);
    v.setBackgroundColor(LINE);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, d(1));
    lp.topMargin = d(12);
    lp.bottomMargin = d(12);
    p.addView(v, lp);
  }

  private void heading(String name, String subtitle) {
    space(root, 16);
    text(root, name, 19, WHITE, true);
    if (!subtitle.isEmpty()) {
      space(root, 4);
      text(root, subtitle, 13, MUTED, false);
    }
    space(root, 10);
  }

  private Button action(LinearLayout p, String value, int color, View.OnClickListener click) {
    Button b = new Button(this);
    b.setText(value);
    b.setTextSize(16);
    b.setTypeface(null, Typeface.BOLD);
    b.setAllCaps(false);
    b.setTextColor(color == MINT ? BG : WHITE);
    b.setMinHeight(d(50));
    b.setMinimumHeight(d(50));
    b.setPadding(d(16), d(6), d(16), d(6));
    b.setStateListAnimator(null);
    touch(b, color, 16);
    b.setOnClickListener(click);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = d(10);
    p.addView(b, lp);
    return b;
  }

  private TextView row(LinearLayout p, String icon, String title, String summary, Runnable click) {
    LinearLayout r = new LinearLayout(this);
    r.setGravity(Gravity.CENTER_VERTICAL);
    pad(r, 0, 6);
    r.setMinimumHeight(d(56));
    KeeperUi.Icon i = new KeeperUi.Icon(this, icon, MINT);
    LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(d(28), d(28));
    il.rightMargin = d(16);
    r.addView(i, il);
    LinearLayout copy = column(this);
    text(copy, title, 16, WHITE, true);
    TextView sub = text(copy, summary, 14, MUTED, false);
    sub.setPadding(0, d(4), d(4), 0);
    r.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    if (click != null) {
      KeeperUi.Icon chevron = new KeeperUi.Icon(this, "next", MUTED);
      r.addView(chevron, new LinearLayout.LayoutParams(d(18), d(18)));
      touch(r, CARD, 10);
      r.setOnClickListener(v -> click.run());
      r.setFocusable(true);
    }
    p.addView(r, new LinearLayout.LayoutParams(-1, -2));
    return sub;
  }

  private void screen(String title) {
    ScrollView sc = new ScrollView(this);
    sc.setFillViewport(true);
    sc.setBackgroundColor(BG);
    root = column(this);
    pad(root, 20, 12);
    sc.addView(root);
    setContentView(sc);
    sc.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    LinearLayout bar = new LinearLayout(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    if (!page.equals("home")) {
      KeeperUi.Icon back = new KeeperUi.Icon(this, "back", MUTED);
      FrameLayout hit = new FrameLayout(this);
      hit.setContentDescription("Back");
      hit.setOnClickListener(v -> back());
      hit.setFocusable(true);
      touch(hit, BG, 16);
      FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(d(24), d(24), Gravity.CENTER);
      hit.addView(back, ip);
      bar.addView(hit, new LinearLayout.LayoutParams(d(48), d(48)));
    } else {
      ImageView logo = new ImageView(this);
      logo.setImageResource(R.drawable.ic_launcher_foreground);
      bar.addView(logo, new LinearLayout.LayoutParams(d(44), d(44)));
    }
    TextView name = KeeperUi.text(this, title, 28, WHITE, true);
    LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
    nl.leftMargin = d(8);
    bar.addView(name, nl);
    if (page.equals("home")) {
      FrameLayout settings = new FrameLayout(this);
      touch(settings, BG, 16);
      settings.setContentDescription("Settings");
      settings.setFocusable(true);
      settings.setOnClickListener(v -> show("settings"));
      settings.addView(
          new KeeperUi.Icon(this, "gear", MUTED),
          new FrameLayout.LayoutParams(d(25), d(25), Gravity.CENTER));
      bar.addView(settings, new LinearLayout.LayoutParams(d(48), d(48)));
    }
    root.addView(bar);
    space(root, 14);
  }

  private void show(String next) {
    page = next;
    connectionInfo = null;
    renderedClients = "";
    screen(
        next.equals("home")
            ? "Keeper"
            : next.equals("settings")
                ? "Settings"
                : next.equals("help")
                    ? "Setup & help"
                    : next.equals("devices")
                        ? "Known devices"
                        : next.equals("connection") ? "Shizuku connection" : "Troubleshooting");
    switch (next) {
      case "home":
        home();
        break;
      case "settings":
        settingsPage();
        break;
      case "help":
        help();
        break;
      case "devices":
        devices();
        break;
      case "connection":
        connectionPage();
        break;
      default:
        troubleshooting();
    }
    space(root, 20);
  }

  private boolean ready() {
    return latest.optBoolean("ready") && latest.optBoolean("ok");
  }

  private boolean enabled() {
    return latest.optBoolean("enabled");
  }

  private boolean transitioning() {
    int s = latest.optInt("state");
    return s == 10
        || s == 12
        || latest.optBoolean("awaitingStart")
        || latest.optBoolean("stopping");
  }

  private void home() {
    connection = text(root, "Connecting…", 14, MUTED, false);
    connection.setPadding(0, 0, 0, d(14));
    connection.setOnClickListener(v -> show("connection"));
    connection.setContentDescription("Controller connection. Tap for details");
    LinearLayout session = card(root);
    state = text(session, "HOTSPOT OFF", 12, MINT, true);
    space(session, 5);
    network = text(session, "", 21, WHITE, true);
    space(session, 8);
    countdown = text(session, "Hotspot off", 36, WHITE, true);
    elapsed = text(session, "Ready with your saved rules", 14, MUTED, false);
    space(session, 12);
    progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(1000);
    progress.setProgressTintList(ColorStateList.valueOf(MINT));
    progress.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
    session.addView(progress, new LinearLayout.LayoutParams(-1, d(6)));
    space(session, 8);
    times = text(session, "", 13, MUTED, false);
    errorLabel = text(session, "", 14, AMBER, false);
    primary =
        action(
            session,
            "Start hotspot",
            MINT,
            v -> {
              if (enabled()) command(4, new JSONObject(), null);
              else if (!ready()) show("connection");
              else openRules(true, false);
            });
    heading("Hotspot", "");
    ruleCaption = text(root, "Saved defaults for next session", 13, MUTED, false);
    space(root, 8);
    LinearLayout rules = card(root);
    rulesValue = row(rules, "shield", "Session rules", "", () -> openRules(false, false));
    divider(rules);
    hotspotValue =
        row(rules, "gear", "Hotspot settings", "Name & password", this::openHotspotSettings);
    connectedSection = column(this);
    root.addView(connectedSection);
    space(connectedSection, 16);
    text(connectedSection, "Connected devices", 19, WHITE, true);
    space(connectedSection, 10);
    connectedCard = card(connectedSection);
    updateHome();
  }

  private String clock(long elapsed) {
    return android.text.format.DateFormat.getTimeFormat(this)
        .format(new Date(System.currentTimeMillis() + elapsed - SystemClock.elapsedRealtime()));
  }

  private String deviceName(String mac) {
    return deviceIdentity(mac).displayName(known().optString(mac.toLowerCase(Locale.ROOT), ""));
  }

  private DeviceIdentity deviceIdentity(String mac) {
    return DeviceIdentityJson.find(latest, mac);
  }

  private void updateHome() {
    if (countdown == null) return;
    boolean on = enabled(), managed = latest.optBoolean("managed"), waiting = transitioning();
    JSONObject rules = managed ? appliedRules() : defaults();
    long deadline = latest.optLong("deadline"),
        start = latest.optLong("sessionStarted"),
        now = SystemClock.elapsedRealtime();
    connection.setText(
        ready() ? "●  Control ready" : "○  " + ControllerClient.get(this).availability());
    connection.setTextColor(ready() ? MINT : AMBER);
    state.setText(
        !ready()
            ? "CONTROL UNAVAILABLE"
            : waiting
                ? (latest.optBoolean("stopping") ? "STOPPING…" : "STARTING…")
                : on ? "HOTSPOT ON" : "HOTSPOT OFF");
    network.setText(latest.optString("ssid", "Your hotspot"));
    countdown.setText(
        !ready()
            ? "Connect Keeper"
            : waiting
                ? "Please wait"
                : !on
                    ? "Hotspot off"
                    : !managed
                        ? "Choose rules"
                        : deadline == 0
                            ? "No time limit"
                            : ((SessionRules.remaining(deadline, now) + 59999) / 60000) + " min");
    elapsed.setText(
        on && start > 0
            ? (managed && deadline > 0 ? "remaining · " : "")
                + ((now - start) / 60000)
                + " minutes elapsed"
            : "Ready with your saved rules");
    progress.setVisibility(on && managed && deadline > 0 ? View.VISIBLE : View.GONE);
    progress.setProgress(
        start > 0 && deadline > start
            ? (int) Math.min(1000, Math.max(0, (now - start) * 1000 / (deadline - start)))
            : 0);
    times.setText(
        on && start > 0
            ? (latest.optBoolean("startObserved") ? "Started " : "Tracked since ")
                + clock(start)
                + (managed && deadline > 0 ? "   ·   Ends " + clock(deadline) : "")
            : "Review your rules before starting");
    String error = latest.optString("error", "");
    errorLabel.setVisibility(error.isEmpty() ? View.GONE : View.VISIBLE);
    errorLabel.setText(error);
    primary.setEnabled(!busy && !waiting);
    primary.setText(
        busy ? "Working…" : !ready() ? "Connect Keeper" : on ? "Stop hotspot" : "Start hotspot");
    touch(primary, on ? 0xffa83e4b : MINT, 16);
    primary.setTextColor(on ? WHITE : BG);
    ruleCaption.setText(
        on
            ? (managed ? "Applied to this session" : "No Keeper rules active · tap to apply")
            : "Saved defaults for next session");
    JSONArray a = rules.optJSONArray("allowed");
    long m = rules.optLong("minutes");
    rulesValue.setText(
        (rules.optBoolean("restricted")
                ? (a == null ? 0 : a.length()) + " permitted devices"
                : "Anyone with password")
            + " · "
            + (m == 0 ? "No time limit" : m + " minutes total"));
    hotspotValue.setText(
        latest.optString("configuredSsid", latest.optString("ssid", ""))
            + (latest.optBoolean("hotspotSettingsPending")
                ? " · Saved for next start"
                : " · Name & password"));
    connectedSection.setVisibility(on ? View.VISIBLE : View.GONE);
    JSONArray clients = latest.optJSONArray("clients");
    String clientsKey =
        on + ":" + clients + ":" + known() + ":" + latest.optJSONArray("clientDetails");
    if (clientsKey.equals(renderedClients)) return;
    renderedClients = clientsKey;
    connectedCard.removeAllViews();
    if (!on) text(connectedCard, "Devices appear when your hotspot is on", 14, MUTED, false);
    else if (clients == null || clients.length() == 0)
      text(connectedCard, "No devices connected", 14, MUTED, false);
    else
      for (int i = 0; i < clients.length(); i++) {
        String mac = clients.optString(i);
        if (i > 0) divider(connectedCard);
        row(
            connectedCard,
            "phone",
            deviceName(mac),
            "● Connected"
                + (deviceIdentity(mac).addresses.isEmpty()
                    ? ""
                    : " · " + String.join(", ", deviceIdentity(mac).addresses)),
            () -> editDevice(mac));
      }
  }

  private void openHotspotSettings() {
    if (hotspotEditor != null) hotspotEditor.dismiss();
    hotspotEditor =
        new HotspotSettingsSheet(
            this,
            (cmd, input, done) -> ControllerClient.get(this).request(cmd, input, done),
            () -> settings(this),
            () -> {
              handler.removeCallbacks(refresh);
              handler.post(refresh);
            });
    hotspotEditor.show();
  }

  private JSONObject defaults() {
    try {
      String saved = prefs(this).getString("lastSuccessfulRules", "");
      if (!saved.isEmpty()) return new JSONObject(saved);
      return new JSONObject()
          .put(
              "minutes",
              SessionRules.snapMinutes(Integer.parseInt(prefs(this).getString("minutes", "0"))))
          .put("restricted", prefs(this).getBoolean("restricted", false))
          .put("allowed", new JSONArray(prefs(this).getString("selected", "[]")));
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  private JSONObject appliedRules() {
    try {
      return new JSONObject()
          .put("minutes", latest.optLong("timeLimitMs") / 60000)
          .put("restricted", latest.optBoolean("restricted"))
          .put("allowed", latest.optJSONArray("allowed"));
    } catch (JSONException e) {
      return defaults();
    }
  }

  private void saveDefaults(JSONObject p) {
    try {
      p = new JSONObject(p.toString());
      p.remove("expectedSession");
    } catch (JSONException e) {
      return;
    }
    prefs(this)
        .edit()
        .putString("lastSuccessfulRules", p.toString())
        .putString("minutes", Long.toString(p.optLong("minutes")))
        .putBoolean("restricted", p.optBoolean("restricted"))
        .putString("selected", p.optJSONArray("allowed").toString())
        .apply();
  }

  private void openRules(boolean start, boolean focusTime) {
    if (editor != null && editor.isShowing()) return;
    boolean active = enabled();
    editorSession = latest.optLong("sessionStarted");
    JSONObject initial = active && latest.optBoolean("managed") ? appliedRules() : defaults();
    LinearLayout sheet = column(this);
    pad(sheet, 20, 20);
    sheet.setBackground(shape(this, CARD, 24, false));
    text(sheet, "Session rules", 24, WHITE, true);
    text(
        sheet,
        active ? "Changes apply only when you confirm" : "Saved rules for your next session",
        14,
        MUTED,
        false);
    space(sheet, 16);
    Switch selected = new Switch(this);
    selected.setText("Only selected devices");
    selected.setTextSize(16);
    selected.setTextColor(WHITE);
    selected.setThumbTintList(ColorStateList.valueOf(MINT));
    selected.setChecked(initial.optBoolean("restricted"));
    selected.setMinHeight(d(48));
    sheet.addView(selected);
    text(sheet, "When off, anyone with your hotspot password can connect.", 13, MUTED, false);
    LinearLayout list = column(this);
    sheet.addView(list);
    choices.clear();
    Set<String> allowed = new HashSet<>();
    JSONArray arr = initial.optJSONArray("allowed");
    if (arr != null) for (int i = 0; i < arr.length(); i++) allowed.add(arr.optString(i));
    JSONObject names = known();
    for (String mac : allowed)
      if (!names.has(mac))
        try {
          names.put(mac, mac);
        } catch (JSONException ignored) {
        }
    List<String> macs = new ArrayList<>();
    names.keys().forEachRemaining(macs::add);
    Collections.sort(macs);
    for (String mac : macs) {
      CheckBox cb = new CheckBox(this);
      cb.setText(deviceName(mac));
      cb.setTextColor(WHITE);
      cb.setTextSize(16);
      cb.setMinHeight(d(48));
      cb.setButtonTintList(
          new ColorStateList(
              new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
              new int[] {MINT, MUTED}));
      cb.setChecked(allowed.contains(mac));
      list.addView(cb);
      choices.put(mac, cb);
    }
    if (macs.isEmpty())
      text(list, "No saved devices. Add them in Settings → Known devices.", 14, MUTED, false);
    list.setVisibility(selected.isChecked() ? View.VISIBLE : View.GONE);
    selected.setOnCheckedChangeListener(
        (v, on) -> list.setVisibility(on ? View.VISIBLE : View.GONE));
    space(sheet, 20);
    limitLabel = text(sheet, "", 18, WHITE, true);
    slider = new TimerSlider(this);
    slider.setMax(120);
    slider.setProgressTintList(ColorStateList.valueOf(MINT));
    slider.setThumbTintList(ColorStateList.valueOf(MINT));
    sheet.addView(slider, new LinearLayout.LayoutParams(-1, d(48)));
    text(sheet, "0 = unlimited · linear scale to 120 min", 11, MUTED, false);
    preview = text(sheet, "", 14, MUTED, false);
    editingMinutes = SessionRules.snapMinutes((int) initial.optLong("minutes"));
    slider.setProgress(editingMinutes);
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int v, boolean user) {
            int snapped = SessionRules.snapMinutes(v);
            if (v != snapped) b.setProgress(snapped);
            editingMinutes = snapped;
            updatePreview();
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
    LinearLayout footer = column(this);
    pad(footer, 20, 8);
    Button confirm =
        action(
            footer,
            start ? "Start hotspot" : active ? "Apply changes" : "Save defaults",
            MINT,
            v -> {
              if (busy) return;
              try {
                List<String> addresses = new ArrayList<>();
                for (Map.Entry<String, CheckBox> item : choices.entrySet())
                  if (item.getValue().isChecked()) addresses.add(item.getKey());
                new SessionRules(editingMinutes, selected.isChecked(), addresses);
                JSONObject p =
                    new JSONObject()
                        .put("minutes", editingMinutes)
                        .put("restricted", selected.isChecked())
                        .put("allowed", new JSONArray(addresses));
                if (!start && !active) {
                  if (enabled()) {
                    toast("Hotspot started. Reopen rules to edit this session.");
                    return;
                  }
                  saveDefaults(p);
                  editor.dismiss();
                  show("home");
                  toast("Defaults saved");
                  return;
                }
                if (!start && (!enabled() || latest.optLong("sessionStarted") != editorSession)) {
                  toast("This session ended. Reopen rules for the next session.");
                  return;
                }
                if (!start
                    && latest.optLong("sessionStarted") > 0
                    && editingMinutes > 0
                    && SessionRules.expired(
                        SessionRules.deadline(
                            latest.optLong("sessionStarted"), editingMinutes * 60000L),
                        SystemClock.elapsedRealtime()))
                  new AlertDialog.Builder(this)
                      .setTitle("Stop hotspot now?")
                      .setMessage("This total session limit has already elapsed.")
                      .setNegativeButton("Keep editing", null)
                      .setPositiveButton("Apply and stop", (a, w) -> applyWithPermission(p, 3))
                      .show();
                else applyWithPermission(p, start ? 2 : 3);
              } catch (Exception e) {
                toast(e.getMessage());
              }
            });
    action(footer, "Cancel", LINE, v -> editor.dismiss());
    ScrollView sc = new ScrollView(this);
    sc.addView(sheet);
    LinearLayout container = column(this);
    container.setBackground(shape(this, CARD, 24, false));
    container.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    container.addView(footer);
    container.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(bars.left, 0, bars.right, bars.bottom);
          return insets;
        });
    editor = new Dialog(this);
    editor.setContentView(container);
    Window w = editor.getWindow();
    if (w != null) {
      w.setBackgroundDrawableResource(android.R.color.transparent);
      w.setLayout(-1, -2);
      w.setGravity(Gravity.BOTTOM);
      w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    editor.show();
    if (w != null) w.setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * .85));
    updatePreview();
    if (focusTime) slider.requestFocus();
  }

  private void updatePreview() {
    if (preview == null) return;
    long start = enabled() ? latest.optLong("sessionStarted") : 0,
        now = SystemClock.elapsedRealtime();
    limitLabel.setText(
        editingMinutes == 0
            ? "No time limit"
            : "Total session limit: " + editingMinutes + " minutes");
    long end = SessionRules.deadline(start == 0 ? now : start, editingMinutes * 60000L);
    preview.setText(
        (start > 0
                ? ((now - start) / 60000)
                    + " min elapsed · "
                    + (latest.optBoolean("startObserved") ? "started " : "tracked since ")
                    + clock(start)
                    + "\n"
                : "")
            + (editingMinutes == 0
                ? "Normal idle shutdown still applies"
                : start > 0 && end <= now
                    ? "Will stop immediately when applied"
                    : "If applied, ends at " + clock(end) + (start == 0 ? " if started now" : "")));
    slider.setContentDescription(
        "Total session limit: "
            + (editingMinutes == 0 ? "unlimited" : editingMinutes + " minutes"));
  }

  private void applyWithPermission(JSONObject p, int cmd) {
    if (cmd == 3) {
      try {
        p.put("expectedSession", editorSession);
      } catch (JSONException e) {
        return;
      }
    }
    withNotifications(
        () ->
            command(
                cmd,
                p,
                () -> {
                  if (editor != null) editor.dismiss();
                }));
  }

  private void command(int cmd, JSONObject p, Runnable success) {
    if (busy) return;
    busy = true;
    if (page.equals("home")) updateHome();
    ControllerClient.get(this)
        .request(
            cmd,
            p,
            o -> {
              busy = false;
              if (o.optBoolean("ok")) {
                if (cmd == 2 || cmd == 3) saveDefaults(p);
                latest = o;
                SessionService.acknowledge(this);
                if (success != null) success.run();
                if (page.equals("home")) show("home");
                if (cmd == 2 || cmd == 3 || prefs(this).getBoolean("watch", false))
                  startForegroundService(
                      new Intent(this, SessionService.class)
                          .putExtra("sessionOnly", cmd == 2 || cmd == 3));
                toast(cmd == 4 ? "Stop requested" : "Rules applied");
              } else {
                new AlertDialog.Builder(this)
                    .setTitle("Rules were not confirmed")
                    .setMessage(
                        o.optString("error") + "\nExisting rules remain. Check Troubleshooting.")
                    .setPositiveButton("OK", null)
                    .show();
                if (page.equals("home")) updateHome();
              }
            });
  }

  private void settingsPage() {
    heading("Devices", "");
    LinearLayout devices = card(root);
    row(
        devices,
        "devices",
        "Known devices",
        known().length() + " saved devices",
        () -> show("devices"));
    heading("Automatic detection", "");
    LinearLayout detect = card(root);
    Switch watch = new Switch(this);
    watch.setText("Detect hotspots started elsewhere");
    watch.setTextColor(WHITE);
    watch.setTextSize(16);
    watch.setMinHeight(d(56));
    watch.setChecked(prefs(this).getBoolean("watch", false));
    watch.setThumbTintList(ColorStateList.valueOf(MINT));
    detect.addView(watch);
    text(detect, "Apply saved rules when Android starts the hotspot.", 14, MUTED, false);
    divider(detect);
    text(
        detect,
        "Keeps a quiet background service running. Turn off if you always start from Keeper.",
        14,
        MUTED,
        false);
    watch.setOnCheckedChangeListener(
        (v, on) -> {
          if (on) {
            pendingWatch = true;
            withNotifications(
                () -> {
                  prefs(this).edit().putBoolean("watch", true).apply();
                  startForegroundService(new Intent(this, SessionService.class));
                  pendingWatch = false;
                });
          } else {
            pendingWatch = false;
            prefs(this).edit().putBoolean("watch", false).apply();
            stopService(new Intent(this, SessionService.class));
          }
        });
    heading("Hotspot control", "");
    LinearLayout control = card(root);
    connectionInfo =
        row(
            control,
            "shield",
            "Shizuku connection",
            ready() ? "● Connected" : ControllerClient.get(this).availability(),
            () -> show("connection"));
    divider(control);
    text(control, "Required for device restrictions and timed shutdown.", 14, MUTED, false);
    heading("Support", "");
    LinearLayout support = card(root);
    row(support, "book", "Setup & help", "Getting started and everyday use", () -> show("help"));
    divider(support);
    row(
        support,
        "restart",
        "Troubleshooting",
        "Connection and notification issues",
        () -> show("trouble"));
    divider(support);
    row(support, "pulse", "Diagnostics", "Technical details", this::diagnostics);
    space(root, 18);
    TextView version = text(root, "Keeper · version 0.3.2", 12, MUTED, false);
    version.setGravity(Gravity.CENTER);
  }

  private void connectionPage() {
    LinearLayout c = card(root);
    connectionInfo =
        row(c, "shield", "Shizuku connection", ControllerClient.get(this).availability(), null);
    space(c, 12);
    text(
        c,
        "Shizuku supplies the elevated Android access needed to control your normal hotspot. No"
            + " root is required.",
        15,
        MUTED,
        false);
    action(
        c,
        "Connect / authorise",
        MINT,
        v -> {
          try {
            toast(ControllerClient.get(this).authorize());
          } catch (RuntimeException e) {
            toast(e.getMessage());
            launchShizuku();
          }
        });
    action(c, "Open Shizuku", LINE, v -> launchShizuku());
    heading("Need a hand?", "");
    row(
        card(root),
        "book",
        "Setup guide",
        "Pairing, permissions and reboot recovery",
        () -> show("help"));
  }

  private void launchShizuku() {
    Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
    if (launch != null) startActivity(launch);
    else
      new AlertDialog.Builder(this)
          .setTitle("Install Shizuku")
          .setMessage("Shizuku is not installed. Open the official download page?")
          .setNegativeButton("Cancel", null)
          .setPositiveButton(
              "Open website", (a, w) -> openUrl("https://shizuku.rikka.app/download/"))
          .show();
  }

  private void openUrl(String url) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (ActivityNotFoundException e) {
      toast("No app can open this link");
    }
  }

  private void expand(
      LinearLayout parent,
      String title,
      String summary,
      String detail,
      String button,
      Runnable task) {
    LinearLayout c = card(parent);
    LinearLayout body = column(this);
    body.setVisibility(View.GONE);
    row(
        c,
        "book",
        title,
        summary,
        () -> body.setVisibility(body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    space(body, 12);
    text(body, detail, 15, MUTED, false);
    if (task != null) action(body, button, LINE, v -> task.run());
    c.addView(body);
    space(parent, 10);
  }

  private void help() {
    text(root, "A little setup. Safer hotspot sessions.", 16, MUTED, false);
    space(root, 18);
    LinearLayout why = card(root);
    row(why, "shield", "Why Shizuku?", "", null);
    text(
        why,
        "Android restricts hotspot controls. Shizuku gives Keeper ADB shell access for device rules"
            + " and timed shutdown. No root needed. This access is broader than a hotspot-only"
            + " permission: authorise apps you trust.",
        15,
        MUTED,
        false);
    heading("First-time setup", "Complete these steps in order");
    expand(
        root,
        "1  Install Shizuku",
        "Install the official app.",
        "Get Shizuku from its official download page, install it and open it. Keeper uses the"
            + " running Shizuku service; installing it alone does not start the service.",
        "Official download",
        () -> openUrl("https://shizuku.rikka.app/download/"));
    expand(
        root,
        "2  Enable Developer options",
        "Tap Build number 7 times in About phone.",
        "On Pixel: Settings → About phone → Build number. Tap seven times, then enter your phone"
            + " PIN if asked. Developer options appears under Settings → System. Enable Wireless"
            + " debugging there. Android generally requires a Wi-Fi network for wireless-debugging"
            + " setup.",
        "Developer options",
        () -> launchSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
    expand(
        root,
        "3  Pair & start Shizuku",
        "Use Wireless debugging and a pairing code.",
        "Connect to Wi-Fi. Open Shizuku → Pairing and follow its guide. In Android Wireless"
            + " debugging choose Pair device with pairing code. Enter that code using Shizuku's"
            + " pairing notification (split screen can help). Return to Shizuku and tap Start."
            + " Confirm it says Shizuku is running. Pairing and starting are separate steps; you do"
            + " not need root or a computer for this method.",
        "Open Shizuku",
        this::launchShizuku);
    expand(
        root,
        "4  Connect Keeper",
        "Authorise, allow notifications and save your rules.",
        "Open Keeper → Settings → Shizuku connection → Connect / authorise, then approve Keeper."
            + " Allow notifications. Save your device access and time limit. Start a hotspot in"
            + " Keeper to review and apply them. Enable Detect hotspots started elsewhere only if"
            + " you want automatic handling of Android's hotspot switch.",
        "Connect Keeper",
        () -> show("connection"));
    LinearLayout reboot = card(root);
    text(reboot, "After restarting your phone", 17, AMBER, true);
    space(reboot, 6);
    text(
        reboot,
        "Start Shizuku again, then open Keeper to reconnect. Pairing is usually retained, but the"
            + " privileged service must be started again. After force-stopping Keeper, reopen it to"
            + " rearm automatic detection.",
        15,
        MUTED,
        false);
    heading("Everyday help", "");
    expand(
        root,
        "Timers & permitted devices",
        "How rules and total session limits work.",
        "The limit is total session time, not time from the last edit. Changing 10 minutes to 20"
            + " adds ten minutes to the end time; editing device access does not reset it. Zero"
            + " means no Keeper time limit; Android's normal idle shutdown still applies. Selected"
            + " devices uses Wi-Fi addresses, which can change with client private-address"
            + " settings. Remember and rename devices in Settings. Saved defaults are distinct from"
            + " applied session rules.",
        "",
        null);
    expand(
        root,
        "Notifications & background detection",
        "Why the quiet watcher is needed.",
        "A detected session raises one vibrating confirmation alert; saved rules apply immediately."
            + " Open Keeper to acknowledge it. Timed cutoff raises a separate tap-to-open alert."
            + " Android notification settings, Do Not Disturb and battery restrictions affect"
            + " delivery. The quiet foreground watcher keeps event listeners available for hotspots"
            + " started outside Keeper. Turning it off does not cancel an active session's device"
            + " rules or timer.",
        "Notification settings",
        this::notificationSettings);
    row(card(root), "restart", "Need more help?", "Open Troubleshooting", () -> show("trouble"));
  }

  private void troubleshooting() {
    expand(
        root,
        "Control unavailable",
        "Check Shizuku first.",
        "Open Shizuku and confirm the service is running. Return to Keeper, authorise access and"
            + " wait for Control ready. Restart Shizuku after a phone reboot. If a rule operation"
            + " fails, existing rules are retained; inspect Diagnostics before retrying.",
        "Open Shizuku",
        this::launchShizuku);
    expand(
        root,
        "No session notification",
        "Check detection and Android preferences.",
        "Enable Detect hotspots started elsewhere, allow Keeper notifications and check the Session"
            + " confirmation and cutoff channel. Disable Do Not Disturb if appropriate. Open Keeper"
            + " after force-stop. Starting inside Keeper does not enable detection for other"
            + " starts.",
        "Notification settings",
        this::notificationSettings);
    expand(
        root,
        "Device cannot connect",
        "Check the selected address.",
        "Check that the device is selected and uses the saved Wi-Fi address. Some devices rotate"
            + " private addresses. If your phone reports no allowlist support, Keeper will reject"
            + " restricted rules rather than pretend they are enforced.",
        "Known devices",
        () -> show("devices"));
    row(card(root), "pulse", "Diagnostics", "View or copy controller details", this::diagnostics);
    space(root, 12);
    action(root, "Android hotspot settings", LINE, v -> settings(this));
  }

  private void diagnostics() {
    String detail = latest.toString();
    new AlertDialog.Builder(this)
        .setTitle("Controller diagnostics")
        .setMessage(detail)
        .setPositiveButton("Close", null)
        .setNeutralButton(
            "Copy",
            (a, w) -> {
              getSystemService(android.content.ClipboardManager.class)
                  .setPrimaryClip(ClipData.newPlainText("Keeper diagnostics", detail));
              toast("Copied diagnostics");
            })
        .show();
  }

  private void notificationSettings() {
    try {
      startActivity(
          new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
              .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    } catch (ActivityNotFoundException e) {
      launchSettings(Settings.ACTION_SETTINGS);
    }
  }

  private void launchSettings(String action) {
    try {
      startActivity(new Intent(action));
    } catch (ActivityNotFoundException e) {
      toast("Open this setting in Android Settings");
    }
  }

  private JSONObject known() {
    try {
      return new JSONObject(prefs(this).getString("known", "{}"));
    } catch (JSONException e) {
      return new JSONObject();
    }
  }

  private void devices() {
    LinearLayout list = card(root);
    JSONObject names = known();
    if (names.length() == 0)
      text(
          list,
          "No saved devices yet. Connect devices once using password access, then remember them.",
          15,
          MUTED,
          false);
    List<String> macs = new ArrayList<>();
    names.keys().forEachRemaining(macs::add);
    Collections.sort(macs);
    for (String mac : macs) row(list, "phone", deviceName(mac), mac, () -> editDevice(mac));
    action(root, "Remember connected devices", MINT, v -> remember());
    action(root, "Add Wi-Fi address", LINE, v -> editDevice(null));
    text(
        root,
        "Tap a device to rename or remove it. Changes to saved names do not alter live access"
            + " rules.",
        13,
        MUTED,
        false);
  }

  private void remember() {
    JSONArray clients = latest.optJSONArray("clients");
    if (clients == null || clients.length() == 0) {
      toast("No connected devices reported");
      return;
    }
    try {
      JSONObject names = known();
      for (int i = 0; i < clients.length(); i++) {
        String mac = clients.getString(i).toLowerCase(Locale.ROOT);
        if (!names.has(mac)) names.put(mac, "");
      }
      prefs(this).edit().putString("known", names.toString()).apply();
      show("devices");
    } catch (JSONException e) {
      toast("Device list unavailable");
    }
  }

  private void editDevice(String existing) {
    LinearLayout form = column(this);
    pad(form, 20, 12);
    EditText name = new EditText(this), address = new EditText(this);
    name.setTextColor(WHITE);
    name.setHintTextColor(MUTED);
    name.setHint("Your friendly name (optional)");
    name.setSingleLine();
    address.setTextColor(WHITE);
    address.setHintTextColor(MUTED);
    address.setHint("Wi-Fi address: aa:bb:cc:dd:ee:ff");
    address.setSingleLine();
    form.addView(name);
    form.addView(address);
    if (existing != null) {
      DeviceIdentity identity = deviceIdentity(existing);
      String savedName = known().optString(existing);
      name.setText(savedName.equalsIgnoreCase(existing) ? "" : savedName);
      text(
          form,
          "Reported name: " + (identity.hostname.isEmpty() ? "Not provided" : identity.hostname),
          14,
          MUTED,
          false);
      text(
          form,
          "IP addresses: "
              + (identity.addresses.isEmpty()
                  ? "Unavailable"
                  : String.join(", ", identity.addresses)),
          14,
          MUTED,
          false);
      text(form, "Reported details are best-effort and do not determine access.", 12, MUTED, false);
      address.setText(existing);
      address.setEnabled(false);
    }
    AlertDialog.Builder builder =
        new AlertDialog.Builder(this)
            .setTitle(existing == null ? "Add device" : "Device details")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null);
    if (existing != null)
      builder.setNeutralButton(
          "Remove",
          (a, w) ->
              new AlertDialog.Builder(this)
                  .setTitle("Remove saved device?")
                  .setMessage(
                      "This removes its name and selection from next-session defaults. Current"
                          + " session rules remain unchanged.")
                  .setNegativeButton("Cancel", null)
                  .setPositiveButton(
                      "Remove",
                      (x, y) -> {
                        JSONObject names = known();
                        names.remove(existing);
                        prefs(this).edit().putString("known", names.toString()).apply();
                        try {
                          JSONObject rules = defaults();
                          JSONArray old = rules.optJSONArray("allowed"), kept = new JSONArray();
                          if (old != null)
                            for (int i = 0; i < old.length(); i++)
                              if (!existing.equals(old.optString(i))) kept.put(old.optString(i));
                          rules.put("allowed", kept);
                          // Preserve selected-only intent even if all saved selections were
                          // removed.
                          saveDefaults(rules);
                        } catch (JSONException ignored) {
                        }
                        show(page);
                      })
                  .show());
    AlertDialog dialog = builder.create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    w -> {
                      try {
                        String mac = address.getText().toString().trim().toLowerCase(Locale.ROOT);
                        new SessionRules(0, true, Collections.singletonList(mac));
                        JSONObject names = known();
                        String label = name.getText().toString().trim();
                        names.put(mac, DeviceIdentity.cleanName(label));
                        prefs(this).edit().putString("known", names.toString()).apply();
                        dialog.dismiss();
                        show(page);
                      } catch (Exception e) {
                        address.setError("Enter a valid Wi-Fi MAC address");
                      }
                    }));
    dialog.show();
  }

  private boolean notificationAllowed() {
    return (Build.VERSION.SDK_INT < 33
            || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == getPackageManager().PERMISSION_GRANTED)
        && getSystemService(NotificationManager.class).areNotificationsEnabled();
  }

  private void withNotifications(Runnable next) {
    if (notificationAllowed()) {
      next.run();
      return;
    }
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != getPackageManager().PERMISSION_GRANTED) {
      afterPermission = next;
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1);
    } else {
      pendingWatch = false;
      toast("Allow Keeper notifications in Android Settings");
      notificationSettings();
    }
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(r, permissions, results);
    Runnable next = afterPermission;
    afterPermission = null;
    if (r == 1 && notificationAllowed() && next != null) next.run();
    else {
      pendingWatch = false;
      toast("Notifications are needed for session alerts");
    }
    if (page.equals("settings")) show("settings");
  }

  private void toast(String s) {
    Toast.makeText(this, s == null ? "Unable to complete action" : s, Toast.LENGTH_LONG).show();
  }

  static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("session", 0);
  }

  static void settings(Context c) {
    for (String action :
        new String[] {
          "com.android.settings.WIFI_TETHER_SETTINGS",
          "android.settings.TETHER_SETTINGS",
          Settings.ACTION_WIRELESS_SETTINGS,
          Settings.ACTION_SETTINGS
        }) {
      try {
        c.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return;
      } catch (ActivityNotFoundException | SecurityException ignored) {
        /* OEMs expose different entries. */
      }
    }
    Toast.makeText(c, "Open hotspot settings in Android Settings", Toast.LENGTH_LONG).show();
  }

  private void back() {
    show(page.equals("settings") ? "home" : page.equals("home") ? "home" : "settings");
  }

  @Override
  public void onBackPressed() {
    if (page.equals("home")) super.onBackPressed();
    else back();
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    SessionService.acknowledge(this);
    handler.removeCallbacks(refresh);
    handler.post(refresh);
  }

  @Override
  protected void onPause() {
    resumed = false;
    handler.removeCallbacks(refresh);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (editor != null) editor.dismiss();
    if (hotspotEditor != null) hotspotEditor.dismiss();
    super.onDestroy();
  }
}
