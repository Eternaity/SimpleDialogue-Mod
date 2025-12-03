package aexlrare.simpledialogue;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class DialogueNode {
    @SerializedName("id")
    public String id;

    @SerializedName("text")
    public String text;

    @SerializedName("options")
    public List<Option> options = new ArrayList<>();

    @SerializedName("one_time")
    public boolean one_time = false;

    @SerializedName("cooldown")
    public int cooldown = 0;

    public String getId() { return id; }
    public String getText() { return text != null ? text : ""; }
    public List<Option> getOptions() { return options; }

    public static class Option {
        @SerializedName("text")
        public String text;

        @SerializedName("target_id")
        public String target_id;

        @SerializedName("conditions")
        public JsonObject conditions;

        @SerializedName("actions")
        public JsonObject actions;

        // QTE 机制字段
        @SerializedName("qte_timeout")
        public int qte_timeout = 0; // 超时秒数，0表示无QTE

        @SerializedName("timeout_target")
        public String timeout_target; // 超时后跳转的对话ID

        public String getText() { return text != null ? text : "继续"; }
        public String getTargetId() { return target_id; }
        public JsonObject getConditions() { return conditions; }
        public JsonObject getActions() { return actions; }
    }
}