package com.ionicframework.online.utils;

import android.content.Context;
import android.util.Log;

import org.apache.cordova.device.Device;

public class InfoUtils {

  private static final String TAG = "InfoUtils";

  /**
   * 获取应用版本名
   */
  public static String getAppVersionName(Context context) {
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
  public static String getDeviceId() {
    return Device.uuid;
  }

  /**
   * 获取IP地址
   */
  public static String getIpAddress() {
    return "127.0.0.1";
  }

  /**
   * 获取操作员号
   */
  public static String getOperNo() {
    return "";
  }
}
