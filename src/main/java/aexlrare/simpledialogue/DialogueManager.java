package aexlrare.simpledialogue;

import aexlrare.simpledialogue.logic.ActionExecutor;
import aexlrare.simpledialogue.logic.ConditionChecker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DialogueManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("simpledialogue");

    private static final Map<UUID, String> bindings = new HashMap<>();
    private static final Map<String, DialogueNode> dialogues = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> cooldownMap = new HashMap<>();

    // 1. 新增：防抖动时间记录 (Anti-spam map)
    private static final Map<UUID, Long> lastInteractionTime = new HashMap<>();

    private static final Map<UUID, String> playerCurrentNodes = new HashMap<>();

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("simpledialogue");
    private static final Path BINDINGS_FILE = CONFIG_DIR.resolve("bindings.json");
    private static final Path DIALOGUES_FILE = CONFIG_DIR.resolve("dialogues.json");

    public static void init() {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(BINDINGS_FILE)) createEmptyBindings();
            loadBindings();
            if (!Files.exists(DIALOGUES_FILE)) createEmptyDialogues();
            loadDialogues();
        } catch (Exception e) {
            LOGGER.error("SimpleDialogue 初始化失败: ", e);
        }
    }

    private static void createEmptyDialogues() throws IOException {
        String defaultJson = "[\n" +
                "  {\n" +
                "    \"id\": \"intro_1\",\n" +
                "    \"text\": \"你好啊，外乡人。看起来你走了很远的路。\",\n" +
                "    \"next_dialogue_id\": \"intro_2\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": \"intro_2\",\n" +
                "    \"text\": \"需要什么帮助吗？\",\n" +
                "    \"options\": [\n" +
                "       { \"text\": \"没事，我这就走\", \"target_id\": \"\" }\n" +
                "    ]\n" +
                "  }\n" +
                "]";
        Files.writeString(DIALOGUES_FILE, defaultJson);
    }

    private static void loadDialogues() throws IOException {
        if (!Files.exists(DIALOGUES_FILE)) return;
        String json = Files.readString(DIALOGUES_FILE);
        List<DialogueNode> nodeList = gson.fromJson(json, new TypeToken<List<DialogueNode>>(){}.getType());
        if (nodeList != null) {
            dialogues.clear();
            for (DialogueNode node : nodeList) {
                if (node.getId() != null) dialogues.put(node.getId(), node);
            }
        }
        LOGGER.info("已加载 " + dialogues.size() + " 条对话剧本。");
    }

    public static void startDialogue(PlayerEntity playerEntity, String dialogueId) {
        if (!(playerEntity instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) playerEntity;

        // 2. 新增：全局防抖动检查 (防止一次右键触发两条消息)
        long now = System.currentTimeMillis();
        long lastTime = lastInteractionTime.getOrDefault(player.getUuid(), 0L);
        // 如果距离上次触发小于 200 毫秒，直接忽略
        if (now - lastTime < 200) {
            return;
        }
        lastInteractionTime.put(player.getUuid(), now);

        DialogueNode node = dialogues.get(dialogueId);
        if (node == null) {
            player.sendMessage(Text.literal("§c错误：找不到对话ID [" + dialogueId + "]"), false);
            return;
        }

        // 检查一次性
        String oneTimeTag = "sd_viewed_" + dialogueId;
        if (node.one_time && player.getCommandTags().contains(oneTimeTag)) return;

        // 检查冷却 (剧本自带的冷却)
        if (node.cooldown > 0) {
            cooldownMap.putIfAbsent(player.getUuid(), new HashMap<>());
            Map<String, Long> playerCooldowns = cooldownMap.get(player.getUuid());
            if (playerCooldowns.containsKey(dialogueId)) {
                if ((now - playerCooldowns.get(dialogueId)) / 1000 < node.cooldown) {
                    player.sendMessage(Text.literal("§c[提示] 暂时无法对话..."), true);
                    return;
                }
            }
            playerCooldowns.put(dialogueId, now);
        }

        if (node.one_time) player.addCommandTag(oneTimeTag);

        playerCurrentNodes.put(player.getUuid(), dialogueId);

        // --- 渲染 UI ---
        Text separator = Text.literal("§8§m---------------------------------------------");
        player.sendMessage(separator, false);

        String[] lines = node.getText().split("\n");
        for(String line : lines) {
            player.sendMessage(Text.literal("§f" + line), false);
        }
        player.sendMessage(Text.literal(""), false);

        List<DialogueNode.Option> options = node.getOptions();
        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                DialogueNode.Option option = options.get(i);

                if (!ConditionChecker.check(player, option.getConditions())) continue;

                String color = "§b";
                if ("[ 继续 ]".equals(option.getText())) color = "§a§l";

                Text optionText;
                if ("[ 继续 ]".equals(option.getText())) {
                    optionText = Text.literal("  " + color + "➤ [ 继续 ]");
                } else {
                    optionText = Text.literal("  §7[" + (i + 1) + "] " + color + option.getText() + " §8(Alt+" + (i + 1) + ")");
                }

                String command = "/sd trigger " + node.getId() + " " + i;

                optionText = optionText.copy().setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击或按 Alt+" + (i+1))))
                );

                player.sendMessage(optionText, false);
            }
        }
        player.sendMessage(separator, false);
    }

    public static void executeOption(ServerPlayerEntity player, String nodeId, int optionIndex) {
        DialogueNode node = getNode(nodeId);
        if (node != null && node.getOptions() != null && optionIndex < node.getOptions().size()) {
            DialogueNode.Option option = node.getOptions().get(optionIndex);

            if (!ConditionChecker.check(player, option.getConditions())) {
                player.sendMessage(Text.literal("§c条件不满足。"), true);
                return;
            }
            if (option.getActions() != null) {
                ActionExecutor.execute(player, option.getActions());
            }
            String targetId = option.getTargetId();
            if (targetId != null && !targetId.isEmpty()) {
                if ("close_dialogue".equals(targetId)) {
                    clearPlayerSession(player.getUuid());
                } else {
                    startDialogue(player, targetId);
                }
            } else {
                clearPlayerSession(player.getUuid());
            }
        }
    }

    public static void handleShortcut(ServerPlayerEntity player, int index) {
        String currentNodeId = playerCurrentNodes.get(player.getUuid());
        // 添加调试信息，确认服务器收到了请求
        // System.out.println("收到快捷键请求: 玩家=" + player.getName().getString() + ", 选项=" + index + ", 当前节点=" + currentNodeId);

        if (currentNodeId != null) {
            executeOption(player, currentNodeId, index);
        }
    }

    public static void clearPlayerSession(UUID uuid) { playerCurrentNodes.remove(uuid); }
    public static DialogueNode getNode(String id) { return dialogues.get(id); }
    private static void createEmptyBindings() throws IOException { bindings.clear(); saveBindings(); }
    private static void loadBindings() throws IOException {
        if (!Files.exists(BINDINGS_FILE)) return;
        String json = Files.readString(BINDINGS_FILE);
        Map<UUID, String> loaded = gson.fromJson(json, new TypeToken<Map<UUID, String>>(){}.getType());
        if (loaded != null) { bindings.clear(); bindings.putAll(loaded); }
    }
    public static void saveBindings() {
        try { Files.writeString(BINDINGS_FILE, gson.toJson(bindings)); } catch (IOException e) { LOGGER.error("保存绑定失败", e); }
    }
    public static String getDialogueId(UUID uuid) { return bindings.get(uuid); }
    public static void setBinding(UUID uuid, String dialogueId) { bindings.put(uuid, dialogueId); saveBindings(); }
}
