package aexlrare.simpledialogue;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;

public class DialogueNode {
    private String id;
    private String text;
    private List<Option> options;

    // 控制字段 (与编辑器直接对应)
    public int cooldown = 0;
    public boolean one_time = false;
    public String next_dialogue_id;

    public String getId() { return id; }
    public String getText() { return text; }

    /**
     * 获取选项列表
     * 包含"多级对话"逻辑：如果没配置选项但有 next_dialogue_id，自动生成跳转按钮。
     */
    public List<Option> getOptions() {
        // 1. 如果 JSON 里配置了选项，优先使用配置的
        if (options != null && !options.isEmpty()) {
            return options;
        }

        // 2. 如果没写选项，但配置了下一段对话 (Editor 中的虚线连接)
        if (next_dialogue_id != null && !next_dialogue_id.isEmpty()) {
            Option autoOption = new Option();
            autoOption.text = "[ 继续 ]"; // 这个文本被 DialogueManager 用于特殊渲染
            autoOption.target_id = next_dialogue_id;
            // conditions 和 actions 默认为 null，但下方的 Getter 会安全处理
            return Collections.singletonList(autoOption);
        }

        // 3. 对话结束
        return options;
    }

    public static class Option {
        // 使用 private 防止外部直接访问 null 字段
        private String text;
        private String target_id;
        private JsonObject conditions;
        private JsonObject actions;

        public Option() {}

        public String getText() {
            return text == null ? "" : text;
        }

        public String getTargetId() {
            return target_id;
        }

        // --- 安全的 Getter (关键修改) ---
        // 即使 JSON 中没有 conditions 字段，这里也会返回一个空的 JsonObject，防止 NPE
        public JsonObject getConditions() {
            return conditions != null ? conditions : new JsonObject();
        }

        public JsonObject getActions() {
            return actions != null ? actions : new JsonObject();
        }
    }
}
