package uk.me.theflat.hotspot;

import java.util.*;

/** Untrusted, optional display metadata. Never use a name or IP to grant access. */
public final class DeviceIdentity {
  public final String mac, hostname;
  public final List<String> addresses;

  public DeviceIdentity(String mac, String hostname, Collection<String> addresses) {
    this.mac = mac == null ? "" : mac.trim().toLowerCase(Locale.ROOT);
    this.hostname = cleanName(hostname);
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String value : addresses) {
      if (value != null && !value.trim().isEmpty() && unique.size() < 16) unique.add(value.trim());
    }
    this.addresses = Collections.unmodifiableList(new ArrayList<>(unique));
  }

  public static String cleanName(String value) {
    if (value == null) return "";
    StringBuilder safe = new StringBuilder();
    value
        .codePoints()
        .filter(
            cp ->
                !Character.isISOControl(cp)
                    && Character.getType(cp) != Character.FORMAT
                    && cp != 0x2028
                    && cp != 0x2029)
        .limit(128)
        .forEach(safe::appendCodePoint);
    return safe.toString().trim();
  }

  public String displayName(String saved) {
    String name = cleanName(saved);
    String suffix = mac.substring(Math.max(0, mac.length() - 5));
    boolean generated =
        name.equalsIgnoreCase(mac)
            || name.equals("Device …" + suffix)
            || name.equals("Device " + suffix);
    if (!name.isEmpty() && !generated) return name;
    if (!hostname.isEmpty()) return hostname;
    return "Unknown device · …" + suffix;
  }
}
