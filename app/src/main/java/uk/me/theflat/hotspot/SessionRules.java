package uk.me.theflat.hotspot;

import java.util.*;

public final class SessionRules {
  public final long durationMs, capBytes;
  public final boolean restricted;
  public final List<String> allowed;

  public SessionRules(long minutes, long mb, boolean restricted, List<String> addresses) {
    if (minutes < 0 || minutes > 10080 || mb < 0 || mb > 1048576)
      throw new IllegalArgumentException("Limits outside range");
    this.durationMs = minutes * 60000;
    this.capBytes = mb * 1048576;
    this.restricted = restricted;
    LinkedHashSet<String> clean = new LinkedHashSet<>();
    for (String s : addresses) {
      s = s.trim().toLowerCase(Locale.ROOT);
      if (!s.matches("(?:[0-9a-f]{2}:){5}[0-9a-f]{2}"))
        throw new IllegalArgumentException("Invalid device address");
      clean.add(s);
    }
    if (restricted && clean.isEmpty())
      throw new IllegalArgumentException("Select at least one device or choose password access");
    this.allowed = Collections.unmodifiableList(new ArrayList<>(clean));
  }

  public static long remaining(long deadline, long now) {
    return deadline == 0 ? 0 : Math.max(0, deadline - now);
  }

  public static boolean expired(long deadline, long now) {
    return deadline > 0 && now >= deadline;
  }
}
