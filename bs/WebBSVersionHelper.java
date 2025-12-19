package com.ionicframework.cordova.webview.bs;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import com.china.ncbcmbs.Constants;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ionicframework.cordova.webview.secret.XcRcpError;
import com.ionicframework.cordova.webview.secret.XcRcpHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Web资源版本网络助手 - 负责网络请求和哈希文件下载
 * 纯网络层，不处理缓存逻辑
 */
public class WebBSVersionHelper {

  private static final String TAG = "WebBSVersionHelper";

  private final Context context;
  private final Gson gson;
  private final OkHttpClient okHttpClient;
  private final ExecutorService executorService;
  private final XcRcpHelper rcpHelper;

  // 单例模式
  private static WebBSVersionHelper instance;

  public static synchronized WebBSVersionHelper getInstance(Context context) {
    if (instance == null) {
      instance = new WebBSVersionHelper(context.getApplicationContext());
    }
    return instance;
  }

  private WebBSVersionHelper(Context context) {
    this.context = context.getApplicationContext();
    this.gson = new Gson();

    // 初始化OkHttpClient（用于下载文件）
    this.okHttpClient = new OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .build();

    // 创建线程池用于文件下载
    this.executorService = Executors.newFixedThreadPool(5);

    // 初始化RCP助手（用于加密API请求）
    this.rcpHelper = XcRcpHelper.getInstance();
    this.rcpHelper.init(String.format("%s/NCB/", Constants.getEnv().getIp()));

  }

  // ==================== 公共接口 ====================

  /**
   * 异步获取版本信息（主方法）
   */
  public void getVersionInfoAsync(final VersionCallback callback) {
    // 检查网络
    if (!isNetworkAvailable()) {
      if (callback != null) {
        callback.onError(new VersionError(VersionErrorType.NETWORK_UNAVAILABLE, "网络不可用"));
      }
      return;
    }

    // 在后台线程执行
    executorService.execute(new Runnable() {
      @Override
      public void run() {
        try {
          // 1. 请求版本接口
          WebBSResFileInfo versionInfo = requestVersionInfo();

          // 2. 下载并解析哈希文件
          Map<String, String> fileRecord = downloadHashFile(versionInfo);

          // 3. 返回结果
          WebBSResFile result = new WebBSResFile(versionInfo, fileRecord);

          if (callback != null) {
            callback.onSuccess(result);
          }

        } catch (VersionError e) {
          if (callback != null) {
            callback.onError(e);
          }
        }
      }
    });
  }

  /**
   * 异步下载哈希文件
   */
  public void downloadHashFileAsync(WebBSResFileInfo versionInfo, HashFileCallback callback) {
    executorService.execute(new Runnable() {
      @Override
      public void run() {
        try {
          Map<String, String> fileRecord = downloadHashFile(versionInfo);
          if (callback != null) {
            callback.onSuccess(fileRecord);
          }
        } catch (VersionError e) {
          if (callback != null) {
            callback.onError(e);
          }
        }
      }
    });
  }

  /**
   * 清除下载的哈希文件缓存
   */
  public void clearDownloadedFiles() {
    // 清理文件缓存
    File cacheDir = context.getFilesDir();
    File resourcesDir = new File(cacheDir, "resources");
    deleteDirectory(resourcesDir);

    Log.d(TAG, "下载的哈希文件已清除");
  }

  /**
   * 关闭资源
   */
  public void shutdown() {
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
    }
    if (rcpHelper != null) {
      rcpHelper.shutdown();
    }
  }

  // ==================== 私有方法 ====================

  /**
   * 请求版本信息
   */
  private WebBSResFileInfo requestVersionInfo() throws VersionError {
    try {
      // 构建请求参数
      Map<String, Object> params = new HashMap<>();
      params.put("channelNo", "mb");
      params.put("bussType", 0);
      params.put("appType", 0);
      params.put("bankAppVersion", getAppVersionName());
      params.put("deviceCode", getDeviceId());
      params.put("termIP", getIpAddress());
      params.put("operNo", getOperNo());

      // 使用CountDownLatch等待异步结果
      final Object[] resultHolder = new Object[2]; // [0]=结果, [1]=异常
      final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

      rcpHelper.postApiAsync("mAppVersionQuery", params, Map.class,
        new XcRcpHelper.RcpCallback<Map>() {
          @Override
          public void onSuccess(Map responseMap) {
            resultHolder[0] = responseMap;
            latch.countDown();
          }

          @Override
          public void onError(XcRcpError rcpError) {
            resultHolder[1] = rcpError;
            latch.countDown();
          }
        });

      // 等待异步结果
      try {
        latch.await(30, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new VersionError(VersionErrorType.API_REQUEST_TIMEOUT, "请求超时");
      }

      // 处理结果
      if (resultHolder[1] != null) {
        XcRcpError rcpError = (XcRcpError) resultHolder[1];
        handleRcpError(rcpError);
      }

      if (resultHolder[0] == null) {
        throw new VersionError(VersionErrorType.API_RESPONSE_NULL, "接口返回空");
      }

      Map<String, Object> responseMap = (Map<String, Object>) resultHolder[0];
      WebBSResFileInfo versionInfo = parseResponseToFileInfo(responseMap);

      if (versionInfo == null) {
        throw new VersionError(VersionErrorType.API_RESPONSE_ERROR, "解析响应失败");
      }

      if (!"CIP0000000".equals(versionInfo.getResCode())) {
        throw new VersionError(VersionErrorType.API_RESPONSE_ERROR,
          versionInfo.getResCode() + " - " + versionInfo.getResMsg());
      }

      return versionInfo;

    } catch (VersionError e) {
      throw e;
    } catch (Exception e) {
      Log.e(TAG, "请求版本信息失败", e);
      throw new VersionError(VersionErrorType.API_REQUEST_FAILED, "请求失败: " + e.getMessage());
    }
  }

  /**
   * 下载并解析哈希文件
   */
  private Map<String, String> downloadHashFile(WebBSResFileInfo versionInfo) throws VersionError {
    String resourcePath = versionInfo.getResourcePath();
    String resourceVersion = versionInfo.getResourceVersion();

    if (resourcePath == null || resourceVersion == null) {
      throw new VersionError(VersionErrorType.API_RESPONSE_ERROR, "资源路径或版本号为空");
    }

    File hashFile = getHashFilePath(resourcePath);
    hashFile.getParentFile().mkdirs();

    // 下载文件（如果不存在）
    if (!hashFile.exists()) {
      downloadHashFileFromServer(resourcePath, hashFile);
    }

    // 读取并解析文件
    return parseHashFile(hashFile);
  }

  // ==================== 辅助方法 ====================

  /**
   * 检查网络是否可用
   */
  private boolean isNetworkAvailable() {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (cm == null) return false;

    NetworkInfo networkInfo = cm.getActiveNetworkInfo();
    return networkInfo != null && networkInfo.isConnected();
  }

  /**
   * 处理RCP错误
   */
  private void handleRcpError(XcRcpError rcpError) throws VersionError {
    int code = rcpError.getCode();
    String message = rcpError.getMessage();

    switch (code) {
      case 503:
        throw new VersionError(VersionErrorType.SERVER_UNAVAILABLE, message);
      case 408:
      case 504:
      case 1007900028:
        throw new VersionError(VersionErrorType.API_REQUEST_TIMEOUT, message);
      default:
        throw new VersionError(VersionErrorType.API_REQUEST_FAILED, message);
    }
  }

  /**
   * 获取哈希文件路径
   */
  private File getHashFilePath(String resourcePath) {
    File cacheDir = context.getFilesDir();
    return new File(cacheDir, "resources/" + resourcePath + "/resource_hashes.json");
  }

  /**
   * 从服务器下载哈希文件
   */
  private void downloadHashFileFromServer(String resourcePath, File targetFile) throws VersionError {
    try {
      String url = String.format("%s/resources/%s/resource_hashes", Constants.getEnv().getIp(), resourcePath);
      Log.d(TAG, "下载哈希文件: " + url);

      Request request = new Request.Builder().url(url).get().build();
      Response response = okHttpClient.newCall(request).execute();

      if (!response.isSuccessful()) {
        throw new VersionError(VersionErrorType.RESOURCE_DOWNLOAD_FAILED, "HTTP " + response.code());
      }

      try (InputStream inputStream = response.body().byteStream();
           FileOutputStream outputStream = new FileOutputStream(targetFile)) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          outputStream.write(buffer, 0, bytesRead);
        }
      }

      Log.d(TAG, "哈希文件下载完成: " + targetFile.getAbsolutePath());

    } catch (IOException e) {
      Log.e(TAG, "下载哈希文件失败", e);
      throw new VersionError(VersionErrorType.RESOURCE_DOWNLOAD_FAILED, e.getMessage());
    }
  }

  /**
   * 解析哈希文件
   */
  private Map<String, String> parseHashFile(File hashFile) throws VersionError {
    try {
      String content = readFileToString(hashFile);
      if (content == null || content.trim().isEmpty()) {
        hashFile.delete();
        throw new VersionError(VersionErrorType.RESOURCE_CONTENT_NULL, "文件内容为空");
      }

      Type type = new TypeToken<Map<String, String>>() {}.getType();
      Map<String, String> fileRecord = gson.fromJson(content, type);

      if (fileRecord == null) {
        hashFile.delete();
        throw new VersionError(VersionErrorType.RESOURCE_PARSE_ERROR, "解析失败");
      }

      Log.d(TAG, "哈希文件解析成功，记录数: " + fileRecord.size());
      return fileRecord;

    } catch (Exception e) {
      Log.e(TAG, "解析哈希文件失败", e);
      hashFile.delete();
      throw new VersionError(VersionErrorType.RESOURCE_PARSE_ERROR, e.getMessage());
    }
  }

  /**
   * 解析响应数据
   */
  private WebBSResFileInfo parseResponseToFileInfo(Map<String, Object> responseMap) {
    try {
      WebBSResFileInfo info = new WebBSResFileInfo();

      // 使用Gson直接转换（更简洁）
      String json = gson.toJson(responseMap);
      info = gson.fromJson(json, WebBSResFileInfo.class);

      Log.d(TAG, "解析版本信息: " + info.getResourceVersion());
      return info;

    } catch (Exception e) {
      Log.e(TAG, "解析响应数据失败", e);
      return null;
    }
  }

  /**
   * 读取文件内容
   */
  private String readFileToString(File file) throws IOException {
    try (InputStream inputStream = new java.io.FileInputStream(file);
         java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int length;
      while ((length = inputStream.read(buffer)) != -1) {
        result.write(buffer, 0, length);
      }
      return result.toString("UTF-8");
    }
  }

  /**
   * 递归删除目录
   */
  private boolean deleteDirectory(File dir) {
    if (dir != null && dir.exists()) {
      File[] files = dir.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            deleteDirectory(file);
          } else {
            file.delete();
          }
        }
      }
      return dir.delete();
    }
    return false;
  }

  /**
   * 获取应用版本名
   */
  private String getAppVersionName() {
    try {
      return context.getPackageManager()
        .getPackageInfo(context.getPackageName(), 0)
        .versionName;
    } catch (Exception e) {
      Log.e(TAG, "获取应用版本名失败", e);
      return "";
    }
  }

  /**
   * 获取设备ID
   */
  private String getDeviceId() {
    return Build.SERIAL;
  }

  /**
   * 获取IP地址
   */
  private String getIpAddress() {
    return "127.0.0.1";
  }

  /**
   * 获取操作员号
   */
  private String getOperNo() {
    // 这个应该从其他地方获取，比如登录后的用户信息
    // 这里先返回空字符串
    return "";
  }

  // ==================== 回调接口 ====================

  public interface VersionCallback {
    void onSuccess(WebBSResFile result);
    void onError(VersionError error);
  }

  public interface HashFileCallback {
    void onSuccess(Map<String, String> fileRecord);
    void onError(VersionError error);
  }
}
