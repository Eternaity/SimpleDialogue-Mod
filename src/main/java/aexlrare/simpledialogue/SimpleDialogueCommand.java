package aexlrare.simpledialogue;

import aexlrare.simpledialogue.item.DialogueWand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class SimpleDialogueCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        SuggestionProvider<ServerCommandSource> REGIONS = (context, builder) ->
                CommandSource.suggestMatching(RegionManager.getAllRegions().stream().map(r -> r.id), builder);

        SuggestionProvider<ServerCommandSource> DIALOGUES = (context, builder) ->
                CommandSource.suggestMatching(DialogueManager.getAllDialogueIds(), builder);

        dispatcher.register(CommandManager.literal("sd")
                // 帮助
                .then(CommandManager.literal("help").executes(context -> {
                    context.getSource().sendFeedback(() -> Text.literal("§eUse /sd [bind|block|region|debug]"), false);
                    return 1;
                }))

                // 重载
                .then(CommandManager.literal("reload")
                        .requires(s -> s.hasPermissionLevel(2))
                        .executes(context -> {
                            DialogueManager.init();
                            RegionManager.init();
                            BlockBindManager.init();
                            context.getSource().sendFeedback(() -> Text.literal("§aReloaded configurations."), true);
                            return 1;
                        }))

                // 列表
                .then(CommandManager.literal("list")
                        .executes(context -> {
                            context.getSource().sendFeedback(() -> Text.literal("Ids: " + DialogueManager.getAllDialogueIds()), false);
                            return 1;
                        }))

                // Debug 工具
                .then(CommandManager.literal("debug")
                        .requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("reset")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(SimpleDialogueCommand::debugReset)))
                        .then(CommandManager.literal("check")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(SimpleDialogueCommand::debugCheck))))

                // 实体绑定 (Bind/Unbind)
                .then(CommandManager.literal("bind")
                        .requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("targets", EntityArgumentType.entities())
                                .then(CommandManager.argument("id", StringArgumentType.string())
                                        .suggests(DIALOGUES)
                                        .executes(SimpleDialogueCommand::bind))))
                .then(CommandManager.literal("unbind")
                        .requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("targets", EntityArgumentType.entities())
                                .executes(SimpleDialogueCommand::unbind)))

                // 方块绑定 (Block)
                .then(CommandManager.literal("block")
                        .requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("bind")
                                .then(CommandManager.argument("id", StringArgumentType.string())
                                        .suggests(DIALOGUES)
                                        .executes(SimpleDialogueCommand::blockBind)))
                        .then(CommandManager.literal("unbind")
                                .executes(SimpleDialogueCommand::blockUnbind)))

                // 区域管理 (Region)
                .then(CommandManager.literal("region")
                        .requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("rid", StringArgumentType.word())
                                        .then(CommandManager.argument("did", StringArgumentType.string())
                                                .suggests(DIALOGUES)
                                                .executes(SimpleDialogueCommand::regCreate))))
                        .then(CommandManager.literal("rename")
                                .then(CommandManager.argument("old", StringArgumentType.string())
                                        .suggests(REGIONS)
                                        .then(CommandManager.argument("new", StringArgumentType.word())
                                                .executes(SimpleDialogueCommand::regRename))))
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("rid", StringArgumentType.string())
                                        .suggests(REGIONS)
                                        .executes(SimpleDialogueCommand::regRemove)))
                        .then(CommandManager.literal("list")
                                .executes(SimpleDialogueCommand::regList))
                        .then(CommandManager.literal("tp")
                                .then(CommandManager.argument("rid", StringArgumentType.string())
                                        .suggests(REGIONS)
                                        .executes(SimpleDialogueCommand::regTp)))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("rid", StringArgumentType.string())
                                        .suggests(REGIONS)
                                        .then(CommandManager.argument("did", StringArgumentType.string())
                                                .suggests(DIALOGUES)
                                                .executes(SimpleDialogueCommand::regSet))))
                        .then(CommandManager.literal("flag")
                                .then(CommandManager.argument("rid", StringArgumentType.string())
                                        .suggests(REGIONS)
                                        .then(CommandManager.literal("one_time")
                                                .then(CommandManager.argument("val", BoolArgumentType.bool())
                                                        .executes(SimpleDialogueCommand::regFlagOneTime)))
                                        .then(CommandManager.literal("cooldown")
                                                .then(CommandManager.argument("sec", IntegerArgumentType.integer(0))
                                                        .executes(SimpleDialogueCommand::regFlagCooldown))))))
        );
    }

    private static int debugReset(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "target");
        Set<String> tagsToRemove = new HashSet<>();
        for (String t : p.getCommandTags()) {
            if (t.startsWith("sd_viewed_")) tagsToRemove.add(t);
        }
        for (String t : tagsToRemove) p.removeCommandTag(t);
        DialogueManager.clearPlayerSession(p);
        ctx.getSource().sendFeedback(() -> Text.literal("§aReset dialogue history for " + p.getName().getString()), true);
        return 1;
    }

    private static int debugCheck(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "target");
        ctx.getSource().sendFeedback(() -> Text.literal("§eTags: " + p.getCommandTags()), false);
        return 1;
    }

    private static int bind(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "id");
        int count = 0;
        for (Entity e : EntityArgumentType.getEntities(ctx, "targets")) {
            DialogueManager.setBinding(e.getUuid(), id);
            count++;
        }
        int finalCount = count;
        ctx.getSource().sendFeedback(() -> Text.literal("§aBound " + finalCount + " entities to " + id), true);
        return 1;
    }

    private static int unbind(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        int count = 0;
        for (Entity e : EntityArgumentType.getEntities(ctx, "targets")) {
            DialogueManager.removeBinding(e.getUuid());
            count++;
        }
        int finalCount = count;
        ctx.getSource().sendFeedback(() -> Text.literal("§aUnbound " + finalCount + " entities"), true);
        return 1;
    }

    private static int blockBind(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        BlockPos[] s = DialogueWand.getPlayerSelection(player.getUuid());
        if (s == null || s[0] == null) {
            ctx.getSource().sendError(Text.literal("§cSelect a block with the Wand first!"));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        BlockBindManager.bind(s[0], id);
        ctx.getSource().sendFeedback(() -> Text.literal("§aBlock bound to " + id), true);
        return 1;
    }

    private static int blockUnbind(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        BlockPos[] s = DialogueWand.getPlayerSelection(player.getUuid());
        if (s == null || s[0] == null) {
            ctx.getSource().sendError(Text.literal("§cSelect a block with the Wand first!"));
            return 0;
        }
        BlockBindManager.bind(s[0], null);
        ctx.getSource().sendFeedback(() -> Text.literal("§aBlock unbound"), true);
        return 1;
    }

    private static int regCreate(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        BlockPos[] s = DialogueWand.getPlayerSelection(player.getUuid());
        if (s == null || s[0] == null || s[1] == null) {
            ctx.getSource().sendError(Text.literal("§cIncomplete selection (Need Pos1 & Pos2)"));
            return 0;
        }
        String rid = StringArgumentType.getString(ctx, "rid");
        String did = StringArgumentType.getString(ctx, "did");

        if (RegionManager.createRegion(rid, did, s[0], s[1])) {
            ctx.getSource().sendFeedback(() -> Text.literal("§aRegion created: " + rid), true);
            return 1;
        }
        ctx.getSource().sendError(Text.literal("Region ID already exists"));
        return 0;
    }

    private static int regRename(CommandContext<ServerCommandSource> ctx) {
        String oldId = StringArgumentType.getString(ctx, "old");
        String newId = StringArgumentType.getString(ctx, "new");
        if (RegionManager.renameRegion(oldId, newId)) {
            ctx.getSource().sendFeedback(() -> Text.literal("§aRenamed region"), true);
            return 1;
        }
        ctx.getSource().sendError(Text.literal("Rename failed (ID exists or not found)"));
        return 0;
    }

    private static int regRemove(CommandContext<ServerCommandSource> ctx) {
        String rid = StringArgumentType.getString(ctx, "rid");
        if (RegionManager.removeRegion(rid)) {
            ctx.getSource().sendFeedback(() -> Text.literal("§aRegion removed"), true);
            return 1;
        }
        return 0;
    }

    private static int regList(CommandContext<ServerCommandSource> ctx) {
        int size = RegionManager.getAllRegions().size();
        ctx.getSource().sendFeedback(() -> Text.literal("Total Regions: " + size), false);
        return 1;
    }

    private static int regTp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String rid = StringArgumentType.getString(ctx, "rid");
        Region r = RegionManager.getRegion(rid);
        if (r != null) {
            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
            p.teleport(ctx.getSource().getWorld(), r.minPos.getX(), r.minPos.getY(), r.minPos.getZ(), 0, 0);
            ctx.getSource().sendFeedback(() -> Text.literal("§aTeleported to " + rid), false);
        } else {
            ctx.getSource().sendError(Text.literal("Region not found"));
        }
        return 1;
    }

    private static int regSet(CommandContext<ServerCommandSource> ctx) {
        String rid = StringArgumentType.getString(ctx, "rid");
        String did = StringArgumentType.getString(ctx, "did");
        if (RegionManager.updateRegionDialogue(rid, did)) {
            ctx.getSource().sendFeedback(() -> Text.literal("§aUpdated region dialogue"), true);
            return 1;
        }
        return 0;
    }

    private static int regFlagOneTime(CommandContext<ServerCommandSource> ctx) {
        String rid = StringArgumentType.getString(ctx, "rid");
        boolean val = BoolArgumentType.getBool(ctx, "val");
        Region r = RegionManager.getRegion(rid);
        if (r != null) {
            r.one_time = val;
            RegionManager.saveRegions();
            ctx.getSource().sendFeedback(() -> Text.literal("§aOne-time flag set to " + val), true);
            return 1;
        }
        return 0;
    }

    private static int regFlagCooldown(CommandContext<ServerCommandSource> ctx) {
        String rid = StringArgumentType.getString(ctx, "rid");
        int sec = IntegerArgumentType.getInteger(ctx, "sec");
        Region r = RegionManager.getRegion(rid);
        if (r != null) {
            r.cooldown = sec;
            RegionManager.saveRegions();
            ctx.getSource().sendFeedback(() -> Text.literal("§aCooldown set to " + sec + "s"), true);
            return 1;
        }
        return 0;
    }
}
