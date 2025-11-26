package aexlrare.simpledialogue;

import aexlrare.simpledialogue.logic.ActionExecutor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class RegionManager {
    private static final List<Region> regions = new ArrayList<>();
    // 记录玩家当前所在的区域 ID
    private static final Map<UUID, String> playerCurrentRegions = new HashMap<>();
    // 区域冷却计时器: PlayerUUID -> (RegionID -> LastTimeMillis)
    private static final Map<UUID, Map<String, Long>> regionCooldowns = new HashMap<>();

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("simpledialogue/regions.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static int tickCounter = 0;

    public static void init() {
        regions.clear();
        playerCurrentRegions.clear();
        regionCooldowns.clear();
        loadRegions();
        System.out.println("SimpleDialogue: RegionManager initialized. Loaded " + regions.size() + " regions.");
    }

    public static void loadRegions() {
        if (!CONFIG_FILE.exists()) return;

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type listType = new TypeToken<ArrayList<Region>>(){}.getType();
            List<Region> loadedData = GSON.fromJson(reader, listType);
            if (loadedData != null) {
                regions.addAll(loadedData);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("SimpleDialogue: Failed to load regions.json");
        }
    }

    public static void saveRegions() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(regions, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Region> getAllRegions() {
        return regions;
    }

    public static Region getRegion(String id) {
        for (Region r : regions) {
            if (r.id.equals(id)) return r;
        }
        return null;
    }

    public static boolean createRegion(String id, String dialogueId, BlockPos pos1, BlockPos pos2) {
        if (getRegion(id) != null) return false;
        Region newRegion = new Region(id, dialogueId, null, pos1, pos2);
        regions.add(newRegion);
        saveRegions();
        return true;
    }

    public static boolean removeRegion(String id) {
        Region target = getRegion(id);
        if (target != null) {
            regions.remove(target);
            saveRegions();
            playerCurrentRegions.values().removeIf(val -> val.equals(id));
            return true;
        }
        return false;
    }

    public static boolean updateRegionDialogue(String id, String newDialogueId) {
        Region target = getRegion(id);
        if (target != null) {
            target.dialogueId = newDialogueId;
            saveRegions();
            return true;
        }
        return false;
    }

    // === 核心检测逻辑 ===

    public static void onTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 10 != 0) return; // 每 0.5 秒检测一次

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            checkPlayer(player);
        }
    }

    private static void checkPlayer(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        UUID uuid = player.getUuid();

        Region foundRegion = null;

        for (Region region : regions) {
            if (region.contains(pos)) {
                foundRegion = region;
                break;
            }
        }

        String lastRegionId = playerCurrentRegions.get(uuid);
        String currentRegionId = (foundRegion != null) ? foundRegion.id : null;

        if (currentRegionId != null) {
            // 玩家进入了一个新区域
            if (!currentRegionId.equals(lastRegionId)) {

                // 1. 检查一次性 (One-time)
                String oneTimeTag = "sd_viewed_region_" + currentRegionId;
                if (foundRegion.one_time) {
                    if (player.getCommandTags().contains(oneTimeTag)) {
                        // 已经来过这个一次性区域了
                        playerCurrentRegions.put(uuid, currentRegionId);
                        return;
                    }
                }

                // 2. 检查冷却 (Cooldown)
                if (foundRegion.cooldown > 0) {
                    regionCooldowns.putIfAbsent(uuid, new HashMap<>());
                    Map<String, Long> pCooldowns = regionCooldowns.get(uuid);

                    if (pCooldowns.containsKey(currentRegionId)) {
                        long lastTime = pCooldowns.get(currentRegionId);
                        long now = System.currentTimeMillis();
                        if ((now - lastTime) / 1000 < foundRegion.cooldown) {
                            // 冷却中，不触发
                            playerCurrentRegions.put(uuid, currentRegionId);
                            return;
                        }
                    }
                    pCooldowns.put(currentRegionId, System.currentTimeMillis());
                }

                // 3. 标记一次性
                if (foundRegion.one_time) {
                    player.addCommandTag(oneTimeTag);
                }

                // --- 触发逻辑 ---
                if (foundRegion.dialogueId != null && !foundRegion.dialogueId.isEmpty()) {
                    DialogueManager.startDialogue(player, foundRegion.dialogueId);
                }

                if (foundRegion.actions != null && foundRegion.actions.size() > 0) {
                    ActionExecutor.execute(player, foundRegion.actions);
                }

                // 更新状态
                playerCurrentRegions.put(uuid, currentRegionId);
            }
        } else {
            // 玩家不在区域内
            if (lastRegionId != null) {
                playerCurrentRegions.remove(uuid);
            }
        }
    }

    public static void onPlayerDisconnect(UUID uuid) {
        playerCurrentRegions.remove(uuid);
        regionCooldowns.remove(uuid); // 只清理 Region 自己的冷却缓存
    }
}