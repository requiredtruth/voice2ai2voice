package com.requiredtruth.voice2ai2voice;

import java.util.*;
import java.util.regex.*;

/** Fail closed on unfinished reasoning: it must never reach the speaker. */
public final class AnswerText {
    private static final Pattern TAG = Pattern.compile("(?is)<\\s*(/?)\\s*(think|thinking|analysis|reasoning)\\b[^>]*>");
    public static String clean(String input) {
        if (input == null) return "";
        // Harmony-style channels: only a clearly delimited final channel is speakable.
        if (input.contains("<|channel|>analysis") || input.contains("<|channel|>final") || input.contains("<|analysis|>")) {
            int at = input.indexOf("<|channel|>final");
            int skip = "<|channel|>final".length();
            if (at < 0) { at = input.indexOf("<|final|>"); skip = "<|final|>".length(); }
            if (at < 0) return "";
            input = input.substring(at + skip).replace("<|message|>", "").replace("<|end|>", "").replace("<|im_end|>", "");
        }
        // Some templates open the think block in the prompt and only return its closing tag.
        Matcher closing = TAG.matcher(input);
        int firstClose = -1;
        if (closing.find() && !closing.group(1).isEmpty()) firstClose = closing.end();
        if (firstClose >= 0) input = input.substring(firstClose);
        Matcher m = TAG.matcher(input);
        StringBuilder out = new StringBuilder();
        int depth = 0, pos = 0;
        while (m.find()) {
            if (depth == 0) out.append(input, pos, m.start());
            if (m.group(1).isEmpty()) depth++; else depth = Math.max(0, depth - 1);
            pos = m.end();
        }
        if (depth == 0) out.append(input.substring(pos));
        return out.toString().replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("!?\\[([^]]+)\\]\\([^)]*\\)", "$1")
                .replace("```", "").replace("**", "").replace("__", "").replace("`", "").trim();
    }
    public static List<String> chunks(String text, int limit) {
        List<String> result = new ArrayList<>();
        while (!text.isEmpty()) {
            int end = Math.min(limit, text.length());
            if (end < text.length()) {
                if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
                int space = text.lastIndexOf(' ', end);
                if (space > end / 2) end = space;
            }
            result.add(text.substring(0, end));
            text = text.substring(end).trim();
        }
        return result;
    }
}
