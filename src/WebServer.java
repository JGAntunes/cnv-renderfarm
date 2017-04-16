import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import raytracer.*;

public class WebServer {

  public static final String INPUT_FILES_DIR = "input";
  public static final String OUTPUT_FILES_DIR = "output";

  public static final String ROUTE_PATH = "/r.html";
  
  public static final String MODEL_FILENAME_PARAM = "f";
  public static final String SCENE_COLUMNS_PARAM = "sc";
  public static final String SCENE_ROWS_PARAM = "sr";
  public static final String WINDOW_COLUMNS_PARAM = "wc";
  public static final String WINDOW_ROWS_PARAM = "wr";
  public static final String COLUMNS_OFFSET_PARAM = "coff";
  public static final String ROWS_OFFSET_PARAM = "roff";
  
  public static final Integer PORT = 8000;

  public static void main(String[] args) throws Exception {
    // Let's bind to all the supported ipv4 interfaces
    HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
    server.createContext(ROUTE_PATH, new RenderHandler());
    // Multi thread support
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
    System.out.println("Server running on port: " + PORT);
  }

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

  static class RenderHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange req) throws IOException {
      String outError = "Invalid Request";
      OutputStream os = req.getResponseBody();
      Map<String,String> queryParams = WebServer.getQueryParameters(req);
      Integer response = 400;
      try {
        int scols = Integer.parseInt(queryParams.get(SCENE_COLUMNS_PARAM));
        int srows = Integer.parseInt(queryParams.get(SCENE_ROWS_PARAM));
	      int wcols = Integer.parseInt(queryParams.get(WINDOW_COLUMNS_PARAM));
        int wrows = Integer.parseInt(queryParams.get(WINDOW_ROWS_PARAM));
        int coff = Integer.parseInt(queryParams.get(COLUMNS_OFFSET_PARAM));
        int roff = - Integer.parseInt(queryParams.get(ROWS_OFFSET_PARAM));
        
        String fileName = queryParams.get(MODEL_FILENAME_PARAM);
        File inFile = new File(INPUT_FILES_DIR + "/" + fileName);
        File outFile = new File(OUTPUT_FILES_DIR + "/" + fileName + ".bmp");
        
        RayTracer rayTracer = new RayTracer(scols, srows, wcols, wrows, coff, roff);
		    
        rayTracer.readScene(inFile);
        rayTracer.draw(outFile);

        response = 200;
        req.getResponseHeaders().set("Content-Type", "image/bmp");
        req.sendResponseHeaders(response, outFile.length());
        Files.copy(outFile.toPath(), os);
      } catch (NumberFormatException e) {
        outError += e.getMessage();
        response = 400;
        req.sendResponseHeaders(response, outError.length());
        os.write(outError.getBytes());
      } catch (Exception e) {
        outError = "Internal Server Error: " + e.getMessage();
        response = 500;
        req.sendResponseHeaders(response, outError.length());
        os.write(outError.getBytes());
      } finally {
        os.close();
      }
    }
  }

}
