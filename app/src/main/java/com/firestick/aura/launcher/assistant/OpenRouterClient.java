package com.firestick.aura.launcher.assistant;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal OpenRouter chat client (OpenAI-compatible API). Blocking: call from a background thread.
 * The API key is only ever written to the Authorization header; it is never logged.
 */
public final class OpenRouterClient {
    public static final String CHAT_URL = "https://openrouter.ai/api/v1/chat/completions";
    public static final String KEY_URL = "https://openrouter.ai/api/v1/key";
    private static final String REFERER = "https://github.com/engziada/Lumora";
    private static final String TITLE = "Lumora";
    private static final int CONNECT_TIMEOUT_MS = 15_000, READ_TIMEOUT_MS = 60_000;
    static final int MAX_TOKENS = 700;

    public static final int OK = 0, ERR_NO_KEY = 1, ERR_AUTH = 2, ERR_CREDITS = 3, ERR_RATE = 4,
            ERR_FLAGGED = 5, ERR_NETWORK = 6, ERR_SERVER = 7, ERR_EMPTY = 8;

    private OpenRouterClient() {
    }

    public static final class Message {
        public final String role, content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static final class Result {
        public final int error;
        public final String text, model, detail;

        Result(int error, String text, String model, String detail) {
            this.error = error;
            this.text = text;
            this.model = model;
            this.detail = detail;
        }

        public boolean ok() {
            return error == OK;
        }

        static Result failure(int error, String detail) {
            return new Result(error, "", "", detail == null ? "" : detail);
        }
    }

    public static Result chat(String apiKey, String model, List<Message> messages) {
        if (apiKey == null || apiKey.trim().isEmpty()) return Result.failure(ERR_NO_KEY, null);
        String body;
        try {
            body = buildRequest(model, messages);
        } catch (JSONException exception) {
            return Result.failure(ERR_SERVER, "request");
        }
        Response response = send("POST", CHAT_URL, apiKey.trim(), body);
        if (response == null) return Result.failure(ERR_NETWORK, null);
        return parseChat(response.status, response.body);
    }

    /** Validates the key without spending a chat request. Text holds the key label when present. */
    public static Result checkKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) return Result.failure(ERR_NO_KEY, null);
        Response response = send("GET", KEY_URL, apiKey.trim(), null);
        if (response == null) return Result.failure(ERR_NETWORK, null);
        if (response.status != HttpURLConnection.HTTP_OK) return Result.failure(errorFor(response.status), errorMessage(response.body));
        try {
            JSONObject data = new JSONObject(response.body).optJSONObject("data");
            String label = data == null || data.isNull("label") ? "" : data.optString("label", "");
            return new Result(OK, label, "", "");
        } catch (JSONException exception) {
            return new Result(OK, "", "", "");
        }
    }

    static String buildRequest(String model, List<Message> messages) throws JSONException {
        JSONArray array = new JSONArray();
        for (Message message : messages) {
            array.put(new JSONObject().put("role", message.role).put("content", message.content));
        }
        return new JSONObject()
                .put("model", model)
                .put("messages", array)
                .put("max_tokens", MAX_TOKENS)
                .toString();
    }

    static Result parseChat(int status, String body) {
        if (status != HttpURLConnection.HTTP_OK) return Result.failure(errorFor(status), errorMessage(body));
        try {
            JSONObject root = new JSONObject(body);
            // OpenRouter can return 200 with an error object when the upstream model fails mid-request.
            JSONObject error = root.optJSONObject("error");
            if (error != null) return Result.failure(errorFor(error.optInt("code", 500)), error.optString("message", ""));
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return Result.failure(ERR_EMPTY, null);
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            String content = message == null || message.isNull("content") ? "" : message.optString("content", "");
            if (content.trim().isEmpty()) return Result.failure(ERR_EMPTY, null);
            String model = root.isNull("model") ? "" : root.optString("model", "");
            return new Result(OK, content.trim(), model, "");
        } catch (JSONException exception) {
            return Result.failure(ERR_SERVER, "response");
        }
    }

    static int errorFor(int status) {
        switch (status) {
            case 401:
                return ERR_AUTH;
            case 402:
                return ERR_CREDITS;
            case 403:
                return ERR_FLAGGED;
            case 429:
                return ERR_RATE;
            default:
                return ERR_SERVER;
        }
    }

    static String errorMessage(String body) {
        if (body == null || body.isEmpty()) return "";
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            return error == null ? "" : error.optString("message", "");
        } catch (JSONException exception) {
            return "";
        }
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static Response send(String method, String url, String apiKey, String body) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("HTTP-Referer", REFERER);
            connection.setRequestProperty("X-Title", TITLE);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = "";
            if (stream != null) {
                try (InputStream in = stream) {
                    text = new String(readAll(in), StandardCharsets.UTF_8);
                }
            }
            return new Response(status, text);
        } catch (IOException | RuntimeException exception) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }
}
