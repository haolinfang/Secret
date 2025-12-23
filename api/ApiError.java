package com.ionicframework.online.api;

/**
 * RCP请求错误
 */
public class ApiError extends Exception {
  private int code;
  private String message;

  public ApiError(int code, String message) {
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
