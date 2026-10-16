package uk.me.theflat.hotspot;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.*;
import rikka.shizuku.Shizuku;

public final class ControllerClient {
  private static ControllerClient instance;
  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private volatile IBinder remote;
  private boolean binding = false;
  private final Shizuku.OnBinderReceivedListener received = () -> connect();
  private final Shizuku.OnBinderDeadListener died =
      () -> {
        remote = null;
        binding = false;
      };
  private final Shizuku.OnRequestPermissionResultListener permission =
      (code, result) -> {
        if (result == PackageManager.PERMISSION_GRANTED) connect();
      };
  private final ServiceConnection connection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName c, IBinder b) {
          remote = b;
          binding = false;
          try {
            b.linkToDeath(
                () -> {
                  remote = null;
                  binding = false;
                },
                0);
          } catch (RemoteException e) {
            remote = null;
          }
        }

        public void onServiceDisconnected(ComponentName c) {
          remote = null;
          binding = false;
        }
      };

  private ControllerClient(Context c) {
    context = c.getApplicationContext();
    Shizuku.addBinderReceivedListenerSticky(received);
    Shizuku.addBinderDeadListener(died);
    Shizuku.addRequestPermissionResultListener(permission);
  }

  static synchronized ControllerClient get(Context c) {
    if (instance == null) instance = new ControllerClient(c);
    return instance;
  }

  boolean connected() {
    return remote != null;
  }

  String availability() {
    try {
      if (!Shizuku.pingBinder()) return "Start Shizuku first";
      if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
        return "Grant Hotspot Keeper Shizuku access";
      if (remote == null) return "Connecting to controller…";
      return "Shizuku connected";
    } catch (RuntimeException e) {
      return "Shizuku unavailable";
    }
  }

  String authorize() {
    if (!Shizuku.pingBinder()) throw new IllegalStateException("Open Shizuku and start it first");
    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
      if (Shizuku.shouldShowRequestPermissionRationale()) {
        return "Shizuku access was denied. Review Keeper's authorisation in Shizuku.";
      }
      Shizuku.requestPermission(10);
      return "Approve Hotspot Keeper in the Shizuku permission prompt";
    }
    connect();
    return remote == null
        ? "Shizuku access already granted; connecting…"
        : "Shizuku access already granted and controller connected";
  }

  synchronized void connect() {
    try {
      if (binding
          || remote != null
          || !Shizuku.pingBinder()
          || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return;
      binding = true;
      Shizuku.bindUserService(
          new Shizuku.UserServiceArgs(new ComponentName(context, HotspotBackend.class))
              .daemon(true)
              .processNameSuffix("controller")
              .tag("keeper-controller")
              .version(8)
              .debuggable(false),
          connection);
    } catch (RuntimeException e) {
      binding = false;
    }
  }

  void request(int command, JSONObject input, Consumer<JSONObject> done) {
    connect();
    io.execute(
        () -> {
          JSONObject result;
          IBinder b = remote;
          if (b == null) {
            result = error(availability());
          } else {
            Parcel data = Parcel.obtain(), reply = Parcel.obtain();
            try {
              data.writeInterfaceToken(HotspotBackend.DESCRIPTOR);
              data.writeString(input.toString());
              if (!b.transact(command, data, reply, 0))
                throw new RemoteException("Unsupported controller command");
              reply.readException();
              result = new JSONObject(reply.readString());
            } catch (Exception e) {
              result = error(e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
              data.recycle();
              reply.recycle();
            }
          }
          JSONObject value = result;
          main.post(() -> done.accept(value));
        });
  }

  private static JSONObject error(String message) {
    JSONObject o = new JSONObject();
    try {
      o.put("ok", false).put("error", message);
    } catch (JSONException ignored) {
    }
    return o;
  }
}
