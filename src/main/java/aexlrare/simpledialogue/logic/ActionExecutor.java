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
                    // 1.21 移除物品的标准写法 (传入 craftingInput 以防鬼影物品，也可传 null)
                    player.getInventory().remove(
                            stack -> stack.isOf(item),
                            count,
                            player.playerScreenHandler.getCraftingInput()
                    );
                }
            }
        }

        // 4. 执行原版指令 (run_command)
        if (actions.has("run_command")) {
            String command = actions.get("run_command").getAsString();
            if (!command.isEmpty()) {
                // 以 2 级权限 (命令方块级别) 执行，并静默输出
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
