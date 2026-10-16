package uk.me.theflat.hotspot;

import java.io.*;

/** API-31-compatible bounded reads; rejects oversized documents before parsing. */
public final class BoundedInput {
  public static byte[] read(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int n;
    while ((n = in.read(buffer, 0, Math.min(buffer.length, limit - out.size() + 1))) != -1) {
      if (out.size() + n > limit) throw new IOException("Input exceeds limit");
      out.write(buffer, 0, n);
    }
    return out.toByteArray();
  }
}
