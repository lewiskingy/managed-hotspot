package uk.me.theflat.hotspot;

import android.app.*;
import android.content.*;

public final class ShellIdentityTest {
  public static void main(String[] args) throws Exception {
    Context system = new ContextImpl("android", "android", 2000);
    Context inherited =
        system.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
    check(inherited.getPackageName().equals("com.android.shell"));
    check(inherited.getOpPackageName().equals("android"));
    rejected(() -> ShellIdentity.validate(inherited));
    Context shell = ShellIdentity.create(system, new ActivityThread());
    check(shell.getPackageName().equals("com.android.shell"));
    check(shell.getOpPackageName().equals("com.android.shell"));
    check(shell.getAttributionSource().getUid() == 2000);
    check(AppOpsManager.checks == 1);
    rejected(
        () ->
            ShellIdentity.validate(
                new ContextImpl("com.android.shell", "com.android.shell", 1000)));
    rejected(() -> ShellIdentity.validate(new ContextImpl("other.app", "com.android.shell", 2000)));
    System.out.println("Shell identity regression checks passed");
  }

  private static void rejected(Runnable action) {
    try {
      action.run();
    } catch (SecurityException expected) {
      return;
    }
    throw new AssertionError("Invalid caller identity accepted");
  }

  private static void check(boolean condition) {
    if (!condition) throw new AssertionError();
  }
}
