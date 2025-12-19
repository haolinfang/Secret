package com.ionicframework.cordova.webview.secret;

/**
 * RCP请求错误
 */
public class XcRcpError extends Exception {
  private int code;
  private String message;

  public XcRcpError(int code) {
    this(code, "RCP请求错误: " + code);
  }

  public XcRcpError(int code, String message) {
    super(message);
    this.code = code;
    this.message = message;
  }

  public int getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
