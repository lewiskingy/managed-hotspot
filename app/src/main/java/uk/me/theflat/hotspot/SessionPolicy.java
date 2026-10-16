package uk.me.theflat.hotspot;

public final class SessionPolicy {
  public static long parse(String value, long max) {
    if (value.trim().isEmpty()) return 0;
    long n = Long.parseLong(value.trim());
    if (n < 0 || n > max) throw new IllegalArgumentException("Value outside range");
    return n;
  }
}
