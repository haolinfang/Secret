package com.ionicframework.online.core;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebResourceResponse;

import com.china.ncbcmbs.Constants;
import com.ionicframework.online.interceptor.LoggingInterceptor;
import com.ionicframework.online.resload.ErrorReason;
import com.ionicframework.online.resload.ErrorResponse;
import com.ionicframework.online.utils.MimeTypeUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * WebView在线资源服务器 - 负责拦截WebView请求并处理缓存，支持文件完整性校验
 */
public class WebViewOnLineServer {

  // 重定向过滤器接口
  public interface RedirectFilter {
    boolean shouldRedirect(Uri uri);
  }

  private static final String TAG = "WebViewOnLineServer";

  private OkHttpClient okHttpClient;
  private String onlineBaseUrl;
  private RedirectFilter redirectFilter;
  private Map<String, String> onlineRequestHeaders;
  private OnLineCacheManager cacheManager;
  private String currentResourcePath;
  private HashFileDownloader hashFileDownloader;
  private PreferenceHelper sharedState;

  public WebViewOnLineServer(Context context) {
    this.cacheManager = OnLineCacheManager.getInstance(context);
    this.sharedState = PreferenceHelper.getInstance(context);
    this.hashFileDownloader = new HashFileDownloader(context);

    // 初始化请求头
    onlineRequestHeaders = new HashMap<>();
    onlineRequestHeaders.put("User-Agent", "Mozilla/5.0 (Android; WebView)");
    onlineRequestHeaders.put("X-Custom-Header", "CustomValue");

    okHttpClient = new OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
      .addInterceptor(new LoggingInterceptor())
      .build();

    setRedirectFilter(uri -> {
      String path = uri.getPath();
      return path != null && (path.startsWith("/build/") || path.startsWith("/assets/"));
    });
  }

  // 设置重定向过滤器
  private void setRedirectFilter(RedirectFilter filter) {
    this.redirectFilter = filter;
  }

  public boolean shouldRedirectToOnline(Uri uri) {
    // 如果设置了过滤器，使用过滤器
    if (redirectFilter != null) {
      return redirectFilter.shouldRedirect(uri);
    }

    // 否则使用默认逻辑
    return false;
  }

  public WebResourceResponse redirectToOnline(Uri uri) {
    String relativePath = uri.getPath();
    if (TextUtils.isEmpty(relativePath)) {
      return createErrorResponse("无效的路径", 400, uri.getPath());
    }

    // 检查当前资源路径是否已设置
    if (TextUtils.isEmpty(currentResourcePath)) {
      // 尝试从SharedPreferences获取资源路径
      String cachedResourcePath = sharedState.getResourcePath();
      if (cachedResourcePath != null) {
        this.currentResourcePath = cachedResourcePath;
        Log.i(TAG, "从SharedPreferences获取资源路径: " + cachedResourcePath);
      } else {
        Log.w(TAG, "资源路径未设置，无法处理请求: " + relativePath);
        return createErrorResponse("资源路径未初始化", 503, relativePath);
      }
    }

    if (TextUtils.isEmpty(this.onlineBaseUrl)) {
      this.onlineBaseUrl = String.format("%s/resources/%s/www", Constants.getEnv().getIp(), currentResourcePath);
    }

    // 1. 首先检查本地缓存
    if (cacheManager != null && cacheManager.isResourcePathSet()) {
      if (cacheManager.isResourceCached(relativePath)) {
        Log.d(TAG, "缓存命中: " + relativePath);
        try {
          InputStream cachedStream = cacheManager.getCachedResourceAsStream(relativePath);
          if (cachedStream != null) {
            // 读取缓存文件进行完整性校验
            byte[] cachedData = readInputStreamToBytes(cachedStream);

            // 校验文件完整性
            boolean isValid = hashFileDownloader.verifyFileIntegrity(relativePath, cachedData);

            if (isValid) {
              String mimeType = MimeTypeUtils.guessMimeTypeFromUrl(relativePath);

              Map<String, String> responseHeaders = new HashMap<>();
              responseHeaders.put("X-Cache", "HIT");
              responseHeaders.put("X-Integrity", "VALID");
              responseHeaders.put("X-Resource-Path", currentResourcePath);
              responseHeaders.put("Content-Type", mimeType + "; charset=UTF-8");
              responseHeaders.put("Cache-Control", "public, max-age=31536000");

              return createWebResourceResponse(mimeType, "UTF-8", 200,
                "OK", responseHeaders, new ByteArrayInputStream(cachedData));
            } else {
              Log.w(TAG, "缓存文件完整性校验失败，将重新下载: " + relativePath);
              // 只删除损坏的单个文件，而不是整个版本目录
              cacheManager.deleteCachedFile(relativePath);
            }
          }
        } catch (Exception e) {
          Log.e(TAG, "读取缓存失败: " + relativePath, e);
          // 如果缓存读取失败，继续在线获取
        }
      }
    }

    try {
      // 2. 缓存未命中或校验失败，从网络获取
      // 构建完整的在线URL
      if (TextUtils.isEmpty(relativePath) || relativePath.equals("/")) {
        relativePath = "";
      } else if (relativePath.startsWith("/")) {
        relativePath = relativePath.substring(1);
      }

      // 构建最终的URL - 使用resourcePath
      String onlineUrl;
      if (onlineBaseUrl.endsWith("/")) {
        onlineUrl = onlineBaseUrl + "/" + relativePath;
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
          mimeType = MimeTypeUtils.guessMimeTypeFromUrl(uri.getPath());
        }

        ResponseBody responseBody = response.body();
        if (responseBody != null) {
          // 关键修改：读取响应体到字节数组（用于缓存）
          byte[] responseData = responseBody.bytes();

          // 校验文件完整性
          boolean isValid = hashFileDownloader.verifyFileIntegrity(relativePath, responseData);

          if (!isValid) {
            Log.e(TAG, "文件完整性校验失败: " + relativePath);
            return createErrorResponse("文件完整性校验失败", 500, relativePath);
          }

          Log.d(TAG, "文件完整性校验通过: " + relativePath);

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
          responseHeaders.put("X-Integrity", "VALID");
          responseHeaders.put("X-Resource-Path", currentResourcePath);

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

  /**
   * 将InputStream读取为字节数组
   */
  private byte[] readInputStreamToBytes(InputStream inputStream) throws IOException {
    try {
      java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
      byte[] data = new byte[8192];
      int bytesRead;
      while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, bytesRead);
      }
      return buffer.toByteArray();
    } finally {
      try {
        inputStream.close();
      } catch (IOException e) {
        Log.e(TAG, "关闭输入流出错", e);
      }
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
}
