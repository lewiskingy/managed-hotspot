package android.app;

public class AppOpsManager {
  public static int checks;

  public void checkPackage(int uid, String name) {
    checks++;
    if (uid != 2000 || !name.equals("com.android.shell"))
      throw new SecurityException("Package " + name + " does not belong to " + uid);
  }
}
