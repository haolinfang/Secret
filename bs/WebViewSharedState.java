package com.ionicframework.cordova.webview.bs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

/**
 * WebView共享状态管理器
 * 通过SharedPreferences在不同组件间共享数据
 */
public class WebViewSharedState {
  private static final String TAG = "WebViewSharedState";
  private static final String PREF_NAME = "webview_shared_state";

  // 键名定义
  public static final String KEY_CURRENT_VERSION = "current_version";
  public static final String KEY_VERSION_INFO_JSON = "version_info_json";
  public static final String KEY_IS_INITIALIZED = "is_initialized";
  public static final String KEY_INIT_TIMESTAMP = "init_timestamp";

  private static WebViewSharedState instance;
  private final SharedPreferences prefs;

  private WebViewSharedState(Context context) {
    this.prefs = context.getApplicationContext()
      .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
  }

  public static synchronized WebViewSharedState getInstance(Context context) {
    if (instance == null) {
      instance = new WebViewSharedState(context);
    }
    return instance;
  }

  // ==================== 版本相关 ====================

  /**
   * 保存当前版本号
   */
  public void saveCurrentVersion(String version) {
    if (TextUtils.isEmpty(version)) {
      return;
    }

    prefs.edit()
      .putString(KEY_CURRENT_VERSION, version)
      .putBoolean(KEY_IS_INITIALIZED, true)
      .putLong(KEY_INIT_TIMESTAMP, System.currentTimeMillis())
      .apply();

    Log.d(TAG, "版本已保存: " + version);
  }

  /**
   * 获取当前版本号
   */
  public String getCurrentVersion() {
    return prefs.getString(KEY_CURRENT_VERSION, null);
  }

  /**
   * 保存版本信息JSON
   */
  public void saveVersionInfoJson(String versionInfoJson) {
    if (!TextUtils.isEmpty(versionInfoJson)) {
      prefs.edit().putString(KEY_VERSION_INFO_JSON, versionInfoJson).apply();
    }
  }

  /**
   * 获取版本信息JSON
   */
  public String getVersionInfoJson() {
    return prefs.getString(KEY_VERSION_INFO_JSON, null);
  }

  /**
   * 检查是否已初始化
   */
  public boolean isInitialized() {
    return prefs.getBoolean(KEY_IS_INITIALIZED, false);
  }

  /**
   * 获取初始化时间戳
   */
  public long getInitTimestamp() {
    return prefs.getLong(KEY_INIT_TIMESTAMP, 0);
  }

  /**
   * 清除所有状态
   */
  public void clearAll() {
    prefs.edit().clear().apply();
    Log.d(TAG, "所有状态已清除");
  }

  /**
   * 清除版本信息
   */
  public void clearVersionInfo() {
    prefs.edit()
      .remove(KEY_CURRENT_VERSION)
      .remove(KEY_VERSION_INFO_JSON)
      .remove(KEY_IS_INITIALIZED)
      .apply();

    Log.d(TAG, "版本信息已清除");
  }

  /**
   * 检查版本是否过期（例如超过24小时）
   */
  public boolean isVersionExpired(long expireMillis) {
    long initTime = getInitTimestamp();
    if (initTime == 0) {
      return true;
    }

    long currentTime = System.currentTimeMillis();
    return (currentTime - initTime) > expireMillis;
  }
}
