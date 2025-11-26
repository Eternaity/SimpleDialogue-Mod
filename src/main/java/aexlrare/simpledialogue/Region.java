package aexlrare.simpledialogue;

import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;

public class Region {
    public String id;
    public String dialogueId;
    public JsonObject actions;
    public BlockPos minPos;
    public BlockPos maxPos;

    // 新增控制字段
    public int cooldown = 0;      // 区域触发冷却 (秒)
    public boolean one_time = false; // 是否只触发一次

    // 构造函数
    public Region(String id, String dialogueId, JsonObject actions, BlockPos minPos, BlockPos maxPos) {
        this.id = id;
        this.dialogueId = dialogueId;
        this.actions = actions;
        this.minPos = minPos;
        this.maxPos = maxPos;
        // 默认 cooldown=0, one_time=false，无需在构造中强制传参，GSON会自动处理默认值
    }

    public boolean contains(BlockPos pos) {
        if (minPos == null || maxPos == null) return false;

        return pos.getX() >= Math.min(minPos.getX(), maxPos.getX()) &&
                pos.getX() <= Math.max(minPos.getX(), maxPos.getX()) &&
                pos.getY() >= Math.min(minPos.getY(), maxPos.getY()) &&
                pos.getY() <= Math.max(minPos.getY(), maxPos.getY()) &&
                pos.getZ() >= Math.min(minPos.getZ(), maxPos.getZ()) &&
                pos.getZ() <= Math.max(minPos.getZ(), maxPos.getZ());
    }
}