package android.app;

import android.content.*;

/** Models AOSP's container-context attribution inheritance, not an Android runtime emulator. */
public class ContextImpl extends Context {
  private final LoadedApk mPackageInfo;
  private final String op;
  private final int uid;

  public ContextImpl(String name, String op, int uid) {
    this.mPackageInfo = new LoadedApk(name);
    this.op = op;
    this.uid = uid;
  }

  @Override
  public Context createPackageContext(String name, int flags) {
    return new ContextImpl(name, op, uid);
  }

  private static ContextImpl createAppContext(ActivityThread t, LoadedApk apk, String op) {
    return new ContextImpl(apk.name, op, 2000);
  }

  @Override
  public String getPackageName() {
    return mPackageInfo.name;
  }

  @Override
  public String getOpPackageName() {
    return op;
  }

  @Override
  public AttributionSource getAttributionSource() {
    return new AttributionSource(uid, op);
  }

  @Override
  public <T> T getSystemService(Class<T> type) {
    return type.cast(new AppOpsManager());
  }
}
