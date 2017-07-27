package renderfarm;

import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.*;
import java.util.*;

public class WebUtils {

  private WebUtils () {}

  public static final String RENDER_PATH = "/r.html";
  public static final String HEALTHCHECK_PATH = "/healthcheck";

  public static Map<String, String> getQueryParameters(HttpExchange req) {
    Map<String, String> result = new HashMap<String, String>();
    String query = req.getRequestURI().getQuery();
    if (query == null) return result;
    for (String param : query.split("&")) {
      String pair[] = param.split("=");
      if (pair.length>1) {
        result.put(pair[0], pair[1]);
      }else{
        result.put(pair[0], "");
      }
    }
    return result;
  }

  public static void pipe(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[1024];
    for (int n = in.read(buffer); n >= 0; n = in.read(buffer))
        out.write(buffer, 0, n);
  }

  public static void simpleReply(HttpExchange request, int code, String message) throws IOException {
    OutputStream os = request.getResponseBody();
    request.sendResponseHeaders(code, message.length());
    os.write(message.getBytes());
    os.close();
  }

  public static HttpURLConnection request(String method, String urlString, int timeout) throws IOException {
    URL url = new URL(urlString);
    // Make the request
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setConnectTimeout(timeout); //set the timeout
    conn.setReadTimeout(timeout); //set the timeout
    conn.setRequestMethod(method);
    conn.connect();
    return conn;
  }
}
