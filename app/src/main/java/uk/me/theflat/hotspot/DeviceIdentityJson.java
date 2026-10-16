package uk.me.theflat.hotspot;

import java.util.*;
import org.json.*;

/** Binder snapshot codec, kept out of UI and pure identity/display policy. */
final class DeviceIdentityJson {
  static DeviceIdentity find(JSONObject snapshot, String mac) {
    JSONArray details = snapshot.optJSONArray("clientDetails");
    if (details != null)
      for (int i = 0; i < details.length(); i++) {
        JSONObject entry = details.optJSONObject(i);
        if (entry == null || !mac.equalsIgnoreCase(entry.optString("mac"))) continue;
        List<String> ips = new ArrayList<>();
        JSONArray addresses = entry.optJSONArray("addresses");
        if (addresses != null)
          for (int j = 0; j < addresses.length(); j++) ips.add(addresses.optString(j));
        return new DeviceIdentity(mac, entry.optString("hostname"), ips);
      }
    return new DeviceIdentity(mac, "", Collections.emptyList());
  }

  static JSONArray encode(Collection<DeviceIdentity> identities) throws JSONException {
    JSONArray result = new JSONArray();
    for (DeviceIdentity d : identities)
      result.put(
          new JSONObject()
              .put("mac", d.mac)
              .put("hostname", d.hostname)
              .put("addresses", new JSONArray(d.addresses)));
    return result;
  }
}
