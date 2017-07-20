package renderfarm;

import java.time.*;

public class Timer {

  Instant begin;

  public Timer() {
    this.begin = Instant.now();
  }

  public long getTime() {
    return Duration.between(this.begin, Instant.now()).toMillis();
  }
}
