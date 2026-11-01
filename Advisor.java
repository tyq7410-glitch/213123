package dev.aurora.core;

import dev.aurora.module.*;
import dev.aurora.module.Pvp.*;
import dev.aurora.module.Movement.*;
import dev.aurora.module.Visuals.*;
import dev.aurora.module.Pve.*;
import dev.aurora.module.Cosmetics.*;
import dev.aurora.module.Misc.*;

import java.util.ArrayList;
import java.util.List;

/** Offline config advisor: tips for the current setup and one-click presets. */
public final class Advisor {
    private Advisor() {}

    public static final String[] PRESETS = {"Сингл-комфорт", "PvE-выживание", "Минимальный HUD", "Максимум визуалов", "Выключить всё"};

    private static Setting<?> find(Mod m, String name) {
        for (Setting<?> s : m.settings) if (s.name.equals(name)) return s;
        return null;
    }

    private static double num(Class<? extends Mod> c, String name, double def) {
        Mod m = ModuleManager.get(c);
        Setting<?> s = m == null ? null : find(m, name);
        return s instanceof Setting.Num n ? n.get() : def;
    }

    private static boolean on(Class<? extends Mod> c) {
        Mod m = ModuleManager.get(c);
        return m != null && m.isEnabled();
    }

    private static void enable(Class<? extends Mod> c, boolean v) {
        Mod m = ModuleManager.get(c);
        if (m != null) m.setEnabled(v);
    }

    private static void set(Class<? extends Mod> c, String setting, String value) {
        Mod m = ModuleManager.get(c);
        Setting<?> s = m == null ? null : find(m, setting);
        if (s != null) s.fromString(value);
    }

    public static List<String> tips() {
        List<String> t = new ArrayList<>();

        // overlapping HUD widgets
        List<HudBase> w = new ArrayList<>();
        for (Mod m : ModuleManager.modules) if (m instanceof HudBase h && h.isEnabled()) w.add(h);
        for (int i = 0; i < w.size(); i++)
            for (int j = i + 1; j < w.size(); j++)
                if (Math.abs(w.get(i).px() - w.get(j).px()) <= 4 && Math.abs(w.get(i).py() - w.get(j).py()) <= 8)
                    t.add(w.get(i).name + " и " + w.get(j).name + " стоят на одном месте. Разведи их настройками «Позиция X/Y» или нажми пресет «Максимум визуалов».");
        if (w.size() > 8) t.add("Включено " + w.size() + " виджетов, экран перегружен. Оставь самые нужные или возьми пресет «Минимальный HUD».");

        // gameplay
        if (on(KillAura.class) && num(KillAura.class, "Range", 3) > 3.0)
            t.add("KillAura: дальность больше 3.0. В ванили дальше 3 блоков удар не засчитывается, такая дальность имеет смысл только в одиночной игре.");
        if (on(Fly.class) && num(Fly.class, "Speed", 2) > 5)
            t.add("Fly: скорость больше 5, можно вылетать за прогруженные чанки и получать подгрузки. Для комфорта 2–3.");
        if (on(AimAssist.class) && num(AimAssist.class, "Strength", 0.25) > 0.6)
            t.add("AimAssist: сила больше 0.6 даёт резкие доводки камеры. Для плавной наводки поставь 0.2–0.3.");

        // performance
        if (on(ESP.class) && num(ESP.class, "Range", 64) > 128)
            t.add("ESP: дальность больше 128, много подсвеченных сущностей может просадить FPS. 48–64 обычно хватает.");
        if (on(Radar.class) && num(Radar.class, "Дальность (блоки)", 32) > 64)
            t.add("Radar: дальность больше 64 блоков, лишние точки только загромождают карту. 32 блока читаются лучше.");

        // cosmetics
        int allPlayers = 0;
        for (Class<? extends Mod> c : List.<Class<? extends Mod>>of(Wings.class, Hat.class, Aura.class)) {
            Mod m = ModuleManager.get(c);
            Setting<?> who = m == null ? null : find(m, "Кому");
            if (m != null && m.isEnabled() && who != null && who.asString().equals("Всем игрокам")) allPlayers++;
        }
        if (allPlayers >= 2)
            t.add("Крылья, шляпа и аура включены для всех игроков, на людном сервере это нагружает FPS. Оставь «Кому: Только мне» хотя бы у части.");
        if (on(BoxESP.class) && on(Tracers.class) && on(Nametags.class))
            t.add("BoxESP, Tracers и Nametags вместе забивают экран. Для чистой картинки оставь BoxESP и Nametags.");

        // interface
        if (num(Interface.class, "Прозрачность фона", 70) < 25)
            t.add("Прозрачность фона меню ниже 25%: игра просвечивает и текст читается хуже. 50–70% обычно оптимально.");
        if (!on(Notifications.class))
            t.add("Включи Notifications, чтобы видеть всплывающие сообщения при включении и выключении модулей.");

        boolean anyBind = false;
        for (Mod m : ModuleManager.modules) if (m.key > 0) anyBind = true;
        if (!anyBind)
            t.add("Ни у одного модуля нет бинда. Открой модуль и нажми «Бинд», чтобы включать его клавишей.");

        if (t.isEmpty()) t.add("Всё выглядит хорошо: наложений нет, значения в разумных пределах.");
        return t;
    }

    public static void applyPreset(int idx) {
        switch (idx) {
            case 0 -> {
                for (Class<? extends Mod> c : List.<Class<? extends Mod>>of(Sprint.class, Fullbright.class,
                        AutoTool.class, Notifications.class, Hud.class)) enable(c, true);
            }
            case 1 -> {
                for (Class<? extends Mod> c : List.<Class<? extends Mod>>of(AutoEat.class, AutoArmor.class,
                        AutoTool.class, AutoRespawn.class, AutoTorch.class, DurabilityWarning.class, Sprint.class, Notifications.class, Hud.class)) enable(c, true);
            }
            case 2 -> {
                for (Mod m : ModuleManager.modules) if (m instanceof HudBase) m.setEnabled(false);
                enable(Hud.class, true);
                enable(Notifications.class, true);
                set(Hud.class, "Coordinates", "false");
                set(Hud.class, "FPS", "true");
            }
            case 3 -> {
                for (Mod m : ModuleManager.modules) if (m instanceof HudBase h) { h.resetPosition(); h.setEnabled(true); }
                enable(Hud.class, true);
                enable(Notifications.class, true);
                enable(Crosshair.class, true);
                set(Hud.class, "Coordinates", "true");
                set(Hud.class, "FPS", "true");
            }
            default -> {
                for (Mod m : ModuleManager.modules)
                    if (!(m instanceof Hud || m instanceof Notifications || m instanceof Interface)) m.setEnabled(false);
            }
        }
    }

    /** Text dump of all modules/settings for the AI assistant. */
    public static String snapshot() {
        StringBuilder sb = new StringBuilder();
        for (Mod m : ModuleManager.modules) {
            sb.append("- ").append(m.name).append(m.isEnabled() ? " [ВКЛ]" : " [выкл]")
              .append(" (").append(m.category.label).append("): ").append(Lang.module(m.name)).append('\n');
            for (Setting<?> s : m.settings) {
                sb.append("    ").append(Lang.setting(s.name)).append(" = ");
                if (s instanceof Setting.Num n) sb.append(n.display()).append(" (").append(n.min).append("..").append(n.max).append(")");
                else sb.append(Lang.value(s.asString()));
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
