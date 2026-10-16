package uk.me.theflat.hotspot;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;
import org.json.*;

/**
 * Shell-only adapter for Android's best-effort DHCP client metadata. All restricted API discovery
 * stays here. Failure must never prevent hotspot control. No network probing or DNS lookups.
 */
final class TetheredDeviceDiscovery {
  private final Map<String, DeviceIdentity> identities = new LinkedHashMap<>();
  private Object callback;
  private String status = "Not registered";

  void start(Object tethering, Executor worker) {
    try {
      Class<?> type = Class.forName("android.net.TetheringManager$TetheringEventCallback");
      callback =
          Proxy.newProxyInstance(
              type.getClassLoader(),
              new Class<?>[] {type},
              (proxy, method, args) -> {
                switch (method.getName()) {
                  case "hashCode":
                    return System.identityHashCode(proxy);
                  case "equals":
                    return proxy == args[0];
                  case "toString":
                    return "KeeperDeviceDiscovery";
                  case "onClientsChanged":
                    if (args != null && args.length > 0 && args[0] instanceof Collection)
                      update((Collection<?>) args[0]);
                    break;
                }
                return null;
              });
      tethering
          .getClass()
          .getMethod("registerTetheringEventCallback", Executor.class, type)
          .invoke(tethering, worker, callback);
      status = "Registered; names are optional";
    } catch (ReflectiveOperationException | RuntimeException e) {
      status = "Unavailable: " + rootMessage(e);
      callback = null;
    }
  }

  private void update(Collection<?> clients) {
    Map<String, DeviceIdentity> fresh = new LinkedHashMap<>();
    int failures = 0;
    for (Object client : clients) {
      try {
        if (((Number) invoke(client, "getTetheringType")).intValue() != 0) continue;
        String mac = invoke(client, "getMacAddress").toString();
        String name = "";
        List<String> ips = new ArrayList<>();
        for (Object address : (Collection<?>) invoke(client, "getAddresses")) {
          try {
            Object advertised = invoke(address, "getHostname");
            if (name.isEmpty())
              name = DeviceIdentity.cleanName(advertised == null ? "" : advertised.toString());
          } catch (ReflectiveOperationException | RuntimeException e) {
            failures++;
          }
          try {
            Object link = invoke(address, "getAddress");
            java.net.InetAddress ip = (java.net.InetAddress) invoke(link, "getAddress");
            ips.add(ip.getHostAddress()); // numeric formatting only; never reverse DNS
          } catch (ReflectiveOperationException | RuntimeException e) {
            failures++;
          }
        }
        DeviceIdentity previous = fresh.get(mac.toLowerCase(Locale.ROOT));
        if (previous != null) {
          ips.addAll(previous.addresses);
          if (name.isEmpty()) name = previous.hostname;
        }
        DeviceIdentity identity = new DeviceIdentity(mac, name, ips);
        fresh.put(identity.mac, identity);
      } catch (ReflectiveOperationException | RuntimeException e) {
        failures++;
      }
    }
    identities.clear();
    identities.putAll(fresh);
    status =
        failures == 0
            ? "Available; names are optional"
            : "Partial metadata; some fields unavailable";
  }

  void clear() {
    identities.clear();
  }

  String status() {
    return status;
  }

  List<DeviceIdentity> connectedIdentities(Collection<String> connected) {
    List<DeviceIdentity> result = new ArrayList<>();
    for (String mac : connected) {
      DeviceIdentity identity = identities.get(mac.toLowerCase(Locale.ROOT));
      if (identity != null) result.add(identity);
    }
    return result;
  }

  JSONArray snapshot(Collection<String> connected) throws JSONException {
    // Association events establish presence; leases only enrich that known client list.
    return DeviceIdentityJson.encode(connectedIdentities(connected));
  }

  private static Object invoke(Object target, String name) throws ReflectiveOperationException {
    return target.getClass().getMethod(name).invoke(target);
  }

  private static String rootMessage(Throwable e) {
    while (e.getCause() != null) e = e.getCause();
    return e.getClass().getSimpleName();
  }
}
