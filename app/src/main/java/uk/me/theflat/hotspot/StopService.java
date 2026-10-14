package uk.me.theflat.hotspot;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;

/** Short command service invoked by a user notification action. */
public final class StopService extends Service {
  @Override
  public int onStartCommand(Intent i, int flags, int id) {
    ControllerClient.get(this)
        .request(
            4,
            new JSONObject(),
            o -> {
              if (!o.optBoolean("ok"))
                SessionService.alert(
                    this,
                    "Could not stop hotspot",
                    o.optString("error") + ". Open Android settings to stop manually.",
                    5);
              stopSelf(id);
            });
    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent i) {
    return null;
  }
}
