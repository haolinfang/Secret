package com.ionicframework.online.model;

/**
 * Web资源文件信息
 */
public class WebBSResFileInfo {
  private String apiCode;
  private String globleSeqNo;
  private Object tlrNo;
  private Object brchCd;
  private Object tlrTp;
  private Object tlrLvl;
  private String resourcePath; // 资源路径
  private String resourceVersion; // 资源版本号
  private String bankAppVersion; // 应用版本号
  private String newAppVersion; // 最新版本号
  private String updateFlag; // 是否强制更新 0否1是
  private String resCode;
  private String resMsg;
  private String originMsg;
  private String originCode;
  private Object privacyInfo; // 隐私政策版本号
  private Object advImgs; // 广告列表

  // 构造函数
  public WebBSResFileInfo() {
  }

  // Getter 和 Setter 方法
  public String getApiCode() {
    return apiCode;
  }

  public void setApiCode(String apiCode) {
    this.apiCode = apiCode;
  }

  public String getGlobleSeqNo() {
    return globleSeqNo;
  }

  public void setGlobleSeqNo(String globleSeqNo) {
    this.globleSeqNo = globleSeqNo;
  }

  public Object getTlrNo() {
    return tlrNo;
  }

  public void setTlrNo(Object tlrNo) {
    this.tlrNo = tlrNo;
  }

  public Object getBrchCd() {
    return brchCd;
  }

  public void setBrchCd(Object brchCd) {
    this.brchCd = brchCd;
  }

  public Object getTlrTp() {
    return tlrTp;
  }

  public void setTlrTp(Object tlrTp) {
    this.tlrTp = tlrTp;
  }

  public Object getTlrLvl() {
    return tlrLvl;
  }

  public void setTlrLvl(Object tlrLvl) {
    this.tlrLvl = tlrLvl;
  }

  public String getResourcePath() {
    return resourcePath;
  }

  public void setResourcePath(String resourcePath) {
    this.resourcePath = resourcePath;
  }

  public String getResourceVersion() {
    return resourceVersion;
  }

  public void setResourceVersion(String resourceVersion) {
    this.resourceVersion = resourceVersion;
  }

  public String getBankAppVersion() {
    return bankAppVersion;
  }

  public void setBankAppVersion(String bankAppVersion) {
    this.bankAppVersion = bankAppVersion;
  }

  public String getNewAppVersion() {
    return newAppVersion;
  }

  public void setNewAppVersion(String newAppVersion) {
    this.newAppVersion = newAppVersion;
  }

  public String getUpdateFlag() {
    return updateFlag;
  }

  public void setUpdateFlag(String updateFlag) {
    this.updateFlag = updateFlag;
  }

  public String getResCode() {
    return resCode;
  }

  public void setResCode(String resCode) {
    this.resCode = resCode;
  }

  public String getResMsg() {
    return resMsg;
  }

  public void setResMsg(String resMsg) {
    this.resMsg = resMsg;
  }

  public String getOriginMsg() {
    return originMsg;
  }

  public void setOriginMsg(String originMsg) {
    this.originMsg = originMsg;
  }

  public String getOriginCode() {
    return originCode;
  }

  public void setOriginCode(String originCode) {
    this.originCode = originCode;
  }

  public Object getPrivacyInfo() {
    return privacyInfo;
  }

  public void setPrivacyInfo(Object privacyInfo) {
    this.privacyInfo = privacyInfo;
  }

  public Object getAdvImgs() {
    return advImgs;
  }

  public void setAdvImgs(Object advImgs) {
    this.advImgs = advImgs;
  }

  @Override
  public String toString() {
    return "WebBSResFileInfo{" +
      "resourcePath='" + resourcePath + '\'' +
      ", resourceVersion='" + resourceVersion + '\'' +
      ", bankAppVersion='" + bankAppVersion + '\'' +
      ", newAppVersion='" + newAppVersion + '\'' +
      ", updateFlag='" + updateFlag + '\'' +
      ", resCode='" + resCode + '\'' +
      ", resMsg='" + resMsg + '\'' +
      '}';
  }
}
