package org.hu.rpcintegration;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.ParserConfig;
import org.hu.rpc.util.JsonUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsonSafetyTest {
    @Test
    public void rpcParserRejectsNetworkSuppliedAutoType() {
        String json = "{\"@type\":\"" + RpcServerHandlerTest.Person.class.getName() + "\",\"name\":\"unsafe\"}";
        assertThrows(JSONException.class, () -> JsonUtils.parse(json, Object.class));
        JSONObject value = JsonUtils.parseObject(json);
        assertThrows(JSONException.class, () -> JsonUtils.convert(value, RpcServerHandlerTest.Person.class));
    }

    @Test
    public void rpcParserDoesNotChangeApplicationParserSettings() {
        ParserConfig applicationConfig = ParserConfig.getGlobalInstance();
        boolean safeMode = applicationConfig.isSafeMode();
        boolean autoType = applicationConfig.isAutoTypeSupport();
        assertEquals("person", ((RpcServerHandlerTest.Person) JsonUtils.convert(
                JsonUtils.parseObject("{\"name\":\"person\"}"), RpcServerHandlerTest.Person.class)).getName());
        assertEquals(safeMode, applicationConfig.isSafeMode());
        assertEquals(autoType, applicationConfig.isAutoTypeSupport());
    }
}
