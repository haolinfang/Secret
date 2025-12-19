package com.ionicframework.cordova.webview.bs;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.ionicframework.cordova.webview.online.OnLineCacheManager;

/**
 * Web资源版本管理器 - 负责获取版本信息并协调设置
 */
public class HashFileManager {
  private static final String TAG = "HashFileManager";

  private final Context context;
  private final WebBSVersionHelper versionHelper;
  private final WebViewSharedState sharedState;

  // 初始化回调接口
  public interface InitializationCallback {
    void onSuccess(String version);
    void onError(String error);
  }

  public HashFileManager(Context context) {
    this.context = context.getApplicationContext();
    this.versionHelper = WebBSVersionHelper.getInstance(this.context);
    this.sharedState = WebViewSharedState.getInstance(this.context);
  }

  /**
   * 检查是否有缓存的版本信息
   */
  public boolean hasCachedVersion() {
    return sharedState.isInitialized() &&
      sharedState.getCurrentVersion() != null;
  }

  /**
   * 初始化版本信息（请求版本接口并下载hash文件）
   * 这是主要的版本获取入口
   */
  public void initialize(final InitializationCallback callback) {
    // 首先检查是否有缓存且未过期（例如7*24小时内）
    if (hasCachedVersion() && !sharedState.isVersionExpired(7 * 24 * 60 * 60 * 1000)) {
      String cachedVersion = sharedState.getCurrentVersion();
      Log.i(TAG, "使用缓存的版本: " + cachedVersion);

      // 设置版本到OnLineCacheManager
      boolean versionSet = setVersionToCacheManager(cachedVersion);

      if (versionSet && callback != null) {
        callback.onSuccess(cachedVersion);
      } else if (callback != null) {
        callback.onError("设置缓存版本失败");
      }
      return;
    }

    // 没有缓存或已过期，请求最新版本
    Log.i(TAG, "开始获取版本信息...");
    versionHelper.getVersionInfoAsync(new WebBSVersionHelper.VersionCallback() {
      @Override
      public void onSuccess(WebBSResFile result) {
        WebBSResFileInfo versionInfo = result.getInfo();
        String version = versionInfo.getResourceVersion();

        // 1. 保存到SharedPreferences
        saveVersionInfo(versionInfo);

        // 2. 设置版本到OnLineCacheManager
        boolean versionSet = setVersionToCacheManager(version);

        if (versionSet) {
          Log.i(TAG, "版本信息请求完成，版本: " + version);

          if (callback != null) {
            callback.onSuccess(version);
          }
        } else if (callback != null) {
          callback.onError("设置版本到缓存管理器失败");
        }
      }

      @Override
      public void onError(VersionError error) {
        Log.e(TAG, "获取版本信息失败: " + error.getErrorType());

        // 尝试使用缓存版本
        String cachedVersion = sharedState.getCurrentVersion();
        if (cachedVersion != null) {
          Log.w(TAG, "使用缓存的版本: " + cachedVersion);

          boolean versionSet = setVersionToCacheManager(cachedVersion);
          if (versionSet && callback != null) {
            callback.onSuccess(cachedVersion);
          } else if (callback != null) {
            callback.onError("使用缓存版本失败");
          }
        } else if (callback != null) {
          callback.onError(error.getErrorType());
        }
      }
    });
  }

  /**
   * 设置版本到OnLineCacheManager
   */
  private boolean setVersionToCacheManager(String version) {
    if (TextUtils.isEmpty(version)) {
      Log.e(TAG, "版本号为空，无法设置到缓存管理器");
      return false;
    }

    try {
      OnLineCacheManager cacheManager = OnLineCacheManager.getInstance(context);
      boolean success = cacheManager.setVersion(version);

      if (success) {
        Log.d(TAG, "版本已设置到缓存管理器: " + version);
      } else {
        Log.e(TAG, "设置版本到缓存管理器失败: " + version);
      }

      return success;
    } catch (Exception e) {
      Log.e(TAG, "设置版本到缓存管理器异常", e);
      return false;
    }
  }

  /**
   * 保存版本信息到SharedPreferences
   */
  private void saveVersionInfo(WebBSResFileInfo versionInfo) {
    if (versionInfo != null) {
      String version = versionInfo.getResourceVersion();
      sharedState.saveCurrentVersion(version);

      Log.d(TAG, "版本信息已保存到SharedPreferences: " + version);
    }
  }

  /**
   * 获取当前版本号
   */
  public String getCurrentVersion() {
    return sharedState.getCurrentVersion();
  }

  /**
   * 清除所有缓存
   */
  public void clearCache() {
    sharedState.clearAll();

    // 清除OnLineCacheManager缓存
    try {
      OnLineCacheManager cacheManager = OnLineCacheManager.getInstance(context);
      cacheManager.clearAllCache();
    } catch (Exception e) {
      Log.e(TAG, "清除OnLineCacheManager缓存失败", e);
    }

    Log.d(TAG, "所有缓存已清除");
  }

  /**
   * 关闭资源
   */
  public void shutdown() {
    versionHelper.shutdown();
  }
}
