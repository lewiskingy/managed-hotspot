package uk.me.theflat.hotspot;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.os.*;
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
    if (i != null && i.getBooleanExtra("sessionOnly", false))
      MainActivity.prefs(this).edit().putBoolean("activeMonitor", true).apply();
    if (!MainActivity.prefs(this).getBoolean("watch", false)
        && !MainActivity.prefs(this).getBoolean("activeMonitor", false)) {
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
    if (i != null && i.getBooleanExtra("acknowledge", false)) {
      MainActivity.prefs(this)
          .edit()
          .putLong("ackSession", MainActivity.prefs(this).getLong("noticeSession", -1))
          .apply();
      getSystemService(NotificationManager.class).cancel(2);
    }
    return START_STICKY;
  }

  private void observed(JSONObject o) {
    inFlight = false;
    if (!alive) return;
    boolean ready = o.optBoolean("ready") && o.optBoolean("ok");
    if (ready) {
      boolean enabled = o.optBoolean("enabled"), managed = o.optBoolean("managed");
      long activeSession = o.optLong("sessionStarted");
      android.content.SharedPreferences sessionPrefs = MainActivity.prefs(this);
      if (enabled
          && MainActivity.prefs(this).getBoolean("watch", false)
          && activeSession > 0
          && sessionPrefs.getLong("noticeSession", -1) != activeSession) {
        sessionPrefs.edit().putLong("noticeSession", activeSession).apply();
        getSystemService(NotificationManager.class).cancel(4);
        alert(
            this,
            "Hotspot detected · confirm settings",
            (sessionPrefs.getString("lastSuccessfulRules", "").isEmpty()
                ? "No saved rules yet. Tap to set permitted devices and time limit."
                : "Review this session's devices and time limit. Saved rules apply automatically"
                    + " unless an error is reported."),
            2);
      }
      if (enabled
          && MainActivity.prefs(this).getBoolean("watch", false)
          && !managed
          && !o.optBoolean("stopping")) {
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
      if (justStopped && reason.equals("Timer reached"))
        alert(this, "Keeper stopped hotspot", reason + ".", 4);
      if (!error.isEmpty() && !error.equals(previousError))
        alert(this, "Hotspot needs attention", error, 5);
      previousError = error;
      long deadline = o.optLong("deadline");
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
                      + " · tap to change rules"
                  : "Hotspot on · no rules applied · tap to set rules";
      if (!enabled
          && !managed
          && !o.optBoolean("awaitingStart")
          && o.optInt("state") != 12
          && !MainActivity.prefs(this).getBoolean("watch", false)) {
        MainActivity.prefs(this).edit().putBoolean("activeMonitor", false).apply();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        return;
      }
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
    handler.postDelayed(poll, ready && !o.optBoolean("enabled") ? 30000 : 3000);
  }

  private void updateMonitor(String title, String detail) {
    String key = title + "\n" + detail;
    if (key.equals(monitorKey)) return;
    monitorKey = key;
    getSystemService(NotificationManager.class).notify(1, notification(this, title, detail, false));
  }

  static void acknowledge(Context c) {
    android.content.SharedPreferences p = MainActivity.prefs(c);
    p.edit().putLong("ackSession", p.getLong("noticeSession", -1)).apply();
    c.getSystemService(NotificationManager.class).cancel(2);
  }

  static void channels(Context c) {
    NotificationManager n = c.getSystemService(NotificationManager.class);
    n.createNotificationChannel(
        new NotificationChannel(
            "idle", "Background monitoring", NotificationManager.IMPORTANCE_MIN));
    n.createNotificationChannel(
        new NotificationChannel("monitor", "Hotspot watcher", NotificationManager.IMPORTANCE_LOW));
    NotificationChannel alerts =
        new NotificationChannel(
            "session-alerts-v2",
            "Session confirmation and cutoff",
            NotificationManager.IMPORTANCE_HIGH);
    alerts.enableVibration(true);
    alerts.setVibrationPattern(new long[] {0, 250, 150, 250});
    alerts.setSound(
        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
    n.createNotificationChannel(alerts);
  }

  static Notification notification(Context c, String title, String text, boolean alert) {
    PendingIntent edit =
        PendingIntent.getActivity(
            c,
            0,
            new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    boolean idle = !alert && text.equals("Background monitoring enabled");
    Notification.Builder builder =
        new Notification.Builder(c, alert ? "session-alerts-v2" : idle ? "idle" : "monitor")
            .setSmallIcon(R.drawable.ic_stat_keeper)
            .setLargeIcon(
                android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_launcher_legacy))
            .setColor(c.getColor(R.color.keeper_icon_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(edit)
            .setOnlyAlertOnce(!alert)
            .setAutoCancel(alert)
            .setOngoing(alert && title.startsWith("Hotspot detected") || !alert);
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
