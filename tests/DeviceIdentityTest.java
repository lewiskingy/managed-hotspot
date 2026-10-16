import java.util.*;
import uk.me.theflat.hotspot.DeviceIdentity;

public class DeviceIdentityTest {
  public static void main(String[] args) {
    DeviceIdentity d =
        new DeviceIdentity(
            "AA:BB:CC:DD:EE:FF",
            "  Callums-Laptop\n\u202e ",
            Arrays.asList("192.168.1.2", "192.168.1.2", "fe80::1"));
    check(d.mac.equals("aa:bb:cc:dd:ee:ff"));
    check(d.hostname.equals("Callums-Laptop"));
    check(d.addresses.size() == 2);
    check(d.displayName("My laptop").equals("My laptop"));
    check(d.displayName("").equals("Callums-Laptop"));
    check(d.displayName(d.mac).equals("Callums-Laptop"));
    check(d.displayName("Device …ee:ff").equals("Callums-Laptop"));
    check(
        new DeviceIdentity(d.mac, null, Collections.emptyList())
            .displayName("")
            .equals("Unknown device · …ee:ff"));
    check(
        new DeviceIdentity(d.mac, "", Collections.emptyList())
            .displayName("Katy Phone")
            .equals("Katy Phone"));
    check(DeviceIdentity.cleanName(String.join("", Collections.nCopies(200, "a"))).length() == 128);
    System.out.println("Device identity checks passed");
  }

  static void check(boolean b) {
    if (!b) throw new AssertionError();
  }
}
