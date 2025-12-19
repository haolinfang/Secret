package com.ionicframework.cordova.webview.online;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebResourceResponse;

import com.ionicframework.cordova.webview.WebViewLocalServer;
import com.ionicframework.cordova.webview.bs.WebViewSharedState;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * WebView在线资源服务器 - 负责拦截WebView请求并处理缓存
 */
public class WebViewOnLineServer {

  // 重定向过滤器接口
  public interface RedirectFilter {
    boolean shouldRedirect(Uri uri);
  }

  private static final String TAG = "WebViewOnLineServer";
  private Context mContext;
  private OkHttpClient okHttpClient;
  private String onlineBaseUrl;
  private RedirectFilter redirectFilter;
  private Map<String, String> onlineRequestHeaders;
  private OnLineCacheManager cacheManager;
  private String currentVersion;

  public WebViewOnLineServer(Context context) {
    this.mContext = context;
    this.cacheManager = OnLineCacheManager.getInstance(context);

    initOkHttpClient();

    // 初始化请求头
    onlineRequestHeaders = new HashMap<>();
    onlineRequestHeaders.put("User-Agent", "Mozilla/5.0 (Android; WebView)");
    onlineRequestHeaders.put("X-Custom-Header", "CustomValue");
    onlineRequestHeaders.put("Authorization", "token");

    setRedirectFilter(new RedirectFilter() {
      @Override
      public boolean shouldRedirect(Uri uri) {
        String path = uri.getPath();
        return path != null && (path.startsWith("/build/") || path.startsWith("/assets/"));
      }
    });
  }

  /**
   * 设置当前资源版本
   * @param version 版本号，如 "2025121800"
   */
  public void setVersion(String version) {
    this.currentVersion = version;
    Log.i(TAG, "WebViewOnLineServer版本设置为: " + version);
  }

  /**
   * 获取当前版本
   */
  public String getCurrentVersion() {
    return currentVersion;
  }

  private void initOkHttpClient() {
    okHttpClient = new OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .build();
  }

  // 设置重定向过滤器
  public void setRedirectFilter(RedirectFilter filter) {
    this.redirectFilter = filter;
  }

  /**
   * 设置在线基础URL
   */
  public void setOnlineBaseUrl(String baseUrl) {
    this.onlineBaseUrl = baseUrl;
  }

  /**
   * 添加自定义请求头
   */
  public void addRequestHeader(String key, String value) {
    onlineRequestHeaders.put(key, value);
  }

  public WebResourceResponse redirectToOnline(Uri uri, WebViewLocalServer.PathHandler handler) {
    String relativePath = uri.getPath();
    if (TextUtils.isEmpty(relativePath)) {
      return createErrorResponse("无效的路径", 400, uri.getPath());
    }

    if (TextUtils.isEmpty(this.onlineBaseUrl)) {
      this.onlineBaseUrl = "https://xxx.yyy.zzz/android/www/";
    }

    // 检查当前版本是否已设置
    if (TextUtils.isEmpty(currentVersion)) {
      // 尝试从缓存管理器获取当前版本

      String cachedVersion = cacheManager.getCurrentVersion();
      if (cachedVersion != null) {
        this.currentVersion = cachedVersion;
        Log.i(TAG, "从缓存管理器获取版本: " + cachedVersion);
      } else {
        Log.w(TAG, "版本未设置，无法处理请求: " + relativePath);
        return createErrorResponse("版本未初始化", 503, relativePath);
      }
    }

    // 1. 首先检查本地缓存
    if (cacheManager != null && cacheManager.isVersionSet()) {
      if (cacheManager.isResourceCached(relativePath)) {
        Log.d(TAG, "缓存命中: " + relativePath);
        try {
          InputStream cachedStream = cacheManager.getCachedResourceAsStream(relativePath);
          if (cachedStream != null) {
            String mimeType = MimeTypeUtil.guessMimeTypeFromUrl(relativePath);

            Map<String, String> responseHeaders = new HashMap<>();
            responseHeaders.put("X-Cache", "HIT");
            responseHeaders.put("X-Version", currentVersion);
            responseHeaders.put("Content-Type", mimeType + "; charset=UTF-8");
            responseHeaders.put("Cache-Control", "public, max-age=31536000");

            return createWebResourceResponse(mimeType, "UTF-8", 200,
              "OK", responseHeaders, cachedStream);
          }
        } catch (Exception e) {
          Log.e(TAG, "读取缓存失败: " + relativePath, e);
          // 如果缓存读取失败，继续在线获取
        }
      }
    }

    try {
      // 2. 缓存未命中，从网络获取
      // 构建完整的在线URL
      if (TextUtils.isEmpty(relativePath) || relativePath.equals("/")) {
        relativePath = "";
      } else if (relativePath.startsWith("/")) {
        relativePath = relativePath.substring(1);
      }

      // 构建最终的URL
      String onlineUrl;
      if (onlineBaseUrl.endsWith("/")) {
        onlineUrl = onlineBaseUrl + relativePath;
      } else {
        onlineUrl = onlineBaseUrl + "/" + relativePath;
      }

      // 处理查询参数
      String query = uri.getQuery();
      if (!TextUtils.isEmpty(query)) {
        onlineUrl += "?" + query;
      }

      Log.d(TAG, "缓存未命中，从网络获取: " + onlineUrl);

      // 构建OkHttp请求
      Request.Builder requestBuilder = new Request.Builder()
        .url(onlineUrl)
        .get();

      // 添加自定义请求头
      for (Map.Entry<String, String> header : onlineRequestHeaders.entrySet()) {
        requestBuilder.addHeader(header.getKey(), header.getValue());
      }

      // 执行请求
      Response response = okHttpClient.newCall(requestBuilder.build()).execute();

      // 获取响应信息
      int statusCode = response.code();

      // 只缓存成功的响应
      if (statusCode == 200) {
        String mimeType = null;
        String charset = "UTF-8";

        // 处理响应头
        Map<String, String> responseHeaders = new HashMap<>();
        for (String headerName : response.headers().names()) {
          responseHeaders.put(headerName, response.header(headerName));
        }

        // 获取Content-Type
        String contentType = response.header("Content-Type");
        if (contentType != null) {
          // 解析Content-Type获取mimeType和charset
          String[] contentTypeParts = contentType.split(";");
          mimeType = contentTypeParts[0].trim();

          for (String part : contentTypeParts) {
            if (part.trim().toLowerCase().startsWith("charset=")) {
              charset = part.split("=")[1].trim();
            }
          }
        }

        // 如果无法从响应头获取mimeType，尝试从URL推断
        if (TextUtils.isEmpty(mimeType)) {
          mimeType = MimeTypeUtil.guessMimeTypeFromUrl(uri.getPath());
        }

        ResponseBody responseBody = response.body();
        if (responseBody != null) {
          // 关键修改：读取响应体到字节数组（用于缓存）
          byte[] responseData = responseBody.bytes();

          // 关键修改：缓存到本地
          if (cacheManager != null) {
            boolean cached = cacheManager.cacheResource(relativePath, responseData);
            if (cached) {
              Log.d(TAG, "资源缓存成功: " + relativePath);
            } else {
              Log.w(TAG, "资源缓存失败: " + relativePath);
            }
          }

          // 关键修改：从字节数组创建输入流（用于响应）
          InputStream inputStream = new ByteArrayInputStream(responseData);

          // 添加缓存相关的响应头
          responseHeaders.put("X-Cache", "MISS");
          responseHeaders.put("X-Version", currentVersion);

          return createWebResourceResponse(mimeType, charset, statusCode,
            ErrorReason.getReasonPhrase(statusCode), responseHeaders, inputStream);
        }
      }

      // 非200响应或不支持缓存的情况
      return createErrorResponse("请求失败，状态码: " + statusCode, statusCode, relativePath);

    } catch (SocketTimeoutException e) {
      Log.e(TAG, "在线请求超时: " + uri.toString(), e);
      return createErrorResponse("请求超时", 504, relativePath);
    } catch (IOException e) {
      Log.e(TAG, "网络错误，重定向到在线资源失败: " + uri.toString(), e);
      return createErrorResponse("网络错误", 502, relativePath);
    } catch (Exception e) {
      Log.e(TAG, "重定向到在线资源时出错: " + uri.toString(), e);
      return createErrorResponse("加载在线资源失败", 500, relativePath);
    }
  }

  private WebResourceResponse createErrorResponse(String message, int statusCode, String relativePath) {
    return ErrorResponse.createErrorResponse(message, statusCode, null, relativePath);
  }

  private static WebResourceResponse createWebResourceResponse(String mimeType, String encoding, int statusCode, String reasonPhrase, Map<String, String> responseHeaders, InputStream data) {
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return new WebResourceResponse(mimeType, encoding, statusCode, reasonPhrase, responseHeaders, data);
    } else {
      return new WebResourceResponse(mimeType, encoding, data);
    }
  }

  public boolean shouldRedirectToOnline(Uri uri) {
    if (onlineBaseUrl == null || onlineBaseUrl.isEmpty()) {
      return false;
    }

    // 如果设置了过滤器，使用过滤器
    if (redirectFilter != null) {
      return redirectFilter.shouldRedirect(uri);
    }

    // 否则使用默认逻辑
    return true;
  }
}
