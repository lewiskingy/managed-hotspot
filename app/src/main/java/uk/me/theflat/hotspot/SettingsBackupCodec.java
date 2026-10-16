package uk.me.theflat.hotspot;

import java.util.*;
import org.json.*;

/** Bounded, allowlisted backup schema; never carries runtime state or credentials. */
public final class SettingsBackupCodec {
  public static JSONObject validate(JSONObject input) throws JSONException {
    if (input.getInt("schemaVersion") != 1) throw new JSONException("Unsupported backup");
    JSONObject known = input.getJSONObject("known"), safe = new JSONObject();
    if (known.length() > 256) throw new JSONException("Too many devices");
    Iterator<String> keys = known.keys();
    while (keys.hasNext()) {
      String mac = keys.next();
      new SessionRules(0, false, Collections.singletonList(mac));
      Object name = known.get(mac);
      if (!(name instanceof String) || ((String) name).length() > 128)
        throw new JSONException("Invalid name");
      safe.put(mac.trim().toLowerCase(Locale.ROOT), DeviceIdentity.cleanName((String) name));
    }
    JSONObject r = input.getJSONObject("rules");
    long minutes = r.length() == 0 ? 0 : r.getLong("minutes");
    boolean restricted = r.length() != 0 && r.getBoolean("restricted");
    JSONArray a = r.optJSONArray("allowed");
    if (a == null) a = new JSONArray();
    if (a.length() > 256) throw new JSONException("Too many rules");
    List<String> allowed = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) allowed.add(a.getString(i));
    SessionRules valid = new SessionRules(minutes, restricted, allowed);
    return new JSONObject()
        .put("schemaVersion", 1)
        .put("known", safe)
        .put(
            "rules",
            new JSONObject()
                .put("minutes", minutes)
                .put("restricted", restricted)
                .put("allowed", new JSONArray(valid.allowed)));
  }
}
