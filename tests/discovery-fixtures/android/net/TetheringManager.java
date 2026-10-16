package android.net;

import java.util.*;
import java.util.concurrent.Executor;

public class TetheringManager {
  public interface TetheringEventCallback {
    default void onClientsChanged(Collection<?> clients) {}
  }

  private Executor worker;
  private TetheringEventCallback callback;

  public void registerTetheringEventCallback(Executor worker, TetheringEventCallback callback) {
    this.worker = worker;
    this.callback = callback;
  }

  public void emit(Collection<?> clients) {
    worker.execute(() -> callback.onClientsChanged(clients));
  }
}
