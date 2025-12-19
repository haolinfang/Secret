package com.ionicframework.cordova.webview.bs;

import java.util.Map;

/**
 * Web资源文件
 */
public class WebBSResFile {
  private WebBSResFileInfo info;
  private Map<String, String> fileRecord;

  public WebBSResFile(WebBSResFileInfo info, Map<String, String> fileRecord) {
    this.info = info;
    this.fileRecord = fileRecord;
  }

  public WebBSResFileInfo getInfo() {
    return info;
  }

  public void setInfo(WebBSResFileInfo info) {
    this.info = info;
  }

  public Map<String, String> getFileRecord() {
    return fileRecord;
  }

  public void setFileRecord(Map<String, String> fileRecord) {
    this.fileRecord = fileRecord;
  }
}
