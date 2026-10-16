package uk.me.theflat.hotspot;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import org.json.*;

public final class SessionService extends Service {
  private final Handler handler = new Handler();
  private boolean alive = true, lastEnabled = false, wasConnected = false, inFlight = false;
  private boolean foregroundStarted = false;
  private String previousError = "", monitorKey = "";
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
    if (!foregroundStarted) {
      foregroundStarted = true;
      startForeground(
          1, notification(this, "Hotspot Keeper", "Background monitoring enabled", false));
    }
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
      if (enabled && !managed && !o.optBoolean("stopping")) {
        long session = o.optLong("sessionStarted");
        android.content.SharedPreferences prefs = MainActivity.prefs(this);
        String rules = prefs.getString("lastSuccessfulRules", "");
        if (session > 0 && !rules.isEmpty() && prefs.getLong("autoAttemptSession", -1) != session) {
          prefs.edit().putLong("autoAttemptSession", session).apply();
          try {
            inFlight = true;
            lastEnabled = true;
            ControllerClient.get(this)
                .request(
                    3,
                    new JSONObject(rules),
                    result -> {
                      inFlight = false;
                      if (!alive) return;
                      if (!result.optBoolean("ok"))
                        alert(
                            this,
                            "Hotspot on — rules not applied",
                            result.optString("error") + ". Tap to change rules or retry.",
                            5);
                      observed(result.optBoolean("ok") ? result : o);
                    });
            return;
          } catch (JSONException e) {
            inFlight = false;
            alert(
                this,
                "Hotspot on — rules not applied",
                "Saved rules unreadable. Tap to set rules.",
                5);
          }
        }
      }
      if (!enabled) getSystemService(NotificationManager.class).cancel(2);
      boolean justStopped = lastEnabled && !enabled;
      lastEnabled = enabled;
      wasConnected = true;
      String reason = o.optString("reason"), error = o.optString("error");
      if (justStopped
          && (reason.equals("Timer reached")
              || reason.equals("Data allowance reached")
              || reason.equals("Data meter unavailable—stopping protected session")))
        alert(this, "Keeper stopped hotspot", reason + ".", 4);
      if (!error.isEmpty() && !error.equals(previousError))
        alert(this, "Hotspot needs attention", error, 5);
      previousError = error;
      long deadline = o.optLong("deadline"),
          used = o.optLong("used", -1),
          cap = o.optLong("capBytes");
      String detail =
          !enabled
              ? "Background monitoring enabled"
              : managed
                  ? "Rules active · "
                      + (deadline == 0
                          ? "no timer"
                          : (SessionRules.remaining(deadline, SystemClock.elapsedRealtime())
                                      + 59999)
                                  / 60000
                              + " min left")
                      + " · tap to change rules · "
                      + (used < 0
                          ? "usage unavailable"
                          : used / 1048576 + " MB" + (cap > 0 ? " / " + cap / 1048576 + " MB" : ""))
                  : "Hotspot on · no rules applied · tap to set rules";
      updateMonitor("Hotspot Keeper", detail);
    } else {
      String e = o.optString("error", ControllerClient.get(this).availability());
      updateMonitor("Hotspot Keeper · control unavailable", e);
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

  private void updateMonitor(String title, String detail) {
    String key = title + "\n" + detail;
    if (key.equals(monitorKey)) return;
    monitorKey = key;
    getSystemService(NotificationManager.class).notify(1, notification(this, title, detail, false));
  }

  static void channels(Context c) {
    NotificationManager n = c.getSystemService(NotificationManager.class);
    n.createNotificationChannel(
        new NotificationChannel(
            "idle", "Background monitoring", NotificationManager.IMPORTANCE_MIN));
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
    boolean idle = !alert && text.equals("Background monitoring enabled");
    Notification.Builder builder =
        new Notification.Builder(c, alert ? "alerts" : idle ? "idle" : "monitor")
            .setSmallIcon(R.drawable.ic_stat_keeper)
            .setLargeIcon(
                android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_launcher_legacy))
            .setColor(c.getColor(R.color.keeper_icon_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(edit)
            .setOnlyAlertOnce(!alert)
            .setOngoing(!alert)
            .setAutoCancel(alert);
    if (!idle)
      builder
          .addAction(new Notification.Action.Builder(null, "Change rules", edit).build())
          .addAction(new Notification.Action.Builder(null, "Stop hotspot", stop).build())
          .addAction(new Notification.Action.Builder(null, "Android settings", settings).build());
    if (idle) builder.setLargeIcon((android.graphics.Bitmap) null);
    return builder.build();
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
