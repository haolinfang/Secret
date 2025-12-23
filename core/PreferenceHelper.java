package com.ionicframework.online.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.ionicframework.online.model.WebBSResFileInfo;

/**
 * WebView共享状态管理器
 * 通过SharedPreferences在不同组件间共享数据
 */
public class PreferenceHelper {
  private static final String TAG = "PreferenceHelper";
  private static final String PREF_NAME = "webview_shared_state";

  // 键名定义 - 版本信息JSON
  public static final String KEY_VERSION_INFO_JSON = "version_info_json";

  private static PreferenceHelper instance;

  private final SharedPreferences prefs;
  private final Gson gson;

  // 内存缓存
  private WebBSResFileInfo webBSResFileInfo;

  private PreferenceHelper(Context context) {
    this.prefs = context.getApplicationContext()
      .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    this.gson = new Gson();
    loadFromPreferences();
  }

  public static synchronized PreferenceHelper getInstance(Context context) {
    if (instance == null) {
      instance = new PreferenceHelper(context);
    }
    return instance;
  }

  /**
   * 从SharedPreferences加载数据到内存
   */
  private void loadFromPreferences() {
    String versionInfoJson = prefs.getString(KEY_VERSION_INFO_JSON, null);
    if (!TextUtils.isEmpty(versionInfoJson)) {
      try {
        webBSResFileInfo = gson.fromJson(versionInfoJson, WebBSResFileInfo.class);
        Log.d(TAG, "从缓存加载版本信息: 版本=" + webBSResFileInfo.getResourceVersion() +
          ", 资源路径=" + webBSResFileInfo.getResourcePath());
      } catch (Exception e) {
        Log.e(TAG, "解析缓存的版本信息失败", e);
        clearVersionInfo(); // 清除损坏的数据
      }
    }
  }

  /**
   * 保存版本信息
   */
  public void saveVersionInfo(WebBSResFileInfo versionInfo) {
    if (versionInfo == null) return;

    webBSResFileInfo = versionInfo;
    String versionInfoJson = gson.toJson(versionInfo);

    prefs.edit()
      .putString(KEY_VERSION_INFO_JSON, versionInfoJson)
      .apply();

    Log.d(TAG, "版本信息已保存: 版本=" + versionInfo.getResourceVersion() +
      ", 资源路径=" + versionInfo.getResourcePath());
  }

  /**
   * 获取版本信息
   */
  public WebBSResFileInfo getVersionInfo() {
    return webBSResFileInfo;
  }

  /**
   * 获取资源路径
   */
  public String getResourcePath() {
    return webBSResFileInfo != null ? webBSResFileInfo.getResourcePath() : null;
  }

  /**
   * 检查是否已初始化（是否有缓存的版本信息）
   */
  public boolean hasCachedVersionInfo() {
    return webBSResFileInfo != null;
  }

  /**
   * 清除所有状态
   */
  public void clearAll() {
    prefs.edit().clear().apply();
    webBSResFileInfo = null;
    Log.d(TAG, "所有状态已清除");
  }

  /**
   * 清除版本信息
   */
  public void clearVersionInfo() {
    prefs.edit()
      .remove(KEY_VERSION_INFO_JSON)
      .apply();

    webBSResFileInfo = null;
    Log.d(TAG, "版本信息已清除");
  }

  /**
   * 检查是否有强制更新标志
   */
  public boolean isForceUpdate() {
    if (webBSResFileInfo != null && webBSResFileInfo.getUpdateFlag() != null) {
      return "1".equals(webBSResFileInfo.getUpdateFlag());
    }
    return false;
  }
}
