package uk.me.theflat.hotspot;

import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Credential input policy; independent of platform APIs and never includes input in errors. */
public final class HotspotProfilePolicy {
  public static final class ValidationException extends IllegalArgumentException {
    public final String field;

    public ValidationException(String field, String message) {
      super(message);
      this.field = field;
    }
  }

  public static void validateSsid(String value) {
    if (value == null || value.trim().isEmpty())
      throw new ValidationException("ssid", "Enter a hotspot name.");
    try {
      int length =
          StandardCharsets.UTF_8
              .newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .encode(CharBuffer.wrap(value))
              .remaining();
      if (length > 32)
        throw new ValidationException("ssid", "Hotspot name must fit within 32 UTF-8 bytes.");
    } catch (CharacterCodingException e) {
      throw new ValidationException("ssid", "Hotspot name contains invalid text.");
    }
  }

  public static boolean usesPassword(int security) {
    return security == 1 || security == 2 || security == 3;
  }

  public static void validatePassword(String value, int security) {
    if (!usesPassword(security))
      throw new ValidationException(
          "password", "Change this security mode in Android hotspot settings.");
    int min = security == 3 ? 1 : 8;
    if (value == null || value.length() < min || value.length() > 63)
      throw new ValidationException("password", "Password must contain " + min + "–63 characters.");
    for (int i = 0; i < value.length(); i++)
      if (value.charAt(i) < 32 || value.charAt(i) > 126)
        throw new ValidationException(
            "password",
            "Use letters, numbers, spaces or printable ASCII symbols for the password.");
  }

  public static boolean deferLiveAccess(
      boolean pending,
      boolean managed,
      boolean currentRestricted,
      Collection<String> applied,
      boolean requestedRestricted,
      Collection<String> requested) {
    if (!pending) return false;
    if (!managed
        || currentRestricted != requestedRestricted
        || !new HashSet<>(applied).equals(new HashSet<>(requested)))
      throw new ValidationException(
          "rules",
          "Name/password changes are saved for the next start. Restart hotspot before changing"
              + " device access.");
    return true;
  }

  public static String securityLabel(int security) {
    switch (security) {
      case 0:
        return "Open network";
      case 1:
        return "WPA2 Personal";
      case 2:
        return "WPA2/WPA3 Personal";
      case 3:
        return "WPA3 Personal";
      case 4:
        return "Enhanced open (transition)";
      case 5:
        return "Enhanced open";
      default:
        return "Security managed by Android";
    }
  }
}
