package renderfarm;

public class NoAvailableInstancesException extends Exception {

  public NoAvailableInstancesException() {
    super();
  }

  public NoAvailableInstancesException(String message) {
    super(message);
  }
}
