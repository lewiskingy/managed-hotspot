package uk.me.theflat.hotspot;

import static uk.me.theflat.hotspot.KeeperUi.*;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.function.Consumer;
import org.json.*;

/** Dedicated credential UI. Secret responses never become the activity's diagnostic snapshot. */
final class HotspotSettingsSheet {
  interface Transport {
    void request(int command, JSONObject p, Consumer<JSONObject> done);
  }

  private final Activity activity;
  private final Transport transport;
  private final Runnable osSettings, changed;
  private Dialog dialog;
  private EditText ssid, password;
  private TextView status;
  private Button save, cancel, os;
  private String revision = "";
  private int security;
  private boolean loaded, busy;

  HotspotSettingsSheet(Activity a, Transport t, Runnable osSettings, Runnable changed) {
    activity = a;
    transport = t;
    this.osSettings = osSettings;
    this.changed = changed;
  }

  private int d(int n) {
    return dp(activity, n);
  }

  private TextView label(LinearLayout p, String s, int size, int color) {
    TextView t = KeeperUi.text(activity, s, size, color, false);
    t.setPadding(0, d(8), 0, d(8));
    p.addView(t);
    return t;
  }

  private Button button(LinearLayout p, String text, int color, Runnable click) {
    Button b = new Button(activity);
    b.setText(text);
    b.setAllCaps(false);
    b.setTextSize(16);
    b.setTypeface(null, Typeface.BOLD);
    b.setTextColor(color == MINT ? BG : WHITE);
    b.setStateListAnimator(null);
    touch(b, color, 16);
    b.setOnClickListener(v -> click.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = d(8);
    p.addView(b, lp);
    return b;
  }

  private EditText field(LinearLayout p, String hint, boolean secret) {
    EditText e = new EditText(activity);
    e.setTextColor(WHITE);
    e.setHintTextColor(MUTED);
    e.setHint(hint);
    e.setSingleLine();
    e.setSaveEnabled(false);
    e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    e.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING | EditorInfo.IME_ACTION_DONE);
    e.setInputType(
        InputType.TYPE_CLASS_TEXT
            | (secret
                ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
    e.setMinHeight(d(48));
    p.addView(e);
    return e;
  }

  void show() {
    LinearLayout body = column(activity);
    body.setPadding(d(20), d(16), d(20), d(16));
    body.setBackground(shape(activity, CARD, 24, false));
    label(body, "Hotspot settings", 24, WHITE);
    status = label(body, "Reading current Android settings…", 14, MUTED);
    label(body, "Hotspot name (SSID)", 14, WHITE);
    ssid = field(body, "Hotspot name", false);
    label(body, "Password", 14, WHITE);
    password = field(body, "Hotspot password", true);
    password.setTransformationMethod(PasswordTransformationMethod.getInstance());
    ssid.setEnabled(false);
    password.setEnabled(false);
    CheckBox reveal = new CheckBox(activity);
    reveal.setText("Show password");
    reveal.setEnabled(false);
    reveal.setTextColor(MUTED);
    reveal.setMinHeight(d(48));
    body.addView(reveal);
    reveal.setOnCheckedChangeListener(
        (v, on) -> {
          password.setTransformationMethod(on ? null : PasswordTransformationMethod.getInstance());
          password.setSelection(password.length());
        });
    label(
        body,
        "Name and password changes take effect the next time the hotspot starts. Restart before"
            + " changing device access afterwards. The current session and its rules are not"
            + " restarted.",
        14,
        MUTED);
    LinearLayout footer = column(activity);
    footer.setPadding(d(20), d(8), d(20), d(12));
    save = button(footer, "Save settings", MINT, this::save);
    save.setEnabled(false);
    os =
        button(
            footer,
            "Full Android hotspot settings",
            LINE,
            () -> {
              dialog.dismiss();
              osSettings.run();
            });
    cancel = button(footer, "Cancel", LINE, () -> dialog.dismiss());
    ScrollView sc = new ScrollView(activity);
    sc.addView(body);
    LinearLayout container = column(activity);
    container.setBackground(shape(activity, CARD, 24, false));
    container.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    container.addView(footer);
    container.setOnApplyWindowInsetsListener(
        (v, i) -> {
          android.graphics.Insets bars = i.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(bars.left, 0, bars.right, bars.bottom);
          return i;
        });
    dialog = new Dialog(activity);
    dialog.setContentView(container);
    Window w = dialog.getWindow();
    if (w != null) {
      w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
      w.setBackgroundDrawableResource(android.R.color.transparent);
      w.setGravity(Gravity.BOTTOM);
      w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    dialog.setOnDismissListener(
        x -> {
          ssid.setText("");
          password.setText("");
          revision = "";
          transport.request(7, new JSONObject(), ignored -> {});
        });
    dialog.show();
    if (w != null)
      w.setLayout(-1, (int) (activity.getResources().getDisplayMetrics().heightPixels * .85));
    transport.request(
        5,
        new JSONObject(),
        result -> {
          if (!dialog.isShowing() || activity.isDestroyed()) {
            result.remove("password");
            return;
          }
          if (!result.optBoolean("ok")) {
            status.setText(result.optString("error"));
            return;
          }
          ssid.setText(result.optString("ssid"));
          password.setText(result.optString("password"));
          security = result.optInt("securityType", -1);
          revision = result.optString("revision");
          result.remove("password");
          ssid.setEnabled(true);
          password.setEnabled(HotspotProfilePolicy.usesPassword(security));
          reveal.setEnabled(password.isEnabled());
          status.setText(
              HotspotProfilePolicy.securityLabel(security)
                  + (password.isEnabled()
                      ? " · existing security mode is preserved"
                      : " · password changes require Android settings"));
          loaded = true;
          save.setEnabled(true);
        });
  }

  private void save() {
    if (!loaded || busy) return;
    try {
      HotspotProfilePolicy.validateSsid(ssid.getText().toString());
      JSONObject input =
          new JSONObject().put("revision", revision).put("ssid", ssid.getText().toString());
      if (password.isEnabled()) input.put("password", password.getText().toString());
      busy = true;
      cancel.setEnabled(false);
      os.setEnabled(false);
      dialog.setCancelable(false);
      dialog.setCanceledOnTouchOutside(false);
      save.setEnabled(false);
      save.setText("Saving…");
      transport.request(
          6,
          input,
          result -> {
            input.remove("password");
            busy = false;
            cancel.setEnabled(true);
            os.setEnabled(true);
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);
            if (!dialog.isShowing() || activity.isDestroyed()) return;
            if (result.optBoolean("ok")) {
              boolean pending = result.optBoolean("restartRequired");
              dialog.dismiss();
              changed.run();
              Toast.makeText(
                      activity,
                      pending ? "Saved for the next hotspot start" : "Hotspot settings saved",
                      Toast.LENGTH_LONG)
                  .show();
            } else {
              status.setText(result.optString("error"));
              save.setEnabled(true);
              save.setText("Save settings");
            }
          });
    } catch (HotspotProfilePolicy.ValidationException e) {
      ssid.setError(e.getMessage());
    } catch (JSONException e) {
      status.setText("Unable to prepare hotspot settings.");
    }
  }

  void dismiss() {
    if (dialog != null) dialog.dismiss();
  }
}
