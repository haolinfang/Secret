package com.ionicframework.online.core;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.ionicframework.online.model.VersionError;
import com.ionicframework.online.model.WebBSResFile;
import com.ionicframework.online.model.WebBSResFileInfo;

/**
 * Web资源版本管理器 - 负责获取版本信息并协调设置
 */
public class HomeManager {
  private static final String TAG = "HomeManager";

  private final Context context;
  private final VersionInfoManager versionInfoManager;
  private final PreferenceHelper preferenceHelper;
  private final OnLineCacheManager onLineCacheManager;

  // 初始化回调接口
  public interface InitializationCallback {
    void onSuccess(String version, String resourcePath);
    void onError(String error);
  }

  public HomeManager(Context context) {
    this.context = context.getApplicationContext();
    this.versionInfoManager = VersionInfoManager.getInstance(this.context);
    this.preferenceHelper = PreferenceHelper.getInstance(this.context);
    this.onLineCacheManager = OnLineCacheManager.getInstance(this.context);
  }

  /**
   * 初始化版本信息（请求版本接口并下载hash文件）
   * 这是主要的版本获取入口
   */
  public void initialize(final InitializationCallback callback) {
    Log.i(TAG, "开始初始化，必须先查询版本信息接口...");

    // 必须请求最新版本信息
    requestLatestVersion(callback);
  }

  /**
   * 请求最新版本信息
   */
  private void requestLatestVersion(final InitializationCallback callback) {
    Log.i(TAG, "请求最新版本信息...");
    versionInfoManager.getVersionInfoAsync(new VersionInfoManager.VersionCallback() {
      @Override
      public void onSuccess(WebBSResFile result) {
        WebBSResFileInfo versionInfo = result.getInfo();
        String version = versionInfo.getResourceVersion();
        String resourcePath = versionInfo.getResourcePath();

        Log.i(TAG, "版本信息请求成功，版本: " + version + ", 资源路径: " + resourcePath);

        // 1. 保存版本信息到SharedPreferences
        saveVersionInfo(versionInfo);

        // 2. 设置资源路径到OnLineCacheManager
        boolean pathSet = setResourcePathToCacheManager(resourcePath);

        if (pathSet) {
          Log.i(TAG, "版本信息设置完成，版本: " + version + ", 资源路径: " + resourcePath);

          if (callback != null) {
            callback.onSuccess(version, resourcePath);
          }
        } else if (callback != null) {
          callback.onError("设置资源路径到缓存管理器失败");
        }
      }

      @Override
      public void onError(VersionError error) {
        Log.e(TAG, "获取版本信息失败: " + error.getErrorType() + ", 错误信息: " + error.getMessage());

        // 尝试使用缓存版本作为降级方案
        WebBSResFileInfo cachedVersionInfo = preferenceHelper.getVersionInfo();
        if (cachedVersionInfo != null) {
          String version = cachedVersionInfo.getResourceVersion();
          String resourcePath = cachedVersionInfo.getResourcePath();

          Log.w(TAG, "网络请求失败，使用缓存的版本信息: " + version + ", 资源路径: " + resourcePath);

          boolean pathSet = setResourcePathToCacheManager(resourcePath);
          if (pathSet && callback != null) {
            // 通知上层使用缓存版本
            callback.onSuccess(version, resourcePath);
          } else if (callback != null) {
            callback.onError("使用缓存资源路径失败");
          }
        } else {
          // 没有缓存，直接返回错误
          if (callback != null) {
            callback.onError("获取版本信息失败: " + error.getErrorType());
          }
        }
      }
    });
  }

  /**
   * 设置资源路径到OnLineCacheManager
   */
  private boolean setResourcePathToCacheManager(String resourcePath) {
    if (TextUtils.isEmpty(resourcePath)) {
      Log.e(TAG, "资源路径为空，无法设置到缓存管理器");
      return false;
    }

    try {
      boolean success = onLineCacheManager.setResourcePath(resourcePath);

      if (success) {
        Log.d(TAG, "资源路径已设置到缓存管理器: " + resourcePath);
      } else {
        Log.e(TAG, "设置资源路径到缓存管理器失败: " + resourcePath);
      }

      return success;
    } catch (Exception e) {
      Log.e(TAG, "设置资源路径到缓存管理器异常", e);
      return false;
    }
  }

  /**
   * 保存版本信息到SharedPreferences
   */
  private void saveVersionInfo(WebBSResFileInfo versionInfo) {
    if (versionInfo != null) {
      preferenceHelper.saveVersionInfo(versionInfo);

      Log.d(TAG, "版本信息已保存到SharedPreferences: " +
        versionInfo.getResourceVersion() + ", 资源路径: " + versionInfo.getResourcePath());
    }
  }

  /**
   * 清除所有缓存
   */
  public void clearAllCache() {
    preferenceHelper.clearAll();
    onLineCacheManager.clearAllCache();
    Log.i(TAG, "所有缓存已清除");
  }
}
