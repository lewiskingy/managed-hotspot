package uk.me.theflat.hotspot;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Local SAF transfer. Imported drafts never alter a running hotspot or grant privileges. */
public final class SettingsBackupActivity extends Activity {
  private boolean waiting;
  private JSONObject pending;

  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    if (saved != null) {
      waiting = saved.getBoolean("waiting");
      String restore = saved.getString("pending");
      if (restore != null) {
        try {
          pending = SettingsBackupCodec.validate(new JSONObject(restore));
          confirm(pending);
          return;
        } catch (Exception e) {
          finish();
          return;
        }
      }
      if (waiting) return;
    }
    new AlertDialog.Builder(this)
        .setTitle("Settings backup")
        .setMessage(
            "Transfer saved device names and next-session rules. Files contain device addresses and"
                + " names: keep them private. Passwords, permissions and active sessions are"
                + " excluded.")
        .setPositiveButton(
            "Export",
            (d, w) ->
                pick(
                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType("application/json")
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE, "keeper-settings.json"),
                    1))
        .setNeutralButton(
            "Import",
            (d, w) ->
                pick(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("application/json")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    2))
        .setNegativeButton("Cancel", (d, w) -> finish())
        .setOnCancelListener(d -> finish())
        .show();
  }

  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    waiting = false;
    if (result != RESULT_OK || data == null || data.getData() == null) {
      finish();
      return;
    }
    try {
      if (request == 1) {
        android.content.SharedPreferences p = MainActivity.prefs(this);
        JSONObject backup =
            new JSONObject()
                .put("schemaVersion", 1)
                .put("known", new JSONObject(p.getString("known", "{}")))
                .put(
                    "rules",
                    new JSONObject(
                        p.getString(
                            "lastSuccessfulRules",
                            new JSONObject()
                                .put("minutes", Long.parseLong(p.getString("minutes", "0")))
                                .put("restricted", p.getBoolean("restricted", false))
                                .put("allowed", new JSONArray(p.getString("selected", "[]")))
                                .toString())));
        JSONObject clean = SettingsBackupCodec.validate(backup);
        try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
          if (out == null) throw new IOException();
          out.write(clean.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        toast("Settings exported");
        finish();
      } else if (request == 2) {
        byte[] bytes;
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
          if (in == null) throw new IOException();
          bytes = BoundedInput.read(in, 262144);
        }
        if (bytes.length > 262144) throw new IOException();
        JSONObject clean =
            SettingsBackupCodec.validate(new JSONObject(new String(bytes, StandardCharsets.UTF_8)));
        pending = clean;
        confirm(clean);
      }
    } catch (Exception e) {
      toast("Unable to transfer settings. Use a valid Keeper backup and an accessible file.");
      finish();
    }
  }

  private void pick(Intent intent, int request) {
    waiting = true;
    try {
      startActivityForResult(intent, request);
    } catch (RuntimeException e) {
      toast("No document picker available");
      finish();
    }
  }

  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putBoolean("waiting", waiting);
    if (pending != null) out.putString("pending", pending.toString());
  }

  private void confirm(JSONObject clean) {
    new AlertDialog.Builder(this)
        .setTitle("Replace saved settings?")
        .setMessage(
            "This replaces saved device names and next-session rules. The current hotspot is"
                + " unchanged. Check rules before the next start.")
        .setPositiveButton(
            "Replace",
            (d, w) -> {
              try {
                JSONObject rules = clean.getJSONObject("rules");
                boolean ok =
                    MainActivity.prefs(this)
                        .edit()
                        .putString("known", clean.getJSONObject("known").toString())
                        .putString("lastSuccessfulRules", rules.toString())
                        .putString("minutes", Long.toString(rules.getLong("minutes")))
                        .putBoolean("restricted", rules.getBoolean("restricted"))
                        .putString("selected", rules.getJSONArray("allowed").toString())
                        .commit();
                toast(ok ? "Settings imported" : "Unable to save settings");
              } catch (JSONException e) {
                toast("Invalid backup");
              }
              finish();
            })
        .setNegativeButton("Cancel", (d, w) -> finish())
        .setOnCancelListener(d -> finish())
        .show();
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }
}
