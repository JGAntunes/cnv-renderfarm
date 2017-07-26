package renderfarm;

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

import java.security.SecureRandom;
import java.math.BigInteger;

import raytracer.*;

public class WebServer {

  private static final String INPUT_FILES_DIR = "input";
  private static final String OUTPUT_FILES_DIR = "output";
  private static final Integer PORT = 80;

  private static final SecureRandom random = new SecureRandom();

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


  static class HealthcheckHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange req) throws IOException {
      WebUtils.simpleReply(req, 200, "OK");
    }
  }


  static class RenderHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange req) throws IOException {
      String outError = "Invalid Request";
      OutputStream os = req.getResponseBody();
      String outputFile = new BigInteger(130, random).toString(32);
      File outFile = new File(OUTPUT_FILES_DIR + "/" + outputFile + ".bmp");

      try {
        RayTracerRequest parsedRequest = new RayTracerRequest(req);
        File inFile = new File(INPUT_FILES_DIR + "/" + parsedRequest.getFileName());

        RayTracer rayTracer = new RayTracer(
          parsedRequest.getSceneColumns(),
          parsedRequest.getSceneRows(),
          parsedRequest.getWindowColumns(),
          parsedRequest.getWindowRows(),
          parsedRequest.getColumnsOffset(),
          parsedRequest.getRowsOffset()
        );

        rayTracer.readScene(inFile);
        rayTracer.draw(outFile);

        int response = 200;
        req.getResponseHeaders().set("Content-Type", "image/bmp");
        req.sendResponseHeaders(response, outFile.length());
        Files.copy(outFile.toPath(), os);
      } catch (NumberFormatException e) {
        outError += e.getMessage();
        WebUtils.simpleReply(req, 400, outError);
      } catch (Exception e) {
        outError = "Internal Server Error: " + e.getMessage();
        WebUtils.simpleReply(req, 500, outError);
      } finally {
        os.close();
        Files.deleteIfExists(outFile.toPath());
      }
    }
  }

}
