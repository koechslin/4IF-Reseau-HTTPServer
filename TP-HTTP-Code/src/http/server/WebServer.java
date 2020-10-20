package http.server;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class WebServer {

  static Socket remote;
  static BufferedReader in;
  static PrintWriter out;
  static int port = 3000;

  protected void start() {
    ServerSocket s;

    System.out.println("Webserver starting up on port " + port);
    System.out.println("(press ctrl-c to exit)");
    try {
      // Création de la socket d'écoute
      s = new ServerSocket(port);
    } catch (Exception e) {
      System.out.println("Error: " + e);
      return;
    }

    System.out.println("Waiting for connection");
    while(true) {
      try {
        // Attend une connexion
        remote = s.accept();
        // remote est connecté
        System.out.println("Connection, sending data.");
        in = new BufferedReader(new InputStreamReader(remote.getInputStream()));

        StringBuilder requestBuilder = new StringBuilder();
        String line;

        // Récupération des headers
        while(!(line = in.readLine()).isBlank()) {
          requestBuilder.append(line + "\r\n");
        }        

        String request = requestBuilder.toString();
        String[] requestSplit = request.split("\r\n");
        String[] requestLine = requestSplit[0].split(" ");
        String method = requestLine[0];
        String path = requestLine[1];
        String version = requestLine[2];
        String host = requestSplit[1].split(" ")[1];

        List<String> headers = new ArrayList<String>();
        for (int h = 2; h < requestSplit.length; h++) {
          String header = requestSplit[h];
          headers.add(header);
        }

        // Récupération du body

        // Taille du body
        int contentLength = -1;
        for (String h : headers) {
          if (h.startsWith("Content-Length")) {
            contentLength = Integer.parseInt(h.substring(16));
            break;
          }
        }

        // Lecture du body
        char[] tempBuf = null;
        if (contentLength != -1) {
          tempBuf = new char[contentLength];
          in.read(tempBuf, 0, contentLength);
        }

        String body = null;
        if (tempBuf != null) {
          body = String.valueOf(tempBuf);
        }

        switch(method) {
          case "GET":
            GETRequest(path, remote);
            break;
          case "DELETE":
            DELETERequest(path, remote);
            break;
          case "PUT":
            PUTRequest(path, body, remote);
            break;
          case "HEAD":
            HEADRequest(path, remote);
            break;
        }

        remote.close();
        
      } catch (Exception e) {
        System.out.println("Error : " + e);
      }
    }
  }

  private static String guessContentType(Path filePath) throws IOException {
    return Files.probeContentType(filePath);
  }

  private static Path getFilePath(String path) {
    if ("/".equals(path)) {
        return Paths.get("/");
    }
    return Paths.get("./http/ressources", path);
  }

  private static void sendResponse(Socket client, String status, String contentType, byte[] content, String requestType) throws IOException {
    PrintWriter pwOut = new PrintWriter(client.getOutputStream());
    BufferedOutputStream buffOut = new BufferedOutputStream(client.getOutputStream());

    pwOut.println("HTTP/1.1 " + status);
    if (requestType.equals("GET") || requestType.equals("HEAD")) {
      pwOut.println("ContentType: " + contentType);
      pwOut.println("Content-Encoding: UTF-8");
      pwOut.println("Content-Length: " + content.length);
    }
    pwOut.println("Server: WebServer Java (Killian)");
    pwOut.println("Connection: close");
    pwOut.println("");
    pwOut.flush();

    if (requestType.equals("GET")) {
      buffOut.write(content, 0, content.length);
      buffOut.flush();
    }

    client.close();
  }

  private static void GETRequest(String path, Socket client) throws IOException {

    if (path.equals("/")) {
      // Liste toutes les ressources
      String res = "<h1>Liste des ressources : </h1><ul>";
      File directoryPath = new File("./http/ressources");
      String[] files = directoryPath.list();
      for (String file : files) {
          res += "<li><a href=\"/" + file + "\">" + file + "</a>" + "</li>";
      }
      res += "</ul>";
      sendResponse(client, "200 OK", "text/html", res.getBytes(), "GET");
    }
    else {
        Path filePath = getFilePath(path);
        if (Files.exists(filePath)) {
            // Le fichier existe
            String contentType = guessContentType(filePath);
            sendResponse(client, "200 OK", contentType, Files.readAllBytes(filePath), "GET");
        } else {
            // Le fichier n'existe pas : 404
            byte[] notFoundContent = "<h1>Not found :(</h1>".getBytes();
            sendResponse(client, "404 Not Found", "text/html", notFoundContent, "GET");
        }
    }
  }

  private static void DELETERequest(String path, Socket client) throws IOException {

    Path filePath = getFilePath(path);

    if (Files.exists(filePath)) {
        // Le fichier existe
        File fileToDelete = filePath.toFile();

        if(fileToDelete.delete()) {
            byte[] deleteConfirmation = "{\"success\": \"true\"}".getBytes();
            sendResponse(client, "200 OK", "application/json", deleteConfirmation, "DELETE");
        } else {
            byte[] deleteFailure = "{\"success\": \"false\"}".getBytes();
            sendResponse(client, "500 Internal Server Error", "application/json", deleteFailure, "DELETE");
        }
    }
    else{
        byte[] fileNotFound = "{\"success\": \"false\"}".getBytes();
        sendResponse(client, "404 Not Found", "application/json", fileNotFound, "DELETE");
    }
  }

  private static void PUTRequest(String path,String newContent,Socket client) throws IOException {

    Path filePath = getFilePath(path);

    String statusCode = "";

    if (Files.exists(filePath)) {
      statusCode = "200 OK";
    } else {
      statusCode = "201 Created";
    }

    File fileToPut = filePath.toFile();
    FileWriter fWriter = new FileWriter(fileToPut.getAbsolutePath(), false);
    try {
        fWriter.append(newContent);
        fWriter.flush();
        sendResponse(client, statusCode, null, null, "PUT");
    } catch (Exception e) {
        System.out.println("Error when writing to file : " + e);
        sendResponse(client, "500 Internal Server Error", null, null, "PUT");
    }
  }

  private static void HEADRequest(String path, Socket client) throws IOException {
    // Méthode similaire à GETRequest, mais on n'envoie pas le body
    if (path.equals("/")) {
      String res = "<h1>Liste des ressources : </h1><ul>";
      File directoryPath = new File("./http/ressources");
      String[] files = directoryPath.list();
      for (String file : files) {
          res += "<li><a href=\"/" + file + "\">" + file + "</a>" + "</li>";
      }
      res += "</ul>";
      sendResponse(client, "200 OK", "text/html", res.getBytes(), "HEAD");
    }
    else {
        Path filePath = getFilePath(path);
        if (Files.exists(filePath)) {
            // Le fichier existe
            String contentType = guessContentType(filePath);
            sendResponse(client, "200 OK", contentType, Files.readAllBytes(filePath), "HEAD");
        } else {
            // Le fichier n'existe pas : 404
            byte[] notFoundContent = "<h1>Not found :(</h1>".getBytes();
            sendResponse(client, "404 Not Found", "text/html", notFoundContent, "HEAD");
        }
    }
  }

  public static void main(String args[]) {
    WebServer ws = new WebServer();
    ws.start();
  }
}
