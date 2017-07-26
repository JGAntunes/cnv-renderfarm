package renderfarm;

import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.IOException;

public class RayTracerResponse {

  private InputStream inputStream;
  private int contentLength;
  private int responseCode;

  public RayTracerResponse(HttpURLConnection conn) throws IOException {
    this.inputStream = conn.getInputStream();
    this.contentLength = conn.getContentLength();
    this.responseCode = conn.getResponseCode();
  }

  public int getContentLength() {
    return this.contentLength;
  }

  public int getResponseCode() {
    return this.responseCode;
  }

  public InputStream getInputStream() {
    return this.inputStream;
  }

  public void close() throws IOException {
    this.inputStream.close();
  }
}
