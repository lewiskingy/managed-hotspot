package uk.me.theflat.hotspot;

public final class SessionPolicy {
  public static long parse(String value, long max) {
    if (value.trim().isEmpty()) return 0;
    long n = Long.parseLong(value.trim());
    if (n < 0 || n > max) throw new IllegalArgumentException("Value outside range");
    return n;
  }

  public static long delta(long baseline, long current) {
    return current >= baseline ? current - baseline : 0;
  }

  public static boolean reached(long used, long cap) {
    return cap > 0 && used >= cap;
  }
}
