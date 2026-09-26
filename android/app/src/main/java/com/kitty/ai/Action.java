package com.kitty.ai;

import org.json.JSONException;
import org.json.JSONObject;

public final class Action {
    public final String kind, target, text;
    public Action(String kind, String target, String text) { this.kind=kind; this.target=target; this.text=text; }
    public JSONObject json() throws JSONException { return new JSONObject().put("kind",kind).put("target",target).put("text",text); }
}
