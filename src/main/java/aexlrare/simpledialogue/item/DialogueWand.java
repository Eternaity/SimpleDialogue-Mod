package aexlrare.simpledialogue.item;

import aexlrare.simpledialogue.BlockBindManager;
import aexlrare.simpledialogue.DialogueManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DialogueWand extends Item {

    public enum Mode {
        REGION("区域模式 (Pos1/Pos2)"),
        BLOCK("方块模式 (Select/View)");
        public final String name;
        Mode(String name) { this.name = name; }
    }

    private static final Map<UUID, BlockPos[]> selections = new HashMap<>();
    private static final Map<UUID, Mode> playerModes = new HashMap<>();

    public DialogueWand(Settings settings) { super(settings); }

    public static void onLeftClickBlock(PlayerEntity player, BlockPos pos) {
        setSelection(player, 0, pos);
        String prefix = (getMode(player) == Mode.REGION) ? "Pos1" : "Block";
        // true = 显示在 ActionBar (物品栏上方)
        player.sendMessage(Text.literal("§d[SD] §a" + prefix + " §7set: " + pos.toShortString()), true);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        // 服务端逻辑
        if (context.getWorld().isClient) return ActionResult.SUCCESS;
        PlayerEntity player = context.getPlayer();
        BlockPos pos = context.getBlockPos();

        if (getMode(player) == Mode.REGION) {
            setSelection(player, 1, pos);
            player.sendMessage(Text.literal("§d[SD] §aPos2 §7set: " + pos.toShortString()), true);
        } else {
            String id = BlockBindManager.getDialogue(pos);
            if (id != null) player.sendMessage(Text.literal("§b[Block] §fBound: §e" + id), false);
            else {
                player.sendMessage(Text.literal("§b[Block] §7Not bound."), true);
                setSelection(player, 0, pos);
            }
        }
        return ActionResult.SUCCESS;
    }

    public static void onUseEntity(PlayerEntity player, Entity entity) {
        if (player.getWorld().isClient) return;
        String boundId = DialogueManager.getDialogueId(entity.getUuid());

        player.sendMessage(Text.literal("§d[Entity] §f" + entity.getName().getString()), false);
        player.sendMessage(Text.literal("§7UUID: §b" + entity.getUuidAsString()).setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entity.getUuidAsString()))), false);

        if (boundId != null) {
            player.sendMessage(Text.literal("§aBound: " + boundId), false);
            if (player instanceof ServerPlayerEntity sp) DialogueManager.startDialogue(sp, boundId);
        } else {
            player.sendMessage(Text.literal("§cUnbound"), false);
            player.sendMessage(Text.literal("§e[Click to Bind]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sd bind " + entity.getUuidAsString() + " "))), false);
        }
    }

    public static void cycleMode(PlayerEntity player, boolean next) {
        Mode c = getMode(player);
        int i = c.ordinal() + (next ? 1 : -1);
        if (i < 0) i = Mode.values().length - 1;
        if (i >= Mode.values().length) i = 0;
        Mode n = Mode.values()[i];
        playerModes.put(player.getUuid(), n);
        player.sendMessage(Text.literal("§d[Mode] §e" + n.name), true);
    }

    public static Mode getMode(PlayerEntity player) { return playerModes.getOrDefault(player.getUuid(), Mode.REGION); }

    private static void setSelection(PlayerEntity p, int i, BlockPos pos) {
        selections.computeIfAbsent(p.getUuid(), k->new BlockPos[2])[i] = pos;
    }

    public static BlockPos[] getPlayerSelection(UUID id) { return selections.get(id); }
}
