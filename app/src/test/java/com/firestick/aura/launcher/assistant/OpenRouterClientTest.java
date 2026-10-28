package com.firestick.aura.launcher.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class OpenRouterClientTest {
    @Test
    public void buildRequestUsesModelMessagesAndTokenCap() throws Exception {
        String body = OpenRouterClient.buildRequest("openrouter/free", Arrays.asList(
                new OpenRouterClient.Message("system", "rules"),
                new OpenRouterClient.Message("user", "\u0645\u0631\u062d\u0628\u0627")));
        JSONObject json = new JSONObject(body);
        assertEquals("openrouter/free", json.getString("model"));
        assertEquals(OpenRouterClient.MAX_TOKENS, json.getInt("max_tokens"));
        JSONArray messages = json.getJSONArray("messages");
        assertEquals(2, messages.length());
        assertEquals("user", messages.getJSONObject(1).getString("role"));
        assertEquals("\u0645\u0631\u062d\u0628\u0627", messages.getJSONObject(1).getString("content"));
    }

    @Test
    public void parseChatReturnsContentAndModel() {
        String body = "{\"model\":\"google/gemma-4-31b-it:free\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\" Hello \"}}]}";
        OpenRouterClient.Result result = OpenRouterClient.parseChat(200, body);
        assertTrue(result.ok());
        assertEquals("Hello", result.text);
        assertEquals("google/gemma-4-31b-it:free", result.model);
    }

    @Test
    public void parseChatTreatsNullOrBlankContentAsEmpty() {
        assertEquals(OpenRouterClient.ERR_EMPTY, OpenRouterClient.parseChat(200,
                "{\"choices\":[{\"message\":{\"content\":null}}]}").error);
        assertEquals(OpenRouterClient.ERR_EMPTY, OpenRouterClient.parseChat(200,
                "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}").error);
        assertEquals(OpenRouterClient.ERR_EMPTY, OpenRouterClient.parseChat(200, "{\"choices\":[]}").error);
    }

    @Test
    public void parseChatMapsErrorObjectInSuccessfulResponse() {
        OpenRouterClient.Result result = OpenRouterClient.parseChat(200,
                "{\"error\":{\"code\":429,\"message\":\"Rate limit exceeded\"}}");
        assertFalse(result.ok());
        assertEquals(OpenRouterClient.ERR_RATE, result.error);
        assertEquals("Rate limit exceeded", result.detail);
    }

    @Test
    public void parseChatMapsHttpStatuses() {
        String body = "{\"error\":{\"message\":\"No auth credentials found\"}}";
        OpenRouterClient.Result auth = OpenRouterClient.parseChat(401, body);
        assertEquals(OpenRouterClient.ERR_AUTH, auth.error);
        assertEquals("No auth credentials found", auth.detail);
        assertEquals(OpenRouterClient.ERR_CREDITS, OpenRouterClient.parseChat(402, "").error);
        assertEquals(OpenRouterClient.ERR_FLAGGED, OpenRouterClient.parseChat(403, "").error);
        assertEquals(OpenRouterClient.ERR_RATE, OpenRouterClient.parseChat(429, "").error);
        assertEquals(OpenRouterClient.ERR_SERVER, OpenRouterClient.parseChat(502, "<html>").error);
    }

    @Test
    public void parseChatRejectsInvalidJson() {
        assertEquals(OpenRouterClient.ERR_SERVER, OpenRouterClient.parseChat(200, "not json").error);
    }

    @Test
    public void chatWithoutKeyFailsWithoutNetwork() {
        assertEquals(OpenRouterClient.ERR_NO_KEY, OpenRouterClient.chat(" ", "openrouter/free", Arrays.asList()).error);
        assertEquals(OpenRouterClient.ERR_NO_KEY, OpenRouterClient.checkKey(null).error);
    }
}
