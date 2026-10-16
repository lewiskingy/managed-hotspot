package uk.me.theflat.hotspot;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import org.json.*;

public final class SessionService extends Service {
  private final Handler handler = new Handler();
  private boolean alive = true, lastEnabled = false, wasConnected = false, inFlight = false;
  private String previousError = "", previousReason = "";
  private final BroadcastReceiver changed =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          handler.removeCallbacks(poll);
          handler.post(poll); /* Verify actual controller state; never trust broadcast extras. */
        }
      };
  private final Runnable poll =
      () -> {
        if (!inFlight) {
          inFlight = true;
          ControllerClient.get(this).request(1, new JSONObject(), this::observed);
        }
      };

  @Override
  public void onCreate() {
    super.onCreate();
    registerReceiver(
        changed,
        new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"),
        Context.RECEIVER_EXPORTED);
  }

  @Override
  public int onStartCommand(Intent i, int flags, int id) {
    channels(this);
    if (!MainActivity.prefs(this).getBoolean("watch", false)) {
      stopSelf();
      return START_NOT_STICKY;
    }
    startForeground(
        1,
        notification(
            this,
            "Hotspot Keeper is watching",
            "Waiting for hotspot · tap to set session rules",
            false));
    handler.removeCallbacks(poll);
    handler.post(poll);
    return START_STICKY;
  }

  private void observed(JSONObject o) {
    inFlight = false;
    if (!alive) return;
    boolean ready = o.optBoolean("ready") && o.optBoolean("ok");
    if (ready) {
      boolean enabled = o.optBoolean("enabled"), managed = o.optBoolean("managed");
      if (enabled && !lastEnabled && !managed)
        alert(
            this,
            "Hotspot enabled · set session rules",
            "Choose permitted devices, timer and data allowance. No Keeper rules are active yet.",
            2);
      if (!enabled) getSystemService(NotificationManager.class).cancel(2);
      lastEnabled = enabled;
      wasConnected = true;
      String reason = o.optString("reason"), error = o.optString("error");
      if (!reason.isEmpty() && !reason.equals(previousReason))
        alert(this, "Hotspot stop requested", reason + ". Tap to verify hotspot state.", 4);
      previousReason = reason;
      if (!error.isEmpty() && !error.equals(previousError))
        alert(this, "Hotspot needs attention", error, 5);
      previousError = error;
      long deadline = o.optLong("deadline"),
          used = o.optLong("used", -1),
          cap = o.optLong("capBytes");
      String detail =
          !enabled
              ? "Hotspot off"
              : managed
                  ? "Rules active · "
                      + (deadline == 0
                          ? "no timer"
                          : (SessionRules.remaining(deadline, SystemClock.elapsedRealtime())
                                      + 59999)
                                  / 60000
                              + " min left")
                      + " · "
                      + (used < 0
                          ? "usage unavailable"
                          : used / 1048576 + " MB" + (cap > 0 ? " / " + cap / 1048576 + " MB" : ""))
                  : "Hotspot on · session rules pending";
      getSystemService(NotificationManager.class)
          .notify(1, notification(this, "Hotspot Keeper", detail, false));
    } else {
      String e = o.optString("error", ControllerClient.get(this).availability());
      getSystemService(NotificationManager.class)
          .notify(1, notification(this, "Hotspot Keeper · control unavailable", e, false));
      if (wasConnected) {
        alert(
            this,
            "Hotspot control lost",
            "Automatic cutoff cannot be guaranteed. Check hotspot Settings. Device rules already"
                + " applied may remain.",
            5);
        wasConnected = false;
      }
    }
    handler.postDelayed(poll, 3000);
  }

  static void channels(Context c) {
    NotificationManager n = c.getSystemService(NotificationManager.class);
    n.createNotificationChannel(
        new NotificationChannel("monitor", "Hotspot watcher", NotificationManager.IMPORTANCE_LOW));
    n.createNotificationChannel(
        new NotificationChannel(
            "alerts", "Hotspot session alerts", NotificationManager.IMPORTANCE_HIGH));
  }

  static Notification notification(Context c, String title, String text, boolean alert) {
    PendingIntent edit =
        PendingIntent.getActivity(
            c,
            0,
            new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent settings =
        PendingIntent.getActivity(
            c,
            1,
            new Intent(Settings.ACTION_WIRELESS_SETTINGS),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop =
        PendingIntent.getService(
            c,
            3,
            new Intent(c, StopService.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(c, alert ? "alerts" : "monitor")
        .setSmallIcon(R.drawable.ic_stat_keeper)
        .setLargeIcon(
            android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_launcher_legacy))
        .setColor(c.getColor(R.color.keeper_icon_accent))
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(new Notification.BigTextStyle().bigText(text))
        .setContentIntent(edit)
        .setOngoing(!alert)
        .setAutoCancel(alert)
        .addAction(new Notification.Action.Builder(null, "Set rules", edit).build())
        .addAction(new Notification.Action.Builder(null, "Stop hotspot", stop).build())
        .addAction(new Notification.Action.Builder(null, "Android settings", settings).build())
        .build();
  }

  static void alert(Context c, String title, String text, int id) {
    channels(c);
    c.getSystemService(NotificationManager.class).notify(id, notification(c, title, text, true));
  }

  @Override
  public IBinder onBind(Intent i) {
    return null;
  }

  @Override
  public void onDestroy() {
    alive = false;
    handler.removeCallbacksAndMessages(null);
    unregisterReceiver(changed);
    super.onDestroy();
  }
}
