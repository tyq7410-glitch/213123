package dev.aurora.core;

import dev.aurora.gui.ClickGuiScreen;
import dev.aurora.module.*;
import dev.aurora.module.Pvp.*;
import dev.aurora.module.Movement.*;
import dev.aurora.module.Visuals.*;
import dev.aurora.module.Pve.*;
import dev.aurora.module.Cosmetics.*;
import dev.aurora.module.Misc.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class ModuleManager {
    public static final List<Mod> modules = new ArrayList<>();
    private static final Map<Class<?>, Mod> BY_CLASS = new HashMap<>();
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean guiKeyDown;

    private ModuleManager() {}

    public static void init() {
        // PvP
        register(new KillAura(), new AimAssist(), new TriggerBot(), new AutoClicker(), new WTap(), new CritTiming(),
                new AutoShield(), new AutoPotion(), new AutoGap(), new Teams(), new AntiBot());
        // movement
        register(new Sprint(), new Fly(), new Speed(), new Step(), new HighJump(), new AirJump(), new AutoJump());
        // visuals
        register(new ESP(), new BoxESP(), new Tracers(), new Nametags(), new TargetESP(), new ChestESP(), new FovCircle(),
                new Fullbright(), new Zoom(), new Hud(), new Keystrokes(), new ArmorHud(), new PotionHud(), new TargetHud(),
                new Radar(), new InfoHud(), new BindList(), new Crosshair(), new Notifications(), new InventoryHud(),
                new ItemCounter(), new ComboCounter(), new ReachDisplay());
        // PvE
        register(new AutoTool(), new Nuker(), new AutoRespawn(), new AutoEat(), new AutoFish(), new AutoFarm(),
                new AutoArmor(), new AutoTotem(), new AutoTorch(), new TreeChopper(), new AutoBonemeal(), new AutoShear(),
                new AutoBreed(), new EdgeSneak(), new AutoDrop(), new DurabilityWarning(), new AutoWalk(), new AutoMine(),
                new AutoUse(), new DeathPoint(), new HomePoint(), new LightLevel(), new AutoRefill(), new AutoSapling(),
                new FoodHud(), new SessionStats(), new HeldCounter(), new LowHealthAlert());
        register(new FastPlace(), new Friends());
        // cosmetics (no particles)
        register(new Wings(), new Hat(), new Aura(), new Trail(), new HitEffects(), new JumpCircles());
        register(new Interface());
        get(Hud.class).setEnabled(true);
        get(Notifications.class).setEnabled(true);
        get(Interface.class).setEnabled(true);
    }

    private static void register(Mod... ms) {
        for (Mod m : ms) { modules.add(m); BY_CLASS.put(m.getClass(), m); }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Mod> T get(Class<T> c) { return (T) BY_CLASS.get(c); }

    public static List<Mod> in(Category c) {
        List<Mod> out = new ArrayList<>();
        for (Mod m : modules) if (m.category == c) out.add(m);
        return out;
    }

    public static void tick() {
        long w = mc.getWindow().getHandle();
        boolean free = mc.currentScreen == null;

        boolean gui = GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (gui && !guiKeyDown && free) mc.setScreen(new ClickGuiScreen());
        guiKeyDown = gui;

        for (Mod m : modules) {
            if (m.key > 0) {
                boolean d = GLFW.glfwGetKey(w, m.key) == GLFW.GLFW_PRESS;
                if (d && !m.keyDown && free) m.toggle();
                m.keyDown = d;
            }
            if (m.isEnabled()) m.onTick();
        }
    }

    public static void renderHud(DrawContext ctx, float tickProgress) {
        dev.aurora.util.Anim.frame();
        dev.aurora.util.Proj.begin(ctx, tickProgress);
        for (Mod m : modules) if (m.isEnabled()) m.onHud(ctx);
    }
}
