package dev.aurora.core;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class Config {
    private static final Path DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path MAIN = DIR.resolve("aurora.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config() {}

    public static Path profile(int slot) { return DIR.resolve("aurora_profile_" + slot + ".json"); }
    public static boolean profileExists(int slot) { return Files.exists(profile(slot)); }

    public static void save() { saveTo(MAIN); }
    public static void load() { loadFrom(MAIN); }

    public static void saveTo(Path file) {
        JsonObject root = new JsonObject();
        for (Mod m : ModuleManager.modules) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            o.addProperty("key", m.key);
            o.addProperty("visible", m.visible);
            for (Setting<?> s : m.settings) o.addProperty(s.name, s.asString());
            m.saveExtra(o);
            root.add(m.name, o);
        }
        try { Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8); }
        catch (IOException e) { e.printStackTrace(); }
    }

    public static void loadFrom(Path file) {
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Mod m : ModuleManager.modules) {
                if (!root.has(m.name)) continue;
                JsonObject o = root.getAsJsonObject(m.name);
                if (o.has("key")) m.key = o.get("key").getAsInt();
                if (o.has("visible")) m.visible = o.get("visible").getAsBoolean();
                for (Setting<?> s : m.settings) {
                    if (!o.has(s.name)) continue;
                    try { s.fromString(o.get(s.name).getAsString()); } catch (Exception ignored) {}
                }
                m.loadExtra(o);
                if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
