package org.hu.rpc.util;

/**
 * @Author: hu.chen
 * @Description: json工具类
 * @DateTime: 2021/12/30 11:56 AM
 **/
public class JsonUtils {


    /**
     * 验证字符串是否是json
     * @param str
     * @return
     */
    public static boolean isJsonType(String str) {
        boolean result = false;
        if (str != null && str.length() > 0) {
            str = str.trim();
            if (str.startsWith("{") && str.endsWith("}")) {
                result = true;
            } else if (str.startsWith("[") && str.endsWith("]")) {
                result = true;
            }
        }
        return result;
    }
}
