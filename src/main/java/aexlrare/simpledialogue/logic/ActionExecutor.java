package aexlrare.simpledialogue.logic;

import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class ActionExecutor {

    public static void execute(ServerPlayerEntity player, JsonObject actions) {
        if (actions == null || actions.size() == 0) {
            return;
        }

        // 1. 添加 Tag (add_tag)
        if (actions.has("add_tag")) {
            String tag = actions.get("add_tag").getAsString();
            if (!tag.isEmpty()) {
                player.addCommandTag(tag);
            }
        }

        // 2. 移除 Tag (remove_tag)
        if (actions.has("remove_tag")) {
            String tag = actions.get("remove_tag").getAsString();
            if (!tag.isEmpty()) {
                player.removeCommandTag(tag);
            }
        }

        // 3. 扣除物品 (remove_item)
        if (actions.has("remove_item")) {
            String itemIdStr = actions.get("remove_item").getAsString();
            int count = actions.has("count") ? actions.get("count").getAsInt() : 1;

            Identifier id = Identifier.of(itemIdStr);
            if (Registries.ITEM.containsId(id)) {
                Item item = Registries.ITEM.get(id);
                // 检查背包里是否有足够的物品
                if (player.getInventory().count(item) >= count) {
                    // 1.21 移除物品
                    // 第三个参数传入 null，表示不在特定的 craftingInventory 中查找，而是直接从玩家背包移除
                    player.getInventory().remove(
                            stack -> stack.isOf(item),
                            count,
                            null
                    );
                }
            }
        }

        // 4. 执行原版指令 (run_command)
        if (actions.has("run_command")) {
            String command = actions.get("run_command").getAsString();
            if (!command.isEmpty()) {
                // 以 2 级权限执行
                player.getServer().getCommandManager().executeWithPrefix(
                        player.getCommandSource().withLevel(2).withSilent(),
                        command
                );
            }
        }

        // 5. 给予物品 (give_item)
        if (actions.has("give_item")) {
            String itemIdStr = actions.get("give_item").getAsString();
            Identifier id = Identifier.of(itemIdStr);

            if (Registries.ITEM.containsId(id)) {
                Item item = Registries.ITEM.get(id);
                int count = actions.has("count") ? actions.get("count").getAsInt() : 1;
                player.giveItemStack(new ItemStack(item, count));
            }
        }
    }
}
