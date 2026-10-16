package android.net.wifi;

public final class SoftApConfiguration {
  public final String ssid, pass;
  public final int security, band;
  public final boolean restricted;

  public SoftApConfiguration(String s, String p, int security, int band, boolean restricted) {
    ssid = s;
    pass = p;
    this.security = security;
    this.band = band;
    this.restricted = restricted;
  }

  public String getSsid() {
    return ssid;
  }

  public String getPassphrase() {
    return pass;
  }

  public int getSecurityType() {
    return security;
  }

  public static final class Builder {
    String s, p;
    int security, band;
    boolean restricted;

    public Builder(SoftApConfiguration c) {
      s = c.ssid;
      p = c.pass;
      security = c.security;
      band = c.band;
      restricted = c.restricted;
    }

    public Builder setSsid(String s) {
      this.s = s;
      return this;
    }

    public Builder setPassphrase(String p, int security) {
      this.p = p;
      this.security = security;
      return this;
    }

    public SoftApConfiguration build() {
      return new SoftApConfiguration(s, p, security, band, restricted);
    }
  }
}
