package uk.me.theflat.hotspot;

import android.net.wifi.SoftApConfiguration;

public class HotspotProfileTest {
  public static class Wifi {
    SoftApConfiguration config = new SoftApConfiguration("Daddy", "old-password", 2, 7, true);
    boolean valid = true, write = true;

    public SoftApConfiguration getSoftApConfiguration() {
      return config;
    }

    public boolean validateSoftApConfiguration(SoftApConfiguration c) {
      return valid;
    }

    public boolean setSoftApConfiguration(SoftApConfiguration c) {
      if (!write) return false;
      config = c;
      return true;
    }
  }

  public static void main(String[] args) throws Exception {
    HotspotProfilePolicy.validateSsid("  My hotspot  ");
    HotspotProfilePolicy.validateSsid("éééééééééééééééé");
    bad(() -> HotspotProfilePolicy.validateSsid("ééééééééééééééééé"));
    bad(() -> HotspotProfilePolicy.validateSsid("\ud800"));
    bad(() -> HotspotProfilePolicy.validateSsid(""));
    HotspotProfilePolicy.validatePassword("12345678", 1);
    HotspotProfilePolicy.validatePassword("a", 3);
    bad(() -> HotspotProfilePolicy.validatePassword("1234567", 2));
    bad(() -> HotspotProfilePolicy.validatePassword("not-ascii-é", 1));
    bad(() -> HotspotProfilePolicy.validatePassword("password", 0));
    check(
        HotspotProfilePolicy.deferLiveAccess(
            true,
            true,
            true,
            java.util.Arrays.asList("a", "b"),
            true,
            java.util.Arrays.asList("b", "a")));
    check(
        !HotspotProfilePolicy.deferLiveAccess(
            false,
            false,
            true,
            java.util.Collections.emptyList(),
            false,
            java.util.Collections.emptyList()));
    bad(
        () ->
            HotspotProfilePolicy.deferLiveAccess(
                true,
                true,
                true,
                java.util.Collections.singletonList("a"),
                false,
                java.util.Collections.singletonList("a")));
    bad(
        () ->
            HotspotProfilePolicy.deferLiveAccess(
                true,
                true,
                true,
                java.util.Collections.singletonList("a"),
                true,
                java.util.Collections.singletonList("b")));
    Wifi wifi = new Wifi();
    HotspotProfileController controller = new HotspotProfileController();
    HotspotProfileController.Snapshot read = controller.readState(wifi);
    check(controller.saveState(wifi, read.revision, "New hotspot", "new-password", true, true));
    check(wifi.config.ssid.equals("New hotspot") && wifi.config.pass.equals("new-password"));
    check(wifi.config.security == 2 && wifi.config.band == 7 && wifi.config.restricted);
    check(controller.pendingRestart());
    controller.clear();
    check(controller.pendingRestart());
    controller.stopped();
    check(!controller.pendingRestart());
    read = controller.readState(wifi);
    String revision = read.revision;
    wifi.config = new SoftApConfiguration("External change", "external-pass", 2, 3, false);
    conflict(() -> controller.saveState(wifi, revision, "My edit", "password-2", true, false));
    check(wifi.config.ssid.equals("External change"));
    read = controller.readState(wifi);
    String fresh = read.revision;
    controller.saveState(wifi, fresh, "SSID only", null, false, false);
    check(wifi.config.pass.equals("external-pass"));
    read = controller.readState(wifi);
    String rejected = read.revision;
    wifi.write = false;
    bad(() -> controller.saveState(wifi, rejected, "Rejected", "valid-pass", true, false));
    check(wifi.config.ssid.equals("SSID only"));
    wifi.write = true;
    controller.clear();
    conflict(() -> controller.saveState(wifi, rejected, "Wrong token", "valid-pass", true, false));
    check(
        !HotspotProfileController.safeError(new IllegalArgumentException("SECRET_PASSWORD"))
            .contains("SECRET_PASSWORD"));
    wifi.config = new SoftApConfiguration("Open", null, 0, 1, false);
    read = controller.readState(wifi);
    controller.saveState(wifi, read.revision, "Renamed open", null, false, false);
    check(wifi.config.security == 0 && wifi.config.pass == null);
    System.out.println("Hotspot profile policy/controller checks passed");
  }

  interface Attempt {
    void run() throws Exception;
  }

  static void bad(Attempt a) throws Exception {
    try {
      a.run();
      throw new AssertionError();
    } catch (IllegalArgumentException | IllegalStateException expected) {
    }
  }

  static void conflict(Attempt a) throws Exception {
    try {
      a.run();
      throw new AssertionError();
    } catch (HotspotProfileController.Conflict expected) {
    }
  }

  static void check(boolean b) {
    if (!b) throw new AssertionError();
  }
}
