package dev.aurora.module;

import dev.aurora.core.*;
import dev.aurora.util.Util;
import net.minecraft.entity.attribute.EntityAttributes;
import dev.aurora.module.Pvp.*;
import dev.aurora.module.Movement.*;
import dev.aurora.module.Visuals.*;
import dev.aurora.module.Pve.*;
import dev.aurora.module.Cosmetics.*;
import dev.aurora.module.Misc.*;

/** All Movement modules in one file (keeps the repository small enough for GitHub web upload). */
public final class Movement {
    private Movement() {}

    public static class Sprint extends Mod {
        private final Setting.Bool omni = add(new Setting.Bool("All directions", false));

        public Sprint() { super("Sprint", Category.MOVEMENT); }

        @Override
        public void onTick() {
            if (WTap.holding()) return;
            var p = mc.player;
            if (p.isSneaking() || p.horizontalCollision || p.getHungerManager().getFoodLevel() <= 6) return;
            boolean go = omni.get() ? Util.moving() : mc.options.forwardKey.isPressed();
            if (go) p.setSprinting(true);
        }
    }

    public static class Fly extends Mod {
        private final Setting.Mode mode = add(new Setting.Mode("Mode", "Vanilla", "Vanilla", "Motion"));
        private final Setting.Num speed = add(new Setting.Num("Speed", 2.0, 0.5, 10.0, 0.5));
        private final Setting.Num vSpeed = add(new Setting.Num("Vertical", 1.0, 0.2, 5.0, 0.2));

        public Fly() { super("Fly", Category.MOVEMENT); }

        @Override
        public void onTick() {
            var p = mc.player;
            if (mode.is("Vanilla")) {
                var a = p.getAbilities();
                a.allowFlying = true;
                a.flying = true;
                a.setFlySpeed(speed.asFloat() * 0.05f);
                return;
            }
            double[] h = Util.horizontal(speed.get() * 0.5);
            double y = 0;
            if (mc.options.jumpKey.isPressed()) y += vSpeed.get() * 0.5;
            if (mc.options.sneakKey.isPressed()) y -= vSpeed.get() * 0.5;
            p.setVelocity(h[0], y, h[1]);
            p.fallDistance = 0;
        }

        @Override
        protected void onDisable() {
            var p = mc.player;
            if (p == null) return;
            var a = p.getAbilities();
            a.setFlySpeed(0.05f);
            if (!p.isCreative() && !p.isSpectator()) {
                a.flying = false;
                a.allowFlying = false;
            }
        }
    }

    public static class Speed extends Mod {
        private final Setting.Num speed = add(new Setting.Num("Speed", 0.35, 0.1, 1.0, 0.05));
        private final Setting.Bool autoJump = add(new Setting.Bool("Auto jump", false));
        private final Setting.Bool groundOnly = add(new Setting.Bool("Ground only", false));

        public Speed() { super("Speed", Category.MOVEMENT); }

        @Override
        public void onTick() {
            var p = mc.player;
            if (!Util.moving() || p.isSneaking() || p.isTouchingWater() || p.isInLava()
                    || p.isClimbing() || p.getAbilities().flying) return;
            if (groundOnly.get() && !p.isOnGround()) return;

            double[] h = Util.horizontal(speed.get());
            p.setVelocity(h[0], p.getVelocity().y, h[1]);
            if (autoJump.get() && p.isOnGround()) p.jump();
        }
    }

    public static class Step extends Mod {
        private final Setting.Num height = add(new Setting.Num("Высота шага", 1.0, 0.6, 5.0, 0.1));

        public Step() { super("Step", Category.MOVEMENT); }

        @Override
        public void onTick() {
            var a = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
            if (a != null && Math.abs(a.getBaseValue() - height.get()) > 0.001) a.setBaseValue(height.get());
        }

        @Override
        protected void onDisable() {
            if (mc.player == null) return;
            var a = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
            if (a != null) a.setBaseValue(0.6);
        }
    }

    public static class HighJump extends Mod {
        private final Setting.Num power = add(new Setting.Num("Сила прыжка", 0.8, 0.5, 2.0, 0.1));
        private boolean boosted;

        public HighJump() { super("HighJump", Category.MOVEMENT); }

        @Override
        public void onTick() {
            var p = mc.player;
            var v = p.getVelocity();
            if (p.isOnGround()) boosted = false;
            else if (!boosted && v.y > 0.3 && v.y < 0.5 && mc.options.jumpKey.isPressed()) {
                p.setVelocity(v.x, power.get(), v.z);
                boosted = true;
            }
        }
    }

    public static class AirJump extends Mod {
        private final Setting.Num delay = add(new Setting.Num("Задержка (тики)", 8, 1, 20, 1));
        private boolean last;
        private int cd;

        public AirJump() { super("AirJump", Category.MOVEMENT); }

        @Override
        public void onTick() {
            var p = mc.player;
            boolean j = mc.options.jumpKey.isPressed();
            if (cd > 0) cd--;
            if (j && !last && cd == 0 && !p.isOnGround() && !p.getAbilities().flying) {
                p.jump();
                cd = delay.asInt();
            }
            last = j;
        }
    }

    public static class AutoJump extends Mod {
        public AutoJump() { super("AutoJump", Category.MOVEMENT); }

        @Override
        public void onTick() {
            var p = mc.player;
            if (Util.moving() && p.horizontalCollision && p.isOnGround() && !p.isSneaking()) p.jump();
        }
    }
}
