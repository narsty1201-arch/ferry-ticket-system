package com.ferry.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 密码加密工具：需求要求密码不能明文存储，这里用 MD5（32位十六进制） */
public class MD5Util {
    public static String md5(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
