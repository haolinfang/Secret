package com.ionicframework.online.core;

import android.content.Context;
import android.util.Log;

import com.ionicframework.online.model.VersionError;
import com.ionicframework.online.model.VersionErrorType;
import com.ionicframework.online.model.WebBSResFile;
import com.ionicframework.online.model.WebBSResFileInfo;
import com.ionicframework.online.utils.NetworkUtils;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Web资源版本协调器 - 负责协调版本信息获取和哈希文件下载
 */
public class VersionInfoManager {
  private static final String TAG = "WebBSVersionHelper";

  private final Context context;
  private final ExecutorService executorService;
  private final VersionInfoFetcher versionInfoFetcher;
  private final HashFileDownloader hashFileDownloader;

  // 单例模式
  private static VersionInfoManager instance;

  public static synchronized VersionInfoManager getInstance(Context context) {
    if (instance == null) {
      instance = new VersionInfoManager(context.getApplicationContext());
    }
    return instance;
  }

  private VersionInfoManager(Context context) {
    this.context = context.getApplicationContext();

    // 直接创建各个组件，让它们自己管理依赖
    this.versionInfoFetcher = new VersionInfoFetcher(context);
    this.hashFileDownloader = new HashFileDownloader(context);

    // 使用单线程执行器（版本获取通常是顺序执行）
    this.executorService = Executors.newSingleThreadExecutor();
  }

  /**
   * 异步获取版本信息（主方法）
   */
  public void getVersionInfoAsync(final VersionCallback callback) {
    // 检查网络
    if (!NetworkUtils.isNetworkAvailable(context)) {
      if (callback != null) {
        callback.onError(new VersionError(VersionErrorType.NETWORK_UNAVAILABLE, "网络不可用"));
      }
      return;
    }

    executorService.execute(new Runnable() {
      @Override
      public void run() {
        try {
          // 1. 请求版本接口
          WebBSResFileInfo versionInfo = versionInfoFetcher.fetchVersionInfo();

          // 2. 下载并解析哈希文件
          Map<String, String> fileRecord = hashFileDownloader.downloadHashFile(versionInfo);

          // 3. 返回结果
          WebBSResFile result = new WebBSResFile(versionInfo, fileRecord);

          if (callback != null) {
            callback.onSuccess(result);
          }

        } catch (VersionError e) {
          Log.e(TAG, "获取版本信息失败", e);
          if (callback != null) {
            callback.onError(e);
          }
        }
      }
    });
  }

  /**
   * 关闭资源
   */
  public void shutdown() {
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
    }
  }

  public interface VersionCallback {
    void onSuccess(WebBSResFile result);
    void onError(VersionError error);
  }
}
