package com.ionicframework.online.core;

import android.content.Context;
import android.util.Log;

import com.ionicframework.online.model.VersionError;
import com.ionicframework.online.model.VersionErrorType;
import com.ionicframework.online.model.WebBSResFileInfo;
import com.ionicframework.online.api.ApiHelper;
import com.ionicframework.online.api.ApiError;
import com.ionicframework.online.utils.InfoUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 版本信息请求器 - 专门负责请求版本信息接口
 */
public class VersionInfoFetcher {
  private static final String TAG = "VersionInfoFetcher";

  private final Context context;

  public VersionInfoFetcher(Context context) {
    this.context = context.getApplicationContext();
  }

  /**
   * 同步请求版本信息
   */
  public WebBSResFileInfo fetchVersionInfo() throws VersionError {
    try {
      ApiHelper apiHelper = ApiHelper.getInstance();

      // 构建请求参数
      Map<String, Object> params = new HashMap<>();
      params.put("channelNo", "mb");
      params.put("bussType", 0);
      params.put("appType", 0);
      params.put("bankAppVersion", InfoUtils.getAppVersionName(context));
      params.put("deviceCode", InfoUtils.getDeviceId());
      params.put("termIP", InfoUtils.getIpAddress());
      params.put("operNo", InfoUtils.getOperNo());

      WebBSResFileInfo versionInfo = apiHelper.postApiSync("mAppVersionQuery", params, WebBSResFileInfo.class);

      if (versionInfo == null) {
        throw new VersionError(VersionErrorType.API_RESPONSE_NULL, "接口返回空");
      }

      Log.d(TAG, "获取版本信息成功: " + versionInfo.getResourceVersion() +
        ", 资源路径: " + versionInfo.getResourcePath());

      if (!"CIP0000000".equals(versionInfo.getResCode())) {
        throw new VersionError(VersionErrorType.API_RESPONSE_ERROR,
          versionInfo.getResCode() + " - " + versionInfo.getResMsg());
      }

      return versionInfo;

    } catch (ApiError e) {
      // 处理apiError
      handleApiError(e);
      // 上面的handleApiError会抛出VersionError，所以这里理论上不会执行到
      throw new VersionError(VersionErrorType.API_REQUEST_FAILED, e.getMessage());
    } catch (Exception e) {
      Log.e(TAG, "请求版本信息失败", e);
      throw new VersionError(VersionErrorType.API_REQUEST_FAILED, "请求失败: " + e.getMessage());
    }
  }

  /**
   * 处理RCP错误
   */
  private void handleApiError(ApiError apiError) throws VersionError {
    int code = apiError.getCode();
    String message = apiError.getMessage();

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
}
