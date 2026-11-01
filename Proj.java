package dev.aurora.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Projects world points onto the screen and draws neon lines/rings/boxes with the GUI renderer.
 * Works for the first-person camera exactly and for third person approximately (camera distance 4, wall-clipped).
 */
public final class Proj {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static double cx, cy, cz, fx, fy, fz, rx, rz, ux, uy, uz, scale, sw, sh;
    public static float pt;
    public static boolean ready;

    private Proj() {}

    public static double scale() { return scale; }
    public static double width() { return sw; }
    public static double height() { return sh; }

    public static void begin(DrawContext ctx, float tickProgress) {
        pt = tickProgress;
        ready = false;
        var p = mc.player;
        if (p == null || mc.world == null) return;
        sw = ctx.getScaledWindowWidth();
        sh = ctx.getScaledWindowHeight();

        Vec3d eye = p.getCameraPosVec(pt);
        Perspective pers = mc.options.getPerspective();
        float yaw = p.getYaw(pt), pitch = p.getPitch(pt);
        if (pers.isFrontView()) { yaw += 180f; pitch = -pitch; }
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        fx = -Math.sin(yr) * Math.cos(pr);
        fy = -Math.sin(pr);
        fz = Math.cos(yr) * Math.cos(pr);

        Vec3d cam = eye;
        if (!pers.isFirstPerson()) {
            Vec3d want = eye.add(-fx * 4, -fy * 4, -fz * 4);
            var hit = mc.world.raycast(new RaycastContext(eye, want, RaycastContext.ShapeType.VISUAL,
                    RaycastContext.FluidHandling.NONE, p));
            cam = hit.getType() == HitResult.Type.MISS ? want : hit.getPos().add(fx * 0.15, fy * 0.15, fz * 0.15);
        }
        cx = cam.x; cy = cam.y; cz = cam.z;

        rx = -Math.cos(yr);
        rz = -Math.sin(yr);
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;

        double fov = mc.options.getFov().getValue();
        double speed = p.getAttributeValue(EntityAttributes.MOVEMENT_SPEED);
        float walk = p.getAbilities().getWalkSpeed();
        float mul = (float) ((speed / Math.max(0.001f, walk) + 1.0) / 2.0);
        if (p.getAbilities().flying) mul *= 1.1f;
        mul = Anim.to("projFov", mul, 12f, 1f);
        fov = Math.min(160, fov * mul);
        scale = (sh / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
        ready = true;
    }

    /** {x, y} on the GUI screen, or null when the point is behind the camera. */
    public static float[] project(double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double zz = dx * fx + dy * fy + dz * fz;
        if (zz < 0.05) return null;
        double xx = dx * rx + dz * rz;
        double yy = dx * ux + dy * uy + dz * uz;
        return new float[]{(float) (sw / 2 + xx / zz * scale), (float) (sh / 2 - yy / zz * scale)};
    }

    public static int alpha(int argb, float a) {
        return (((int) (Math.max(0f, Math.min(1f, a)) * 255)) << 24) | (argb & 0xFFFFFF);
    }

    // ---------------------------------------------------------------- 2D primitives
    public static void line(DrawContext c, float x1, float y1, float x2, float y2, int color, int th) {
        float dx = x2 - x1, dy = y2 - y1;
        int n = (int) Math.max(Math.abs(dx), Math.abs(dy));
        if (n > 4000) return;
        if (n == 0) { c.fill((int) x1, (int) y1, (int) x1 + th, (int) y1 + th, color); return; }
        for (int i = 0; i <= n; i++) {
            int px = Math.round(x1 + dx * i / n), py = Math.round(y1 + dy * i / n);
            if (px < -4 || py < -4 || px > sw || py > sh) continue;
            c.fill(px, py, px + th, py + th, color);
        }
    }

    public static void rect(DrawContext c, float x1, float y1, float x2, float y2, int color, int th) {
        int a = Math.round(x1), b = Math.round(y1), d = Math.round(x2), e = Math.round(y2);
        c.fill(a, b, d, b + th, color);
        c.fill(a, e - th, d, e, color);
        c.fill(a, b, a + th, e, color);
        c.fill(d - th, b, d, e, color);
    }

    public static void corners(DrawContext c, float x1, float y1, float x2, float y2, int color, int th) {
        int a = Math.round(x1), b = Math.round(y1), d = Math.round(x2), e = Math.round(y2);
        int lw = Math.max(3, (d - a) / 4), lh = Math.max(3, (e - b) / 4);
        c.fill(a, b, a + lw, b + th, color); c.fill(a, b, a + th, b + lh, color);
        c.fill(d - lw, b, d, b + th, color); c.fill(d - th, b, d, b + lh, color);
        c.fill(a, e - th, a + lw, e, color); c.fill(a, e - lh, a + th, e, color);
        c.fill(d - lw, e - th, d, e, color); c.fill(d - th, e - lh, d, e, color);
    }

    // ---------------------------------------------------------------- 3D primitives
    /** Line between two world points; `glow` draws a soft halo under it. */
    public static void neon3(DrawContext c, Vec3d a, Vec3d b, int argb, int th, boolean glow) {
        float[] p = project(a.x, a.y, a.z), q = project(b.x, b.y, b.z);
        if (p == null || q == null) return;
        if (glow) {
            int ga = (int) (((argb >>> 24) & 255) * 0.25f);
            line(c, p[0] - 1, p[1] - 1, q[0] - 1, q[1] - 1, (ga << 24) | (argb & 0xFFFFFF), th + 2);
        }
        line(c, p[0], p[1], q[0], q[1], argb, th);
    }

    public static void ring(DrawContext c, double x, double y, double z, double r, int seg, double rot,
                            int argb, int th, boolean glow) {
        Vec3d prev = null;
        for (int i = 0; i <= seg; i++) {
            double a = rot + 2 * Math.PI * i / seg;
            Vec3d cur = new Vec3d(x + Math.cos(a) * r, y, z + Math.sin(a) * r);
            if (prev != null) neon3(c, prev, cur, argb, th, glow);
            prev = cur;
        }
    }

    /** Wire box; false when nothing of it can be projected. */
    public static void box3(DrawContext c, Box b, int argb, int th, boolean glow) {
        Vec3d[] v = new Vec3d[8];
        for (int i = 0; i < 8; i++)
            v[i] = new Vec3d((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
        int[][] e = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] k : e) neon3(c, v[k[0]], v[k[1]], argb, th, glow);
    }

    /** 2D screen bounds {minX, minY, maxX, maxY} of a world box, or null if any corner is behind the camera. */
    public static float[] bounds(Box b) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            float[] p = project((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
            if (p == null) return null;
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        return new float[]{minX, minY, maxX, maxY};
    }
}
