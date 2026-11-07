package com.requiredtruth.voice2ai2voice;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
public class ChatTests {
    @Test public void freshConversations() throws Exception {
        JSONObject first=ChatApi.request("local-model","First");
        JSONObject second=ChatApi.request("local-model","Second");
        assertEquals(2,second.getJSONArray("messages").length());
        assertEquals("Second",second.getJSONArray("messages").getJSONObject(1).getString("content"));
        assertFalse(second.toString().contains("First"));
        assertFalse(first.getBoolean("stream"));
        assertEquals(4096,first.getInt("max_tokens"));
    }
    @Test public void endpoints() throws Exception {
        assertEquals("https://example.com/v1/chat/completions",ChatApi.endpoint("https://example.com/",false).toString());
        assertEquals("https://example.com/v1/chat/completions",ChatApi.endpoint("https://example.com/v1/",false).toString());
        assertEquals("http://192.168.1.2:8080/v1/chat/completions",ChatApi.endpoint("http://192.168.1.2:8080/v1/chat/completions",true).toString());
        assertEquals("https://example.com/proxy/v1/chat/completions",ChatApi.endpoint("https://example.com/proxy/v1",false).toString());
    }
    @Test public void insecureUrlsRejected() {
        for(String url:new String[]{"http://example.com","https://user:secret@example.com","https://example.com?key=secret","file:///tmp/x","garbage","https://example.com/#key"}) {
            try { ChatApi.endpoint(url,false); fail(url); } catch(Exception expected) {}
        }
    }
    @Test public void removesReasoning() {
        assertEquals("The answer is 42.",AnswerText.clean("<think>secret</think>The answer is 42."));
        assertEquals("Hello",AnswerText.clean("<THINK>nested <analysis>private</analysis></THINK> Hello"));
        assertEquals("",AnswerText.clean("<think>unfinished private content"));
        assertEquals("Hello",AnswerText.clean("private prompt-opened reasoning</think>Hello"));
        assertEquals("Visible",AnswerText.clean("<|channel|>analysis private <|channel|>final<|message|>Visible<|end|>"));
        assertEquals("",AnswerText.clean("<|channel|>analysis private"));
        assertEquals("Before After",AnswerText.clean("Before <reasoning>private</reasoning>After"));
    }
    @Test public void separateReasoningNeverRead() throws Exception {
        assertEquals("Hello",ChatApi.answer("{\"choices\":[{\"message\":{\"reasoning_content\":\"private\",\"reasoning\":\"also private\",\"content\":\"<think>hidden</think>Hello\"}}]}"));
        assertEquals("Hello",ChatApi.answer("{\"choices\":[{\"message\":{\"content\":[{\"type\":\"reasoning\",\"text\":\"private\"},{\"type\":\"text\",\"text\":\"Hello\"}]}}]}"));
    }
    @Test public void emptyOrTruncatedFailsClosed() throws Exception {
        for(String body:new String[]{"{\"choices\":[]}","{\"choices\":[{\"message\":{\"reasoning_content\":\"private\",\"content\":null}}]}","{\"choices\":[{\"message\":{\"content\":\"<think>private\"}}]}","{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\"}}]}"}) {
            try { ChatApi.answer(body); fail(body); } catch(JSONException expected) {}
        }
    }
    @Test public void chunksLongAnswers() {
        String longAnswer="A complete answer. ".repeat(1000);
        java.util.List<String> chunks=AnswerText.chunks(longAnswer,3000);
        assertTrue(chunks.size()>1);
        for(String part:chunks) assertTrue(part.length()<=3000);
        assertEquals(longAnswer.trim(),String.join(" ",chunks));
    }
}
