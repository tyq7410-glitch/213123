package dev.aurora.util;

import dev.aurora.module.Misc.Friends;
import dev.aurora.module.Misc.Interface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class Util {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private Util() {}

    public static boolean moving() {
        var o = mc.options;
        return o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed();
    }

    /** Horizontal velocity {x, z} in the direction of WASD input. */
    public static double[] horizontal(double speed) {
        var o = mc.options;
        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        if (f == 0 && s == 0) return new double[]{0, 0};
        double len = Math.sqrt(f * f + s * s);
        f /= len; s /= len;
        double yaw = Math.toRadians(mc.player.getYaw());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        return new double[]{(-sin * f + cos * s) * speed, (cos * f + sin * s) * speed};
    }

    public static Vec3d center(Entity e) { return e.getBoundingBox().getCenter(); }

    public static float[] rotationsTo(Vec3d t) {
        Vec3d eye = mc.player.getEyePos();
        double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[]{yaw, pitch};
    }

    /** Angular distance (deg) between the crosshair and the entity. */
    public static double angleTo(Entity e) {
        float[] r = rotationsTo(center(e));
        ClientPlayerEntity p = mc.player;
        return Math.hypot(MathHelper.wrapDegrees(r[0] - p.getYaw()), r[1] - p.getPitch());
    }

    /** Turns the camera toward the entity. factor = fraction of the remaining angle per tick, maxStep = deg/tick cap. */
    public static void aim(Entity e, float factor, float maxStep) {
        ClientPlayerEntity p = mc.player;
        float[] r = rotationsTo(center(e));
        float dy = MathHelper.wrapDegrees(r[0] - p.getYaw());
        float dp = r[1] - p.getPitch();
        p.setYaw(p.getYaw() + MathHelper.clamp(dy * factor, -maxStep, maxStep));
        p.setPitch(MathHelper.clamp(p.getPitch() + MathHelper.clamp(dp * factor, -maxStep, maxStep), -90f, 90f));
    }

    public static boolean matches(Entity e, boolean players, boolean mobs, boolean animals) {
        if (Friends.isFriend(e) || dev.aurora.module.Pvp.Teams.same(e) || dev.aurora.module.Pvp.AntiBot.isBot(e)) return false;
        if (players && e instanceof PlayerEntity pe && !pe.isSpectator()) return true;
        if (mobs && e instanceof Monster) return true;
        return animals && e instanceof AnimalEntity;
    }

    public static List<LivingEntity> targets(double range, boolean players, boolean mobs, boolean animals,
                                             boolean invisible, boolean walls) {
        List<LivingEntity> out = new ArrayList<>();
        ClientPlayerEntity p = mc.player;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || e == p || !le.isAlive()) continue;
            if (p.distanceTo(le) > range) continue;
            if (!invisible && le.isInvisible()) continue;
            if (!matches(le, players, mobs, animals)) continue;
            if (!walls && !p.canSee(le)) continue;
            out.add(le);
        }
        return out;
    }

    public static final String[] PALETTE = {"Белый", "Красный", "Оранжевый", "Жёлтый", "Зелёный",
            "Голубой", "Синий", "Фиолетовый", "Розовый", "Радуга", "Акцент"};

    public static int rainbow(int i) {
        float h = (float) (((System.currentTimeMillis() / 18.0) + i * 14) % 360.0) / 360f;
        return 0xFF000000 | MathHelper.hsvToRgb(h, 0.55f, 1f);
    }

    /** Interface accent color (set in the Interface module) or full rainbow. Always opaque (ARGB). */
    public static int color(int i, boolean rainbow) {
        return rainbow ? rainbow(i) : Interface.accent(i);
    }

    public static int palette(String name, int i) {
        return switch (name) {
            case "Белый" -> 0xFFFFFFFF;
            case "Красный" -> 0xFFFF4040;
            case "Оранжевый" -> 0xFFFF9A2E;
            case "Жёлтый" -> 0xFFFFE94D;
            case "Зелёный" -> 0xFF4DFF6A;
            case "Голубой" -> 0xFF4DD2FF;
            case "Синий" -> 0xFF4D6BFF;
            case "Фиолетовый" -> 0xFFA84DFF;
            case "Розовый" -> 0xFFFF6BD0;
            case "Радуга" -> rainbow(i);
            default -> Interface.accent(i);
        };
    }

    /** Arrow pointing from the player toward a world offset (dx, dz), relative to where the player looks. */
    public static String arrow(double dx, double dz) {
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = MathHelper.wrapDegrees(target - mc.player.getYaw());
        String[] a = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int i = (int) Math.round(rel / 45.0);
        return a[(i % 8 + 8) % 8];
    }
}
