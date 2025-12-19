package com.ionicframework.cordova.webview.bs;

/**
 * 版本信息错误类
 */
public class VersionError extends Exception {
  private String errorType; // 错误类型
  private String errorCode; // 错误码

  public VersionError(String errorType, String errorCode) {
    super("VersionError: " + errorType + " (" + errorCode + ")");
    this.errorType = errorType;
    this.errorCode = errorCode;
  }

  public VersionError(String errorType, String errorCode, Throwable cause) {
    super("VersionError: " + errorType + " (" + errorCode + ")", cause);
    this.errorType = errorType;
    this.errorCode = errorCode;
  }

  public String getErrorType() {
    return errorType;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
