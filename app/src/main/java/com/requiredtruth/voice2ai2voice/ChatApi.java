package com.requiredtruth.voice2ai2voice;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ChatApi {
    public static URL endpoint(String address, boolean allowHttp) throws Exception {
        URI uri = new URI(address.trim());
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || (allowHttp && "http".equalsIgnoreCase(scheme))) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Enter a valid HTTPS server URL. Enable HTTP only for a trusted local server.");
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
        if (!path.endsWith("/chat/completions")) path += path.endsWith("/v1") ? "/chat/completions" : "/v1/chat/completions";
        return new URI(scheme, null, uri.getHost(), uri.getPort(), path, null, null).toURL();
    }
    public static JSONObject request(String model, String text) throws JSONException {
        return request(model, new JSONArray().put(message("user", text)), false);
    }
    public static JSONObject message(String role, String text) throws JSONException {
        return new JSONObject().put("role", role).put("content", text);
    }
    public static JSONObject request(String model, JSONArray history, boolean generateUser) throws JSONException {
        JSONArray messages = new JSONArray().put(message("system", "Answer the user directly in plain spoken language. Return only your final answer. Do not include thinking, analysis, or reasoning traces. Be concise unless asked for detail."));
        // Copy the complete session: never mutate it or silently drop old turns.
        for (int i=0;i<history.length();i++) messages.put(new JSONObject(history.getJSONObject(i).toString()));
        if (generateUser) messages.put(message("system", "For this turn only, play the USER in this conversation. Write only the user's next natural follow-up message to the last assistant response. Do not answer it, label roles, or include reasoning. Continue the topic concisely."));
        return new JSONObject().put("model", model).put("stream", false).put("max_tokens", 4096).put("messages", messages);
    }
    public static String answer(String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new JSONException("Server returned no answer choices.");
        JSONObject choice = choices.getJSONObject(0);
        if ("length".equals(choice.optString("finish_reason"))) throw new JSONException("The model reached its output limit. Ask a shorter question or use a model with less reasoning.");
        JSONObject message = choice.getJSONObject("message");
        Object content = message.opt("content"); // reasoning_content and reasoning are deliberately ignored.
        StringBuilder text = new StringBuilder();
        if (content instanceof String) text.append(content);
        else if (content instanceof JSONArray) {
            JSONArray parts = (JSONArray)content;
            for (int i=0;i<parts.length();i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null && "text".equals(part.optString("type"))) text.append(part.optString("text")).append('\n');
            }
        }
        String answer = AnswerText.clean(text.toString());
        if (answer.isEmpty()) throw new JSONException("The server returned no final answer. Reasoning was not spoken. Try a non-reasoning model.");
        return answer;
    }
    public static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count=in.read(buffer)) != -1) {
                if (out.size()+count > 2_000_000) throw new IOException("Server response is too large.");
                out.write(buffer,0,count);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
