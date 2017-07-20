package renderfarm;

public class Logger {

  String scope;
  String formatString;

  public Logger(String scope) {
    this.scope = scope;
    this.formatString = "[%s] (%s) - %s";
  }

  private void write(String level, String msg) {
    System.out.println(String.format(this.formatString, level, this.scope, msg));
  }

  public Logger getChild(String scope) {
    return new Logger(this.scope + " > " + scope);
  }

  public void warning(String msg) {
    this.write("warning", msg);
  }

  public void error(String msg) {
    this.write("error", msg);
  }

  public void log(String msg) {
    this.write("info", msg);
  }

  public void debug(String msg) {
    this.write("debug", msg);
  }
}
