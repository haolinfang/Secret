package com.ionicframework.online.core;

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
 * Web资源缓存管理器 - 纯文件缓存操作
 */
public class OnLineCacheManager {

  private static final String TAG = "OnLineCacheManager";
  private static final String CACHE_ROOT_DIR_NAME = "webview_cache";

  private final File cacheRootDir;
  private String currentResourcePath;
  private File currentResourceDir;

  // 单例模式
  private static OnLineCacheManager instance;

  public static synchronized OnLineCacheManager getInstance(Context context) {
    if (instance == null) {
      instance = new OnLineCacheManager(context.getApplicationContext());
    }
    return instance;
  }

  private OnLineCacheManager(Context context) {
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

    Log.d(TAG, "OnLineCacheManager初始化完成，等待设置资源路径");
  }

  /**
   * 设置当前资源路径
   * @param resourcePath 资源路径，如 "20251218/v1"
   * @return 是否成功设置
   */
  public boolean setResourcePath(String resourcePath) {
    if (TextUtils.isEmpty(resourcePath)) {
      Log.e(TAG, "资源路径不能为空");
      return false;
    }

    this.currentResourcePath = resourcePath;
    this.currentResourceDir = new File(cacheRootDir, resourcePath);

    if (!currentResourceDir.exists()) {
      boolean created = currentResourceDir.mkdirs();
      if (!created) {
        Log.e(TAG, "创建资源目录失败: " + currentResourceDir.getAbsolutePath());
        return false;
      }
      Log.i(TAG, "创建资源目录: " + currentResourceDir.getAbsolutePath());
    }

    Log.i(TAG, "资源路径设置为: " + resourcePath);
    return true;
  }

  /**
   * 根据相对路径获取本地缓存文件
   * @param relativePath 相对路径，如 "www/build/main.js"
   * @return 缓存文件，如果不存在返回null
   */
  public File getCachedFile(String relativePath) {
    if (currentResourceDir == null || !currentResourceDir.exists()) {
      Log.w(TAG, "资源目录未初始化");
      return null;
    }

    if (TextUtils.isEmpty(relativePath)) {
      return currentResourceDir;
    }

    // 构建完整的文件路径
    File cachedFile = new File(currentResourceDir, relativePath);

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
    if (currentResourceDir == null) {
      Log.e(TAG, "资源路径未设置，无法缓存资源");
      return false;
    }

    if (TextUtils.isEmpty(relativePath)) {
      Log.e(TAG, "相对路径不能为空");
      return false;
    }

    // 确保目录存在
    File targetFile = new File(currentResourceDir, relativePath);
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
   * 删除指定资源文件
   * @param relativePath 相对路径
   * @return 是否删除成功
   */
  public boolean deleteCachedFile(String relativePath) {
    if (currentResourceDir == null || !currentResourceDir.exists()) {
      Log.w(TAG, "资源目录未初始化");
      return false;
    }

    if (TextUtils.isEmpty(relativePath)) {
      Log.e(TAG, "相对路径不能为空");
      return false;
    }

    File targetFile = new File(currentResourceDir, relativePath);
    if (!targetFile.exists()) {
      Log.d(TAG, "文件不存在，无需删除: " + relativePath);
      return true;
    }

    boolean success = targetFile.delete();
    if (success) {
      Log.d(TAG, "删除文件成功: " + relativePath);

      // 尝试删除空目录
      deleteEmptyParentDirectories(targetFile);
    } else {
      Log.e(TAG, "删除文件失败: " + relativePath);
    }

    return success;
  }

  /**
   * 删除指定资源路径的缓存
   */
  public boolean deleteResourcePath(String resourcePath) {
    File resourceDir = new File(cacheRootDir, resourcePath);
    if (!resourceDir.exists()) {
      Log.d(TAG, "资源目录不存在，无需删除: " + resourcePath);
      return true;
    }

    boolean success = deleteDirectory(resourceDir);
    if (success) {
      Log.d(TAG, "删除资源缓存成功: " + resourcePath);
    } else {
      Log.e(TAG, "删除资源缓存失败: " + resourcePath);
    }

    // 如果删除的是当前资源路径，清除内存中的信息
    if (resourcePath.equals(currentResourcePath)) {
      currentResourcePath = null;
      currentResourceDir = null;
    }

    return success;
  }

  /**
   * 删除所有缓存
   */
  public boolean clearAllCache() {
    if (!cacheRootDir.exists()) {
      Log.d(TAG, "缓存根目录不存在，无需清除");
      return true;
    }

    boolean success = deleteDirectory(cacheRootDir);
    if (success) {
      // 清除内存中的信息
      currentResourcePath = null;
      currentResourceDir = null;

      Log.d(TAG, "所有缓存已清除");
    } else {
      Log.e(TAG, "清除所有缓存失败");
    }
    return success;
  }

  /**
   * 检查当前资源路径是否已设置
   */
  public boolean isResourcePathSet() {
    return !TextUtils.isEmpty(currentResourcePath) &&
      currentResourceDir != null &&
      currentResourceDir.exists();
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
   * 递归删除空目录
   */
  private void deleteEmptyParentDirectories(File file) {
    if (file == null) return;

    File parent = file.getParentFile();
    if (parent != null && parent.exists() && parent.isDirectory() &&
      !parent.equals(currentResourceDir)) { // 不要删除资源根目录

      File[] files = parent.listFiles();
      if (files != null && files.length == 0) {
        boolean deleted = parent.delete();
        if (deleted) {
          Log.d(TAG, "删除空目录: " + parent.getAbsolutePath());
          // 继续向上检查父目录
          deleteEmptyParentDirectories(parent);
        }
      }
    }
  }
}
