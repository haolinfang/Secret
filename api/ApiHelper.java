package com.ionicframework.online.api;

import android.text.TextUtils;
import android.util.Log;

import com.china.ncbcmbs.Constants;
import com.google.gson.Gson;
import com.ionicframework.online.interceptor.EncryptionInterceptor;
import com.ionicframework.online.utils.EncryptUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * RCP网络请求助手
 */
public class ApiHelper {
  private static final String TAG = "XcRcpHelper";
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private static ApiHelper instance;
  private final OkHttpClient okHttpClient;
  private final Gson gson;

  private ApiHelper() {
    this.okHttpClient = new OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .addInterceptor(new EncryptionInterceptor())
      .build();
    this.gson = new Gson();
  }

  public static synchronized ApiHelper getInstance() {
    if (instance == null) {
      instance = new ApiHelper();
    }
    return instance;
  }

  /**
   * 同步发送POST请求
   * @param url API路径（不包含baseUrl）
   * @param params 请求参数
   * @param clazz 返回数据类型的Class
   * @return 反序列化后的对象
   * @throws ApiError 请求失败时抛出
   */
  public <T> T postApiSync(String url, Map<String, Object> params, Class<T> clazz) throws ApiError {
    try {
      // 1. 生成AES密钥和IV
      String aesKey = EncryptUtils.syncGenerator();
      String aesIv = EncryptUtils.syncGenerator();

      // 2. 使用RSA加密AES密钥和IV
      String encryptedAesKey = EncryptUtils.rsaEncrypt(aesKey);
      String encryptedAesIv = EncryptUtils.rsaEncrypt(aesIv);

      if (TextUtils.isEmpty(encryptedAesKey) || TextUtils.isEmpty(encryptedAesIv)) {
        throw new ApiError(1002, "RSA加密失败");
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
        throw new ApiError(1003, "参数加密失败");
      }

      // 6. 构建请求
      String fullUrl = String.format("%s/NCB/%s", Constants.getEnv().getIp(), url);

      // 使用新的RequestBody创建方式，避免废弃的方法
      RequestBody body = createRequestBody(encryptedParams);

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
          throw new ApiError(503, "服务暂时不可用");
        } else if (code == 408 || code == 504) {
          throw new ApiError(1007900028, "请求超时");
        } else {
          throw new ApiError(code, "HTTP错误: " + code);
        }
      }

      // 9. 读取响应体
      String responseBody = response.body().string();
      Log.d(TAG, "响应原始数据: " + responseBody);

      // 10. 解析响应JSON
      JSONObject responseJson = new JSONObject(responseBody);

      // 检查是否包含503错误页面
      if (responseBody.contains("503 Service Temporarily Unavailable")) {
        throw new ApiError(503, "服务暂时不可用");
      }

      // 11. 获取加密的数据字段
      String encryptedData = responseJson.optString("date");
      if (encryptedData.isEmpty()) {
        throw new ApiError(1004, "响应数据字段缺失");
      }

      // 12. AES解密响应数据
      String decryptedData = EncryptUtils.decryptAes(encryptedData, aesKey, aesIv);
      if (decryptedData.isEmpty()) {
        throw new ApiError(1005, "响应数据解密失败");
      }

      Log.d(TAG, "解密后的响应数据: " + decryptedData);

      // 13. 反序列化为指定类型
      return deserializeResponse(decryptedData, clazz);

    } catch (ApiError e) {
      throw e;
    } catch (org.json.JSONException e) {
      Log.e(TAG, "JSON解析失败", e);
      throw new ApiError(1006, "JSON解析失败: " + e.getMessage());
    } catch (IOException e) {
      Log.e(TAG, "网络请求失败", e);
      throw new ApiError(1007, "网络请求失败: " + e.getMessage());
    } catch (Exception e) {
      Log.e(TAG, "请求失败", e);
      throw new ApiError(1008, "请求失败: " + e.getMessage());
    }
  }

  /**
   * 创建RequestBody（兼容新版OkHttp）
   */
  private RequestBody createRequestBody(String content) {
    return RequestBody.create(JSON, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /**
   * 反序列化响应数据
   */
  private <T> T deserializeResponse(String decryptedData, Class<T> clazz) throws JSONException {
    if (clazz == String.class) {
      return clazz.cast(decryptedData);
    } else if (clazz == JSONObject.class) {
      return clazz.cast(new JSONObject(decryptedData));
    } else {
      // 使用Gson进行反序列化
      return gson.fromJson(decryptedData, clazz);
    }
  }
}
