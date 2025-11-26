package aexlrare.simpledialogue;

import aexlrare.simpledialogue.item.DialogueWand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.List;

public class SimpleDialogueCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        SuggestionProvider<ServerCommandSource> REGION_SUGGESTIONS = (context, builder) ->
                CommandSource.suggestMatching(RegionManager.getAllRegions().stream().map(r -> r.id), builder);

        dispatcher.register(CommandManager.literal("sd")
                // 0. 帮助
                .then(CommandManager.literal("help")
                        .executes(SimpleDialogueCommand::executeHelp))

                // 1. 重载
                .then(CommandManager.literal("reload")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(SimpleDialogueCommand::executeReload))

                // 2. 区域管理
                .then(CommandManager.literal("region")
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("region_id", StringArgumentType.string())
                                        .then(CommandManager.argument("dialogue_id", StringArgumentType.string())
                                                .executes(SimpleDialogueCommand::executeRegionCreate))))
                        .then(CommandManager.literal("list")
                                .executes(SimpleDialogueCommand::executeRegionList))
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("region_id", StringArgumentType.string())
                                        .suggests(REGION_SUGGESTIONS)
                                        .executes(SimpleDialogueCommand::executeRegionRemove)))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("region_id", StringArgumentType.string())
                                        .suggests(REGION_SUGGESTIONS)
                                        .then(CommandManager.argument("new_dialogue_id", StringArgumentType.string())
                                                .executes(SimpleDialogueCommand::executeRegionSet))))
                )

                // 3. 实体绑定
                .then(CommandManager.literal("bind")
                        .then(CommandManager.argument("targets", EntityArgumentType.entities())
                                .then(CommandManager.argument("dialogue_id", StringArgumentType.string())
                                        .executes(SimpleDialogueCommand::executeBind))))

                // 4. 触发器 (逻辑已移至 DialogueManager)
                .then(CommandManager.literal("trigger")
                        .then(CommandManager.argument("node_id", StringArgumentType.string())
                                .then(CommandManager.argument("option_index", IntegerArgumentType.integer())
                                        .executes(SimpleDialogueCommand::executeTrigger))))
        );
    }

    // --- 执行逻辑 ---

    private static int executeHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> Text.literal("§e=== SimpleDialogue 帮助手册 ==="), false);
        source.sendFeedback(() -> Text.literal("§b[工具] §f使用 §d/give @s simpledialogue:wand §f获取对话魔杖。"), false);
        source.sendFeedback(() -> Text.literal("§b[实体] §f手持魔杖右键生物查看UUID，使用 §d/sd bind <uuid> <id> §f绑定对话。"), false);
        source.sendFeedback(() -> Text.literal("§b[区域] §f魔杖右键方块选两点，Shift+右键空气快速创建。使用 §d/sd region list §f管理。"), false);
        source.sendFeedback(() -> Text.literal("§b[操作] §f对话中点击选项，或按 §6Alt+数字键 §f快速选择。"), false);
        return 1;
    }

    private static int executeReload(CommandContext<ServerCommandSource> context) {
        DialogueManager.init();
        RegionManager.init();
        context.getSource().sendFeedback(() -> Text.literal("§a[SD] 配置文件已重载！"), true);
        return 1;
    }

    private static int executeRegionCreate(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            String regionId = StringArgumentType.getString(context, "region_id");
            String dialogueId = StringArgumentType.getString(context, "dialogue_id");
            BlockPos[] selection = DialogueWand.getPlayerSelection(player.getUuid());

            if (selection == null || selection[0] == null || selection[1] == null) {
                context.getSource().sendError(Text.literal("请先使用对话魔杖选择两个点 (右键/Shift+右键)。"));
                return 0;
            }
            if(RegionManager.createRegion(regionId, dialogueId, selection[0], selection[1])) {
                context.getSource().sendFeedback(() -> Text.literal("§a区域创建成功: " + regionId), true);
                return 1;
            } else {
                context.getSource().sendError(Text.literal("ID 已存在。"));
                return 0;
            }
        } catch (Exception e) { return 0; }
    }

    private static int executeRegionList(CommandContext<ServerCommandSource> context) {
        List<Region> list = RegionManager.getAllRegions();
        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> Text.literal("§e=== 区域列表 (" + list.size() + ") ==="), false);
        for (Region region : list) {
            Text btnTp = Text.literal("§b[TP] ").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + region.minPos.getX() + " " + region.minPos.getY() + " " + region.minPos.getZ())));
            Text btnRemove = Text.literal("§c[删] ").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sd region remove " + region.id)));
            source.sendFeedback(() -> Text.literal("§f- §e" + region.id + " §7(" + region.dialogueId + ") ").append(btnTp).append(btnRemove), false);
        }
        return 1;
    }

    private static int executeRegionRemove(CommandContext<ServerCommandSource> context) {
        String regionId = StringArgumentType.getString(context, "region_id");
        if(RegionManager.removeRegion(regionId)) {
            context.getSource().sendFeedback(() -> Text.literal("§a已删除: " + regionId), true);
            return 1;
        }
        context.getSource().sendError(Text.literal("找不到区域"));
        return 0;
    }

    private static int executeRegionSet(CommandContext<ServerCommandSource> context) {
        String regionId = StringArgumentType.getString(context, "region_id");
        String newId = StringArgumentType.getString(context, "new_dialogue_id");
        if(RegionManager.updateRegionDialogue(regionId, newId)) {
            context.getSource().sendFeedback(() -> Text.literal("§a更新成功"), true);
            return 1;
        }
        return 0;
    }

    private static int executeBind(CommandContext<ServerCommandSource> context) {
        try {
            Collection<? extends Entity> targets = EntityArgumentType.getEntities(context, "targets");
            String dialogueId = StringArgumentType.getString(context, "dialogue_id");
            for (Entity entity : targets) DialogueManager.setBinding(entity.getUuid(), dialogueId);
            context.getSource().sendFeedback(() -> Text.literal("§a绑定成功"), true);
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int executeTrigger(CommandContext<ServerCommandSource> context) {
        try {
            String nodeId = StringArgumentType.getString(context, "node_id");
            int idx = IntegerArgumentType.getInteger(context, "option_index");
            // 使用 Manager 的统一逻辑
            DialogueManager.executeOption(context.getSource().getPlayerOrThrow(), nodeId, idx);
            return 1;
        } catch (Exception e) { return 0; }
    }
}
