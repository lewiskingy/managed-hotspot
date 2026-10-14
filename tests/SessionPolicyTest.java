import uk.me.theflat.hotspot.SessionPolicy;

public class SessionPolicyTest {
  public static void main(String[] args) {
    check(SessionPolicy.parse("", 100) == 0);
    check(SessionPolicy.parse("30", 100) == 30);
    for (String bad : new String[] {"-1", "101", "1.5", "foo", "999999999999999999999"}) {
      boolean rejected = false;
      try {
        SessionPolicy.parse(bad, 100);
      } catch (IllegalArgumentException e) {
        rejected = true;
      }
      check(rejected);
    }
    check(SessionPolicy.delta(50, 100) == 50);
    check(SessionPolicy.delta(100, 50) == 0);
    check(!SessionPolicy.reached(100, 0));
    check(!SessionPolicy.reached(99, 100));
    check(SessionPolicy.reached(100, 100));
    System.out.println("Session policy checks passed");
  }

  static void check(boolean b) {
    if (!b) throw new AssertionError();
  }
}
