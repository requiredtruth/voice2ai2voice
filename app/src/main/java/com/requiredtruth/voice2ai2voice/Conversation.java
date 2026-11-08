package com.requiredtruth.voice2ai2voice;

import org.json.*;

/** UI-thread-owned session. Network workers receive immutable serialized snapshots. */
public final class Conversation {
    public enum Mode { SINGLE, CONVERSATION, SELF_TALK }
    private Mode mode = Mode.SINGLE;
    private JSONArray history = new JSONArray();
    public Mode mode() { return mode; }
    public void start(Mode next) { mode=next; history=new JSONArray(); }
    public void stop() { start(Mode.SINGLE); }
    public void add(String role, String text) throws JSONException { history.put(ChatApi.message(role,text)); }
    public String payload(String model, boolean generateUser) throws JSONException {
        if (generateUser && mode!=Mode.SELF_TALK) throw new IllegalStateException("Self-talk is not active.");
        return ChatApi.request(model,history,generateUser).toString();
    }
}
