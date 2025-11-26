package aexlrare.simpledialogue.item;

import aexlrare.simpledialogue.RegionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DialogueWand extends Item {

    // 内存中暂存玩家的选择：玩家UUID -> 坐标数组 [Pos1, Pos2]
    private static final Map<UUID, BlockPos[]> selections = new HashMap<>();

    public DialogueWand(Settings settings) {
        super(settings);
    }

    // === 功能 A：右键方块 (设置 Pos1 / Pos2) ===
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!world.isClient) {
            PlayerEntity player = context.getPlayer();
            BlockPos pos = context.getBlockPos();

            if (player.isSneaking()) {
                // Shift + 右键 -> Pos2
                setSelection(player, 1, pos);
                player.sendMessage(Text.literal("§b[Pos2] 设置为: " + pos.toShortString()), true);
            } else {
                // 普通右键 -> Pos1
                setSelection(player, 0, pos);
                player.sendMessage(Text.literal("§a[Pos1] 设置为: " + pos.toShortString()), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    // === 功能 B：Shift + 右键空气 (快捷创建测试区域) ===
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (!world.isClient && player.isSneaking()) {
            BlockPos[] points = selections.get(player.getUuid());

            // 检查是否选好了点
            if (points != null && points[0] != null && points[1] != null) {

                // 自动生成一个随机区域 ID
                String autoId = "quick_" + (System.currentTimeMillis() % 10000);

                // [重要] 将默认剧本设为 'intro_1'，确保它在默认 JSON 里存在，防止报错
                String defaultDialogue = "intro_1";

                boolean success = RegionManager.createRegion(autoId, defaultDialogue, points[0], points[1]);

                if (success) {
                    player.sendMessage(Text.literal("§a[Wand] 快速创建区域成功! ID: " + autoId), true);
                    player.sendMessage(Text.literal("§7绑定剧本: " + defaultDialogue), false);

                    // 清空选区，防止误触
                    selections.remove(player.getUuid());
                } else {
                    player.sendMessage(Text.literal("§c[Wand] 创建失败，ID可能重复。"), true);
                }

                return TypedActionResult.success(player.getStackInHand(hand));
            } else {
                player.sendMessage(Text.literal("§c[Wand] 请先右键方块选择 Pos1 和 Pos2。"), true);
            }
        }
        return super.use(world, player, hand);
    }

    // === 辅助方法 ===

    private void setSelection(PlayerEntity player, int index, BlockPos pos) {
        UUID uuid = player.getUuid();
        BlockPos[] points = selections.computeIfAbsent(uuid, k -> new BlockPos[2]);
        points[index] = pos;
    }

    // 供 Command 类调用，获取玩家当前的选点
    public static BlockPos[] getPlayerSelection(UUID playerUUID) {
        return selections.get(playerUUID);
    }
}
