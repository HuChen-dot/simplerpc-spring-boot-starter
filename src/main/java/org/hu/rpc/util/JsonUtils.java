package org.hu.rpc.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.util.TypeUtils;

import java.lang.reflect.Type;

public class JsonUtils {
    // 独立配置，避免改变宿主应用的 Fastjson 全局设置。
    private static final ParserConfig RPC_PARSER = new ParserConfig();
    static {
        RPC_PARSER.setSafeMode(true);
    }

    public static <T> T parse(String json, Type type) {
        return JSON.parseObject(json, type, RPC_PARSER);
    }

    public static JSONObject parseObject(String json) {
        return JSON.parseObject(json, JSONObject.class, RPC_PARSER, Feature.DisableSpecialKeyDetect);
    }

    public static Object convert(Object value, Type type) {
        return TypeUtils.cast(value, type, RPC_PARSER);
    }

}
