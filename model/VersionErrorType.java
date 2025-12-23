package com.ionicframework.online.model;

/**
 * 版本信息错误类型枚举
 */
public class VersionErrorType {
  public static final String NETWORK_UNAVAILABLE = "NETWORK_UNAVAILABLE"; // 网络不可用
  public static final String SERVER_UNAVAILABLE = "SERVER_UNAVAILABLE"; // 服务不可用
  public static final String API_REQUEST_TIMEOUT = "API_REQUEST_TIMEOUT"; // 服务器连接超时
  public static final String API_REQUEST_FAILED = "API_REQUEST_FAILED"; // 接口请求失败
  public static final String API_RESPONSE_ERROR = "API_RESPONSE_ERROR"; // 接口返回错误
  public static final String API_RESPONSE_NULL = "API_RESPONSE_NULL"; // 接口返回NULL
  public static final String RESOURCE_DOWNLOAD_FAILED = "RESOURCE_DOWNLOAD_FAILED"; // 资源下载失败
  public static final String RESOURCE_CONTENT_NULL = "RESOURCE_CONTENT_NULL"; // hash文件NULL
  public static final String RESOURCE_PARSE_ERROR = "RESOURCE_PARSE_ERROR"; // hash文件转JSON错误
  public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR"; // 未知错误
}
