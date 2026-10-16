package uk.me.theflat.hotspot;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

/** A linear minute scale with discrete, keyboard and TalkBack-accessible stops. */
final class TimerSlider extends SeekBar {
  TimerSlider(Context c) {
    super(c);
    setMax(120);
  }

  private void step(boolean forward) {
    setProgress(SessionRules.nextMinutes(getProgress(), forward));
  }

  @Override
  public boolean onKeyDown(int key, KeyEvent event) {
    if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
      step(key == KeyEvent.KEYCODE_DPAD_RIGHT);
      return true;
    }
    return super.onKeyDown(key, event);
  }

  @Override
  public boolean performAccessibilityAction(int action, Bundle args) {
    if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
      step(action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
      return true;
    }
    return super.performAccessibilityAction(action, args);
  }
}
