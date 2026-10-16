package android.content;

public abstract class Context {
  public static final int CONTEXT_IGNORE_SECURITY = 2;

  public abstract Context createPackageContext(String name, int flags);

  public abstract String getPackageName();

  public abstract String getOpPackageName();

  public abstract AttributionSource getAttributionSource();

  public abstract <T> T getSystemService(Class<T> type);
}
