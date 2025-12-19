package com.ionicframework.cordova.webview.online;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 版本化Web资源缓存管理器 - 纯文件缓存操作
 * 根据版本号在沙箱中创建目录结构
 */
public class OnLineCacheManager {

  private static final String TAG = "OnLineCacheManager";
  private static final String CACHE_ROOT_DIR_NAME = "webview_cache";

  private final Context context;
  private final File cacheRootDir;
  private String currentVersion;
  private File currentVersionDir;

  // 单例模式
  private static OnLineCacheManager instance;

  public static synchronized OnLineCacheManager getInstance(Context context) {
    if (instance == null) {
      instance = new OnLineCacheManager(context.getApplicationContext());
    }
    return instance;
  }

  private OnLineCacheManager(Context context) {
    this.context = context.getApplicationContext();
    this.cacheRootDir = new File(context.getFilesDir(), CACHE_ROOT_DIR_NAME);

    // 确保根目录存在
    if (!cacheRootDir.exists()) {
      boolean created = cacheRootDir.mkdirs();
      if (created) {
        Log.i(TAG, "创建缓存根目录: " + cacheRootDir.getAbsolutePath());
      } else {
        Log.e(TAG, "创建缓存根目录失败");
      }
    }

    // 注意：这里不加载任何版本信息，等待外部设置
    Log.d(TAG, "OnLineCacheManager初始化完成，等待设置版本");
  }

  /**
   * 设置当前资源版本
   * @param version 版本号，如 "2025121800"
   * @return 是否成功设置
   */
  public boolean setVersion(String version) {
    if (TextUtils.isEmpty(version)) {
      Log.e(TAG, "版本号不能为空");
      return false;
    }

    // 如果版本没有变化，直接返回
    if (version.equals(currentVersion) && currentVersionDir != null && currentVersionDir.exists()) {
      Log.d(TAG, "版本已设置，无需重复设置: " + version);
      return true;
    }

    this.currentVersion = version;
    this.currentVersionDir = new File(cacheRootDir, version);

    // 确保版本目录存在
    if (!currentVersionDir.exists()) {
      boolean created = currentVersionDir.mkdirs();
      if (created) {
        Log.i(TAG, "创建版本目录: " + currentVersionDir.getAbsolutePath());
      } else {
        Log.e(TAG, "创建版本目录失败: " + currentVersionDir.getAbsolutePath());
        return false;
      }
    }

    Log.i(TAG, "版本设置为: " + version);
    return true;
  }

  /**
   * 获取当前版本号
   */
  public String getCurrentVersion() {
    return currentVersion;
  }

  /**
   * 根据相对路径获取本地缓存文件
   * @param relativePath 相对路径，如 "www/build/main.js"
   * @return 缓存文件，如果不存在返回null
   */
  public File getCachedFile(String relativePath) {
    if (currentVersionDir == null || !currentVersionDir.exists()) {
      Log.w(TAG, "版本目录未初始化");
      return null;
    }

    if (TextUtils.isEmpty(relativePath)) {
      return currentVersionDir;
    }

    // 构建完整的文件路径
    File cachedFile = new File(currentVersionDir, relativePath);

    if (!cachedFile.exists()) {
      return null;
    }

    return cachedFile;
  }

  /**
   * 缓存资源到本地
   * @param relativePath 相对路径，如 "www/build/main.js"
   * @param inputStream 资源输入流
   * @return 是否缓存成功
   */
  public boolean cacheResource(String relativePath, InputStream inputStream) {
    if (currentVersionDir == null) {
      Log.e(TAG, "版本未设置，无法缓存资源");
      return false;
    }

    if (TextUtils.isEmpty(relativePath)) {
      Log.e(TAG, "相对路径不能为空");
      return false;
    }

    // 确保目录存在
    File targetFile = new File(currentVersionDir, relativePath);
    File parentDir = targetFile.getParentFile();

    if (parentDir != null && !parentDir.exists()) {
      if (!parentDir.mkdirs()) {
        Log.e(TAG, "创建目录失败: " + parentDir.getAbsolutePath());
        return false;
      }
    }

    // 写入文件
    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
      byte[] buffer = new byte[8192];
      int bytesRead;
      long totalBytes = 0;

      while ((bytesRead = inputStream.read(buffer)) != -1) {
        fos.write(buffer, 0, bytesRead);
        totalBytes += bytesRead;
      }

      Log.d(TAG, "资源已缓存: " + relativePath + " (" + totalBytes + " bytes)");
      return true;

    } catch (IOException e) {
      Log.e(TAG, "缓存资源出错: " + relativePath, e);
      // 如果写入失败，删除可能损坏的文件
      if (targetFile.exists()) {
        targetFile.delete();
      }
      return false;
    } finally {
      try {
        inputStream.close();
      } catch (IOException e) {
        Log.e(TAG, "关闭输入流出错", e);
      }
    }
  }

  /**
   * 缓存从字节数组获取的资源
   */
  public boolean cacheResource(String relativePath, byte[] data) {
    if (data == null || data.length == 0) {
      Log.e(TAG, "数据不能为空或长度为0");
      return false;
    }

    try (InputStream is = new ByteArrayInputStream(data)) {
      return cacheResource(relativePath, is);
    } catch (IOException e) {
      Log.e(TAG, "从字节数组创建输入流出错", e);
      return false;
    }
  }

  /**
   * 检查资源是否已缓存
   */
  public boolean isResourceCached(String relativePath) {
    File cachedFile = getCachedFile(relativePath);
    return cachedFile != null && cachedFile.exists() && cachedFile.length() > 0;
  }

  /**
   * 获取已缓存资源的输入流
   */
  public InputStream getCachedResourceAsStream(String relativePath) {
    File cachedFile = getCachedFile(relativePath);
    if (cachedFile == null) {
      return null;
    }

    try {
      return new FileInputStream(cachedFile);
    } catch (IOException e) {
      Log.e(TAG, "打开缓存文件出错: " + relativePath, e);
      return null;
    }
  }

  /**
   * 获取已缓存资源的大小
   */
  public long getCachedResourceSize(String relativePath) {
    File cachedFile = getCachedFile(relativePath);
    return cachedFile != null && cachedFile.exists() ? cachedFile.length() : 0;
  }

  /**
   * 删除指定版本的缓存
   */
  public boolean deleteVersion(String version) {
    File versionDir = new File(cacheRootDir, version);
    if (!versionDir.exists()) {
      Log.d(TAG, "版本目录不存在，无需删除: " + version);
      return true;
    }

    boolean success = deleteDirectory(versionDir);
    if (success) {
      Log.d(TAG, "删除版本缓存成功: " + version);
    } else {
      Log.e(TAG, "删除版本缓存失败: " + version);
    }

    // 如果删除的是当前版本，清除内存中的版本信息
    if (version.equals(currentVersion)) {
      currentVersion = null;
      currentVersionDir = null;
    }

    return success;
  }

  /**
   * 删除所有版本的缓存
   */
  public boolean clearAllCache() {
    if (!cacheRootDir.exists()) {
      Log.d(TAG, "缓存根目录不存在，无需清除");
      return true;
    }

    boolean success = deleteDirectory(cacheRootDir);
    if (success) {
      // 清除内存中的版本信息
      currentVersion = null;
      currentVersionDir = null;

      Log.d(TAG, "所有缓存已清除");
    } else {
      Log.e(TAG, "清除所有缓存失败");
    }
    return success;
  }

  /**
   * 获取指定版本的缓存大小
   */
  public long getVersionCacheSize(String version) {
    File versionDir = new File(cacheRootDir, version);
    if (!versionDir.exists()) {
      return 0;
    }

    return getDirectorySize(versionDir);
  }

  /**
   * 获取所有版本的总缓存大小
   */
  public long getAllCacheSize() {
    return getDirectorySize(cacheRootDir);
  }

  /**
   * 获取所有缓存的版本号列表
   */
  public String[] getAllCachedVersions() {
    if (!cacheRootDir.exists()) {
      return new String[0];
    }

    File[] versionDirs = cacheRootDir.listFiles();
    if (versionDirs == null) {
      return new String[0];
    }

    String[] versions = new String[versionDirs.length];
    for (int i = 0; i < versionDirs.length; i++) {
      versions[i] = versionDirs[i].getName();
    }

    return versions;
  }

  /**
   * 检查当前版本是否已设置
   */
  public boolean isVersionSet() {
    return !TextUtils.isEmpty(currentVersion) && currentVersionDir != null && currentVersionDir.exists();
  }

  /**
   * 获取当前版本目录的绝对路径
   */
  public String getCurrentVersionPath() {
    return currentVersionDir != null ? currentVersionDir.getAbsolutePath() : null;
  }

  /**
   * 删除目录（递归）
   */
  private boolean deleteDirectory(File dir) {
    if (dir == null || !dir.exists()) {
      return true;
    }

    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          deleteDirectory(file);
        } else {
          if (!file.delete()) {
            Log.w(TAG, "删除文件失败: " + file.getAbsolutePath());
          }
        }
      }
    }

    return dir.delete();
  }

  /**
   * 计算目录大小（递归）
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
   * 获取资源在服务器上的完整URL
   * @param serverBaseUrl 服务器基础URL
   * @param relativePath 相对路径
   * @return 完整的URL
   */
  public String getServerUrl(String serverBaseUrl, String relativePath) {
    if (TextUtils.isEmpty(serverBaseUrl) || TextUtils.isEmpty(currentVersion) || TextUtils.isEmpty(relativePath)) {
      return null;
    }

    // 确保基础URL以斜杠结尾
    if (!serverBaseUrl.endsWith("/")) {
      serverBaseUrl += "/";
    }

    // 构建完整URL: baseUrl + version + "/" + relativePath
    return serverBaseUrl + currentVersion + "/" + relativePath;
  }

  /**
   * 从服务器URL解析出版本号和相对路径
   */
  public static String[] parseUrl(String serverBaseUrl, String fullUrl) {
    if (TextUtils.isEmpty(serverBaseUrl) || TextUtils.isEmpty(fullUrl)) {
      return null;
    }

    // 确保基础URL以斜杠结尾
    if (!serverBaseUrl.endsWith("/")) {
      serverBaseUrl += "/";
    }

    // 检查URL是否以基础URL开头
    if (!fullUrl.startsWith(serverBaseUrl)) {
      return null;
    }

    // 移除基础URL部分
    String remaining = fullUrl.substring(serverBaseUrl.length());

    // 第一个部分是版本号
    int firstSlashIndex = remaining.indexOf("/");
    if (firstSlashIndex <= 0) {
      return null;
    }

    String version = remaining.substring(0, firstSlashIndex);
    String relativePath = remaining.substring(firstSlashIndex + 1);

    return new String[]{version, relativePath};
  }
}
