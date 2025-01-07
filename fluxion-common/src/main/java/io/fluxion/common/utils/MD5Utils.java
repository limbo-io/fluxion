/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.common.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * @author Brozen
 */
public final class MD5Utils {


    /**
     * 对入参进行MD5加密，并返回签名二进制数据。
     * @param origin 原始字符串
     * @return 签名二进制数据
     */
    public static byte[] bytes(String origin) {
        return bytes(origin.getBytes(StandardCharsets.UTF_8));
    }


    public static String md5(String origin) {
        byte[] messageDigest = bytes(origin);
        // 将 byte 转换为 16 进制字符串
        StringBuilder hexString = new StringBuilder();
        for (byte b : messageDigest) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0'); // 如果是单个字符前加 0
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 对入参进行MD5加密，并返回签名二进制数据。
     * @param origin 原始字符串
     * @param charset 原始字符串字符集
     * @return 签名二进制数据
     */
    public static byte[] bytes(String origin, Charset charset) {
        Objects.requireNonNull(charset, "charset");
        return bytes(origin.getBytes(charset));
    }


    /**
     * 对入参进行MD5加密，并返回签名二进制数据。
     * @param origin 原始字符串
     * @param charset 原始字符串字符集
     * @return 签名二进制数据
     */
    public static byte[] bytes(String origin, String charset) {
        try {
            charset = StringUtils.defaultIfBlank(charset, StandardCharsets.UTF_8.name());
            return bytes(origin.getBytes(charset));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("unsupported encoding origin:" + origin + " charset: " + charset, e);
        }
    }

    /**
     * 计算 MD5 签名，返回签名二进制数据。
     * @param bytes	原始数据
     * @return 签名二进制数据
     */
    public static byte[] bytes(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("MD5 digest fail", e);
        }
    }

}
