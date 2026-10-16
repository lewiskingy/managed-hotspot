import java.util.*;
import uk.me.theflat.hotspot.SessionRules;

public class SessionRulesTest {
  public static void main(String[] args) {
    SessionRules r =
        new SessionRules(30, true, Arrays.asList("AA:BB:CC:DD:EE:FF", "aa:bb:cc:dd:ee:ff"));
    check(r.durationMs == 1800000);
    check(r.allowed.size() == 1);
    check(r.allowed.get(0).equals("aa:bb:cc:dd:ee:ff"));
    rejected(() -> new SessionRules(0, true, Collections.emptyList()));
    rejected(() -> new SessionRules(0, true, Arrays.asList("invalid")));
    rejected(() -> new SessionRules(-1, false, Collections.emptyList()));
    check(!SessionRules.expired(0, 500));
    check(!SessionRules.expired(100, 99));
    check(SessionRules.expired(100, 100));
    check(SessionRules.remaining(100, 101) == 0);
    check(SessionRules.remaining(100, 99) == 1);
    long started = 100000;
    long now = started + 7 * 60000;
    long end = SessionRules.deadline(started, 10 * 60000);
    check(SessionRules.remaining(end, now) == 3 * 60000);
    check(SessionRules.deadline(started, 10 * 60000) == end); // reapply keeps end
    check(SessionRules.expired(SessionRules.deadline(started, 5 * 60000), now));
    check(SessionRules.deadline(started, 0) == 0);
    check(SessionRules.deadline(0, 60000) == 0);
    for (int stop : SessionRules.TIMER_MINUTES) check(SessionRules.snapMinutes(stop) == stop);
    check(SessionRules.snapMinutes(8) == 10);
    check(SessionRules.snapMinutes(76) == 90);
    check(SessionRules.nextMinutes(10, true) == 20);
    check(SessionRules.nextMinutes(90, false) == 60);
    check(SessionRules.nextMinutes(0, false) == 0);
    check(SessionRules.nextMinutes(120, true) == 120);
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
