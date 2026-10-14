import java.util.*;
import uk.me.theflat.hotspot.SessionRules;

public class SessionRulesTest {
  public static void main(String[] args) {
    SessionRules r =
        new SessionRules(30, 500, true, Arrays.asList("AA:BB:CC:DD:EE:FF", "aa:bb:cc:dd:ee:ff"));
    check(r.durationMs == 1800000);
    check(r.capBytes == 524288000);
    check(r.allowed.size() == 1);
    check(r.allowed.get(0).equals("aa:bb:cc:dd:ee:ff"));
    rejected(() -> new SessionRules(0, 0, true, Collections.emptyList()));
    rejected(() -> new SessionRules(0, 0, true, Arrays.asList("invalid")));
    rejected(() -> new SessionRules(-1, 0, false, Collections.emptyList()));
    rejected(() -> new SessionRules(0, 1048577, false, Collections.emptyList()));
    check(!SessionRules.expired(0, 500));
    check(!SessionRules.expired(100, 99));
    check(SessionRules.expired(100, 100));
    check(SessionRules.remaining(100, 101) == 0);
    check(SessionRules.remaining(100, 99) == 1);
    System.out.println("Session rules checks passed");
  }

  static void rejected(Runnable r) {
    boolean bad = false;
    try {
      r.run();
    } catch (IllegalArgumentException e) {
      bad = true;
    }
    check(bad);
  }

  static void check(boolean b) {
    if (!b) throw new AssertionError();
  }
}
