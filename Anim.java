package dev.aurora.util;

import java.util.HashMap;
import java.util.Map;

/** Frame-rate independent smooth animations (exponential approach). */
public final class Anim {
    private static final Map<Object, Float> VALUES = new HashMap<>();
    private static long last = System.nanoTime();
    private static float dt = 0.016f;

    private Anim() {}

    /** Call at the start of every frame that uses animations (safe to call several times per frame). */
    public static void frame() {
        long now = System.nanoTime();
        if (now - last < 2_000_000L) return;
        dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
    }

    public static float dt() { return dt; }

    /** Moves the stored value for `key` toward `target`. First call starts at `start`. */
    public static float to(Object key, float target, float speed, float start) {
        float cur = VALUES.getOrDefault(key, start);
        cur += (target - cur) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - cur) < 0.002f) cur = target;
        VALUES.put(key, cur);
        return cur;
    }

    /** Starts at the target, so nothing animates on the very first frame. */
    public static float to(Object key, float target, float speed) { return to(key, target, speed, target); }

    public static void set(Object key, float v) { VALUES.put(key, v); }

    public static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        int ar = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int ag = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int ab = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }
}
