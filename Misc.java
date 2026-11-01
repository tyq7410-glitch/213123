package dev.aurora.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aurora.core.*;
import dev.aurora.mixin.MinecraftClientAccessor;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import dev.aurora.module.Pvp.*;
import dev.aurora.module.Movement.*;
import dev.aurora.module.Visuals.*;
import dev.aurora.module.Pve.*;
import dev.aurora.module.Cosmetics.*;
import dev.aurora.module.Misc.*;

/** All Misc modules in one file (keeps the repository small enough for GitHub web upload). */
public final class Misc {
    private Misc() {}

    public static class FastPlace extends Mod {
        private final Setting.Num delay = add(new Setting.Num("Delay (ticks)", 0, 0, 4, 1));

        public FastPlace() { super("FastPlace", Category.MISC); }

        @Override
        public void onTick() {
            var acc = (MinecraftClientAccessor) mc;
            if (mc.options.useKey.isPressed() && acc.getItemUseCooldown() > delay.asInt())
                acc.setItemUseCooldown(delay.asInt());
        }
    }

    public static class Friends extends Mod {
        private final Setting.Bool protect = add(new Setting.Bool("Не бить друзей", true));
        private static final Set<String> NAMES = new HashSet<>();
        private boolean lastMiddle;

        public Friends() { super("Friends", Category.MISC); }

        public static boolean isFriend(Entity e) {
            Friends f = ModuleManager.get(Friends.class);
            return f != null && f.isEnabled() && f.protect.get()
                    && e instanceof PlayerEntity p && NAMES.contains(p.getName().getString());
        }

        /** Friend regardless of the "protect" switch (for colouring). */
        public static boolean listed(Entity e) {
            Friends f = ModuleManager.get(Friends.class);
            return f != null && f.isEnabled() && e instanceof PlayerEntity p && NAMES.contains(p.getName().getString());
        }

        @Override
        public void onTick() {
            boolean mid = mc.currentScreen == null
                    && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
            if (mid && !lastMiddle && mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof PlayerEntity p) {
                String n = p.getName().getString();
                if (NAMES.remove(n)) Notifications.pushText("Друг удалён: " + n, false);
                else { NAMES.add(n); Notifications.pushText("Друг добавлен: " + n, true); }
            }
            lastMiddle = mid;
        }

        @Override
        public void saveExtra(JsonObject o) {
            JsonArray a = new JsonArray();
            for (String n : NAMES) a.add(n);
            o.add("friends", a);
        }

        @Override
        public void loadExtra(JsonObject o) {
            if (!o.has("friends")) return;
            NAMES.clear();
            for (var e : o.getAsJsonArray("friends")) NAMES.add(e.getAsString());
        }
    }

    public static class Interface extends Mod {
        public final Setting.Mode background = add(new Setting.Mode("Фон меню", "Градиент",
                "Затемнение", "Градиент", "Закат", "Океан", "Неон", "Свой цвет"));
        public final Setting.Num opacity = add(new Setting.Num("Прозрачность фона", 70, 0, 100, 5));
        public final Setting.Num red = add(new Setting.Num("Свой цвет: красный", 20, 0, 255, 5));
        public final Setting.Num green = add(new Setting.Num("Свой цвет: зелёный", 20, 0, 255, 5));
        public final Setting.Num blue = add(new Setting.Num("Свой цвет: синий", 45, 0, 255, 5));
        public final Setting.Bool animate = add(new Setting.Bool("Анимация фона", true));
        public final Setting.Bool particles = add(new Setting.Bool("Частицы в меню", true));
        public final Setting.Mode accent = add(new Setting.Mode("Цвет акцента", "Аврора",
                "Аврора", "Фиолетовый", "Океан", "Огонь", "Мята", "Розовый", "Радуга", "Свой"));
        public final Setting.Num hue = add(new Setting.Num("Оттенок (для «Свой»)", 270, 0, 360, 5));

        public Interface() { super("Interface", Category.CLIENT); visible = false; }

        /** Accent color for index i (animated wave around the chosen hue). */
        public static int accent(int i) {
            Interface ui = ModuleManager.get(Interface.class);
            String a = ui == null ? "Аврора" : ui.accent.get();
            double t = System.currentTimeMillis();
            float base, amp = 0.05f;
            switch (a) {
                case "Аврора" -> { base = 0.58f; amp = 0.17f; }
                case "Океан" -> base = 0.55f;
                case "Огонь" -> base = 0.04f;
                case "Мята" -> base = 0.42f;
                case "Розовый" -> base = 0.90f;
                case "Радуга" -> { return dev.aurora.util.Util.rainbow(i); }
                case "Свой" -> { base = ui.hue.asFloat() / 360f; amp = 0.03f; }
                default -> { base = 0.72f; amp = 0.08f; }
            }
            float h = base + amp * (float) Math.sin(t / 700.0 + i * 0.18);
            h = (h % 1f + 1f) % 1f;
            return 0xFF000000 | MathHelper.hsvToRgb(h, 0.55f, 1f);
        }
    }
}
