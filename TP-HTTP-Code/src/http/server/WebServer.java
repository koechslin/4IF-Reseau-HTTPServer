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
import java.util.Date;
import java.util.List;

/**
 * WebServer est une classe qui représente (comme son nom l'indique) un serveur web. 
 * Ce dernier est capable de gérer différentes requêtes (GET, PUT, DELETE ...) et dispose 
 * d'un ensemble de ressources dans un dossier séparé.
 * 
 * @author Killian OECHSLIN
 * @author Thomas MIGNOT
 */
public class WebServer {

  /**
   * Socket de connexion du client.
   */
  static Socket remote;
  
  /**
   * Variable permettant de lire la requête envoyée 
   * par le client.
   */
  static BufferedReader in;

  /**
   * Variable permettant d'envoyer la réponse au client.
   */
  static PrintWriter out;

  /**
   * Numéro de port du serveur.
   */
  static int port = 3000;

  /**
   * Chemin vers les ressources du serveur.
   */
  static String ressourcesPath;

  /**
   * Lance le serveur. Ce dernier est alors capable de recevoir 
   * une connexion d'un client, de lire la requête, d'y répondre (selon 
   * son type) et de fermer la connexion avec le client.
   */
  protected void start() {
    ServerSocket s;

    System.out.println("Webserver starting up on port " + port);
    System.out.println("(press ctrl-c to exit)");
    try {
      // Création de la socket d'écoute
      s = new ServerSocket(port);
    } catch (IOException e) {
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
        while((line = in.readLine()) != null && !line.isBlank()) {
          requestBuilder.append(line + "\r\n");
        }

        String request = requestBuilder.toString();

        if (request.isBlank() || request == null) {
          remote.close();
          continue;
        }

        String[] requestSplit = request.split("\r\n");
        String[] requestLine = requestSplit[0].split(" ");
        String method = requestLine[0];
        String path = requestLine[1];
        String version = requestLine[2];
        String host = requestSplit[1].split(" ")[1];

        // Affichage du log des requêtes
        System.out.println("["+ (new Date()) + "] " + remote.getInetAddress().getHostAddress() + " : " + method + " " + path );

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
          case "POST":
            POSTRequest(path, body, remote);
            break;
          default:
            // Pour les méthodes non implémentées
            sendResponse(remote, "501 Not Implemented", null, null, null, method);
            break;
        }

        remote.close();
        
      } catch (IOException e) {
        System.out.println("Error : " + e);
      }
    }
  }

  /**
   * Permet de trouver le type du contenu d'un fichier.
   * @param filePath Le chemin vers le fichier dont on veut trouver le type.
   * @return Le type de contenu du fichier.
   * @throws IOException
   */
  protected static String guessContentType(Path filePath) throws IOException {
    return Files.probeContentType(filePath);
  }

  /**
   * Permet de trouver le chemin (sur le serveur) vers l'une des 
   * ressources du serveur.
   * @param path Le chemin de la ressource récupéré dans la requête.
   * @return Le chemin (sur le serveur) vers la ressource cherchée.
   */
  protected static Path getFilePath(String path) {
    if ("/".equals(path)) {
        return Paths.get("/");
    }
    return Paths.get(ressourcesPath, path.replaceAll("%20", " "));
  }

  /**
   * Envoie la réponse au client suite à sa requête.
   * @param client Le client (socket) visé.
   * @param status Le code de retour HTTP.
   * @param contentType Le type de la réponse.
   * @param content Le corps de la réponse.
   * @param contentLocation L'emplacement de la ressource (si besoin).
   * @param requestType Le type de requête.
   * @throws IOException
   */
  protected static void sendResponse(Socket client, String status, String contentType, byte[] content, String contentLocation, String requestType) throws IOException {
    PrintWriter pwOut = new PrintWriter(client.getOutputStream());
    BufferedOutputStream buffOut = new BufferedOutputStream(client.getOutputStream());

    pwOut.println("HTTP/1.1 " + status);
    if (requestType.equals("GET") || (requestType.equals("HEAD") && content != null) || (requestType.equals("POST") && !status.equals("303 See Other"))) {
      pwOut.println("ContentType: " + contentType);
      pwOut.println("Content-Encoding: UTF-8");
      pwOut.println("Content-Length: " + content.length);
    }
    if (requestType.equals("PUT")) {
      pwOut.println("Content-Location: " + contentLocation);
    }
    if (requestType.equals("POST") && status.equals("303 See Other")) {
      // Redirection
      pwOut.println("Location: " + contentLocation);
    }
    pwOut.println("Server: WebServer Java (Killian)");
    pwOut.println("Connection: close");
    pwOut.println("");
    pwOut.flush();

    if (requestType.equals("GET") || (requestType.equals("POST") && !status.equals("303 See Other"))) {
      buffOut.write(content, 0, content.length);
      buffOut.flush();
    }
  }
  
  /**
   * Méthode qui permet d'effectuer une requête HTTP GET.
   * @param path Le chemin de la ressource visée.
   * @param client Le client (socket) effectuant la demande.
   * @throws IOException
   */
  protected static void GETRequest(String path, Socket client) throws IOException {

    if (path.equals("/")) {
      // Liste toutes les ressources
      String res = "<h1>Liste des ressources : </h1><ul>";
      File directoryPath = new File("./http/ressources");
      String[] files = directoryPath.list();
      for (String file : files) {
          res += "<li><a href=\"/" + file.replaceAll(" ", "%20") + "\">" + file + "</a>" + "</li>";
      }
      res += "</ul>";
      sendResponse(client, "200 OK", "text/html", res.getBytes(), null, "GET");
    }
    else {
        Path filePath = getFilePath(path);
        if (Files.exists(filePath)) {
            // Le fichier existe
            String contentType = guessContentType(filePath);
            sendResponse(client, "200 OK", contentType, Files.readAllBytes(filePath), null, "GET");
        } else {
            // Le fichier n'existe pas : 404
            byte[] notFoundContent = "<h1>Not found :(</h1>".getBytes();
            sendResponse(client, "404 Not Found", "text/html", notFoundContent, null, "GET");
        }
    }
  }

  /**
   * Méthode qui permet d'effectuer une requête HTTP DELETE.
   * @param path Le chemin de la ressource à supprimer.
   * @param client Le client (socket) effectuant la demande.
   * @throws IOException
   */
  protected static void DELETERequest(String path, Socket client) throws IOException {

    Path filePath = getFilePath(path);

    if (Files.exists(filePath)) {
        // Le fichier existe
        File fileToDelete = filePath.toFile();

        if(fileToDelete.delete()) {
            sendResponse(client, "204 No Content", null, null, null, "DELETE");
        } else {
            sendResponse(client, "500 Internal Server Error", null, null, null, "DELETE");
        }
    }
    else{
        sendResponse(client, "404 Not Found", null, null, null, "DELETE");
    }
  }

  /**
   * Méthode qui permet d'effectuer une requête HTTP PUT.
   * @param path Le chemin de la ressource à modifer/créer.
   * @param newContent Le nouveau contenu.
   * @param client Le client (socket) effectuant la demande.
   * @throws IOException
   */
  protected static void PUTRequest(String path, String newContent, Socket client) throws IOException {

    Path filePath = getFilePath(path);
    String statusCode = "";

    if (Files.exists(filePath)) {
      statusCode = "204 No Content";
    } else {
      statusCode = "201 Created";
    }

    File fileToPut = filePath.toFile();
    FileWriter fWriter = new FileWriter(fileToPut.getAbsolutePath(), false);
    try {
        fWriter.append(newContent);
        fWriter.flush();
        sendResponse(client, statusCode, null, null, path, "PUT");
    } catch (Exception e) {
        System.out.println("Error when writing to file : " + e);
        sendResponse(client, "500 Internal Server Error", null, null, null, "PUT");
    }
  }

  /**
   * Méthode qui permet d'effectuer une requête HTTP HEAD.
   * @param path Le chemin de la ressource visée.
   * @param client Le client (socket) effectuant la demande.
   * @throws IOException
   */
  protected static void HEADRequest(String path, Socket client) throws IOException {
    // Méthode similaire à GETRequest, mais on n'envoie pas le body
    if (path.equals("/")) {
      String res = "<h1>Liste des ressources : </h1><ul>";
      File directoryPath = new File("./http/ressources");
      String[] files = directoryPath.list();
      for (String file : files) {
          res += "<li><a href=\"/" + file + "\">" + file + "</a>" + "</li>";
      }
      res += "</ul>";
      sendResponse(client, "204 No Content", "text/html", res.getBytes(), null, "HEAD");
    }
    else {
        Path filePath = getFilePath(path);
        if (Files.exists(filePath)) {
            // Le fichier existe
            String contentType = guessContentType(filePath);
            sendResponse(client, "204 No Content", contentType, Files.readAllBytes(filePath), null, "HEAD");
        } else {
            // Le fichier n'existe pas : 404
            sendResponse(client, "404 Not Found", null, null, null, "HEAD");
        }
    }
  }

  /**
   * Permet de formatter les nombres pour que leur représentation
   * contienne toujours au moins 2 digits (ex : 9 devient 09).
   * @param n Le nombre a formatter.
   * @return La représentation du nombre avec au moins 2 digits.
   */
  protected static String formatNumber(int n) {
    return n < 10 ? ("0" + n) : Integer.toString(n);
  }

  /**
   * Permet d'écrire dans le fichier des scores (pour le jeu du snake) un nouveau
   * score d'un joueur.
   * @param name Le nom du joueur.
   * @param score Le score du joueur.
   */
  protected static void savePlayerScore(String name, int score) {
    Path filePath = getFilePath("/scores.txt");
    File fileToPut = filePath.toFile();
    try {
      FileWriter fWriter = new FileWriter(fileToPut.getAbsolutePath(), true);
      // Avec le nom et le score on écrit également la date
      Date today = new Date();
      String newContent = "[" + today.getDate() + "/" + (today.getMonth() + 1) + "/" + (today.getYear() + 1900);
      newContent += " - " + formatNumber(today.getHours()) + "h" + formatNumber(today.getMinutes()) + "] ";
      newContent += name + " : " + score + "\n";
      fWriter.append(newContent);
      fWriter.flush();
      fWriter.close();
    } catch (IOException e) {
      System.out.println("Error in savePlayerScore : " + e);
    }
  }

  /**
   * Méthode qui permet d'effectuer une requête HTTP POST.
   * @param path La ressource visée.
   * @param body Le corps de la requête.
   * @param client Le client (socket) effectuant la demande.
   * @throws IOException
   */
  protected static void POSTRequest(String path, String body, Socket client) throws IOException {
    switch(path) {
      case "/snake.html":
        String[] infos = body.split("&");
        String playerName = infos[0].substring(5);
        int playerScore = Integer.parseInt(infos[1].substring(6));
        savePlayerScore(playerName, playerScore);
        sendResponse(client, "303 See Other", null, null, "/", "POST");
      default:
        String methodNotAllowed = "<html><body><h1>Method POST not allowed on this ressource</h1></body></html>";
        sendResponse(client, "405 Method Not Allowed", "text/html", methodNotAllowed.getBytes(), null, "POST");
        break;
    }
  }
  
  /**
   * Méthode main qui permet de démarrer le serveur web.
   * @param args Contient le chemin vers le dossier des ressources du serveur.
   */
  public static void main(String args[]) {
    if (args.length != 1) {
      System.out.println("Usage: java WebServer <Ressources path>");
      System.exit(1);
    }
    ressourcesPath = args[0];
    WebServer ws = new WebServer();
    ws.start();
  }
}