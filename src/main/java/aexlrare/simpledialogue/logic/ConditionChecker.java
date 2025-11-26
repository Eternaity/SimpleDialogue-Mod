package aexlrare.simpledialogue.logic;

import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class ConditionChecker {

    public static boolean check(ServerPlayerEntity player, JsonObject conditions) {
        // 如果没有条件 (null 或 空)，默认允许显示
        if (conditions == null || conditions.size() == 0) {
            return true;
        }

        // 1. 检查: 必须拥有的 Tag (has_tag)
        if (conditions.has("has_tag")) {
            String tag = conditions.get("has_tag").getAsString();
            if (!tag.isEmpty() && !player.getCommandTags().contains(tag)) {
                return false; // 缺少 Tag，验证失败
            }
        }

        // 2. 检查: 不能拥有的 Tag (missing_tag)
        if (conditions.has("missing_tag")) {
            String tag = conditions.get("missing_tag").getAsString();
            if (!tag.isEmpty() && player.getCommandTags().contains(tag)) {
                return false; // 拥有该 Tag，验证失败
            }
        }

        // 3. 检查: 物品需求 (has_item)
        if (conditions.has("has_item")) {
            String itemIdStr = conditions.get("has_item").getAsString();
            int count = conditions.has("count") ? conditions.get("count").getAsInt() : 1;

            Identifier id = Identifier.of(itemIdStr);
            if (Registries.ITEM.containsId(id)) {
                Item item = Registries.ITEM.get(id);
                int inventoryCount = player.getInventory().count(item);

                if (inventoryCount < count) {
                    return false; // 物品不够
                }
            } else {
                // 如果物品ID写错了，为了安全起见，我们认为条件不满足
                return false;
            }
        }

        return true; // 所有检查通过
    }
}
