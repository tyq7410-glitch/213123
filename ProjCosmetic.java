package dev.aurora.module;

import dev.aurora.core.*;
import dev.aurora.util.Proj;
import dev.aurora.util.Util;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Neon, line-drawn cosmetics rendered through screen projection (no particles). Client-side only. */
public abstract class ProjCosmetic extends Mod {
    protected final Setting.Mode color = add(new Setting.Mode("Цвет", "Акцент", Util.PALETTE));
    protected final Setting.Mode who = add(new Setting.Mode("Кому", "Только мне", "Только мне", "Всем игрокам"));
    protected final Setting.Bool glow = add(new Setting.Bool("Свечение", true));
    protected final Setting.Num thick = add(new Setting.Num("Толщина", 1, 1, 3, 1));

    protected ProjCosmetic(String name) { super(name, Category.COSMETIC); }

    protected int col(int i) { return Util.palette(color.get(), i); }

    @Override
    public void onHud(DrawContext ctx) {
        if (!Proj.ready || mc.player == null) return;
        var me = mc.player;
        if (!mc.options.getPerspective().isFirstPerson()) draw(ctx, me);
        if (who.is("Всем игрокам"))
            for (var e : mc.world.getPlayers()) {
                if (e == me || e.isInvisible() || e.distanceTo(me) > 40) continue;
                draw(ctx, e);
            }
    }

    protected abstract void draw(DrawContext ctx, PlayerEntity pl);

    /** Point relative to the body: right / absolute up / back (rotated by body yaw). */
    protected Vec3d at(PlayerEntity pl, double right, double up, double back) {
        Vec3d base = pl.getLerpedPos(Proj.pt);
        double yaw = Math.toRadians(pl.getBodyYaw());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw), bx = Math.sin(yaw), bz = -Math.cos(yaw);
        return new Vec3d(base.x + rx * right + bx * back, base.y + up, base.z + rz * right + bz * back);
    }
}
