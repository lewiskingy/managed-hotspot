package uk.me.theflat.hotspot;

import android.net.TetheringManager;
import java.net.*;
import java.util.*;

public final class TetheredDeviceDiscoveryTest {
  public static class Client {
    final int type;
    final String mac;
    final List<Object> addresses;

    Client(int type, String mac, Object... addresses) {
      this.type = type;
      this.mac = mac;
      this.addresses = Arrays.asList(addresses);
    }

    public int getTetheringType() {
      return type;
    }

    public String getMacAddress() {
      return mac;
    }

    public List<Object> getAddresses() {
      return addresses;
    }
  }

  public static class Address {
    public String getHostname() {
      return "Callums-Laptop";
    }

    public Link getAddress() {
      return new Link();
    }
  }

  public static class AddressWithoutName {
    public Link getAddress() {
      return new Link();
    }
  }

  public static class Link {
    public InetAddress getAddress() throws Exception {
      return InetAddress.getByAddress(new byte[] {(byte) 192, (byte) 168, 1, 2});
    }
  }

  public static class Denied extends TetheringManager {
    public void registerTetheringEventCallback(
        java.util.concurrent.Executor e, TetheringEventCallback c) {
      throw new SecurityException("denied");
    }
  }

  public static void main(String[] args) {
    TetheredDeviceDiscovery d = new TetheredDeviceDiscovery();
    TetheringManager manager = new TetheringManager();
    d.start(manager, Runnable::run);
    String mac = "aa:bb:cc:dd:ee:ff";
    manager.emit(
        Arrays.asList(
            new Client(0, mac, new Address()), new Client(1, "11:22:33:44:55:66", new Address())));
    List<DeviceIdentity> items =
        d.connectedIdentities(Arrays.asList(mac.toUpperCase(Locale.ROOT), "11:22:33:44:55:66"));
    check(items.size() == 1);
    check(items.get(0).hostname.equals("Callums-Laptop"));
    check(items.get(0).addresses.equals(Collections.singletonList("192.168.1.2")));
    check(d.connectedIdentities(Collections.emptyList()).isEmpty());
    manager.emit(Arrays.asList(new Client(0, mac, new Address(), new Object())));
    check(d.status().startsWith("Partial"));
    check(
        d.connectedIdentities(Collections.singletonList(mac))
            .get(0)
            .hostname
            .equals("Callums-Laptop"));
    manager.emit(Collections.singletonList(new Client(0, mac, new AddressWithoutName())));
    check(d.connectedIdentities(Collections.singletonList(mac)).get(0).hostname.isEmpty());
    check(d.connectedIdentities(Collections.singletonList(mac)).get(0).addresses.size() == 1);
    manager.emit(Collections.emptyList());
    check(d.connectedIdentities(Collections.singletonList(mac)).isEmpty());
    manager.emit(Collections.singletonList(new Client(0, mac, new Address())));
    d.clear();
    check(d.connectedIdentities(Collections.singletonList(mac)).isEmpty());
    TetheredDeviceDiscovery denied = new TetheredDeviceDiscovery();
    denied.start(new Denied(), Runnable::run);
    check(denied.status().contains("SecurityException"));
    check(denied.connectedIdentities(Collections.singletonList(mac)).isEmpty());
    System.out.println("Tethered device discovery checks passed");
  }

  private static void check(boolean b) {
    if (!b) throw new AssertionError();
  }
}
