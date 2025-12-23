package com.ionicframework.online.interceptor;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

public class RetryInterceptor implements Interceptor {
  private final int maxRetries;

  public RetryInterceptor(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    Response response = null;
    IOException exception = null;

    // 重试逻辑
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        if (attempt > 0) {
          // 如果不是第一次尝试，等待一段时间
          try {
            Thread.sleep(attempt * 1000L); // 指数退避
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during retry", e);
          }
        }

        response = chain.proceed(request);

        // 如果响应成功，直接返回
        if (response.isSuccessful()) {
          return response;
        }

        // 如果是服务器错误，关闭响应体并继续重试
        if (response.code() >= 500) {
          response.close();
          continue;
        }

        // 如果是客户端错误，不重试
        return response;

      } catch (IOException e) {
        exception = e;
      }
    }

    // 所有重试都失败了
    if (exception != null) {
      throw exception;
    }

    return response;
  }
}
