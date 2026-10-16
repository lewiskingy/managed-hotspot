package uk.me.theflat.hotspot;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared native styles and small line icons for Keeper's screens. */
final class KeeperUi {
  static final int BG = 0xff071827,
      CARD = 0xff102b3b,
      LINE = 0xff254454,
      WHITE = 0xffeef6fa,
      MUTED = 0xffabc5d5,
      MINT = 0xff28e6b5,
      RED = 0xffe86b73,
      AMBER = 0xfff2c66a;

  static int dp(Context c, float n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable shape(Context c, int color, int radius, boolean border) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    if (border) d.setStroke(dp(c, 1), LINE);
    return d;
  }

  static void touch(View v, int color, int radius) {
    v.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x3028e6b5), shape(v.getContext(), color, radius, false), null));
    v.setMinimumHeight(dp(v.getContext(), 48));
  }

  static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(1);
    return l;
  }

  static TextView text(Context c, String value, int size, int color, boolean bold) {
    TextView t = new TextView(c);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("tnum");
    if (bold) t.setTypeface(null, 1);
    return t;
  }

  static final class Icon extends View {
    private final String kind;
    private final int color;
    private final Paint p = new Paint(3);

    Icon(Context c, String kind, int color) {
      super(c);
      this.kind = kind;
      this.color = color;
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    protected void onDraw(Canvas c) {
      super.onDraw(c);
      c.save();
      c.scale(getWidth() / 24f, getHeight() / 24f);
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.7f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      switch (kind) {
        case "back":
          path(c, 15, 5, 8, 12, 15, 19);
          break;
        case "next":
          path(c, 9, 5, 16, 12, 9, 19);
          break;
        case "down":
          path(c, 5, 9, 12, 16, 19, 9);
          break;
        case "gear":
          c.drawCircle(12, 12, 6, p);
          c.drawCircle(12, 12, 2, p);
          for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45, 12, 12);
            c.drawLine(12, 2, 12, 5, p);
            c.restore();
          }
          break;
        case "shield":
          path(c, 12, 2, 21, 6, 19, 15, 12, 22, 5, 15, 3, 6, 12, 2);
          path(c, 8, 11, 11, 14, 16, 8);
          break;
        case "phone":
          c.drawRoundRect(6, 2, 18, 22, 2, 2, p);
          c.drawLine(10, 5, 14, 5, p);
          c.drawLine(10, 19, 14, 19, p);
          break;
        case "devices":
          c.drawRoundRect(2, 3, 18, 16, 1, 1, p);
          c.drawLine(1, 20, 13, 20, p);
          c.drawRoundRect(15, 10, 22, 22, 1, 1, p);
          break;
        case "book":
          path(
              c, 12, 5, 7, 3, 2, 4, 2, 20, 7, 19, 12, 21, 17, 19, 22, 20, 22, 4, 17, 3, 12, 5, 12,
              21);
          break;
        case "pulse":
          path(c, 1, 12, 5, 12, 8, 3, 12, 21, 16, 8, 19, 12, 23, 12);
          break;
        case "stop":
          p.setStyle(Paint.Style.FILL);
          c.drawRoundRect(6, 6, 18, 18, 2, 2, p);
          break;
        case "plus":
          c.drawLine(12, 4, 12, 20, p);
          c.drawLine(4, 12, 20, 12, p);
          break;
        case "restart":
          c.drawArc(3, 3, 21, 21, 40, 290, false, p);
          path(c, 20, 3, 20, 9, 14, 9);
          break;
        default:
          c.drawCircle(12, 13, 8, p);
          c.drawLine(12, 13, 12, 8, p);
          c.drawLine(9, 2, 15, 2, p);
          c.drawLine(12, 2, 12, 5, p);
          break;
      }
      c.restore();
    }

    private void path(Canvas c, float... xy) {
      Path a = new Path();
      a.moveTo(xy[0], xy[1]);
      for (int i = 2; i < xy.length; i += 2) a.lineTo(xy[i], xy[i + 1]);
      c.drawPath(a, p);
    }
  }
}
