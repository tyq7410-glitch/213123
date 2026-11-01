package dev.aurora.module;

import dev.aurora.core.*;
import dev.aurora.util.Anim;
import dev.aurora.util.Proj;
import dev.aurora.util.Util;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import dev.aurora.module.Pvp.*;
import dev.aurora.module.Movement.*;
import dev.aurora.module.Visuals.*;
import dev.aurora.module.Pve.*;
import dev.aurora.module.Cosmetics.*;
import dev.aurora.module.Misc.*;

/** All Visuals modules in one file (keeps the repository small enough for GitHub web upload). */
public final class Visuals {
    private Visuals() {}

    /** Outline ESP through walls using the vanilla glow effect (client-side only). */
    public static class ESP extends Mod {
        private final Setting.Bool players = add(new Setting.Bool("Players", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Mobs", true));
        private final Setting.Bool animals = add(new Setting.Bool("Animals", false));
        private final Setting.Bool items = add(new Setting.Bool("Items", false));
        private final Setting.Num range = add(new Setting.Num("Range", 64, 8, 256, 8));

        public ESP() { super("ESP", Category.VISUAL); }

        /** Called from EntityMixin. */
        public static boolean shouldGlow(Entity e) {
            ESP esp = ModuleManager.get(ESP.class);
            if (esp == null || !esp.isEnabled() || mc.player == null || mc.world == null) return false;
            if (e == mc.player || mc.world.getEntityById(e.getId()) != e) return false; // client-side copies only
            if (e.distanceTo(mc.player) > esp.range.get()) return false;
            return (esp.players.get() && e instanceof PlayerEntity)
                    || (esp.mobs.get() && e instanceof Monster)
                    || (esp.animals.get() && e instanceof AnimalEntity)
                    || (esp.items.get() && e instanceof ItemEntity);
        }
    }

    public static class BoxESP extends Mod {
        private final Setting.Bool players = add(new Setting.Bool("Игроки", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Мобы", true));
        private final Setting.Bool animals = add(new Setting.Bool("Животные", false));
        private final Setting.Bool items = add(new Setting.Bool("Предметы", false));
        private final Setting.Num range = add(new Setting.Num("Дальность", 64, 8, 200, 8));
        private final Setting.Mode style = add(new Setting.Mode("Стиль", "Углы", "Рамка", "Углы", "3D"));
        private final Setting.Bool health = add(new Setting.Bool("Полоска здоровья", true));
        private final Setting.Bool fill = add(new Setting.Bool("Заливка", false));
        private final Setting.Mode color = add(new Setting.Mode("Цвет", "Акцент", Util.PALETTE));

        public BoxESP() { super("BoxESP", Category.VISUAL); }

        @Override
        public void onHud(DrawContext ctx) {
            if (!Proj.ready) return;
            var me = mc.player;
            int i = 0;
            for (Entity e : mc.world.getEntities()) {
                if (e == me) continue;
                boolean ok = (players.get() && e instanceof PlayerEntity) || (mobs.get() && e instanceof Monster)
                        || (animals.get() && e instanceof AnimalEntity) || (items.get() && e instanceof ItemEntity);
                if (!ok || e.distanceTo(me) > range.get()) continue;

                Vec3d l = e.getLerpedPos(Proj.pt);
                Box box = e.getBoundingBox().offset(l.x - e.getX(), l.y - e.getY(), l.z - e.getZ());
                int c = Friends.listed(e) ? 0xFF55AAFF : Util.palette(color.get(), i++ * 3);

                if (style.is("3D")) { Proj.box3(ctx, box, c, 1, true); continue; }
                float[] b = Proj.bounds(box);
                if (b == null) continue;
                if (fill.get()) ctx.fill(Math.round(b[0]), Math.round(b[1]), Math.round(b[2]), Math.round(b[3]), Proj.alpha(c, 0.15f));
                if (style.is("Рамка")) Proj.rect(ctx, b[0], b[1], b[2], b[3], c, 1);
                else Proj.corners(ctx, b[0], b[1], b[2], b[3], c, 1);

                if (health.get() && e instanceof LivingEntity le) {
                    float r = Math.max(0f, Math.min(1f, le.getHealth() / le.getMaxHealth()));
                    int x2 = Math.round(b[0]) - 3, x1 = x2 - 2, top = Math.round(b[1]), bot = Math.round(b[3]);
                    ctx.fill(x1, top, x2, bot, 0xAA000000);
                    int g = (int) (255 * r), rd = 255 - g;
                    ctx.fill(x1, bot - (int) ((bot - top) * r), x2, bot, 0xFF000000 | (rd << 16) | (g << 8) | 0x30);
                }
            }
        }
    }

    public static class Tracers extends Mod {
        private final Setting.Bool players = add(new Setting.Bool("Игроки", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Мобы", false));
        private final Setting.Bool animals = add(new Setting.Bool("Животные", false));
        private final Setting.Num range = add(new Setting.Num("Дальность", 64, 8, 200, 8));
        private final Setting.Mode from = add(new Setting.Mode("Откуда", "Низ экрана", "Низ экрана", "Центр"));
        private final Setting.Mode color = add(new Setting.Mode("Цвет", "Акцент", Util.PALETTE));
        private final Setting.Bool glow = add(new Setting.Bool("Свечение", true));

        public Tracers() { super("Tracers", Category.VISUAL); }

        @Override
        public void onHud(DrawContext ctx) {
            if (!Proj.ready) return;
            var me = mc.player;
            float ox = (float) Proj.width() / 2, oy = from.is("Центр") ? (float) Proj.height() / 2 : (float) Proj.height();
            int i = 0;
            for (Entity e : mc.world.getEntities()) {
                if (e == me || !(e instanceof LivingEntity)) continue;
                if (!Util.matches(e, players.get(), mobs.get(), animals.get()) && !Friends.listed(e)) continue;
                if (!players.get() && Friends.listed(e)) continue;
                if (e.distanceTo(me) > range.get()) continue;
                var l = e.getLerpedPos(Proj.pt);
                float[] p = Proj.project(l.x, l.y + e.getHeight() * 0.5, l.z);
                if (p == null) continue;
                int c = Friends.listed(e) ? 0xFF55AAFF : Util.palette(color.get(), i++ * 3);
                if (glow.get()) Proj.line(ctx, ox - 1, oy - 1, p[0] - 1, p[1] - 1, Proj.alpha(c, 0.25f), 3);
                Proj.line(ctx, ox, oy, p[0], p[1], c, 1);
            }
        }
    }

    public static class Nametags extends Mod {
        private final Setting.Bool players = add(new Setting.Bool("Игроки", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Мобы", false));
        private final Setting.Bool animals = add(new Setting.Bool("Животные", false));
        private final Setting.Num range = add(new Setting.Num("Дальность", 48, 8, 128, 8));
        private final Setting.Bool hp = add(new Setting.Bool("Здоровье", true));
        private final Setting.Bool dist = add(new Setting.Bool("Расстояние", true));

        public Nametags() { super("Nametags", Category.VISUAL); }

        @Override
        public void onHud(DrawContext ctx) {
            if (!Proj.ready) return;
            var me = mc.player;
            var tr = mc.textRenderer;
            for (Entity e : mc.world.getEntities()) {
                if (e == me || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
                boolean ok = (players.get() && e instanceof PlayerEntity) || (mobs.get() && e instanceof Monster)
                        || (animals.get() && e instanceof AnimalEntity);
                if (!ok || e.distanceTo(me) > range.get()) continue;
                var l = e.getLerpedPos(Proj.pt);
                float[] p = Proj.project(l.x, l.y + e.getHeight() + 0.35, l.z);
                if (p == null) continue;

                StringBuilder sb = new StringBuilder(le.getName().getString());
                if (hp.get()) sb.append("  ").append(Math.round(le.getHealth())).append(" HP");
                if (dist.get()) sb.append("  ").append(Math.round(e.distanceTo(me))).append("м");
                String t = sb.toString();
                int w = tr.getWidth(t) + 8, x = Math.round(p[0]) - w / 2, y = Math.round(p[1]) - 14;
                ctx.fill(x, y, x + w, y + 12, 0xA0101018);
                float r = Math.max(0f, Math.min(1f, le.getHealth() / le.getMaxHealth()));
                int g = (int) (255 * r), rd = 255 - g;
                ctx.fill(x, y + 11, x + (int) (w * r), y + 12, 0xFF000000 | (rd << 16) | (g << 8) | 0x30);
                ctx.drawText(tr, t, x + 4, y + 2, Friends.listed(e) ? 0xFF55AAFF : 0xFFFFFFFF, true);
            }
        }
    }

    /** Marks the current target (KillAura target or the entity you look at). */
    public static class TargetESP extends Mod {
        private final Setting.Mode style = add(new Setting.Mode("Стиль", "Кольцо", "Кольцо", "Бокс 3D", "Кольцо + бокс"));
        private final Setting.Num speed = add(new Setting.Num("Скорость", 1.5, 0.5, 4.0, 0.5));
        private final Setting.Num hold = add(new Setting.Num("Держать цель (сек)", 2, 1, 6, 1));
        private final Setting.Mode color = add(new Setting.Mode("Цвет", "Акцент", Util.PALETTE));
        private LivingEntity target;
        private long seen;

        public TargetESP() { super("TargetESP", Category.VISUAL); }

        @Override
        public void onTick() {
            long now = System.currentTimeMillis();
            KillAura ka = ModuleManager.get(KillAura.class);
            LivingEntity t = ka != null && ka.isEnabled() ? ka.target() : null;
            if (t == null && mc.crosshairTarget instanceof EntityHitResult h && h.getEntity() instanceof LivingEntity le) t = le;
            if (t != null && t.isAlive()) { target = t; seen = now; }
            if (target != null && (now - seen > hold.get() * 1000 || !target.isAlive())) target = null;
        }

        @Override
        public void onHud(DrawContext ctx) {
            if (!Proj.ready || target == null) return;
            Vec3d l = target.getLerpedPos(Proj.pt);
            double w = target.getWidth() * 0.85, h = target.getHeight();
            double t = System.currentTimeMillis() / 1000.0 * speed.get();
            if (!style.is("Бокс 3D")) {
                double k = Math.sin(t * 2) * 0.5 + 0.5;
                for (int i = 0; i < 3; i++) {
                    double kk = Math.max(0, Math.min(1, k - i * 0.08));
                    Proj.ring(ctx, l.x, l.y + h * kk, l.z, w, 28, t, Proj.alpha(Util.palette(color.get(), i * 4), 1f - i * 0.3f), 1 + (i == 0 ? 1 : 0), true);
                }
            }
            if (!style.is("Кольцо")) {
                Box b = target.getBoundingBox().offset(l.x - target.getX(), l.y - target.getY(), l.z - target.getZ());
                Proj.box3(ctx, b, Util.palette(color.get(), 0), 1, true);
            }
        }

        @Override protected void onDisable() { target = null; }
    }

    public static class ChestESP extends Mod {
        private final Setting.Num radius = add(new Setting.Num("Радиус поиска", 16, 8, 32, 4));
        private final Setting.Bool ender = add(new Setting.Bool("Эндер-сундуки", true));
        private final Setting.Bool shulker = add(new Setting.Bool("Шалкеры", true));
        private final Setting.Bool barrel = add(new Setting.Bool("Бочки", true));
        private final Setting.Mode color = add(new Setting.Mode("Цвет", "Оранжевый", Util.PALETTE));
        private final List<BlockPos> found = new ArrayList<>();
        private int t;

        public ChestESP() { super("ChestESP", Category.VISUAL); }

        @Override
        public void onTick() {
            if (t++ % 20 != 0) return;
            found.clear();
            var w = mc.world;
            BlockPos base = mc.player.getBlockPos();
            int r = radius.asInt();
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                BlockPos pos = base.add(x, y, z);
                Block b = w.getBlockState(pos).getBlock();
                if (b instanceof ChestBlock || (ender.get() && b instanceof EnderChestBlock)
                        || (shulker.get() && b instanceof ShulkerBoxBlock) || (barrel.get() && b instanceof BarrelBlock)) found.add(pos);
            }
        }

        @Override
        public void onHud(DrawContext ctx) {
            if (!Proj.ready) return;
            int i = 0;
            for (BlockPos p : found) {
                Box b = new Box(p).contract(0.06);
                Proj.box3(ctx, b, Util.palette(color.get(), i++ * 2), 1, true);
            }
        }

        @Override protected void onDisable() { found.clear(); }
    }

    /** Draws the AimAssist field-of-view circle in the middle of the screen. */
    public static class FovCircle extends Mod {
        private final Setting.Mode color = add(new Setting.Mode("Цвет", "Акцент", Util.PALETTE));
        private final Setting.Bool glow = add(new Setting.Bool("Свечение", true));

        public FovCircle() { super("FovCircle", Category.VISUAL); }

        @Override
        public void onHud(DrawContext ctx) {
            AimAssist aa = ModuleManager.get(AimAssist.class);
            if (!Proj.ready || aa == null || !aa.isEnabled() || aa.fovValue() >= 170) return;
            double r = Proj.scale() * Math.tan(Math.toRadians(aa.fovValue() / 2.0));
            double cx = Proj.width() / 2, cy = Proj.height() / 2;
            int n = 64;
            for (int i = 0; i < n; i++) {
                double a1 = 2 * Math.PI * i / n, a2 = 2 * Math.PI * (i + 1) / n;
                int c = Util.palette(color.get(), i);
                float x1 = (float) (cx + Math.cos(a1) * r), y1 = (float) (cy + Math.sin(a1) * r);
                float x2 = (float) (cx + Math.cos(a2) * r), y2 = (float) (cy + Math.sin(a2) * r);
                if (glow.get()) Proj.line(ctx, x1 - 1, y1 - 1, x2 - 1, y2 - 1, Proj.alpha(c, 0.25f), 3);
                Proj.line(ctx, x1, y1, x2, y2, c, 1);
            }
        }
    }

    public static class Fullbright extends Mod {
        public Fullbright() { super("Fullbright", Category.VISUAL); }

        @Override
        public void onTick() {
            var p = mc.player;
            var cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 400) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false, false));
            }
        }

        @Override
        protected void onDisable() {
            if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }

    public static class Zoom extends Mod {
        private final Setting.Num factor = add(new Setting.Num("Приближение (x)", 3.0, 1.5, 10.0, 0.5));
        private final Setting.Num smooth = add(new Setting.Num("Плавность", 0.3, 0.1, 1.0, 0.05));
        private int original = -1;
        private double cur;

        public Zoom() { super("Zoom", Category.VISUAL); }

        @Override
        public void onTick() {
            if (original < 0) { original = mc.options.getFov().getValue(); cur = original; }
            double target = original / factor.get();
            cur += (target - cur) * smooth.get();
            mc.options.getFov().setValue(Math.max(5, (int) Math.round(cur)));
        }

        @Override protected void onDisable() { restore(); }

        public void restore() {
            if (original > 0 && mc.options != null) mc.options.getFov().setValue(original);
            original = -1;
        }
    }

    public static class Hud extends Mod {
        private final Setting.Bool watermark = add(new Setting.Bool("Watermark", true));
        private final Setting.Bool arrayList = add(new Setting.Bool("Module list", true));
        private final Setting.Bool background = add(new Setting.Bool("Background", true));
        private final Setting.Bool coords = add(new Setting.Bool("Coordinates", true));
        private final Setting.Bool fps = add(new Setting.Bool("FPS", true));
        private final Setting.Bool rainbow = add(new Setting.Bool("Rainbow", false));

        public Hud() { super("HUD", Category.VISUAL); visible = false; }

        @Override
        public void onHud(DrawContext ctx) {
            var tr = mc.textRenderer;
            boolean rb = rainbow.get();

            if (watermark.get()) {
                String t = "Aurora";
                int x = 4;
                for (int i = 0; i < t.length(); i++) {
                    String ch = String.valueOf(t.charAt(i));
                    ctx.drawText(tr, ch, x, 4, Util.color(i * 2, rb), true);
                    x += tr.getWidth(ch);
                }
            }

            if (arrayList.get()) {
                record Row(Mod m, float a) {}
                List<Row> rows = new ArrayList<>();
                for (Mod m : ModuleManager.modules) {
                    float a = Anim.to("hud:" + m.name, m.isEnabled() && m.visible ? 1f : 0f, 12f, 0f);
                    if (a > 0.01f) rows.add(new Row(m, a));
                }
                rows.sort(Comparator.comparingInt((Row r) -> -tr.getWidth(r.m().name)));
                int sw = ctx.getScaledWindowWidth();
                float yf = 2;
                for (int i = 0; i < rows.size(); i++) {
                    Row r = rows.get(i);
                    String n = r.m().name;
                    int w = tr.getWidth(n) + 5;
                    int x = sw - (int) (w * Anim.ease(r.a()));
                    int y = (int) yf;
                    int c = Util.color(i, rb);
                    if (background.get()) ctx.fill(x - 2, y - 1, sw, y + 10, 0x90000000);
                    ctx.fill(sw - 1, y - 1, sw, y + 10, c);
                    ctx.drawText(tr, n, x, y, c, true);
                    yf += 11 * r.a();
                }
            }

            int bottom = ctx.getScaledWindowHeight() - 11;
            if (coords.get() && mc.player != null) {
                String s = String.format("XYZ: %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
                ctx.drawText(tr, s, 4, bottom, 0xFFFFFFFF, true);
                bottom -= 11;
            }
            if (fps.get()) ctx.drawText(tr, "FPS: " + mc.getCurrentFps(), 4, bottom, 0xFFFFFFFF, true);
        }
    }

    public static class Keystrokes extends HudBase {
        private final Setting.Bool mouse = add(new Setting.Bool("Показывать мышь", true));
        private final Setting.Bool cps = add(new Setting.Bool("Показывать CPS", true));
        private final Deque<Long> lmb = new ArrayDeque<>(), rmb = new ArrayDeque<>();
        private boolean lastL, lastR;

        public Keystrokes() { super("Keystrokes", 2, 55); }

        @Override
        public void onTick() {
            long now = System.currentTimeMillis();
            boolean l = mc.options.attackKey.isPressed(), r = mc.options.useKey.isPressed();
            if (l && !lastL) lmb.add(now);
            if (r && !lastR) rmb.add(now);
            lastL = l; lastR = r;
            while (!lmb.isEmpty() && now - lmb.peekFirst() > 1000) lmb.pollFirst();
            while (!rmb.isEmpty() && now - rmb.peekFirst() > 1000) rmb.pollFirst();
        }

        @Override
        public void onHud(DrawContext ctx) {
            int s = 18, g = 2, w = s * 3 + g * 2, h = s * 2 + g + (mouse.get() ? s + g : 0);
            int x = ox(ctx, w), y = oy(ctx, h);
            var o = mc.options;
            key(ctx, x + s + g, y, s, s, "W", o.forwardKey);
            key(ctx, x, y + s + g, s, s, "A", o.leftKey);
            key(ctx, x + s + g, y + s + g, s, s, "S", o.backKey);
            key(ctx, x + 2 * (s + g), y + s + g, s, s, "D", o.rightKey);
            if (mouse.get()) {
                int mw = (w - g) / 2, my = y + 2 * (s + g);
                key(ctx, x, my, mw, s, cps.get() ? "ЛКМ " + lmb.size() : "ЛКМ", o.attackKey);
                key(ctx, x + mw + g, my, mw, s, cps.get() ? "ПКМ " + rmb.size() : "ПКМ", o.useKey);
            }
        }

        private void key(DrawContext ctx, int x, int y, int w, int h, String t, KeyBinding k) {
            float a = Anim.to(k, k.isPressed() ? 1f : 0f, 22f);
            int c = Anim.lerpColor(0x90101018, 0xB0000000 | (Util.color(x / 8, false) & 0xFFFFFF), a);
            ctx.fill(x, y, x + w, y + h, c);
            var tr = mc.textRenderer;
            ctx.drawText(tr, t, x + (w - tr.getWidth(t)) / 2, y + (h - 8) / 2,
                    Anim.lerpColor(0xFFFFFFFF, 0xFF000000, a), false);
        }
    }

    public static class ArmorHud extends HudBase {
        private final Setting.Bool vertical = add(new Setting.Bool("Вертикально", true));
        private final Setting.Bool durability = add(new Setting.Bool("Прочность в %", true));
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        public ArmorHud() { super("ArmorHUD", 98, 60); }

        @Override
        public void onHud(DrawContext ctx) {
            var tr = mc.textRenderer;
            boolean v = vertical.get(), d = durability.get();
            int w = v ? 20 + (d ? 28 : 0) : 4 * 20 + 2;
            int h = v ? 4 * 18 + 2 : 18 + (d ? 10 : 0);
            int x = ox(ctx, w), y = oy(ctx, h);
            box(ctx, x, y, w, h);
            for (int i = 0; i < 4; i++) {
                ItemStack st = mc.player.getEquippedStack(SLOTS[i]);
                if (st.isEmpty()) continue;
                int ix = v ? x + 2 : x + 2 + i * 20, iy = v ? y + 1 + i * 18 : y + 1;
                ctx.drawItem(st, ix, iy);
                if (d && st.isDamageable()) {
                    int pct = (st.getMaxDamage() - st.getDamage()) * 100 / st.getMaxDamage();
                    int c = pct > 60 ? 0xFF55FF55 : pct > 30 ? 0xFFFFDD55 : 0xFFFF5555;
                    String t = pct + "%";
                    if (v) ctx.drawText(tr, t, ix + 20, iy + 4, c, true);
                    else ctx.drawText(tr, t, ix, iy + 18, c, true);
                }
            }
        }
    }

    public static class PotionHud extends HudBase {
        public PotionHud() { super("PotionHUD", 98, 2); }

        @Override
        public void onHud(DrawContext ctx) {
            var tr = mc.textRenderer;
            List<String> lines = new ArrayList<>();
            for (var e : mc.player.getStatusEffects()) {
                String name = e.getEffectType().value().getName().getString() + " " + (e.getAmplifier() + 1);
                int s = e.getDuration() / 20;
                lines.add(name + "  " + (e.isInfinite() ? "∞" : String.format("%d:%02d", s / 60, s % 60)));
            }
            if (lines.isEmpty()) return;
            int w = 0;
            for (String l : lines) w = Math.max(w, tr.getWidth(l));
            w += 10;
            int h = lines.size() * 11 + 4;
            int x = ox(ctx, w), y = oy(ctx, h);
            box(ctx, x, y, w, h);
            for (int i = 0; i < lines.size(); i++) ctx.drawText(tr, lines.get(i), x + 5, y + 3 + i * 11, 0xFFFFFFFF, true);
        }
    }

    public static class TargetHud extends HudBase {
        private final Setting.Num hold = add(new Setting.Num("Держать цель (сек)", 3, 1, 10, 1));
        private LivingEntity target;
        private long seen;
        private float shown;

        public TargetHud() { super("TargetHUD", 50, 62); }

        @Override
        public void onTick() {
            long now = System.currentTimeMillis();
            if (mc.crosshairTarget instanceof EntityHitResult h && h.getEntity() instanceof LivingEntity le && le.isAlive()) {
                if (target != le) shown = le.getHealth() / le.getMaxHealth();
                target = le;
                seen = now;
            }
            if (target != null && (now - seen > hold.get() * 1000 || !target.isAlive())) target = null;
        }

        @Override
        public void onHud(DrawContext ctx) {
            if (target == null) return;
            var tr = mc.textRenderer;
            int w = 130, h = 34;
            int x = ox(ctx, w), y = oy(ctx, h);
            box(ctx, x, y, w, h);
            float ratio = Math.max(0f, Math.min(1f, target.getHealth() / target.getMaxHealth()));
            shown += (ratio - shown) * 0.2f;
            ctx.drawText(tr, target.getName().getString(), x + 6, y + 5, 0xFFFFFFFF, true);
            int bx = x + 6, by = y + 20, bw = w - 12;
            ctx.fill(bx, by, bx + bw, by + 6, 0xFF222230);
            int g = (int) (255 * shown), r = 255 - g;
            ctx.fill(bx, by, bx + (int) (bw * shown), by + 6, 0xFF000000 | (r << 16) | (g << 8) | 0x40);
            String t = String.format("%.1f / %.1f", target.getHealth(), target.getMaxHealth());
            ctx.drawText(tr, t, x + w - tr.getWidth(t) - 6, y + 5, 0xFFCCCCCC, false);
        }
    }

    public static class Radar extends HudBase {
        private final Setting.Num size = add(new Setting.Num("Размер", 90, 60, 160, 10));
        private final Setting.Num range = add(new Setting.Num("Дальность (блоки)", 32, 8, 96, 4));
        private final Setting.Bool players = add(new Setting.Bool("Игроки", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Мобы", true));
        private final Setting.Bool animals = add(new Setting.Bool("Животные", false));

        public Radar() { super("Radar", 1, 98); }

        @Override
        public void onHud(DrawContext ctx) {
            var p = mc.player;
            int s = size.asInt();
            int x = ox(ctx, s), y = oy(ctx, s);
            box(ctx, x, y, s, s);
            int cx = x + s / 2, cy = y + s / 2;
            ctx.fill(x, cy, x + s, cy + 1, 0x30FFFFFF);
            ctx.fill(cx, y, cx + 1, y + s, 0x30FFFFFF);

            double scale = (s / 2.0 - 2) / range.get();
            double yaw = Math.toRadians(p.getYaw());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);

            for (Entity e : mc.world.getEntities()) {
                if (e == p || !(e instanceof LivingEntity)) continue;
                int c;
                if (players.get() && e instanceof PlayerEntity) c = Friends.listed(e) ? 0xFF55AAFF : 0xFFFF5555;
                else if (mobs.get() && e instanceof Monster) c = 0xFFFFAA33;
                else if (animals.get() && e instanceof AnimalEntity) c = 0xFF55FF55;
                else continue;
                double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
                if (Math.hypot(dx, dz) > range.get()) continue;
                int px = cx + (int) ((dx * rx + dz * rz) * scale);
                int py = cy + (int) (-(dx * fx + dz * fz) * scale);
                ctx.fill(px - 1, py - 1, px + 2, py + 2, c);
            }
            ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        }
    }

    public static class InfoHud extends HudBase {
        private final Setting.Bool ping = add(new Setting.Bool("Пинг", true));
        private final Setting.Bool bps = add(new Setting.Bool("Скорость (блоков/сек)", true));
        private final Setting.Bool dir = add(new Setting.Bool("Направление", true));
        private final Setting.Bool time = add(new Setting.Bool("Время", true));
        private final Setting.Bool server = add(new Setting.Bool("Сервер", false));
        private double lx, lz, speed;

        public InfoHud() { super("InfoHUD", 2, 30); }

        @Override
        public void onTick() {
            var p = mc.player;
            speed = Math.hypot(p.getX() - lx, p.getZ() - lz) * 20.0;
            lx = p.getX(); lz = p.getZ();
        }

        @Override
        public void onHud(DrawContext ctx) {
            var tr = mc.textRenderer;
            List<String> l = new ArrayList<>();
            if (ping.get()) {
                int ms = 0;
                var nh = mc.getNetworkHandler();
                if (nh != null) { var e = nh.getPlayerListEntry(mc.player.getUuid()); if (e != null) ms = e.getLatency(); }
                l.add("Пинг: " + ms + " мс");
            }
            if (bps.get()) l.add(String.format("Скорость: %.1f б/с", speed));
            if (dir.get()) l.add("Куда: " + switch (mc.player.getHorizontalFacing()) {
                case NORTH -> "Север (-Z)";
                case SOUTH -> "Юг (+Z)";
                case EAST -> "Восток (+X)";
                case WEST -> "Запад (-X)";
                default -> "?";
            });
            if (time.get()) l.add("Время: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            if (server.get()) l.add("Сервер: " + (mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "одиночная игра"));
            if (l.isEmpty()) return;

            int w = 0;
            for (String s : l) w = Math.max(w, tr.getWidth(s));
            w += 10;
            int h = l.size() * 11 + 4;
            int x = ox(ctx, w), y = oy(ctx, h);
            box(ctx, x, y, w, h);
            for (int i = 0; i < l.size(); i++) ctx.drawText(tr, l.get(i), x + 5, y + 3 + i * 11, 0xFFFFFFFF, true);
        }
    }

    public static class BindList extends HudBase {
        public BindList() { super("BindList", 98, 35); }

        @Override
        public void onHud(DrawContext ctx) {
            var tr = mc.textRenderer;
            List<Mod> l = new ArrayList<>();
            for (Mod m : ModuleManager.modules) if (m.key > 0) l.add(m);
            if (l.isEmpty()) return;
            int w = 60;
            List<String> t = new ArrayList<>();
            for (Mod m : l) {
                String kn = GLFW.glfwGetKeyName(m.key, 0);
                String s = "[" + (kn != null ? kn.toUpperCase() : "#" + m.key) + "] " + m.name;
                t.add(s);
                w = Math.max(w, tr.getWidth(s) + 10);
            }
            int h = l.size() * 11 + 4;
            int x = ox(ctx, w), y = oy(ctx, h);
            box(ctx, x, y, w, h);
            for (int i = 0; i < l.size(); i++)
                ctx.drawText(tr, t.get(i), x + 5, y + 3 + i * 11, l.get(i).isEnabled() ? 0xFFFFFFFF : 0xFF777788, true);
        }
    }

    public static class Crosshair extends Mod {
        private final Setting.Num size = add(new Setting.Num("Длина линий", 5, 1, 14, 1));
        private final Setting.Num gap = add(new Setting.Num("Зазор", 3, 0, 10, 1));
        private final Setting.Num thick = add(new Setting.Num("Толщина", 1, 1, 4, 1));
        private final Setting.Bool dot = add(new Setting.Bool("Точка в центре", false));
        private final Setting.Bool outline = add(new Setting.Bool("Обводка", true));
        private final Setting.Mode color = add(new Setting.Mode("Цвет", "Акцент", Util.PALETTE));

        public Crosshair() { super("Crosshair", Category.VISUAL); }

        /** Called from the HUD element that replaces the vanilla crosshair. */
        public void draw(DrawContext ctx) {
            int cx = ctx.getScaledWindowWidth() / 2, cy = ctx.getScaledWindowHeight() / 2;
            int s = size.asInt(), g = gap.asInt(), t = thick.asInt(), h = t / 2;
            int c = Util.palette(color.get(), 0);
            bar(ctx, cx - g - s, cy - h, cx - g, cy - h + t, c);
            bar(ctx, cx + g, cy - h, cx + g + s, cy - h + t, c);
            bar(ctx, cx - h, cy - g - s, cx - h + t, cy - g, c);
            bar(ctx, cx - h, cy + g, cx - h + t, cy + g + s, c);
            if (dot.get()) bar(ctx, cx - h, cy - h, cx - h + t, cy - h + t, c);
        }

        private void bar(DrawContext ctx, int x1, int y1, int x2, int y2, int c) {
            if (outline.get()) ctx.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF000000);
            ctx.fill(x1, y1, x2, y2, c);
        }
    }

    public static class Notifications extends Mod {
        private final Setting.Num seconds = add(new Setting.Num("Длительность (сек)", 2, 1, 6, 1));
        private record Entry(String text, boolean on, long time) {}
        private static final List<Entry> LIST = new ArrayList<>();

        public Notifications() { super("Notifications", Category.VISUAL); }

        public static void push(String module, boolean on) { pushText(module + (on ? " включён" : " выключен"), on); }

        public static void pushText(String text, boolean good) {
            Notifications n = ModuleManager.get(Notifications.class);
            if (n == null || !n.isEnabled()) return;
            LIST.add(new Entry(text, good, System.currentTimeMillis()));
            if (LIST.size() > 6) LIST.remove(0);
        }

        @Override
        public void onHud(DrawContext ctx) {
            var tr = mc.textRenderer;
            long now = System.currentTimeMillis();
            long life = seconds.asInt() * 1000L;
            LIST.removeIf(e -> now - e.time() > life);
            int sw = ctx.getScaledWindowWidth(), y = ctx.getScaledWindowHeight() - 40;
            for (int i = LIST.size() - 1; i >= 0; i--) {
                Entry e = LIST.get(i);
                long age = now - e.time();
                int w = tr.getWidth(e.text()) + 16;
                double in = Math.min(1.0, age / 200.0), out = Math.min(1.0, (life - age) / 250.0);
                int x = sw - (int) (w * Math.min(in, out)) - 4;
                ctx.fill(x, y, x + w, y + 16, 0xC0101018);
                ctx.fill(x, y, x + 2, y + 16, e.on() ? 0xFF55FF77 : 0xFFFF5566);
                ctx.drawText(tr, e.text(), x + 8, y + 4, 0xFFFFFFFF, true);
                y -= 19;
            }
        }
    }

    public static class InventoryHud extends HudBase {
        public InventoryHud() { super("InventoryHUD", 50, 98); }

        @Override
        public void onHud(DrawContext ctx) {
            int w = 9 * 18 + 4, h = 3 * 18 + 4;
            int x = ox(ctx, w), y = oy(ctx, h);
            box(ctx, x, y, w, h);
            var inv = mc.player.getInventory();
            for (int i = 0; i < 27; i++) {
                ItemStack st = inv.getStack(9 + i);
                if (st.isEmpty()) continue;
                int ix = x + 2 + (i % 9) * 18, iy = y + 2 + (i / 9) * 18;
                ctx.drawItem(st, ix, iy);
                ctx.drawStackOverlay(mc.textRenderer, st, ix, iy);
            }
        }
    }

    public static class ItemCounter extends HudBase {
        private final Setting.Bool totem = add(new Setting.Bool("Тотемы", true));
        private final Setting.Bool gapple = add(new Setting.Bool("Золотые яблоки", true));
        private final Setting.Bool pearl = add(new Setting.Bool("Жемчуг края", true));
        private final Setting.Bool crystal = add(new Setting.Bool("Кристаллы края", false));
        private final Setting.Bool xp = add(new Setting.Bool("Пузырьки опыта", false));

        public ItemCounter() { super("ItemCounter", 98, 85); }

        @Override
        public void onHud(DrawContext ctx) {
            List<Item> items = new ArrayList<>();
            if (totem.get()) items.add(Items.TOTEM_OF_UNDYING);
            if (gapple.get()) items.add(Items.GOLDEN_APPLE);
            if (pearl.get()) items.add(Items.ENDER_PEARL);
            if (crystal.get()) items.add(Items.END_CRYSTAL);
            if (xp.get()) items.add(Items.EXPERIENCE_BOTTLE);
            if (items.isEmpty()) return;

            var inv = mc.player.getInventory();
            var tr = mc.textRenderer;
            int w = 50, h = items.size() * 18 + 2;
            int x = ox(ctx, w), y = oy(ctx, h);
            box(ctx, x, y, w, h);
            for (int i = 0; i < items.size(); i++) {
                int count = 0;
                for (int s = 0; s < inv.size(); s++) {
                    ItemStack st = inv.getStack(s);
                    if (st.isOf(items.get(i))) count += st.getCount();
                }
                int iy = y + 1 + i * 18;
                ctx.drawItem(new ItemStack(items.get(i)), x + 2, iy);
                ctx.drawText(tr, "x" + count, x + 22, iy + 5, count == 0 ? 0xFFFF6666 : 0xFFFFFFFF, true);
            }
        }
    }
}
