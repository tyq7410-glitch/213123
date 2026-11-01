package dev.aurora.core;

import net.minecraft.client.MinecraftClient;
import com.google.gson.JsonObject;
import dev.aurora.module.Visuals.Notifications;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public abstract class Mod {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    public final String name;
    public final Category category;
    public final List<Setting<?>> settings = new ArrayList<>();
    public int key = -1;
    public boolean visible = true;   // shown in HUD arraylist
    boolean keyDown;
    private boolean enabled;

    protected Mod(String name, Category category) { this.name = name; this.category = category; }

    protected <T extends Setting<?>> T add(T s) { settings.add(s); return s; }

    public boolean isEnabled() { return enabled; }
    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean e) {
        if (e == enabled) return;
        enabled = e;
        if (e) onEnable(); else onDisable();
        if (mc.player != null && visible) Notifications.push(name, e);
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick() {}
    public void onHud(DrawContext ctx) {}

    /** Extra data (besides settings) stored in the config, e.g. the friends list. */
    public void saveExtra(JsonObject o) {}
    public void loadExtra(JsonObject o) {}
}
