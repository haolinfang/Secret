package com.ionicframework.online.interceptor;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

public class EncryptionInterceptor implements Interceptor {
  @Override
  public Response intercept(Chain chain) throws IOException {
    Request originalRequest = chain.request();

    // 为所有RCP请求添加公共的加密头
    Request newRequest = originalRequest.newBuilder()
      .header("Content-Type", "application/json;charset=utf-8")
      .header("Accept", "text/html,application/json,application/xml;q=0.9,image/webp,*/*;q=0.8")
      .header("Accept-Language", "zh-CN")
      .header("channelType", "0")
      .header("baseVersion", "v2")
      .build();

    return chain.proceed(newRequest);
  }
}
