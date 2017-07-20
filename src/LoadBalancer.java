package renderfarm;

import java.nio.file.Files;
import java.io.*;
import java.net.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import raytracer.*;

public class LoadBalancer {

  public static final Integer PORT = 80;

  public static void main(String[] args) throws Exception {
    // Let's bind to all the supported ipv4 interfaces
    HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
    // Set routes
    server.createContext(WebUtils.RENDER_PATH, new RenderHandler());
    server.createContext(WebUtils.HEALTHCHECK_PATH, new HealthcheckHandler());
    // Multi thread support
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
    System.out.println("Server running on port: " + PORT);
  }

  static String getInstance() {
    List<String> availableInstances = AWSUtils.getAvailableInstances();
    return availableInstances.get(0);
  }

  static class HealthcheckHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange req) throws IOException {
      String outResponse = "OK";
      OutputStream os = req.getResponseBody();
      Integer responseCode = 200;
      req.sendResponseHeaders(responseCode, outResponse.length());
      os.write(outResponse.getBytes());
      os.close();
    }
  }


  static class RenderHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange req) throws IOException {
      // Get the instance hostname to use
      String hostName = getInstance();
      // Assemble the URL
      String urlString = "http://" + hostName + req.getRequestURI();
      URL url = new URL(urlString);
      // Make the request
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(50000); //set the timeout
      conn.connect();

      InputStream is = conn.getInputStream();
      int contentLength = conn.getContentLength();
      int responseCode = conn.getResponseCode();

      OutputStream os = req.getResponseBody();
      req.sendResponseHeaders(responseCode, contentLength);
      try {
        WebUtils.pipe(is, os);
      }
      catch (Exception e) {
        String outError = "Internal Server Error: " + e.getMessage();
        int response = 500;
        req.sendResponseHeaders(response, outError.length());
        os.write(outError.getBytes());
      } finally {
        is.close();
        os.close();
      }
    }
  }

}
