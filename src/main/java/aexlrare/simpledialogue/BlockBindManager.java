package aexlrare.simpledialogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class BlockBindManager {
    private static final Map<String, String> data = new HashMap<>();
    private static final File DIR = FabricLoader.getInstance().getConfigDir().resolve("simpledialogue").toFile();
    private static final File FILE = new File(DIR, "block_bindings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void init() {
        if (!DIR.exists()) DIR.mkdirs();
        if (FILE.exists()) {
            try (FileReader r = new FileReader(FILE)) {
                Map<String, String> m = GSON.fromJson(r, new TypeToken<Map<String, String>>() {}.getType());
                if (m != null) data.putAll(m);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void bind(BlockPos p, String did) {
        if (did == null) data.remove(key(p));
        else data.put(key(p), did);
        save();
    }

    public static String getDialogue(BlockPos p) {
        return data.get(key(p));
    }

    private static String key(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static void save() {
        if (!DIR.exists()) DIR.mkdirs();
        try (FileWriter w = new FileWriter(FILE)) {
            GSON.toJson(data, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
