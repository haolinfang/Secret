package com.ionicframework.cordova.webview.online;

public class MimeTypeUtil {
  public static String guessMimeTypeFromUrl(String path) {
    if (path == null) {
      return "application/octet-stream";
    }

    String lowerPath = path.toLowerCase();

    if (lowerPath.endsWith(".html") || lowerPath.endsWith(".htm")) {
      return "text/html";
    } else if (lowerPath.endsWith(".js")) {
      return "application/javascript";
    } else if (lowerPath.endsWith(".css")) {
      return "text/css";
    } else if (lowerPath.endsWith(".png")) {
      return "image/png";
    } else if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
      return "image/jpeg";
    } else if (lowerPath.endsWith(".gif")) {
      return "image/gif";
    } else if (lowerPath.endsWith(".svg")) {
      return "image/svg+xml";
    } else if (lowerPath.endsWith(".json")) {
      return "application/json";
    } else if (lowerPath.endsWith(".xml")) {
      return "application/xml";
    } else if (lowerPath.endsWith(".pdf")) {
      return "application/pdf";
    } else if (lowerPath.endsWith(".woff")) {
      return "font/woff";
    } else if (lowerPath.endsWith(".woff2")) {
      return "font/woff2";
    } else if (lowerPath.endsWith(".ttf")) {
      return "font/ttf";
    } else if (lowerPath.endsWith(".eot")) {
      return "application/vnd.ms-fontobject";
    } else if (lowerPath.endsWith(".otf")) {
      return "font/otf";
    } else if (lowerPath.endsWith(".wasm")) {
      return "application/wasm";
    } else if (lowerPath.endsWith(".mp4")) {
      return "video/mp4";
    } else if (lowerPath.endsWith(".webm")) {
      return "video/webm";
    } else if (lowerPath.endsWith(".mp3")) {
      return "audio/mpeg";
    } else if (lowerPath.endsWith(".wav")) {
      return "audio/wav";
    } else if (lowerPath.endsWith(".ogg")) {
      return "audio/ogg";
    } else {
      return "application/octet-stream";
    }
  }
}
