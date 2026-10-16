package android.content;

public final class AttributionSource {
  private final int uid;
  private final String name;

  public AttributionSource(int uid, String name) {
    this.uid = uid;
    this.name = name;
  }

  public int getUid() {
    return uid;
  }

  public String getPackageName() {
    return name;
  }
}
