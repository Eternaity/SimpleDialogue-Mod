package aexlrare.simpledialogue;
import net.minecraft.util.math.BlockPos;
import java.util.List;
public class Region {
    public String id;
    public String dialogueId;
    public List<String> actions;
    public BlockPos minPos, maxPos;
    public boolean one_time = false;
    public int cooldown = 0;

    public Region(String id, String did, List<String> acts, BlockPos p1, BlockPos p2) {
        this.id = id; this.dialogueId = did; this.actions = acts;
        this.minPos = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
        this.maxPos = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
    }
    public boolean contains(BlockPos p) {
        return p.getX()>=minPos.getX() && p.getX()<=maxPos.getX() &&
                p.getY()>=minPos.getY() && p.getY()<=maxPos.getY() &&
                p.getZ()>=minPos.getZ() && p.getZ()<=maxPos.getZ();
    }
}
