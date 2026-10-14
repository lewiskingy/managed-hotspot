package uk.me.theflat.hotspot;

import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())
        && MainActivity.prefs(c).getBoolean("watch", false)) {
      try {
        c.startForegroundService(new Intent(c, SessionService.class));
      } catch (RuntimeException ignored) {
        /* User can rearm watcher on launch if Android blocks restart. */
      }
    }
  }
}
