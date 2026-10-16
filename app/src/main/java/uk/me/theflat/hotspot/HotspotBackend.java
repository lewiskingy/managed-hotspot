package uk.me.theflat.hotspot;

import android.content.Context;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Runs only in the Shizuku shell user-service. Never runs restricted APIs in the app process. */
public final class HotspotBackend extends Binder {
  static final String DESCRIPTOR = "uk.me.theflat.hotspot.Controller";
  private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
  private Context shell;
  private WifiManager wifi;
  private TetheringManager tether;
  private int appUid = -1;
  private PowerManager.WakeLock sessionWake;
  private long stopRequestedAt;
  private Object callback, capability, original;
  private List<String> clients = new ArrayList<>(), appliedAllowed = new ArrayList<>();
  private boolean managed = false,
      restricted = false,
      restoring = false,
      stopping = false,
      awaitingStart = false;
  private long startDeadline = 0;
  private long sessionStarted = 0, timeLimitMs = 0;
  private boolean observedOff = false, startObserved = false;
  private long deadline = 0;
  private String error = "", reason = "", initError = "", startError = "";
  private long generation = 0;
  private int lastState = 11;
  private boolean callbackReady = false;

  public HotspotBackend() {
    attachInterface(null, DESCRIPTOR);
    worker.execute(this::initialize);
    worker.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
  }

  public HotspotBackend(Context ignored) {
    this();
  }

  private void initialize() {
    try {
      Class<?> at = Class.forName("android.app.ActivityThread");
      Object thread = at.getMethod("currentActivityThread").invoke(null);
      if (thread == null) thread = at.getMethod("systemMain").invoke(null);
      Context system = (Context) at.getMethod("getSystemContext").invoke(thread);
      shell = ShellIdentity.create(system, thread);
      appUid = shell.getPackageManager().getApplicationInfo("uk.me.theflat.hotspot", 0).uid;
      wifi = shell.getSystemService(WifiManager.class);
      tether = shell.getSystemService(TetheringManager.class);
      Class<?> callbackClass = Class.forName("android.net.wifi.WifiManager$SoftApCallback");
      callback =
          java.lang.reflect.Proxy.newProxyInstance(
              callbackClass.getClassLoader(),
              new Class<?>[] {callbackClass},
              (proxy, method, args) -> {
                switch (method.getName()) {
                  case "hashCode":
                    return System.identityHashCode(proxy);
                  case "equals":
                    return proxy == args[0];
                  case "toString":
                    return "HotspotKeeperCallback";
                  case "onCapabilityChanged":
                    capability = args[0];
                    break;
                  case "onStateChanged":
                    lastState = (Integer) args[0];
                    observeSession(lastState);
                    callbackReady = true;
                    break;
                  case "onConnectedClientsChanged":
                    if (args.length == 1 && args[0] instanceof List) {
                      List<String> result = new ArrayList<>();
                      for (Object c : (List<?>) args[0])
                        result.add(call(c, "getMacAddress").toString());
                      clients = result;
                    }
                    break;
                }
                return null;
              });
      wifi.getClass()
          .getMethod("registerSoftApCallback", Executor.class, callbackClass)
          .invoke(wifi, worker, callback);
      recoverSavedRules();
    } catch (Throwable e) {
      initError = message(e);
    }
  }

  private void updateWake(boolean needed) {
    if (needed) {
      if (sessionWake == null) {
        sessionWake =
            shell
                .getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HotspotKeeper:protected-session");
        sessionWake.setReferenceCounted(false);
      }
      if (!sessionWake.isHeld()) sessionWake.acquire();
    } else if (sessionWake != null && sessionWake.isHeld()) sessionWake.release();
  }

  private static Object call(Object target, String name) throws Exception {
    return target.getClass().getMethod(name).invoke(target);
  }

  private int state() throws Exception {
    return (Integer) call(wifi, "getWifiApState");
  }

  private boolean canAllow() {
    try {
      if (capability == null) return false;
      long feature =
          capability.getClass().getField("SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT").getLong(null);
      return (Boolean)
          capability
              .getClass()
              .getMethod("areFeaturesSupported", long.class)
              .invoke(capability, feature);
    } catch (Exception e) {
      return false;
    }
  }

  private Object configuration() throws Exception {
    return call(wifi, "getSoftApConfiguration");
  }

  private static final java.nio.file.Path RECOVERY =
      java.nio.file.Paths.get("/data/local/tmp/managed-hotspot-rules.json");

  private void storeRecovery(SessionRules r) throws Exception {
    JSONObject record =
        new JSONObject()
            .put("restricted", call(original, "isClientControlByUserEnabled"))
            .put("allowed", new JSONArray(addresses(original, "getAllowedClientList")))
            .put("blocked", new JSONArray(addresses(original, "getBlockedClientList")))
            .put("appliedRestricted", r.restricted)
            .put("appliedAllowed", new JSONArray(r.allowed));
    writeRecord(record.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private void writeRecord(byte[] bytes) throws Exception {
    java.nio.file.Path temporary =
        java.nio.file.Files.createTempFile(RECOVERY.getParent(), "keeper-", ".tmp");
    try {
      android.system.Os.chmod(temporary.toString(), 0600);
      java.nio.file.Files.write(temporary, bytes);
      java.nio.file.Files.move(
          temporary,
          RECOVERY,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    } finally {
      java.nio.file.Files.deleteIfExists(temporary);
    }
  }

  private void recoverSavedRules() throws Exception {
    if (!java.nio.file.Files.exists(RECOVERY)) return;
    JSONObject record =
        new JSONObject(
            new String(
                java.nio.file.Files.readAllBytes(RECOVERY),
                java.nio.charset.StandardCharsets.UTF_8));
    restricted = record.getBoolean("appliedRestricted");
    appliedAllowed = strings(record.getJSONArray("appliedAllowed"));
    Collections.sort(appliedAllowed);
    Object current = configuration();
    if ((Boolean) call(current, "isClientControlByUserEnabled") != restricted
        || !addresses(current, "getAllowedClientList").equals(appliedAllowed)
        || !addresses(current, "getBlockedClientList").isEmpty()) {
      java.nio.file.Files.deleteIfExists(RECOVERY);
      error = "Recovery record differs from current device rules; left settings unchanged.";
      return;
    }
    Object reconstructed =
        withRules(
            current, record.getBoolean("restricted"), strings(record.getJSONArray("allowed")));
    Class<?> cls = Class.forName("android.net.wifi.SoftApConfiguration"),
        builder = Class.forName("android.net.wifi.SoftApConfiguration$Builder");
    Object b = builder.getConstructor(cls).newInstance(reconstructed);
    List<MacAddress> blocked = new ArrayList<>();
    for (String mac : strings(record.getJSONArray("blocked")))
      blocked.add(MacAddress.fromString(mac));
    builder.getMethod("setBlockedClientList", List.class).invoke(b, blocked);
    original = call(b, "build");
    if (state() == 11) {
      restore();
      reason = "Restored device settings from previous session";
    } else {
      managed = true;
      updateWake(true);
      stop("Controller restarted; ending previous protected session safely");
    }
  }

  private static List<String> strings(JSONArray array) throws JSONException {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
    return result;
  }

  private void setConfiguration(Object c) throws Exception {
    boolean ok =
        (Boolean)
            wifi.getClass()
                .getMethod(
                    "setSoftApConfiguration", Class.forName("android.net.wifi.SoftApConfiguration"))
                .invoke(wifi, c);
    if (!ok) throw new IllegalStateException("Pixel rejected hotspot configuration");
  }

  private List<String> addresses(Object config, String getter) throws Exception {
    List<String> out = new ArrayList<>();
    for (Object a : (List<?>) call(config, getter)) out.add(a.toString());
    Collections.sort(out);
    return out;
  }

  private Object withRules(Object source, boolean limit, List<String> allowed) throws Exception {
    Class<?> config = Class.forName("android.net.wifi.SoftApConfiguration"),
        builder = Class.forName("android.net.wifi.SoftApConfiguration$Builder");
    Object b = builder.getConstructor(config).newInstance(source);
    builder.getMethod("setClientControlByUserEnabled", boolean.class).invoke(b, limit);
    List<MacAddress> macs = new ArrayList<>();
    for (String s : allowed) macs.add(MacAddress.fromString(s));
    builder.getMethod("setAllowedClientList", List.class).invoke(b, macs);
    builder.getMethod("setBlockedClientList", List.class).invoke(b, new ArrayList<MacAddress>());
    return call(b, "build");
  }

  private SessionRules parse(JSONObject p) throws Exception {
    JSONArray a = p.optJSONArray("allowed");
    List<String> allowed = new ArrayList<>();
    if (a != null) for (int i = 0; i < a.length(); i++) allowed.add(a.getString(i));
    return new SessionRules(p.optLong("minutes"), p.optBoolean("restricted"), allowed);
  }

  private void apply(JSONObject input, boolean start) throws Exception {
    if (wifi == null || !initError.isEmpty())
      throw new IllegalStateException(initError.isEmpty() ? "Controller initializing" : initError);
    int s = state();
    observeSession(s);
    if (!start
        && input.has("expectedSession")
        && input.optLong("expectedSession") != sessionStarted)
      throw new IllegalStateException("The hotspot session changed. Reopen the session editor.");
    if (!start && s != 13) throw new IllegalStateException("Hotspot is not running");
    if (start && (s == 12 || s == 10))
      throw new IllegalStateException("Hotspot is changing state; retry shortly");
    SessionRules r = parse(input);
    if (r.restricted && !canAllow())
      throw new IllegalStateException("Pixel has not reported support for device allowlists");
    boolean newSession = !managed;
    Object currentConfig = configuration();
    Object saved = original;
    if (newSession) original = currentConfig;
    byte[] previousRecord =
        java.nio.file.Files.exists(RECOVERY) ? java.nio.file.Files.readAllBytes(RECOVERY) : null;
    try {
      updateWake(r.durationMs > 0);
      storeRecovery(r);
      setConfiguration(withRules(currentConfig, r.restricted, r.allowed));
    } catch (Exception e) {
      original = saved;
      updateWake(managed && deadline > 0);
      if (previousRecord == null) java.nio.file.Files.deleteIfExists(RECOVERY);
      else writeRecord(previousRecord);
      throw e;
    }
    restricted = r.restricted;
    appliedAllowed = new ArrayList<>(r.allowed);
    Collections.sort(appliedAllowed);
    timeLimitMs = r.durationMs;
    deadline = SessionRules.deadline(sessionStarted, timeLimitMs);
    if (newSession) {
      generation++;
    }
    managed = true;
    reason = "";
    error = "";
    stopping = false;
    startError = "";
    if (start && s != 13) {
      awaitingStart = true;
      startDeadline = SystemClock.elapsedRealtime() + 30000;
      try {
        // Legacy system method uses the existing saved default configuration, including carrier
        // provisioning.
        tether
            .getClass()
            .getMethod(
                "startTethering",
                int.class,
                Executor.class,
                TetheringManager.StartTetheringCallback.class)
            .invoke(
                tether,
                0,
                worker,
                new TetheringManager.StartTetheringCallback() {
                  @Override
                  public void onTetheringFailed(int code) {
                    startError = "Android rejected hotspot start (" + code + ")";
                    awaitingStart = false;
                    managed = false;
                    updateWake(false);
                    try {
                      restore();
                    } catch (Exception e) {
                      error = message(e);
                    }
                  }
                });
      } catch (Exception e) {
        awaitingStart = false;
        managed = false;
        updateWake(false);
        restore();
        throw e;
      }
    }
  }

  private void restore() throws Exception {
    if (original == null || restoring) return;
    restoring = true;
    try {
      Object now = configuration();
      boolean currentRestricted = (Boolean) call(now, "isClientControlByUserEnabled");
      if (currentRestricted != restricted
          || !addresses(now, "getAllowedClientList").equals(appliedAllowed)
          || !addresses(now, "getBlockedClientList").isEmpty()) {
        error = "Saved device rules changed outside Keeper; restoration skipped.";
        original = null;
        java.nio.file.Files.deleteIfExists(RECOVERY);
        return;
      }
      Class<?> cls = Class.forName("android.net.wifi.SoftApConfiguration"),
          bcls = Class.forName("android.net.wifi.SoftApConfiguration$Builder");
      Object b = bcls.getConstructor(cls).newInstance(now);
      bcls.getMethod("setClientControlByUserEnabled", boolean.class)
          .invoke(b, call(original, "isClientControlByUserEnabled"));
      bcls.getMethod("setAllowedClientList", List.class)
          .invoke(b, call(original, "getAllowedClientList"));
      bcls.getMethod("setBlockedClientList", List.class)
          .invoke(b, call(original, "getBlockedClientList"));
      setConfiguration(call(b, "build"));
      original = null;
      java.nio.file.Files.deleteIfExists(RECOVERY);
    } finally {
      restoring = false;
    }
  }

  private void stop(String why) throws Exception {
    if (wifi == null) throw new IllegalStateException("Controller unavailable");
    tether.getClass().getMethod("stopTethering", int.class).invoke(tether, 0);
    stopping = true;
    stopRequestedAt = SystemClock.elapsedRealtime();
    reason = why;
    // Keep rules until the AP is actually down; restore in tick so rejected stop requests remain
    // protected.
  }

  private void observeSession(int state) {
    if (state == 11) {
      sessionStarted = 0;
      startObserved = false;
      observedOff = true;
    } else if (state == 12) {
      observedOff = true;
    } else if (state == 13 && sessionStarted == 0) {
      sessionStarted = SystemClock.elapsedRealtime();
      if (!managed) reason = "";
      startObserved = observedOff;
      if (managed) deadline = SessionRules.deadline(sessionStarted, timeLimitMs);
    }
  }

  private void tick() {
    try {
      if (wifi == null) return;
      int s = state();
      observeSession(s);
      if (awaitingStart) {
        if (s == 13) {
          awaitingStart = false;
        } else if (SystemClock.elapsedRealtime() < startDeadline) {
          return;
        } else {
          awaitingStart = false;
          startError = "Hotspot did not start within 30 seconds";
          managed = false;
          updateWake(false);
          restore();
          return;
        }
      }
      if (managed && s == 11) {
        managed = false;
        deadline = 0;
        stopping = false;
        error = "";
        updateWake(false);
        restore();
      }
      if (!managed) {
        if (original != null && s == 11) restore();
        return;
      }
      if (stopping) {
        if (s == 13 && SystemClock.elapsedRealtime() - stopRequestedAt > 10000) {
          error = "Stop requested; waiting for Android to turn hotspot off";
        }
        return;
      }
      if (SessionRules.expired(deadline, SystemClock.elapsedRealtime())) {
        stop("Timer reached");
        return;
      }
    } catch (Throwable e) {
      error = message(e);
    }
  }

  private JSONObject snapshot() throws Exception {
    JSONObject o = new JSONObject();
    if (wifi == null || !initError.isEmpty()) {
      o.put("ready", false)
          .put("error", initError.isEmpty() ? "Controller initializing" : initError);
      return o;
    }
    int s = state();
    observeSession(s);
    o.put("ready", true)
        .put("android", Build.VERSION.RELEASE)
        .put("uid", android.os.Process.myUid())
        .put("callerPackage", shell.getOpPackageName())
        .put("attributionPackage", shell.getAttributionSource().getPackageName())
        .put("attributionUid", shell.getAttributionSource().getUid())
        .put("state", s)
        .put("enabled", s == 13)
        .put("managed", managed)
        .put("restricted", restricted)
        .put("allowed", new JSONArray(appliedAllowed))
        .put("allowSupported", canAllow())
        .put("clientsReady", callbackReady)
        .put("clients", new JSONArray(clients))
        .put("sessionStarted", sessionStarted)
        .put("startObserved", startObserved)
        .put("timeLimitMs", timeLimitMs)
        .put("deadline", deadline)
        .put("generation", generation)
        .put("stopping", stopping)
        .put("awaitingStart", awaitingStart)
        .put("sessionCpuAwake", sessionWake != null && sessionWake.isHeld())
        .put("reason", reason)
        .put("error", error.isEmpty() ? startError : error);
    Object conf = configuration();
    o.put("ssid", call(conf, "getSsid"));
    return o;
  }

  @Override
  protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
      throws RemoteException {
    if (code == INTERFACE_TRANSACTION) {
      reply.writeString(DESCRIPTOR);
      return true;
    }
    if (code == 16777114) {
      if (Binder.getCallingUid() != 2000
          && Binder.getCallingUid() != appUid
          && Binder.getCallingUid() != 0) throw new SecurityException();
      worker.execute(
          () -> {
            try {
              if (managed) stop("Controller disabled");
              restore();
            } catch (Exception ignored) {
            }
            System.exit(0);
          });
      return true;
    }
    data.enforceInterface(DESCRIPTOR);
    int caller = Binder.getCallingUid();
    if (appUid < 0) {
      reply.writeNoException();
      reply.writeString("{\"ok\":false,\"error\":\"Controller initializing\"}");
      return true;
    }
    if (caller != appUid && caller != 2000 && caller != 0)
      throw new SecurityException("Unexpected controller caller");
    String input = data.readString();
    final int command = code;
    try {
      String result =
          worker
              .submit(
                  () -> {
                    try {
                      JSONObject p = new JSONObject(input == null ? "{}" : input);
                      if (command == 2) apply(p, true);
                      else if (command == 3) apply(p, false);
                      else if (command == 4) stop("Stopped by you");
                      else if (command != 1) throw new IllegalArgumentException("Unknown command");
                      return snapshot().put("ok", true).toString();
                    } catch (Throwable e) {
                      return new JSONObject().put("ok", false).put("error", message(e)).toString();
                    }
                  })
              .get(15, TimeUnit.SECONDS);
      reply.writeNoException();
      reply.writeString(result);
    } catch (Exception e) {
      reply.writeNoException();
      reply.writeString(
          "{\"ok\":false,\"error\":\"Controller did not respond; check status before retrying\"}");
    }
    return true;
  }

  private static String message(Throwable e) {
    while (e instanceof InvocationTargetException
        && ((InvocationTargetException) e).getCause() != null) e = e.getCause();
    String m = e.getMessage();
    return e.getClass().getSimpleName() + (m == null ? "" : ": " + m);
  }
}
