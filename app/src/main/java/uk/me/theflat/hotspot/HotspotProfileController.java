package uk.me.theflat.hotspot;

import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Shell-only credential adapter. Dedicated read response, no credentials in routine snapshots. */
final class HotspotProfileController {
  private String token;
  private String originalSsid, originalPassword;
  private int originalSecurity;
  private boolean pendingRestart;

  static final class Snapshot {
    final String ssid, password, revision;
    final int security;

    Snapshot(String ssid, String password, int security, String revision) {
      this.ssid = ssid;
      this.password = password;
      this.security = security;
      this.revision = revision;
    }
  }

  static final class Conflict extends IllegalStateException {
    Conflict() {
      super("Hotspot name or security changed elsewhere. Reopen Hotspot settings.");
    }
  }

  JSONObject read(Object wifi) throws Exception {
    Snapshot read = readState(wifi);
    return new JSONObject()
        .put("ssid", read.ssid == null ? "" : read.ssid)
        .put("password", read.password == null ? "" : read.password)
        .put("securityType", read.security)
        .put("revision", read.revision);
  }

  Snapshot readState(Object wifi) throws Exception {
    clear();
    Object current = get(wifi);
    originalSsid = (String) call(current, "getSsid");
    originalPassword = (String) call(current, "getPassphrase");
    originalSecurity = ((Number) call(current, "getSecurityType")).intValue();
    token = UUID.randomUUID().toString();
    return new Snapshot(originalSsid, originalPassword, originalSecurity, token);
  }

  boolean save(Object wifi, JSONObject input, boolean running) throws Exception {
    return saveState(
        wifi,
        input.optString("revision"),
        input.getString("ssid"),
        input.optString("password"),
        input.has("password"),
        running);
  }

  boolean saveState(
      Object wifi,
      String revision,
      String ssid,
      String newPassword,
      boolean replacePassword,
      boolean running)
      throws Exception {
    if (token == null || !token.equals(revision)) throw new Conflict();
    Object current = get(wifi);
    String currentSsid = (String) call(current, "getSsid"),
        currentPassword = (String) call(current, "getPassphrase");
    int security = ((Number) call(current, "getSecurityType")).intValue();
    if (!Objects.equals(originalSsid, currentSsid)
        || !Objects.equals(originalPassword, currentPassword)
        || security != originalSecurity) throw new Conflict();
    HotspotProfilePolicy.validateSsid(ssid);
    String password = replacePassword ? newPassword : currentPassword;
    boolean passwordChanged = !Objects.equals(password, currentPassword);
    if (passwordChanged) HotspotProfilePolicy.validatePassword(password, security);
    Class<?> config = Class.forName("android.net.wifi.SoftApConfiguration");
    Class<?> builder = Class.forName("android.net.wifi.SoftApConfiguration$Builder");
    Object b = builder.getConstructor(config).newInstance(current);
    builder.getMethod("setSsid", String.class).invoke(b, ssid);
    if (passwordChanged)
      builder.getMethod("setPassphrase", String.class, int.class).invoke(b, password, security);
    Object desired = call(b, "build");
    try {
      if (!(Boolean)
          wifi.getClass().getMethod("validateSoftApConfiguration", config).invoke(wifi, desired))
        throw new HotspotProfilePolicy.ValidationException(
            "profile",
            "Android rejected these hotspot settings. Check name, password and security in Android"
                + " settings.");
    } catch (NoSuchMethodException ignored) {
      /* Older platforms validate during write. */
    }
    boolean changed = !Objects.equals(ssid, currentSsid) || passwordChanged;
    if (changed
        && !(Boolean)
            wifi.getClass().getMethod("setSoftApConfiguration", config).invoke(wifi, desired))
      throw new IllegalStateException();
    Object saved = get(wifi);
    if (!Objects.equals(ssid, call(saved, "getSsid"))
        || !Objects.equals(password, call(saved, "getPassphrase"))
        || security != ((Number) call(saved, "getSecurityType")).intValue())
      throw new IllegalStateException();
    clear();
    pendingRestart = running && (pendingRestart || changed);
    return pendingRestart;
  }

  boolean pendingRestart() {
    return pendingRestart;
  }

  void stopped() {
    pendingRestart = false;
  }

  void clear() {
    token = null;
    originalSsid = null;
    originalPassword = null;
  }

  static String safeError(Throwable error) {
    while (error instanceof InvocationTargetException && error.getCause() != null)
      error = error.getCause();
    if (error instanceof HotspotProfilePolicy.ValidationException || error instanceof Conflict)
      return error.getMessage();
    return "Unable to read or save hotspot settings. Check Shizuku access and Android hotspot"
        + " settings.";
  }

  private static Object get(Object wifi) throws Exception {
    return call(wifi, "getSoftApConfiguration");
  }

  private static Object call(Object target, String name) throws Exception {
    return target.getClass().getMethod(name).invoke(target);
  }
}
