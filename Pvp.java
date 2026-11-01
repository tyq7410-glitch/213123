package dev.aurora.module;

import dev.aurora.core.*;
import dev.aurora.util.Util;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import dev.aurora.module.Pvp.*;
import dev.aurora.module.Movement.*;
import dev.aurora.module.Visuals.*;
import dev.aurora.module.Pve.*;
import dev.aurora.module.Cosmetics.*;
import dev.aurora.module.Misc.*;

/** All Pvp modules in one file (keeps the repository small enough for GitHub web upload). */
public final class Pvp {
    private Pvp() {}

    public static class KillAura extends Mod {
        private final Setting.Num range = add(new Setting.Num("Range", 3.0, 2.0, 6.0, 0.1));
        private final Setting.Num fov = add(new Setting.Num("FOV", 360, 30, 360, 10));
        private final Setting.Mode priority = add(new Setting.Mode("Priority", "Distance", "Distance", "Health", "Angle"));
        private final Setting.Bool players = add(new Setting.Bool("Players", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Mobs", true));
        private final Setting.Bool animals = add(new Setting.Bool("Animals", false));
        private final Setting.Bool invisible = add(new Setting.Bool("Invisible", false));
        private final Setting.Bool walls = add(new Setting.Bool("Through walls", false));
        private final Setting.Bool rotate = add(new Setting.Bool("Rotate", true));
        private final Setting.Num rotSpeed = add(new Setting.Num("Rot speed", 30, 5, 180, 5));
        private final Setting.Bool cooldown = add(new Setting.Bool("Cooldown aware", true));
        private final Setting.Num cps = add(new Setting.Num("CPS", 8, 1, 20, 1));
        private long last;
        private LivingEntity current;

        public LivingEntity target() { return current; }

        public KillAura() { super("KillAura", Category.COMBAT); }

        @Override
        protected void onDisable() { current = null; }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null) return;
            var p = mc.player;

            List<LivingEntity> list = Util.targets(range.get(), players.get(), mobs.get(), animals.get(),
                    invisible.get(), walls.get());
            list.removeIf(e -> Util.angleTo(e) > fov.get() / 2.0);
            if (list.isEmpty()) { current = null; return; }

            Comparator<LivingEntity> cmp = switch (priority.get()) {
                case "Health" -> Comparator.<LivingEntity>comparingDouble(LivingEntity::getHealth);
                case "Angle" -> Comparator.<LivingEntity>comparingDouble(Util::angleTo);
                default -> Comparator.<LivingEntity>comparingDouble(e -> p.distanceTo(e));
            };
            list.sort(cmp);
            LivingEntity t = list.get(0);
            current = t;

            if (rotate.get()) Util.aim(t, 1f, rotSpeed.asFloat());
            if (rotate.get() && Util.angleTo(t) > 25) return; // wait until we are looking at it

            if (!CritTiming.ok()) return;
            boolean ready = cooldown.get()
                    ? p.getAttackCooldownProgress(0.5f) >= 0.95f
                    : System.currentTimeMillis() - last >= 1000L / cps.asInt();
            if (ready) {
                mc.interactionManager.attackEntity(p, t);
                p.swingHand(Hand.MAIN_HAND);
                last = System.currentTimeMillis();
            }
        }
    }

    public static class AimAssist extends Mod {
        private final Setting.Num range = add(new Setting.Num("Range", 5.0, 2.0, 8.0, 0.5));
        private final Setting.Num fov = add(new Setting.Num("FOV", 90, 10, 360, 5));
        private final Setting.Num strength = add(new Setting.Num("Strength", 0.25, 0.05, 1.0, 0.05));
        private final Setting.Bool players = add(new Setting.Bool("Players", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Mobs", false));
        private final Setting.Bool onlyClick = add(new Setting.Bool("Only when clicking", true));
        private final Setting.Bool walls = add(new Setting.Bool("Through walls", false));

        public double fovValue() { return fov.get(); }

        public AimAssist() { super("AimAssist", Category.COMBAT); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null) return;
            if (onlyClick.get() && !mc.options.attackKey.isPressed()) return;

            List<LivingEntity> list = Util.targets(range.get(), players.get(), mobs.get(), false, false, walls.get());
            LivingEntity best = null;
            double bestAngle = fov.get() / 2.0;
            for (LivingEntity e : list) {
                double a = Util.angleTo(e);
                if (a < bestAngle) { bestAngle = a; best = e; }
            }
            if (best != null) Util.aim(best, strength.asFloat(), 10f);
        }
    }

    public static class TriggerBot extends Mod {
        private final Setting.Bool players = add(new Setting.Bool("Players", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Mobs", true));
        private final Setting.Bool animals = add(new Setting.Bool("Animals", false));
        private final Setting.Num charge = add(new Setting.Num("Min charge", 0.95, 0.5, 1.0, 0.05));

        public TriggerBot() { super("TriggerBot", Category.COMBAT); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null) return;
            if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
            Entity e = hit.getEntity();
            if (!(e instanceof LivingEntity le) || !le.isAlive()) return;
            if (!Util.matches(e, players.get(), mobs.get(), animals.get())) return;

            if (CritTiming.ok() && mc.player.getAttackCooldownProgress(0.5f) >= charge.asFloat()) {
                mc.interactionManager.attackEntity(mc.player, e);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    public static class AutoClicker extends Mod {
        private final Setting.Num cps = add(new Setting.Num("CPS", 10, 1, 20, 1));
        private final Setting.Bool cooldown = add(new Setting.Bool("Ждать заряд удара", false));
        private long last;

        public AutoClicker() { super("AutoClicker", Category.COMBAT); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null || !mc.options.attackKey.isPressed()) return;
            if (!(mc.crosshairTarget instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity e)) return;
            if (Friends.isFriend(e) || !CritTiming.ok()) return;
            var p = mc.player;
            long now = System.currentTimeMillis();
            boolean ready = cooldown.get() ? p.getAttackCooldownProgress(0.5f) >= 0.95f : now - last >= 1000L / cps.asInt();
            if (ready) {
                mc.interactionManager.attackEntity(p, e);
                p.swingHand(Hand.MAIN_HAND);
                last = now;
            }
        }
    }

    /** Releases sprint for a moment after every hit, so the next hit gets full knockback again. */
    public static class WTap extends Mod {
        private final Setting.Num ticks = add(new Setting.Num("Пауза спринта (тики)", 2, 1, 6, 1));
        private static int left;

        public WTap() { super("WTap", Category.COMBAT); }

        public void hit() { left = ticks.asInt(); }

        public static boolean holding() {
            WTap w = ModuleManager.get(WTap.class);
            return w != null && w.isEnabled() && left > 0;
        }

        @Override
        public void onTick() {
            if (left > 0) {
                left--;
                mc.player.setSprinting(false);
                mc.options.sprintKey.setPressed(false);
            }
        }

        @Override protected void onDisable() { left = 0; }
    }

    /** KillAura / TriggerBot / AutoClicker only swing while you are falling, so every hit is a critical. */
    public static class CritTiming extends Mod {
        private final Setting.Num fall = add(new Setting.Num("Мин. падение (блоки)", 0.05, 0.0, 1.0, 0.05));

        public CritTiming() { super("CritTiming", Category.COMBAT); }

        public static boolean ok() {
            CritTiming c = ModuleManager.get(CritTiming.class);
            if (c == null || !c.isEnabled()) return true;
            var p = mc.player;
            // criticals are impossible in these states, so don't block the attack
            if (p.isTouchingWater() || p.isClimbing() || p.hasVehicle() || p.getAbilities().flying) return true;
            return !p.isOnGround() && p.getVelocity().y < 0 && p.fallDistance > c.fall.get();
        }
    }

    public static class AutoShield extends Mod {
        private final Setting.Num range = add(new Setting.Num("Дистанция угрозы", 4.0, 2.0, 8.0, 0.5));
        private final Setting.Bool players = add(new Setting.Bool("Игроки", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Мобы", true));
        private boolean holding;

        public AutoShield() { super("AutoShield", Category.COMBAT); }

        @Override
        public void onTick() {
            var p = mc.player;
            boolean shield = p.getOffHandStack().isOf(Items.SHIELD) || p.getMainHandStack().isOf(Items.SHIELD);
            boolean threat = shield && mc.currentScreen == null && !mc.options.attackKey.isPressed()
                    && !Util.targets(range.get(), players.get(), mobs.get(), false, false, true).isEmpty();
            if (threat) { mc.options.useKey.setPressed(true); holding = true; }
            else if (holding) { mc.options.useKey.setPressed(false); holding = false; }
        }

        @Override protected void onDisable() { if (holding) mc.options.useKey.setPressed(false); holding = false; }
    }

    /** Throws a splash potion of healing at your feet when your health is low. */
    public static class AutoPotion extends Mod {
        private final Setting.Num health = add(new Setting.Num("Здоровье ≤", 8, 1, 19, 1));
        private final Setting.Num pause = add(new Setting.Num("Пауза (тики)", 40, 10, 100, 1));
        private int cd;

        public AutoPotion() { super("AutoPotion", Category.COMBAT); }

        private static boolean heals(ItemStack s) {
            PotionContentsComponent pc = s.get(DataComponentTypes.POTION_CONTENTS);
            if (pc == null) return false;
            for (StatusEffectInstance e : pc.getEffects()) if (e.getEffectType() == StatusEffects.INSTANT_HEALTH) return true;
            return false;
        }

        @Override
        public void onTick() {
            if (cd > 0) { cd--; return; }
            var p = mc.player;
            if (mc.currentScreen != null || mc.interactionManager == null || p.getHealth() > health.get()) return;
            int slot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (s.isOf(Items.SPLASH_POTION) && heals(s)) { slot = i; break; }
            }
            if (slot < 0) return;
            var inv = p.getInventory();
            int prev = inv.getSelectedSlot();
            float oldPitch = p.getPitch();
            inv.setSelectedSlot(slot);
            p.setPitch(90f);
            mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
            p.setPitch(oldPitch);
            inv.setSelectedSlot(prev);
            cd = pause.asInt();
        }
    }

    /** Eats a golden apple when your health is low. */
    public static class AutoGap extends Mod {
        private final Setting.Num health = add(new Setting.Num("Здоровье ≤", 10, 1, 19, 1));
        private final Setting.Bool ench = add(new Setting.Bool("Зачарованные тоже", false));
        private boolean eating;
        private int prev, ticks;

        public AutoGap() { super("AutoGap", Category.COMBAT); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null) return;
            var p = mc.player;
            var inv = p.getInventory();
            if (eating) {
                ticks++;
                ItemStack cur = inv.getStack(inv.getSelectedSlot());
                if (ticks > 60 || !(cur.isOf(Items.GOLDEN_APPLE) || cur.isOf(Items.ENCHANTED_GOLDEN_APPLE))) stop();
                else mc.options.useKey.setPressed(true);
                return;
            }
            if (p.getHealth() > health.get() || p.isUsingItem()) return;
            for (int i = 0; i < 9; i++) {
                ItemStack s = inv.getStack(i);
                if (s.isOf(Items.GOLDEN_APPLE) || (ench.get() && s.isOf(Items.ENCHANTED_GOLDEN_APPLE))) {
                    prev = inv.getSelectedSlot();
                    inv.setSelectedSlot(i);
                    eating = true;
                    ticks = 0;
                    return;
                }
            }
        }

        private void stop() {
            mc.options.useKey.setPressed(false);
            if (mc.player != null) mc.player.getInventory().setSelectedSlot(prev);
            eating = false;
        }

        @Override protected void onDisable() { if (eating) stop(); }
    }

    public static class Teams extends Mod {
        public Teams() { super("Teams", Category.COMBAT); }

        public static boolean same(Entity e) {
            Teams t = ModuleManager.get(Teams.class);
            return t != null && t.isEnabled() && mc.player != null && e instanceof PlayerEntity && mc.player.isTeammate(e);
        }
    }

    public static class AntiBot extends Mod {
        public AntiBot() { super("AntiBot", Category.COMBAT); }

        /** A "player" that is not in the tab list is an NPC / fake. */
        public static boolean isBot(Entity e) {
            AntiBot a = ModuleManager.get(AntiBot.class);
            if (a == null || !a.isEnabled() || !(e instanceof PlayerEntity pe)) return false;
            var nh = mc.getNetworkHandler();
            return nh == null || nh.getPlayerListEntry(pe.getUuid()) == null;
        }
    }

    public static class ComboCounter extends HudBase {
        private int combo, best;
        private long last;

        public ComboCounter() { super("ComboCounter", 50, 40); }

        public void hit() { combo++; best = Math.max(best, combo); last = System.currentTimeMillis(); }

        @Override
        public void onTick() {
            if (mc.player.hurtTime > 0 || System.currentTimeMillis() - last > 3000) combo = 0;
        }

        @Override
        public void onHud(DrawContext ctx) {
            if (combo > 0) drawLines(ctx, List.of("Комбо: " + combo, "Рекорд: " + best));
        }
    }

    public static class ReachDisplay extends HudBase {
        private double lastHit = -1;
        private long when;

        public ReachDisplay() { super("ReachDisplay", 50, 46); }

        public void hit(Entity e) { lastHit = mc.player.distanceTo(e); when = System.currentTimeMillis(); }

        @Override
        public void onHud(DrawContext ctx) {
            List<String> l = new ArrayList<>();
            if (mc.crosshairTarget instanceof EntityHitResult h) l.add(String.format("До цели: %.2f", mc.player.distanceTo(h.getEntity())));
            if (lastHit >= 0 && System.currentTimeMillis() - when < 5000) l.add(String.format("Последний удар: %.2f", lastHit));
            drawLines(ctx, l);
        }
    }
}
