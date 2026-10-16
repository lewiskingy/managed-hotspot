package uk.me.theflat.hotspot;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.*;
import java.nio.charset.StandardCharsets;

final class AppInformation {
  static void privacy(Activity a) {
    try {
      String policy;
      try (java.io.InputStream in = a.getAssets().open("privacy.txt")) {
        policy = new String(BoundedInput.read(in, 65536), StandardCharsets.UTF_8);
      }
      new AlertDialog.Builder(a)
          .setTitle("Privacy & source")
          .setMessage(policy)
          .setPositiveButton("Done", null)
          .setNeutralButton(
              "Source code", (d, w) -> open(a, "https://github.com/lewiskingy/managed-hotspot"))
          .show();
    } catch (java.io.IOException e) {
      Toast.makeText(a, "Privacy information unavailable", Toast.LENGTH_LONG).show();
    }
  }

  static void addSupport(Activity a, LinearLayout parent) {
    String url = a.getString(R.string.support_url).trim();
    if (!url.isEmpty() && url.startsWith("https://")) {
      Button b = new Button(a);
      b.setText("Buy me a coffee · optional");
      b.setOnClickListener(
          v ->
              new AlertDialog.Builder(a)
                  .setTitle("Optional support")
                  .setMessage(
                      "Keeper is free and ad-free. Contributions unlock nothing and are never"
                          + " required. This opens an external support website with its own privacy"
                          + " terms.")
                  .setPositiveButton("Open website", (d, w) -> open(a, url))
                  .setNegativeButton("Cancel", null)
                  .show());
      parent.addView(b);
    }
  }

  private static void open(Activity a, String url) {
    try {
      a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (RuntimeException e) {
      Toast.makeText(a, "No browser available", Toast.LENGTH_LONG).show();
    }
  }
}
