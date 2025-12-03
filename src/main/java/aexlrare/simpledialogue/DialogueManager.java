package aexlrare.simpledialogue;

import aexlrare.simpledialogue.logic.ActionExecutor;
import aexlrare.simpledialogue.logic.ConditionChecker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DialogueManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("simpledialogue");
    private static final Map<UUID, String> bindings = new HashMap<>();
    private static final Map<String, DialogueNode> dialogues = new HashMap<>();
    private static final Map<UUID, String> playerCurrentNodes = new HashMap<>();
    private static final Map<UUID, List<Integer>> playerValidOptions = new HashMap<>();
    private static final Map<UUID, Long> qteStartTime = new HashMap<>();
    private static final Map<UUID, Integer> qteTimeout = new HashMap<>();
    private static final Map<UUID, String> qteTimeoutTarget = new HashMap<>();
    private static final Map<UUID, Long> lastInteractionTime = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> cooldownMap = new HashMap<>();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("simpledialogue");
    private static final Path BINDINGS_FILE = CONFIG_DIR.resolve("bindings.json");
    private static final Path DIALOGUES_FILE = CONFIG_DIR.resolve("dialogues.json");

    public static void init() {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(BINDINGS_FILE)) { bindings.clear(); saveBindings(); }
            loadBindings();
            if (!Files.exists(DIALOGUES_FILE)) {
                Files.writeString(DIALOGUES_FILE, "[{\"id\":\"demo\",\"text\":\"Hello World\",\"options\":[{\"text\":\"Bye\",\"target_id\":\"\"}]}]");
            }
            loadDialogues();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static Set<String> getAllDialogueIds() { return dialogues.keySet(); }

    private static void loadDialogues() throws IOException {
        String json = Files.readString(DIALOGUES_FILE);
        List<DialogueNode> list = gson.fromJson(json, new TypeToken<List<DialogueNode>>(){}.getType());
        if (list != null) {
            dialogues.clear();
            for (DialogueNode node : list) {
                if (node.getId() != null) {
                    dialogues.put(node.getId(), node);
                    LOGGER.info("Loaded dialogue: " + node.getId() + " with " + (node.getOptions() == null ? "NULL" : node.getOptions().size()) + " options.");
                }
            }
        }
        LOGGER.info("Loaded " + dialogues.size() + " dialogues total.");
    }

    public static void startDialogue(PlayerEntity playerEntity, String dialogueId) {
        if (!(playerEntity instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) playerEntity;

        long now = System.currentTimeMillis();
        if (now - lastInteractionTime.getOrDefault(player.getUuid(), 0L) < 100) return;
        lastInteractionTime.put(player.getUuid(), now);

        DialogueNode node = dialogues.get(dialogueId);
        if (node == null) {
            player.sendMessage(Text.literal("§cUnknown Dialogue: " + dialogueId), false);
            return;
        }

        String tag = "sd_viewed_" + dialogueId;
        if (node.one_time && player.getCommandTags().contains(tag)) return;

        if (node.cooldown > 0) {
            cooldownMap.putIfAbsent(player.getUuid(), new HashMap<>());
            long last = cooldownMap.get(player.getUuid()).getOrDefault(dialogueId, 0L);
            if ((now - last) / 1000 < node.cooldown) return;
            cooldownMap.get(player.getUuid()).put(dialogueId, now);
        }

        if (node.one_time) player.addCommandTag(tag);
        playerCurrentNodes.put(player.getUuid(), dialogueId);

        List<Integer> realIndices = new ArrayList<>();
        List<String> displayTexts = new ArrayList<>();
        int maxQte = 0;
        String timeoutTarget = null;

        if (node.getOptions() != null && !node.getOptions().isEmpty()) {
            for (int i = 0; i < node.getOptions().size(); i++) {
                DialogueNode.Option opt = node.getOptions().get(i);
                if (opt == null) continue;

                if (ConditionChecker.check(player, opt.getConditions())) {
                    realIndices.add(i);
                    displayTexts.add(opt.getText());

                    if (opt.qte_timeout > maxQte) {
                        maxQte = opt.qte_timeout;
                        timeoutTarget = opt.timeout_target;
                    }
                }
            }
        }
        playerValidOptions.put(player.getUuid(), realIndices);

        if (maxQte > 0) {
            qteStartTime.put(player.getUuid(), now);
            qteTimeout.put(player.getUuid(), maxQte);
            qteTimeoutTarget.put(player.getUuid(), timeoutTarget);
        } else {
            qteStartTime.remove(player.getUuid());
            qteTimeout.remove(player.getUuid());
            qteTimeoutTarget.remove(player.getUuid());
        }

        ServerPlayNetworking.send(player, new SimpleDialogue.DialogueStatePayload(node.getText(), displayTexts, maxQte));

        player.sendMessage(Text.literal("§7[对话] §f" + node.getText()), false);
        for (int i = 0; i < displayTexts.size(); i++) {
            player.sendMessage(Text.literal("§e  " + (i + 1) + ". " + displayTexts.get(i)), false);
        }
    }

    public static void handleShortcut(ServerPlayerEntity player, int visualIndex) {
        String nodeId = playerCurrentNodes.get(player.getUuid());
        List<Integer> validMap = playerValidOptions.get(player.getUuid());

        if (nodeId != null && validMap != null) {
            if (visualIndex >= 0 && visualIndex < validMap.size()) {
                int realIndex = validMap.get(visualIndex);

                DialogueNode node = dialogues.get(nodeId);
                if (node != null && node.getOptions() != null) {
                    DialogueNode.Option opt = node.getOptions().get(realIndex);
                    player.sendMessage(Text.literal("§a[你选择了] " + opt.getText()), false);
                }

                executeOption(player, nodeId, realIndex);
            }
        }
    }

    public static void executeOption(ServerPlayerEntity player, String nodeId, int realIndex) {
        DialogueNode node = dialogues.get(nodeId);
        if (node != null && node.getOptions() != null) {
            DialogueNode.Option opt = node.getOptions().get(realIndex);

            if (opt.getActions() != null) ActionExecutor.execute(player, opt.getActions());

            String target = opt.getTargetId();
            if (target != null && !target.isEmpty() && !"close_dialogue".equals(target)) {
                startDialogue(player, target);
            } else {
                clearPlayerSession(player);
            }
        }
    }

    public static void checkQteTimeout(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (!qteStartTime.containsKey(uuid)) return;

        long elapsed = (System.currentTimeMillis() - qteStartTime.get(uuid)) / 1000;
        int timeout = qteTimeout.get(uuid);

        if (elapsed >= timeout) {
            String target = qteTimeoutTarget.get(uuid);

            player.sendMessage(Text.literal("§c[超时] 未在 " + timeout + " 秒内选择"), false);

            qteStartTime.remove(uuid);
            qteTimeout.remove(uuid);
            qteTimeoutTarget.remove(uuid);

            if (target != null && !target.isEmpty()) {
                startDialogue(player, target);
            } else {
                clearPlayerSession(player);
            }
        }
    }

    public static void clearPlayerSession(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        playerCurrentNodes.remove(uuid);
        playerValidOptions.remove(uuid);
        qteStartTime.remove(uuid);
        qteTimeout.remove(uuid);
        qteTimeoutTarget.remove(uuid);
        ServerPlayNetworking.send(player, new SimpleDialogue.DialogueStatePayload("", new ArrayList<>(), 0));
    }

    public static void clearPlayerSession(UUID uuid) {
        playerCurrentNodes.remove(uuid);
        playerValidOptions.remove(uuid);
        qteStartTime.remove(uuid);
        qteTimeout.remove(uuid);
        qteTimeoutTarget.remove(uuid);
    }

    private static void loadBindings() throws IOException {
        if (!Files.exists(BINDINGS_FILE)) return;
        Map<UUID, String> data = gson.fromJson(Files.readString(BINDINGS_FILE), new TypeToken<Map<UUID, String>>(){}.getType());
        if (data != null) { bindings.clear(); bindings.putAll(data); }
    }
    public static void saveBindings() { try { Files.writeString(BINDINGS_FILE, gson.toJson(bindings)); } catch (IOException e) {} }
    public static void setBinding(UUID uuid, String id) { bindings.put(uuid, id); saveBindings(); }
    public static void removeBinding(UUID uuid) { bindings.remove(uuid); saveBindings(); }
    public static String getDialogueId(UUID uuid) { return bindings.get(uuid); }
}