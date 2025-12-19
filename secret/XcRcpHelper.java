package com.ionicframework.cordova.webview.secret;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * RCP网络请求助手（异步版本）
 */
public class XcRcpHelper {
  private static final String TAG = "XcRcpHelper";
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private static XcRcpHelper instance;
  private String baseUrl;
  private final OkHttpClient okHttpClient;
  private final ExecutorService executorService; // 线程池

  // 回调接口
  public interface RcpCallback<T> {
    void onSuccess(T result);
    void onError(XcRcpError error);
  }

  private XcRcpHelper() {
    this.okHttpClient = new OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .build();

    // 创建固定大小的线程池
    this.executorService = Executors.newFixedThreadPool(3);
  }

  public static synchronized XcRcpHelper getInstance() {
    if (instance == null) {
      instance = new XcRcpHelper();
    }
    return instance;
  }

  public void init(String url) {
    this.baseUrl = url;
    Log.d(TAG, "XcRcpHelper初始化，baseUrl: " + url);
  }

  /**
   * 异步发送POST请求（带完整加密流程）
   */
  public <T> void postApiAsync(final String url, final Map<String, Object> params,
                               final Class<T> clazz, final RcpCallback<T> callback) {
    executorService.execute(new Runnable() {
      @Override
      public void run() {
        try {
          T result = postApiSync(url, params, clazz);
          if (callback != null) {
            callback.onSuccess(result);
          }
        } catch (XcRcpError e) {
          if (callback != null) {
            callback.onError(e);
          }
        }
      }
    });
  }

  /**
   * 同步发送POST请求（在后台线程执行）
   */
  public <T> T postApiSync(String url, Map<String, Object> params, Class<T> clazz) throws XcRcpError {
    try {
      // 1. 生成AES密钥和IV
      String aesKey = EncryptUtils.syncGenerator();
      String aesIv = EncryptUtils.syncGenerator();

      // 2. 使用RSA加密AES密钥和IV
      String encryptedAesKey = EncryptUtils.rsaEncrypt(aesKey);
      String encryptedAesIv = EncryptUtils.rsaEncrypt(aesIv);

      if (TextUtils.isEmpty(encryptedAesKey) || TextUtils.isEmpty(encryptedAesIv)) {
        throw new XcRcpError(1002, "RSA加密失败");
      }

      // 3. 准备请求头
      Map<String, String> headers = new HashMap<>();
      headers.put("Content-Type", "application/json;charset=utf-8");
      headers.put("Accept", "text/html,application/json,application/xml;q=0.9,image/webp,*/*;q=0.8");
      headers.put("Accept-Language", "zh-CN");
      headers.put("refreshToken", "");
      headers.put("channelType", "0");
      headers.put("aesKey", encryptedAesKey);
      headers.put("aesIv", encryptedAesIv);
      headers.put("baseVersion", "v2");

      // 4. 计算baseParams（HMAC-SHA512签名）
      String paramsJson = new JSONObject(params).toString();
      String baseParams = EncryptUtils.hmacSHA512(encryptedAesKey, paramsJson);
      headers.put("baseParams", baseParams);

      // 5. 使用RSA-2048加密请求参数
      String encryptedParams = EncryptUtils.rsaEncrypt2048(paramsJson);
      if (TextUtils.isEmpty(encryptedParams)) {
        throw new XcRcpError(1003, "参数加密失败");
      }

      // 6. 构建请求
      String fullUrl = baseUrl + url;
      RequestBody body = RequestBody.create(JSON, encryptedParams);

      Request.Builder requestBuilder = new Request.Builder()
        .url(fullUrl)
        .post(body);

      // 添加请求头
      for (Map.Entry<String, String> header : headers.entrySet()) {
        requestBuilder.addHeader(header.getKey(), header.getValue());
      }

      // 7. 发送请求
      Log.d(TAG, "发送请求到: " + fullUrl);
      Response response = okHttpClient.newCall(requestBuilder.build()).execute();

      // 8. 检查响应
      if (!response.isSuccessful()) {
        int code = response.code();
        if (code == 503) {
          throw new XcRcpError(503, "服务暂时不可用");
        } else if (code == 408 || code == 504) {
          throw new XcRcpError(1007900028, "请求超时");
        } else {
          throw new XcRcpError(code, "HTTP错误: " + code);
        }
      }

      // 9. 读取响应体
      String responseBody = response.body().string();
      Log.d(TAG, "响应原始数据: " + responseBody);

      // 10. 解析响应JSON
      JSONObject responseJson = new JSONObject(responseBody);

      // 检查是否包含503错误页面
      if (responseBody.contains("503 Service Temporarily Unavailable")) {
        throw new XcRcpError(503, "服务暂时不可用");
      }

      // 11. 获取加密的数据字段
      String encryptedData = responseJson.optString("date");
      if (encryptedData.isEmpty()) {
        throw new XcRcpError(1004, "响应数据字段缺失");
      }

      // 12. AES解密响应数据
      String decryptedData = EncryptUtils.decryptAes(encryptedData, aesKey, aesIv);
      if (decryptedData.isEmpty()) {
        throw new XcRcpError(1005, "响应数据解密失败");
      }

      Log.d(TAG, "解密后的响应数据: " + decryptedData);

      // 13. 反序列化为指定类型
      if (clazz == String.class) {
        return clazz.cast(decryptedData);
      } else if (clazz == JSONObject.class) {
        return clazz.cast(new JSONObject(decryptedData));
      } else {
        // 使用Gson进行反序列化
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.fromJson(decryptedData, clazz);
      }

    } catch (XcRcpError e) {
      throw e;
    } catch (org.json.JSONException e) {
      Log.e(TAG, "JSON解析失败", e);
      throw new XcRcpError(1006, "JSON解析失败: " + e.getMessage());
    } catch (IOException e) {
      Log.e(TAG, "网络请求失败", e);
      throw new XcRcpError(1007, "网络请求失败: " + e.getMessage());
    } catch (Exception e) {
      Log.e(TAG, "请求失败", e);
      throw new XcRcpError(1008, "请求失败: " + e.getMessage());
    }
  }

  // 关闭线程池（在应用退出时调用）
  public void shutdown() {
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
    }
  }
}
