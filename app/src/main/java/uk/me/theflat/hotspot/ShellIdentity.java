package uk.me.theflat.hotspot;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Process;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Builds a root shell context, without inheriting the system context's AppOps attribution. */
final class ShellIdentity {
  static final String PACKAGE = "com.android.shell";

  private ShellIdentity() {}

  static Context create(Context system, Object activityThread) throws Exception {
    Context packageContext = system.createPackageContext(PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
    // createPackageContext() copies the container's base/op package. In a Shizuku user-service
    // that container belongs to "android", even though Linux/Binder calls run as shell (UID 2000).
    // createAppContext() constructs a fresh ContextImpl with no container or inherited service
    // cache.
    Class<?> contextImpl = Class.forName("android.app.ContextImpl");
    Field loadedPackage = contextImpl.getDeclaredField("mPackageInfo");
    loadedPackage.setAccessible(true);
    Object loaded = loadedPackage.get(packageContext);
    Method factory =
        contextImpl.getDeclaredMethod(
            "createAppContext",
            Class.forName("android.app.ActivityThread"),
            Class.forName("android.app.LoadedApk"),
            String.class);
    factory.setAccessible(true);
    Context shell = (Context) factory.invoke(null, activityThread, loaded, PACKAGE);
    validate(shell);
    return shell;
  }

  static void validate(Context context) {
    if (!PACKAGE.equals(context.getPackageName())
        || !PACKAGE.equals(context.getOpPackageName())
        || !PACKAGE.equals(context.getAttributionSource().getPackageName())
        || context.getAttributionSource().getUid() != Process.myUid()) {
      throw new SecurityException("Controller shell attribution is inconsistent; no rules changed");
    }
    // Ask Android to validate real package ownership, rather than merely trusting a package string.
    context.getSystemService(AppOpsManager.class).checkPackage(Process.myUid(), PACKAGE);
  }
}
