package com.ionicframework.online.core;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.china.ncbcmbs.Constants;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ionicframework.online.model.VersionError;
import com.ionicframework.online.model.VersionErrorType;
import com.ionicframework.online.model.WebBSResFileInfo;
import com.ionicframework.online.utils.EncryptUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 哈希文件下载器 - 专门负责哈希文件的下载、缓存和解析
 * key: 文件相对路径的MD5值
 * value: 文件流的MD5值
 */
public class HashFileDownloader {
  private static final String TAG = "HashFileDownloader";

  private final Context context;
  private final Gson gson;
  private final OkHttpClient okHttpClient;

  // 内存缓存文件记录
  private Map<String, String> fileRecordCache = new ConcurrentHashMap<>();

  // 哈希文件缓存目录
  private static final String HASH_CACHE_DIR = "hash_cache";

  public HashFileDownloader(Context context) {
    this.context = context.getApplicationContext();
    this.gson = new Gson();

    this.okHttpClient = new OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .build();
  }

  /**
   * 下载并解析哈希文件
   * 返回格式：Map<String, String> 其中key=文件相对路径的MD5，value=文件内容的MD5
   */
  public Map<String, String> downloadHashFile(WebBSResFileInfo versionInfo) throws VersionError {
    String resourcePath = versionInfo.getResourcePath();

    if (resourcePath == null) {
      throw new VersionError(VersionErrorType.API_RESPONSE_ERROR, "资源路径为空");
    }

    Log.d(TAG, "开始处理哈希文件，资源路径: " + resourcePath);

    // 1. 先检查内存缓存
    if (!fileRecordCache.isEmpty()) {
      Log.d(TAG, "使用内存缓存的哈希文件，记录数: " + fileRecordCache.size());
      return fileRecordCache;
    }

    // 2. 检查本地文件缓存
    Map<String, String> cachedRecord = loadCachedHashFile(resourcePath);
    if (cachedRecord != null) {
      Log.d(TAG, "使用本地缓存的哈希文件，记录数: " + cachedRecord.size());
      // 存入内存缓存
      fileRecordCache.putAll(cachedRecord);
      return cachedRecord;
    }

    Log.d(TAG, "缓存未命中，从服务器下载哈希文件");

    // 3. 缓存不存在，从服务器下载
    String hashFileContent = downloadHashFileContentFromServer(resourcePath);

    // 4. 解析文件
    Map<String, String> fileRecord = parseHashFileContent(hashFileContent);

    Log.d(TAG, "哈希文件解析成功，记录数: " + fileRecord.size());

    // 5. 存入内存缓存
    fileRecordCache.putAll(fileRecord);

    // 6. 保存文件缓存
    saveHashFileCache(resourcePath, hashFileContent);

    return fileRecord;
  }

  /**
   * 根据文件路径获取预期的MD5值
   * @param relativePath 文件相对路径
   * @return 预期的文件内容MD5值，如果不存在返回null
   */
  public String getExpectedMd5(String relativePath) {
    if (TextUtils.isEmpty(relativePath) || fileRecordCache.isEmpty()) {
      return null;
    }

    // 计算文件相对路径的MD5
    String pathMd5 = EncryptUtils.md5(relativePath);

    // 从缓存中查找
    String expectedMd5 = fileRecordCache.get(pathMd5);

    if (expectedMd5 == null) {
      // 尝试规范化路径后再查找（比如去掉开头的斜杠）
      if (relativePath.startsWith("/")) {
        String normalizedPath = relativePath.substring(1);
        pathMd5 = EncryptUtils.md5(normalizedPath);
        expectedMd5 = fileRecordCache.get(pathMd5);
      }
    }

    return expectedMd5;
  }

  /**
   * 验证文件完整性
   * @param relativePath 文件相对路径
   * @param fileData 文件数据
   * @return 验证结果
   */
  public boolean verifyFileIntegrity(String relativePath, byte[] fileData) {
    try {
      // 1. 获取预期的MD5值
      String expectedMd5 = getExpectedMd5(relativePath);
      if (expectedMd5 == null) {
        Log.w(TAG, "哈希记录中找不到文件: " + relativePath);
        return true;
      }

      // 2. 计算文件内容的MD5
      String actualMd5 = calculateFileMd5(fileData);
      if (actualMd5 == null) {
        return false;
      }

      // 3. 比较MD5值
      boolean isValid = expectedMd5.equalsIgnoreCase(actualMd5);

      Log.d(TAG, String.format("文件完整性校验: %s\n预期: %s\n实际: %s\n结果: %s",
        relativePath, expectedMd5, actualMd5, isValid ? "通过" : "失败"));

      return isValid;

    } catch (Exception e) {
      Log.e(TAG, "文件完整性校验异常: " + relativePath, e);
      return false;
    }
  }

  /**
   * 计算文件内容的MD5
   */
  private String calculateFileMd5(byte[] fileData) {
    try {
      return EncryptUtils.md5Bytes(fileData);
    } catch (Exception e) {
      Log.e(TAG, "计算文件MD5失败", e);
      return null;
    }
  }

  /**
   * 从服务器下载哈希文件内容
   */
  private String downloadHashFileContentFromServer(String resourcePath) throws VersionError {
    try {
      // 构建哈希文件URL
      String url = String.format("%s/resources/%s/resource_hashes",
        Constants.getEnv().getIp(), resourcePath);

      Log.d(TAG, "下载哈希文件URL: " + url);

      Request request = new Request.Builder()
        .url(url)
        .get()
        .build();

      Response response = okHttpClient.newCall(request).execute();

      if (!response.isSuccessful()) {
        throw new VersionError(VersionErrorType.RESOURCE_DOWNLOAD_FAILED,
          "HTTP " + response.code());
      }

      String content = response.body().string();
      Log.d(TAG, "哈希文件下载完成，大小: " + content.length() + " 字节");

      return content;

    } catch (IOException e) {
      Log.e(TAG, "下载哈希文件失败", e);
      throw new VersionError(VersionErrorType.RESOURCE_DOWNLOAD_FAILED, e.getMessage());
    }
  }

  /**
   * 加载缓存的哈希文件
   */
  private Map<String, String> loadCachedHashFile(String resourcePath) {
    File cacheDir = getHashCacheDir();
    File hashFile = new File(cacheDir, getHashFileName(resourcePath));

    if (!hashFile.exists() || hashFile.length() == 0) {
      Log.d(TAG, "哈希文件缓存不存在: " + hashFile.getAbsolutePath());
      return null;
    }

    try {
      String content = readFileToString(hashFile);
      Map<String, String> record = parseHashFileContent(content);
      Log.d(TAG, "从缓存加载哈希文件成功，记录数: " + record.size());
      return record;
    } catch (Exception e) {
      Log.w(TAG, "加载缓存的哈希文件失败，将重新下载", e);
      // 删除损坏的缓存文件
      if (hashFile.exists()) {
        hashFile.delete();
      }
      return null;
    }
  }

  /**
   * 解析哈希文件内容
   */
  private Map<String, String> parseHashFileContent(String content) throws VersionError {
    try {
      if (content == null || content.trim().isEmpty()) {
        throw new VersionError(VersionErrorType.RESOURCE_CONTENT_NULL, "文件内容为空");
      }

      // 解析JSON，key=相对路径的MD5，value=文件内容MD5
      Type type = new TypeToken<Map<String, String>>() {}.getType();
      Map<String, String> fileRecord = gson.fromJson(content, type);

      if (fileRecord == null || fileRecord.isEmpty()) {
        throw new VersionError(VersionErrorType.RESOURCE_PARSE_ERROR, "解析失败或文件为空");
      }

      // 打印一些示例记录用于调试
      int count = 0;
      for (Map.Entry<String, String> entry : fileRecord.entrySet()) {
        if (count < 3) { // 只打印前3条记录
          Log.d(TAG, "哈希记录示例: " + entry.getKey() + " -> " + entry.getValue());
        }
        count++;
      }
      Log.d(TAG, "总共解析出 " + count + " 条哈希记录");

      return fileRecord;

    } catch (Exception e) {
      Log.e(TAG, "解析哈希文件失败", e);
      throw new VersionError(VersionErrorType.RESOURCE_PARSE_ERROR, e.getMessage());
    }
  }

  /**
   * 保存哈希文件到缓存
   */
  private void saveHashFileCache(String resourcePath, String content) {
    File cacheDir = getHashCacheDir();
    File targetFile = new File(cacheDir, getHashFileName(resourcePath));

    // 如果已经存在，先删除
    if (targetFile.exists()) {
      targetFile.delete();
    }

    // 确保目录存在
    targetFile.getParentFile().mkdirs();

    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
      fos.write(content.getBytes("UTF-8"));
      fos.flush();
      Log.d(TAG, "哈希文件已缓存: " + targetFile.getAbsolutePath() +
        " (" + content.length() + " bytes)");
    } catch (IOException e) {
      Log.e(TAG, "缓存哈希文件失败", e);
    }
  }

  /**
   * 获取哈希缓存目录
   */
  private File getHashCacheDir() {
    File cacheDir = new File(context.getFilesDir(), HASH_CACHE_DIR);
    if (!cacheDir.exists()) {
      cacheDir.mkdirs();
    }
    return cacheDir;
  }

  /**
   * 生成哈希文件名
   */
  private String getHashFileName(String resourcePath) {
    // 将路径中的斜杠替换为下划线
    String safeName = resourcePath.replace("/", "_").replace("\\", "_");
    return safeName + ".json";
  }

  /**
   * 读取文件内容
   */
  private String readFileToString(File file) throws IOException {
    try (InputStream inputStream = new FileInputStream(file);
         java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int length;
      while ((length = inputStream.read(buffer)) != -1) {
        result.write(buffer, 0, length);
      }
      return result.toString("UTF-8");
    }
  }

  /**
   * 清除内存缓存
   */
  public void clearMemoryCache() {
    fileRecordCache.clear();
    Log.d(TAG, "哈希文件内存缓存已清除");
  }

  /**
   * 清除文件缓存
   */
  public void clearFileCache() {
    File cacheDir = getHashCacheDir();
    deleteDirectory(cacheDir);
    Log.d(TAG, "哈希文件缓存已清除");
  }

  /**
   * 获取缓存大小
   */
  public long getCacheSize() {
    File cacheDir = getHashCacheDir();
    return getDirectorySize(cacheDir);
  }

  /**
   * 获取内存缓存记录数
   */
  public int getMemoryCacheSize() {
    return fileRecordCache.size();
  }

  /**
   * 递归删除目录
   */
  private boolean deleteDirectory(File dir) {
    if (dir != null && dir.exists()) {
      File[] files = dir.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            deleteDirectory(file);
          } else {
            file.delete();
          }
        }
      }
      return dir.delete();
    }
    return false;
  }

  /**
   * 计算目录大小
   */
  private long getDirectorySize(File dir) {
    if (dir == null || !dir.exists()) {
      return 0;
    }

    long size = 0;
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          size += getDirectorySize(file);
        } else {
          size += file.length();
        }
      }
    }

    return size;
  }

  /**
   * 获取哈希文件记录数量
   */
  public int getFileRecordCount() {
    return fileRecordCache.size();
  }

  /**
   * 检查是否包含某个文件的哈希记录
   */
  public boolean containsFile(String relativePath) {
    return getExpectedMd5(relativePath) != null;
  }
}
