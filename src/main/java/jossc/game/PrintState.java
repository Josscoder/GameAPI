package jossc.game;

import java.time.Duration;
import net.minikloon.fsmgasm.State;
import org.jetbrains.annotations.NotNull;

class PrintState extends State {

  private final String toPrint;

  public PrintState(String toPrint) {
    this.toPrint = toPrint;
  }

  @Override
  protected void onStart() {
    System.out.println(toPrint);
  }

  @Override
  public void onUpdate() {}

  @Override
  protected void onEnd() {}

  @NotNull
  @Override
  public Duration getDuration() {
    return Duration.ofSeconds(1);
  }
}
