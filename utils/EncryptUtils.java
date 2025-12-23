package com.ionicframework.online.utils;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 加解密工具类
 */
public class EncryptUtils {
  private static final String TAG = "EncryptUtils";

  // RSA 2048位公钥（与前端保持一致）
  public static final String PUBLIC_2048_KEY =
    "-----BEGIN PUBLIC KEY-----\n" +
      "\n" +
      "-----END PUBLIC KEY-----";

  // AES加密用的RSA公钥
  public static final String PUBLIC_AES_KEY =
    "-----BEGIN PUBLIC KEY-----\n" +
      "\n" +
      "-----END PUBLIC KEY-----";

  /**
   * 生成16位随机字符串（数字和小写字母）
   */
  public static String syncGenerator() {
    String characters = "0123456789abcdefghijklmnopqrstuvwxyz";
    StringBuilder result = new StringBuilder(16);
    Random random = new SecureRandom();

    for (int i = 0; i < 16; i++) {
      int index = random.nextInt(characters.length());
      result.append(characters.charAt(index));
    }

    return result.toString();
  }

  /**
   * 使用RSA公钥加密数据
   * @param text 明文
   * @param publicKeyStr 公钥字符串
   * @return Base64编码的密文
   */
  public static String rsaEncrypt(String text, String publicKeyStr) {
    try {
      // 1. 移除PEM格式的标记和换行符
      String publicKeyPEM = publicKeyStr
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replaceAll("\\s", "");

      // 2. Base64解码公钥
      byte[] publicKeyBytes = Base64.decode(publicKeyPEM, Base64.DEFAULT);

      // 3. 生成PublicKey对象
      X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      PublicKey publicKey = keyFactory.generatePublic(keySpec);

      // 4. 使用RSA/ECB/PKCS1Padding加密
      Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
      cipher.init(Cipher.ENCRYPT_MODE, publicKey);

      // 5. 加密数据
      byte[] encryptedBytes = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));

      // 6. Base64编码结果
      return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);

    } catch (Exception e) {
      Log.e(TAG, "RSA加密失败", e);
      return "";
    }
  }

  /**
   * 使用AES公钥加密数据（包装方法）
   */
  public static String rsaEncrypt(String text) {
    return rsaEncrypt(text, PUBLIC_AES_KEY);
  }

  /**
   * 使用2048位RSA公钥加密数据（包装方法）
   */
  public static String rsaEncrypt2048(String text) {
    return rsaEncrypt(text, PUBLIC_2048_KEY);
  }

  /**
   * HMAC-SHA512签名
   * @param key 密钥
   * @param data 数据
   * @return 十六进制字符串
   */
  public static String hmacSHA512(String key, String data) {
    try {
      // 1. 创建Mac实例
      Mac mac = Mac.getInstance("HmacSHA512");

      // 2. 创建密钥
      SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");

      // 3. 初始化和计算
      mac.init(secretKey);
      byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

      // 4. 转换为十六进制字符串
      return bytesToHex(hash);

    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      Log.e(TAG, "HMAC-SHA512计算失败", e);
      return "";
    }
  }

  /**
   * AES解密（CBC模式，PKCS5Padding）
   * @param encryptedData Base64编码的密文
   * @param keyStr AES密钥（16位）
   * @param ivStr IV（16位）
   * @return 解密后的明文
   */
  public static String decryptAes(String encryptedData, String keyStr, String ivStr) {
    try {
      // 1. 验证参数
      if (encryptedData == null || keyStr == null || ivStr == null) {
        throw new IllegalArgumentException("参数不能为空");
      }

      // 2. Base64解码密文
      byte[] encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT);

      // 3. 创建密钥和IV
      byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
      byte[] ivBytes = ivStr.getBytes(StandardCharsets.UTF_8);

      if (keyBytes.length != 16) {
        throw new IllegalArgumentException("密钥必须是16字节（UTF-8编码）");
      }
      if (ivBytes.length != 16) {
        throw new IllegalArgumentException("IV必须是16字节（UTF-8编码）");
      }

      // 4. 创建SecretKeySpec和IvParameterSpec
      SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
      IvParameterSpec iv = new IvParameterSpec(ivBytes);

      // 5. 创建并初始化解密器
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);

      // 6. 解密
      byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

      // 7. 转换为字符串
      return new String(decryptedBytes, StandardCharsets.UTF_8);

    } catch (Exception e) {
      Log.e(TAG, "AES解密失败", e);
      return "";
    }
  }

  /**
   * MD5加密
   */
  public static String md5(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(digest);
    } catch (NoSuchAlgorithmException e) {
      Log.e(TAG, "MD5计算失败", e);
      return "";
    }
  }

  /**
   * MD5加密
   */
  public static String md5Bytes(byte[] input) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(input);
      return bytesToHex(digest);
    } catch (NoSuchAlgorithmException e) {
      Log.e(TAG, "MD5计算失败", e);
      return "";
    }
  }

  /**
   * SHA256加密
   */
  public static String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(digest);
    } catch (NoSuchAlgorithmException e) {
      Log.e(TAG, "SHA256计算失败", e);
      return "";
    }
  }

  /**
   * 字节数组转十六进制字符串
   */
  private static String bytesToHex(byte[] bytes) {
    StringBuilder hexString = new StringBuilder();
    for (byte b : bytes) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }
}
