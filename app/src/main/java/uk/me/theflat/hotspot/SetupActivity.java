package uk.me.theflat.hotspot;

import static uk.me.theflat.hotspot.KeeperUi.*;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;

/** Foreground-only, resumable guidance. Never changes protected settings or starts tethering. */
public final class SetupActivity extends Activity {
  private int step;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable refresh =
      new Runnable() {
        public void run() {
          render();
          handler.postDelayed(this, 1500);
        }
      };
  private LinearLayout body;
  private TextView status;
  private static final String[] TITLES = {
    "Welcome to Keeper",
    "Install Shizuku",
    "Enable Developer options",
    "Connect to Wi-Fi",
    "Enable wireless debugging",
    "Pair and start Shizuku",
    "Connect Keeper",
    "You're ready"
  };

  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    step =
        saved == null ? MainActivity.prefs(this).getInt("setupStep", 0) : saved.getInt("step", 0);
    step = Math.max(0, Math.min(7, step));
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(BG);
    body = column(this);
    body.setPadding(dp(this, 24), dp(this, 24), dp(this, 24), dp(this, 32));
    scroll.addView(body);
    setContentView(scroll);
    scroll.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets i = insets.getInsets(android.view.WindowInsets.Type.systemBars());
          v.setPadding(i.left, i.top, i.right, i.bottom);
          return insets;
        });
    render();
  }

  protected void onResume() {
    super.onResume();
    handler.post(refresh);
  }

  protected void onPause() {
    handler.removeCallbacks(refresh);
    super.onPause();
  }

  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putInt("step", step);
  }

  private void copy(String s, int size, int color) {
    TextView t = text(this, s, size, color, false);
    t.setPadding(0, dp(this, 10), 0, dp(this, 10));
    body.addView(t);
  }

  private void button(String label, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextColor(WHITE);
    touch(b, CARD, 16);
    b.setOnClickListener(v -> action.run());
    body.addView(b, new LinearLayout.LayoutParams(-1, dp(this, 56)));
  }

  private void open(String action) {
    try {
      Intent intent = new Intent(action);
      if (action.equals(Settings.ACTION_APP_NOTIFICATION_SETTINGS))
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
      startActivity(intent);
    } catch (RuntimeException e) {
      Toast.makeText(this, "Open Android Settings manually", Toast.LENGTH_LONG).show();
    }
  }

  private void shizuku() {
    Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
    if (i != null) startActivity(i);
    else web("https://shizuku.rikka.app/download/");
  }

  private void web(String url) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (RuntimeException e) {
      Toast.makeText(this, url, Toast.LENGTH_LONG).show();
    }
  }

  private boolean installed() {
    try {
      getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
      return true;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
  }

  private String stepStatus() {
    if (step == 1) return installed() ? "Shizuku installed" : "Shizuku is not installed yet";
    if (step == 2 || step == 4) {
      try {
        String key = step == 2 ? Settings.Global.DEVELOPMENT_SETTINGS_ENABLED : "adb_wifi_enabled";
        return Settings.Global.getInt(getContentResolver(), key, 0) == 1
            ? "Enabled · continue when ready"
            : "Enable this in Android Settings, then continue";
      } catch (RuntimeException e) {
        return "Confirm this step in Android Settings";
      }
    }
    if (step == 3) {
      android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
      for (android.net.Network n : cm.getAllNetworks()) {
        android.net.NetworkCapabilities c = cm.getNetworkCapabilities(n);
        if (c != null && c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI))
          return "Wi-Fi connected";
      }
      return "Connect to trusted Wi-Fi before pairing";
    }
    return ControllerClient.get(this).availability();
  }

  private void next() {
    step = Math.min(7, step + 1);
    MainActivity.prefs(this).edit().putInt("setupStep", step).apply();
    render();
  }

  private void render() {
    // Do not rebuild focused controls during a permission dialog or while reading instructions.
    if (body.getChildCount() > 0 && body.getTag() != null && ((Integer) body.getTag()) == step) {
      if (status != null) status.setText(stepStatus());
      return;
    }
    body.removeAllViews();
    body.setTag(step);
    status = null;
    copy("SETUP · " + (step + 1) + " OF 8", 12, MINT);
    copy(TITLES[step], 28, WHITE);
    switch (step) {
      case 0:
        copy(
            "Free, open source and ad-free. Keeper protects your existing hotspot with permitted"
                + " devices and a total session timer. It does not measure data usage.",
            17,
            MUTED);
        copy(
            "Android restricts hotspot management. Shizuku runs a separate helper with Android's"
                + " debugging (shell) privileges, without root. Grant access only to apps you"
                + " trust. Keeper cannot bundle those privileges into its APK.",
            16,
            MUTED);
        button("Read privacy information", () -> AppInformation.privacy(this));
        break;
      case 1:
        copy(
            "Install the official Shizuku app. Return here after installation. Keeper does not"
                + " download or install another APK itself.",
            17,
            MUTED);
        button("Get Shizuku", () -> web("https://shizuku.rikka.app/download/"));
        button("Open Shizuku", this::shizuku);
        break;
      case 2:
        copy(
            "On Pixel: Settings → About phone → Build number. Tap Build number seven times and"
                + " confirm your screen lock. Then return here. Other phones may place Build number"
                + " under Software information.",
            17,
            MUTED);
        button("Open About phone", () -> open(Settings.ACTION_DEVICE_INFO_SETTINGS));
        button(
            "Open Developer options", () -> open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        break;
      case 3:
        copy(
            "Connect this phone to a trusted Wi-Fi network for wireless debugging setup. Mobile"
                + " data or enabling your hotspot alone is not the same as connecting to Wi-Fi."
                + " Wi-Fi is normally only needed to pair/start Shizuku, not continuously after it"
                + " is running.",
            17,
            MUTED);
        button("Open Wi-Fi settings", () -> open(Settings.ACTION_WIFI_SETTINGS));
        break;
      case 4:
        copy(
            "In Developer options, turn on Wireless debugging and approve your trusted network."
                + " Android requires you to do this yourself. You do not need to enable USB"
                + " debugging for this wireless method.",
            17,
            MUTED);
        button(
            "Open Developer options", () -> open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        break;
      case 5:
        copy(
            "In Shizuku, tap Pairing. In Android's Wireless debugging screen choose Pair device"
                + " with pairing code, then enter that code through Shizuku's pairing notification."
                + " Return to Shizuku and tap Start. Wait until it says Shizuku is running. Pairing"
                + " alone does not start it.",
            17,
            MUTED);
        button("Open Shizuku", this::shizuku);
        button("Official step-by-step guide", () -> web("https://shizuku.rikka.app/guide/setup/"));
        break;
      case 6:
        copy(
            "Tap Authorise and allow Hotspot Keeper in Shizuku's prompt. Allow notifications so"
                + " Keeper can alert you when a session starts or its timer stops it. You can"
                + " revisit denied permissions in Android Settings.",
            17,
            MUTED);
        button(
            "Authorise Keeper",
            () -> {
              try {
                Toast.makeText(this, ControllerClient.get(this).authorize(), Toast.LENGTH_LONG)
                    .show();
              } catch (RuntimeException e) {
                Toast.makeText(this, "Start Shizuku first", Toast.LENGTH_LONG).show();
              }
            });
        button(
            "Allow notifications",
            () -> {
              if (Build.VERSION.SDK_INT >= 33
                  && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                      != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 21);
              else open(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            });
        break;
      case 7:
        copy(
            "Choose Session rules on the home page before starting. Saving rules does not start a"
                + " hotspot. Automatic detection of hotspots started outside Keeper is optional in"
                + " Settings.",
            17,
            MUTED);
        copy(
            "After a phone reboot you normally need to start Shizuku again; pairing is usually"
                + " remembered. If control stops working, reopen this guide. Android and Shizuku"
                + " can restrict debugging on some devices.",
            17,
            MUTED);
        break;
    }
    status = text(this, stepStatus(), 15, MINT, false);
    status.setPadding(0, dp(this, 18), 0, dp(this, 18));
    body.addView(status);
    button(
        step == 7 ? "Finish setup" : "Continue",
        () -> {
          if (step == 1 && !installed()) {
            Toast.makeText(this, "Install Shizuku before continuing", Toast.LENGTH_LONG).show();
            return;
          }
          if (step == 5 && !rikka.shizuku.Shizuku.pingBinder()) {
            Toast.makeText(this, "Start Shizuku before continuing", Toast.LENGTH_LONG).show();
            return;
          }
          if (step == 6 && !ControllerClient.get(this).connected()) {
            Toast.makeText(
                    this,
                    "Authorise Keeper and wait for the controller to connect first",
                    Toast.LENGTH_LONG)
                .show();
            return;
          }
          if (step == 7) {
            MainActivity.prefs(this).edit().putBoolean("setupComplete", true).apply();
            finish();
          } else next();
        });
    if (step > 0)
      button(
          "Back",
          () -> {
            step--;
            render();
          });
    button("Set up later", this::finish);
  }
}
