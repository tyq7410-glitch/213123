package dev.aurora.module;

import dev.aurora.core.*;
import dev.aurora.util.Anim;
import dev.aurora.util.Proj;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import dev.aurora.module.Pvp.*;
import dev.aurora.module.Movement.*;
import dev.aurora.module.Visuals.*;
import dev.aurora.module.Pve.*;
import dev.aurora.module.Cosmetics.*;
import dev.aurora.module.Misc.*;

/** All Cosmetics modules in one file (keeps the repository small enough for GitHub web upload). */
public final class Cosmetics {
    private Cosmetics() {}

    public static class Wings extends ProjCosmetic {
        private final Setting.Mode style = add(new Setting.Mode("Стиль", "Ангельские", "Ангельские", "Драконьи", "Бабочка"));
        private final Setting.Num size = add(new Setting.Num("Размер", 1.0, 0.5, 2.0, 0.1));
        private final Setting.Num flap = add(new Setting.Num("Скорость взмахов", 1.0, 0.0, 3.0, 0.1));

        public Wings() { super("Wings"); }

        @Override
        protected void draw(DrawContext ctx, PlayerEntity pl) {
            boolean air = !pl.isOnGround();
            double amp = air ? 0.55 : 0.18;
            double ang = Math.sin(System.currentTimeMillis() / 1000.0 * 4.0 * flap.get()) * amp + (air ? 0.3 : 0.45);
            double cos = Math.cos(ang), sin = Math.sin(ang);
            double base = pl.isSneaking() ? 1.05 : 1.25, back = pl.isSneaking() ? 0.32 : 0.18;
            double s = size.get();
            int idx = 0;
            for (double[][] poly : shape()) {
                for (int side = -1; side <= 1; side += 2) {
                    Vec3d prev = null;
                    for (double[] q : poly) {
                        Vec3d w = at(pl, side * q[0] * s * cos, base + q[1] * s, back + q[0] * s * sin);
                        if (prev != null) Proj.neon3(ctx, prev, w, col(idx), thick.asInt(), glow.get());
                        prev = w;
                    }
                }
                idx += 3;
            }
        }

        private List<double[][]> shape() {
            List<double[][]> l = new ArrayList<>();
            switch (style.get()) {
                case "Драконьи" -> {
                    double[] th = {12, 42, 72}, len = {1.5, 1.35, 1.0};
                    double[][] tips = new double[4][];
                    for (int k = 0; k < 3; k++) {
                        double a = Math.toRadians(th[k]);
                        tips[k] = new double[]{len[k] * Math.cos(a), len[k] * Math.sin(a)};
                        l.add(new double[][]{{0.1, 0}, tips[k]});
                    }
                    tips[3] = new double[]{0.15, -0.55};
                    l.add(new double[][]{tips[0], tips[1], tips[2]});
                    l.add(new double[][]{tips[0], tips[3], {0.1, 0}});
                }
                case "Бабочка" -> {
                    l.add(ellipse(0.65, 0.55, 0.6, 0.5, 16));
                    l.add(ellipse(0.45, -0.3, 0.4, 0.35, 12));
                }
                default -> {
                    double[] len = {0.9, 1.1, 1.25, 1.3, 1.2, 1.0, 0.75};
                    double[][] tips = new double[len.length][];
                    for (int k = 0; k < len.length; k++) {
                        double a = Math.toRadians(-24 + k * 14);
                        tips[k] = new double[]{len[k] * Math.cos(a), len[k] * Math.sin(a)};
                        l.add(new double[][]{{0.1, 0}, tips[k]});
                    }
                    l.add(tips);
                }
            }
            return l;
        }

        private static double[][] ellipse(double cx, double cy, double rx, double ry, int n) {
            double[][] r = new double[n + 1][];
            for (int i = 0; i <= n; i++) {
                double a = 2 * Math.PI * i / n;
                r[i] = new double[]{cx + rx * Math.cos(a), cy + ry * Math.sin(a)};
            }
            return r;
        }
    }

    public static class Hat extends ProjCosmetic {
        private final Setting.Mode style = add(new Setting.Mode("Стиль", "Нимб", "Нимб", "Корона", "Колпак", "Китайская"));
        private final Setting.Num size = add(new Setting.Num("Размер", 1.0, 0.5, 2.0, 0.1));
        private final Setting.Num height = add(new Setting.Num("Высота над головой", 0.1, 0.0, 1.0, 0.05));
        private final Setting.Num spin = add(new Setting.Num("Вращение", 1.0, 0.0, 4.0, 0.25));

        public Hat() { super("Hat"); }

        @Override
        protected void draw(DrawContext ctx, PlayerEntity pl) {
            Vec3d b = pl.getLerpedPos(Proj.pt);
            double top = b.y + pl.getHeight() + height.get();
            double rot = System.currentTimeMillis() / 1000.0 * spin.get();
            double s = size.get();
            int th = thick.asInt();
            boolean g = glow.get();

            switch (style.get()) {
                case "Корона" -> {
                    int n = 16;
                    Vec3d prev = null;
                    for (int i = 0; i <= n; i++) {
                        double a = rot + 2 * Math.PI * i / n;
                        double y = top + 0.05 + (i % 2 == 0 ? 0.22 * s : 0.0);
                        Vec3d cur = new Vec3d(b.x + Math.cos(a) * 0.3 * s, y, b.z + Math.sin(a) * 0.3 * s);
                        if (prev != null) Proj.neon3(ctx, prev, cur, col(i), th, g);
                        prev = cur;
                    }
                    Proj.ring(ctx, b.x, top + 0.05, b.z, 0.3 * s, 24, rot, col(0), th, g);
                }
                case "Колпак" -> cone(ctx, b, top, 0.42 * s, 0.85 * s, 8, rot, th, g);
                case "Китайская" -> cone(ctx, b, top + 0.02, 0.65 * s, 0.4 * s, 14, rot, th, g);
                default -> Proj.ring(ctx, b.x, top + 0.25, b.z, 0.32 * s, 28, rot, col(0), th, g);
            }
        }

        private void cone(DrawContext ctx, Vec3d b, double y, double r, double h, int lines, double rot, int th, boolean g) {
            Proj.ring(ctx, b.x, y, b.z, r, 28, rot, col(0), th, g);
            Proj.ring(ctx, b.x, y + h * 0.5, b.z, r * 0.5, 20, rot, col(4), th, g);
            Vec3d tip = new Vec3d(b.x, y + h, b.z);
            for (int i = 0; i < lines; i++) {
                double a = rot + 2 * Math.PI * i / lines;
                Proj.neon3(ctx, new Vec3d(b.x + Math.cos(a) * r, y, b.z + Math.sin(a) * r), tip, col(i * 2), th, g);
            }
        }
    }

    public static class Aura extends ProjCosmetic {
        private final Setting.Num count = add(new Setting.Num("Количество", 4, 1, 12, 1));
        private final Setting.Num radius = add(new Setting.Num("Радиус", 0.9, 0.4, 2.0, 0.1));
        private final Setting.Num speed = add(new Setting.Num("Скорость", 2.0, 0.5, 5.0, 0.5));
        private final Setting.Num span = add(new Setting.Num("Высота спирали", 1.6, 0.2, 2.5, 0.1));

        public Aura() { super("Aura"); }

        private Vec3d orb(PlayerEntity pl, double t, int i, int n) {
            Vec3d b = pl.getLerpedPos(Proj.pt);
            double a = t + 2 * Math.PI * i / n;
            double y = b.y + 0.1 + (Math.sin(t * 0.7 + i) * 0.5 + 0.5) * span.get();
            return new Vec3d(b.x + Math.cos(a) * radius.get(), y, b.z + Math.sin(a) * radius.get());
        }

        @Override
        protected void draw(DrawContext ctx, PlayerEntity pl) {
            double t = System.currentTimeMillis() / 1000.0 * speed.get();
            int n = count.asInt();
            for (int i = 0; i < n; i++) {
                int c = col(i * 3);
                Vec3d cur = orb(pl, t, i, n);
                for (int k = 1; k <= 4; k++) {   // short comet tail
                    Vec3d a = orb(pl, t - 0.08 * (k - 1), i, n), b = orb(pl, t - 0.08 * k, i, n);
                    Proj.neon3(ctx, a, b, Proj.alpha(c, 1f - k * 0.22f), thick.asInt(), glow.get());
                }
                float[] p = Proj.project(cur.x, cur.y, cur.z);
                if (p != null) {
                    int s = 1 + thick.asInt();
                    if (glow.get()) ctx.fill((int) p[0] - s - 1, (int) p[1] - s - 1, (int) p[0] + s + 2, (int) p[1] + s + 2, Proj.alpha(c, 0.3f));
                    ctx.fill((int) p[0] - s, (int) p[1] - s, (int) p[0] + s + 1, (int) p[1] + s + 1, c);
                }
            }
        }
    }

    /** A glowing ribbon behind you (your own movement only). */
    public static class Trail extends ProjCosmetic {
        private final Setting.Num length = add(new Setting.Num("Длина", 40, 10, 120, 5));
        private final Setting.Num heightOff = add(new Setting.Num("Высота ленты", 0.1, 0.0, 1.5, 0.1));
        private final List<Vec3d> points = new ArrayList<>();

        public Trail() { super("Trail"); settings.remove(who); }

        @Override
        public void onTick() {
            var p = mc.player;
            Vec3d cur = new Vec3d(p.getX(), p.getY(), p.getZ());
            if (points.isEmpty() || points.get(points.size() - 1).squaredDistanceTo(cur) > 0.01) points.add(cur);
            else if (!points.isEmpty() && points.size() > 1 && mc.world.getTime() % 4 == 0) points.remove(0); // fades when standing
            while (points.size() > length.asInt()) points.remove(0);
        }

        @Override
        public void onHud(DrawContext ctx) {
            if (!Proj.ready) return;
            double off = heightOff.get();
            for (int i = 1; i < points.size(); i++) {
                Vec3d a = points.get(i - 1).add(0, off, 0), b = points.get(i).add(0, off, 0);
                float f = (float) i / points.size();
                Proj.neon3(ctx, a, b, Proj.alpha(col(i), f), thick.asInt(), glow.get());
                if (off > 0.3) Proj.neon3(ctx, points.get(i - 1), points.get(i), Proj.alpha(col(i + 8), f * 0.6f), 1, false);
            }
        }

        @Override protected void draw(DrawContext ctx, PlayerEntity pl) {}
        @Override protected void onDisable() { points.clear(); }
    }

    /** A neon "bubble" (three expanding rings) on the entity you hit. */
    public static class HitEffects extends ProjCosmetic {
        private final Setting.Num size = add(new Setting.Num("Размер", 1.0, 0.4, 2.5, 0.1));
        private final Setting.Num life = add(new Setting.Num("Время жизни (мс)", 600, 200, 1500, 50));
        private record Hit(Vec3d c, long t) {}
        private final List<Hit> hits = new ArrayList<>();

        public HitEffects() { super("HitEffects"); settings.remove(who); }

        public void burst(Entity e) { hits.add(new Hit(e.getBoundingBox().getCenter(), System.currentTimeMillis())); }

        @Override
        public void onHud(DrawContext ctx) {
            if (!Proj.ready) return;
            long now = System.currentTimeMillis();
            hits.removeIf(h -> now - h.t() > life.get());
            for (Hit h : hits) {
                float k = (float) ((now - h.t()) / life.get());
                double r = (0.2 + 0.9 * k) * size.get();
                for (int i = -1; i <= 1; i++) {
                    double rr = r * Math.cos(i * 0.8), y = h.c().y + r * Math.sin(i * 0.8);
                    Proj.ring(ctx, h.c().x, y, h.c().z, rr, 24, k * 3, Proj.alpha(col(i + 2), 1f - k), thick.asInt(), glow.get());
                }
            }
        }

        @Override protected void draw(DrawContext ctx, PlayerEntity pl) {}
        @Override protected void onDisable() { hits.clear(); }
    }

    /** An expanding neon ring on the ground each time you jump. */
    public static class JumpCircles extends ProjCosmetic {
        private final Setting.Num radius = add(new Setting.Num("Макс. радиус", 1.4, 0.6, 3.0, 0.2));
        private final Setting.Num life = add(new Setting.Num("Время жизни (мс)", 900, 300, 2000, 100));
        private record Ring(double x, double y, double z, long t) {}
        private final List<Ring> rings = new ArrayList<>();
        private boolean wasGround = true;

        public JumpCircles() { super("JumpCircles"); settings.remove(who); }

        @Override
        public void onTick() {
            var p = mc.player;
            if (wasGround && !p.isOnGround() && p.getVelocity().y > 0.2) rings.add(new Ring(p.getX(), p.getY() + 0.03, p.getZ(), System.currentTimeMillis()));
            wasGround = p.isOnGround();
        }

        @Override
        public void onHud(DrawContext ctx) {
            if (!Proj.ready) return;
            long now = System.currentTimeMillis();
            rings.removeIf(r -> now - r.t() > life.get());
            for (Ring r : rings) {
                float k = (float) ((now - r.t()) / life.get());
                double rad = 0.2 + radius.get() * Anim.ease(k);
                float a = 1f - k;
                Proj.ring(ctx, r.x(), r.y(), r.z(), rad, 36, 0, Proj.alpha(col(0), a), thick.asInt(), glow.get());
                Proj.ring(ctx, r.x(), r.y(), r.z(), rad * 0.7, 28, 0, Proj.alpha(col(6), a * 0.7f), 1, false);
            }
        }

        @Override protected void draw(DrawContext ctx, PlayerEntity pl) {}
        @Override protected void onDisable() { rings.clear(); }
    }
}
