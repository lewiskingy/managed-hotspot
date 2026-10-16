import org.json.*;
import uk.me.theflat.hotspot.SettingsBackupCodec;

public final class SettingsBackupTest {
  static void check(boolean b) {
    if (!b) throw new AssertionError();
  }

  static void reject(JSONObject b) {
    try {
      SettingsBackupCodec.validate(b);
      throw new AssertionError("Accepted invalid backup");
    } catch (IllegalArgumentException | JSONException expected) {
    }
  }

  static JSONObject base() throws Exception {
    return new JSONObject(
        "{\"schemaVersion\":1,\"known\":{\"AA:BB:CC:DD:EE:FF\":\"Phone\"},\"rules\":{\"minutes\":10,\"restricted\":true,\"allowed\":[\"aa:bb:cc:dd:ee:ff\"]}}");
  }

  public static void main(String[] args) throws Exception {
    JSONObject b = base();
    b.put("password", "secret").put("watch", true);
    b.getJSONObject("rules").put("expectedSession", 123).put("password", "secret");
    JSONObject clean = SettingsBackupCodec.validate(b);
    check(!clean.toString().contains("secret"));
    check(!clean.has("watch"));
    check(!clean.getJSONObject("rules").has("expectedSession"));
    check(clean.getJSONObject("known").getString("aa:bb:cc:dd:ee:ff").equals("Phone"));
    reject(base().put("schemaVersion", 2));
    b = base();
    b.getJSONObject("rules").put("allowed", new JSONArray());
    reject(b);
    b = base();
    b.getJSONObject("rules").put("minutes", -1);
    reject(b);
    b = base();
    b.getJSONObject("known").put("invalid", "Phone");
    reject(b);
    b = base();
    b.getJSONObject("known").put("aa:bb:cc:dd:ee:ff", "x".repeat(129));
    reject(b);
    b = base();
    JSONArray many = new JSONArray();
    for (int i = 0; i < 257; i++) many.put("aa:bb:cc:dd:ee:ff");
    b.getJSONObject("rules").put("allowed", many);
    reject(b);
    clean =
        SettingsBackupCodec.validate(
            new JSONObject()
                .put("schemaVersion", 1)
                .put("known", new JSONObject())
                .put("rules", new JSONObject()));
    check(!clean.getJSONObject("rules").getBoolean("restricted"));
    System.out.println("Backup schema checks passed");
  }
}
