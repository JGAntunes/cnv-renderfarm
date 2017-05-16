package renderfarm;

import com.sun.net.httpserver.HttpExchange;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

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
}
