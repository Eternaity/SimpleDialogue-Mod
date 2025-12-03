package aexlrare.simpledialogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.util.*;

public class RegionManager {
    private static final List<Region> regions = new ArrayList<>();
    private static final Map<UUID, String> playerCurrentRegions = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> regionCooldowns = new HashMap<>();
    private static final File DIR = FabricLoader.getInstance().getConfigDir().resolve("simpledialogue").toFile();
    private static final File FILE = new File(DIR, "regions.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static int tick = 0;

    public static void init() {
        regions.clear();
        playerCurrentRegions.clear();
        if(!DIR.exists()) DIR.mkdirs();
        if(FILE.exists()) try(FileReader r=new FileReader(FILE)){ List<Region> l=GSON.fromJson(r, new TypeToken<List<Region>>(){}.getType()); if(l!=null) regions.addAll(l); } catch(Exception e){}
    }

    public static void saveRegions() {
        if(!DIR.exists()) DIR.mkdirs();
        try(FileWriter w=new FileWriter(FILE)){ GSON.toJson(regions, w); } catch(Exception e){}
    }

    public static List<Region> getAllRegions() { return regions; }
    public static Region getRegion(String id) { for(Region r : regions) if(r.id.equals(id)) return r; return null; }
    public static boolean createRegion(String id, String did, BlockPos p1, BlockPos p2) { if(getRegion(id)!=null) return false; regions.add(new Region(id, did, null, p1, p2)); saveRegions(); return true; }
    public static boolean removeRegion(String id) { Region r=getRegion(id); if(r!=null) { regions.remove(r); saveRegions(); return true; } return false; }
    public static boolean renameRegion(String old, String nid) { if(getRegion(nid)!=null) return false; Region r=getRegion(old); if(r!=null) { r.id=nid; saveRegions(); return true; } return false; }
    public static boolean updateRegionDialogue(String id, String did) { Region r=getRegion(id); if(r!=null) { r.dialogueId=did; saveRegions(); return true; } return false; }

    public static void onTick(MinecraftServer s) {
        if(++tick%10!=0) return;
        for(ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            Region r = null;
            for(Region reg : regions) if(reg.contains(p.getBlockPos())) { r=reg; break; }
            String curr = (r!=null) ? r.id : null;
            String last = playerCurrentRegions.get(p.getUuid());

            if(curr!=null && !curr.equals(last)) {
                String tag = "sd_viewed_region_" + curr;
                boolean skip = r.one_time && p.getCommandTags().contains(tag);
                if(!skip && r.cooldown>0) {
                    regionCooldowns.putIfAbsent(p.getUuid(), new HashMap<>());
                    if((System.currentTimeMillis()-regionCooldowns.get(p.getUuid()).getOrDefault(curr, 0L))/1000 < r.cooldown) skip=true;
                }
                if(!skip) {
                    if(r.one_time) p.addCommandTag(tag);
                    if(r.cooldown>0) regionCooldowns.get(p.getUuid()).put(curr, System.currentTimeMillis());
                    if(r.dialogueId!=null) DialogueManager.startDialogue(p, r.dialogueId);
                }
                playerCurrentRegions.put(p.getUuid(), curr);
            } else if(curr==null && last!=null) playerCurrentRegions.remove(p.getUuid());
        }
    }
    public static void onPlayerDisconnect(UUID uuid) { playerCurrentRegions.remove(uuid); regionCooldowns.remove(uuid); }
}
