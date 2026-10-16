import java.io.*;
import uk.me.theflat.hotspot.BoundedInput;

public class BoundedInputTest {
  public static void main(String[] args) throws Exception {
    if (BoundedInput.read(new ByteArrayInputStream(new byte[4]), 4).length != 4)
      throw new AssertionError();
    try {
      BoundedInput.read(new ByteArrayInputStream(new byte[5]), 4);
      throw new AssertionError();
    } catch (IOException expected) {
    }
    if (BoundedInput.read(new ByteArrayInputStream(new byte[0]), 0).length != 0)
      throw new AssertionError();
    System.out.println("Bounded document checks passed");
  }
}
