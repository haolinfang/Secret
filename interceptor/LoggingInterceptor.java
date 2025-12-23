package com.ionicframework.online.interceptor;

import android.util.Log;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.io.IOException;

public class LoggingInterceptor implements Interceptor {
  private static final String TAG = "OkHttp";

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    long startTime = System.nanoTime();

    Log.d(TAG, String.format("--> %s %s", request.method(), request.url()));

    Response response = chain.proceed(request);
    long endTime = System.nanoTime();

    long duration = (endTime - startTime) / 1000000;
    ResponseBody responseBody = response.body();
    String bodyString = responseBody.string();

    Log.d(TAG, String.format("<-- %d %s (%dms, %d bytes)",
      response.code(), response.message(), duration, bodyString.length()));

    // 重新构建response，因为body只能读取一次
    return response.newBuilder()
      .body(ResponseBody.create(responseBody.contentType(), bodyString))
      .build();
  }
}
