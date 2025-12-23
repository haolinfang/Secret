package com.ionicframework.online.resload;

import android.os.Build;
import android.util.Log;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ErrorResponse {
  private static String TAG = "ErrorResponse";

  /**
   * 创建带自定义HTML的错误响应
   *
   * @param message
   * @param statusCode
   * @param customHtml
   * @return
   */
  public static WebResourceResponse createErrorResponse(String message, int statusCode, String customHtml, String relativePath) {
    try {
      String errorHtml;
      if (customHtml != null) {
        errorHtml = customHtml;
      } else {
        errorHtml = "<!DOCTYPE html>\n" +
          "<html>\n" +
          "<head>\n" +
          "    <meta charset='UTF-8'>\n" +
          "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
          "    <title>" + statusCode + " " + ErrorReason.getReasonPhrase(statusCode) + "</title>\n" +
          "    <style>\n" +
          "        body {\n" +
          "            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
          "            line-height: 1.6;\n" +
          "            color: #333;\n" +
          "            max-width: 600px;\n" +
          "            margin: 0 auto;\n" +
          "            padding: 20px;\n" +
          "        }\n" +
          "        h1 {\n" +
          "            color: #d32f2f;\n" +
          "            border-bottom: 2px solid #f0f0f0;\n" +
          "            padding-bottom: 10px;\n" +
          "        }\n" +
          "        .code {\n" +
          "            background-color: #f5f5f5;\n" +
          "            padding: 2px 6px;\n" +
          "            border-radius: 3px;\n" +
          "            font-family: monospace;\n" +
          "        }\n" +
          "        .url {\n" +
          "            color: #666;\n" +
          "            font-size: 0.9em;\n" +
          "            word-break: break-all;\n" +
          "        }\n" +
          "        .btn {\n" +
          "            display: inline-block;\n" +
          "            background-color: #2196F3;\n" +
          "            color: white;\n" +
          "            padding: 8px 16px;\n" +
          "            text-decoration: none;\n" +
          "            border-radius: 4px;\n" +
          "            margin-top: 10px;\n" +
          "        }\n" +
          "    </style>\n" +
          "</head>\n" +
          "<body>\n" +
          "    <h1>" + statusCode + " - " + ErrorReason.getReasonPhrase(statusCode) + "</h1>\n" +
          "    <p>" + message + "</p>\n" +
          "    <p><span class='code'>" + statusCode + "</span> " + ErrorReason.getReasonPhrase(statusCode) + "</p>\n" +
          "    <p class='url'>Request URL: " + relativePath + "</p>\n" +
          "    <a href='javascript:window.location.reload()' class='btn'>Retry</a>\n" +
          "</body>\n" +
          "</html>";
      }

      ByteArrayInputStream errorStream = new ByteArrayInputStream(errorHtml.getBytes("UTF-8"));

      // 构建响应头
      Map<String, String> responseHeaders = new HashMap<>();
      responseHeaders.put("Cache-Control", "no-cache");
      responseHeaders.put("Content-Type", "text/html; charset=UTF-8");

      return createWebResourceResponse("text/html", "UTF-8", statusCode,
        ErrorReason.getReasonPhrase(statusCode), responseHeaders, errorStream);
    } catch (Exception e) {
      Log.e(TAG, "Error creating error response", e);
      // 如果创建错误页面失败，返回一个最简单的错误响应
      try {
        String simpleError = "<html><body><h1>" + statusCode + "</h1></body></html>";
        ByteArrayInputStream simpleStream = new ByteArrayInputStream(simpleError.getBytes("UTF-8"));
        return new WebResourceResponse("text/html", "UTF-8", simpleStream);
      } catch (Exception ex) {
        // 如果连这个都失败，返回null
        return null;
      }
    }
  }

  private static WebResourceResponse createWebResourceResponse(String mimeType, String encoding, int statusCode, String reasonPhrase, Map<String, String> responseHeaders, InputStream data) {
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return new WebResourceResponse(mimeType, encoding, statusCode, reasonPhrase, responseHeaders, data);
    } else {
      return new WebResourceResponse(mimeType, encoding, data);
    }
  }
}
