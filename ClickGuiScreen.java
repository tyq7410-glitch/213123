package dev.aurora.gui;

import dev.aurora.core.*;
import dev.aurora.module.Misc.Interface;
import dev.aurora.module.Visuals.Notifications;
import dev.aurora.util.Anim;
import dev.aurora.util.Util;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout: categories | modules | settings of the selected module.
 * "Помощник" tab: presets, profiles, config tips and the optional AI chat.
 * Everything is animated (open, hover, switches, sliders, scrolling).
 */
public class ClickGuiScreen extends Screen {
    private static final int W = 440, H = 270, SIDE = 92, LIST = 150;
    private static final int CAT = 0, MOD_SEL = 1, MOD_TOG = 2, SET = 3, BIND = 4, VIS = 5,
            PRESET = 6, PSAVE = 7, PLOAD = 8, SEND = 9;

    private static Category selCat = Category.COMBAT;
    private static Mod selMod;
    private static float scrollListT, scrollSetT, maxList, maxSet;

    private record Hit(int x, int y, int w, int h, int kind, Object a) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();
    private Hit sliding;
    private Mod binding;
    private TextFieldWidget askField;
    private int wx, wy, ww, wh;
    private List<String> tips = List.of();
    private long tipsAt;
    private int lastChat;

    public ClickGuiScreen() { super(Text.literal("Aurora")); }

    @Override
    protected void init() {
        askField = new TextFieldWidget(textRenderer, 0, 0, 100, 16, Text.literal(""));
        askField.setMaxLength(400);
        askField.setPlaceholder(Text.literal("Спроси: как настроить визуалы?"));
        askField.visible = false;
        addDrawableChild(askField);
    }

    // ------------------------------------------------------------------ helpers
    private static double frac(double v) { return v - Math.floor(v); }

    private void drawBackground(DrawContext ctx, float open) {
        Interface ui = ModuleManager.get(Interface.class);
        int a = ((int) (ui.opacity.get() / 100.0 * 255 * open)) << 24;
        double t = ui.animate.get() ? System.currentTimeMillis() / 8000.0 : 0;
        switch (ui.background.get()) {
            case "Градиент" -> {
                float h1 = (float) frac(0.62 + 0.12 * Math.sin(t * 6.28));
                float h2 = (float) frac(h1 + 0.18);
                ctx.fillGradient(0, 0, width, height,
                        a | MathHelper.hsvToRgb(h1, 0.7f, 0.55f), a | MathHelper.hsvToRgb(h2, 0.8f, 0.30f));
            }
            case "Закат" -> ctx.fillGradient(0, 0, width, height, a | 0x1B1040, a | 0xE0507A);
            case "Океан" -> ctx.fillGradient(0, 0, width, height, a | 0x071A2B, a | 0x1F7A8C);
            case "Неон" -> ctx.fillGradient(0, 0, width, height, a | 0x0B0B1E, a | 0x7A1FD1);
            case "Свой цвет" -> ctx.fill(0, 0, width, height,
                    a | (ui.red.asInt() << 16) | (ui.green.asInt() << 8) | ui.blue.asInt());
            default -> ctx.fill(0, 0, width, height, a);
        }
        if (ui.particles.get()) {
            double now = System.currentTimeMillis() / 1000.0;
            int pa = ((int) (0x55 * open)) << 24;
            for (int i = 0; i < 60; i++) {
                double sp = 0.01 + frac(i * 0.3247) * 0.03;
                int x = (int) (frac(i * 0.618034 + now * sp * 0.4) * width);
                int y = (int) (frac(i * 0.754877 - now * sp) * height);
                int sz = 1 + (i % 3 == 0 ? 1 : 0);
                ctx.fill(x, y, x + sz, y + sz, pa | 0xFFFFFF);
            }
        }
    }

    private void pill(DrawContext ctx, int x, int y, Object key, boolean on) {
        float k = Anim.to(key, on ? 1f : 0f, 16f);
        ctx.fill(x, y, x + 22, y + 10, Anim.lerpColor(0xFF30303F, Util.color(x / 8, false), k));
        int kx = x + 1 + Math.round(12 * k);
        ctx.fill(kx, y + 1, kx + 8, y + 9, 0xFFFFFFFF);
    }

    private void button(DrawContext ctx, int x, int y, int w, int h, String text, Object key, int mx, int my) {
        float hv = Anim.to(key, mx >= x && mx < x + w && my >= y && my < y + h ? 1f : 0f, 16f);
        ctx.fill(x, y, x + w, y + h, Anim.lerpColor(0xFF1E1E2C, 0xFF2C2C44, hv));
        ctx.fill(x, y + h - 1, x + w, y + h, Anim.lerpColor(0x00000000, Util.color(x / 8, false), hv));
        TextRenderer tr = textRenderer;
        ctx.drawText(tr, text, x + (w - tr.getWidth(text)) / 2, y + (h - 8) / 2 + 1, 0xFFFFFFFF, true);
    }

    private static String keyName(int key) {
        if (key <= 0) return "—";
        String kn = GLFW.glfwGetKeyName(key, 0);
        return kn != null ? kn.toUpperCase() : "#" + key;
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        Anim.frame();
        hits.clear();
        TextRenderer tr = textRenderer;

        float open = Anim.ease((System.currentTimeMillis() - openedAt) / 260f);
        drawBackground(ctx, open);

        ww = Math.min(W, width - 8);
        wh = Math.min(H, height - 8);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2 + (int) ((1 - open) * 16);

        ctx.fill(wx - 1, wy - 1, wx + ww + 1, wy + wh + 1, 0xFF000000);
        ctx.fill(wx, wy, wx + ww, wy + wh, 0xF0111118);
        for (int i = 0; i < ww; i += 4) ctx.fill(wx + i, wy, wx + i + 4, wy + 2, Util.color(i / 6, false));

        // ---- sidebar
        ctx.fill(wx, wy + 2, wx + SIDE, wy + wh, 0xFF0D0D14);
        String logo = "Aurora";
        int lxp = wx + 10;
        for (int i = 0; i < logo.length(); i++) {
            String ch = String.valueOf(logo.charAt(i));
            ctx.drawText(tr, ch, lxp, wy + 10, Util.color(i * 2, false), true);
            lxp += tr.getWidth(ch);
        }
        ctx.drawText(tr, "Cheat Client 1.21.11", wx + 10, wy + 21, 0xFF666677, false);

        int cy = wy + 40;
        for (Category c : Category.values()) {
            boolean hover = mx >= wx && mx < wx + SIDE && my >= cy && my < cy + 20;
            float sel = Anim.to("cat:" + c, c == selCat ? 1f : 0f, 14f);
            float hv = Anim.to("cath:" + c, hover ? 1f : 0f, 16f);
            ctx.fill(wx, cy, wx + SIDE, cy + 20, Anim.lerpColor(Anim.lerpColor(0xFF0D0D14, 0xFF15151F, hv), 0xFF1C1C2C, sel));
            int bar = (int) (20 * sel);
            ctx.fill(wx, cy + (20 - bar) / 2, wx + 2, cy + (20 - bar) / 2 + bar, Util.color(c.ordinal() * 3, false));
            ctx.drawText(tr, c.label, wx + 14 + (int) (3 * sel), cy + 6,
                    Anim.lerpColor(0xFF9A9AAA, 0xFFFFFFFF, Math.max(sel, hv * 0.6f)), true);
            hits.add(new Hit(wx, cy, SIDE, 20, CAT, c));
            cy += 22;
        }

        int lx = wx + SIDE;
        int sx = lx + LIST, sw = wx + ww - sx;
        float cs = Anim.to("catSwitch", 1f, 12f, 1f);

        if (selCat == Category.ASSISTANT) renderAssistant(ctx, mx, my, delta, lx, sx, sw, cs);
        else {
            askField.visible = false;
            askField.setFocused(false);
            renderModules(ctx, mx, my, lx, sx, sw, cs);
        }

        ctx.drawText(tr, "ЛКМ — выбрать / переключить   Колесо — прокрутка   RShift — закрыть",
                wx, wy + wh + 4, Anim.lerpColor(0x00999AAA, 0xFF9999AA, open), true);

        int fade = (int) ((1 - open) * 255);
        if (fade > 0) ctx.fill(wx - 1, wy - 1, wx + ww + 1, wy + wh + 1, fade << 24);
    }

    private void renderModules(DrawContext ctx, int mx, int my, int lx, int sx, int sw, float cs) {
        TextRenderer tr = textRenderer;
        int ly = wy + 2, lw = LIST;
        ctx.fill(lx, ly, lx + lw, wy + wh, 0xFF14141E);
        ctx.drawText(tr, selCat.label, lx + 10, ly + 8, 0xFFFFFFFF, true);
        int ltop = ly + 24, lbot = wy + wh - 4;

        List<Mod> mods = ModuleManager.in(selCat);
        if (selMod == null || selMod.category != selCat) selMod = mods.isEmpty() ? null : mods.get(0);

        maxList = Math.max(0, mods.size() * 18 - (lbot - ltop));
        scrollListT = MathHelper.clamp(scrollListT, 0, maxList);
        float scrollList = Anim.to("scrollList", scrollListT, 18f);

        ctx.enableScissor(lx, ltop, lx + lw, lbot);
        int ry = ltop - Math.round(scrollList);
        int slide = (int) ((1 - cs) * 16);
        for (Mod m : mods) {
            boolean hover = mx >= lx && mx < lx + lw && my >= ry && my < ry + 17 && my >= ltop && my < lbot;
            float hv = Anim.to("h:" + m.name, hover ? 1f : 0f, 16f);
            float sel = Anim.to("s:" + m.name, m == selMod ? 1f : 0f, 14f);
            float en = Anim.to("e:" + m.name, m.isEnabled() ? 1f : 0f, 14f);
            int bg = Anim.lerpColor(Anim.lerpColor(0xFF14141E, 0xFF1B1B2A, hv), 0xFF222236, sel);
            ctx.fill(lx + 4 + slide, ry, lx + lw - 4, ry + 17, bg);
            int bar = (int) (17 * en);
            ctx.fill(lx + 4 + slide, ry + (17 - bar) / 2, lx + 6 + slide, ry + (17 - bar) / 2 + bar, Util.color(ry / 8, false));
            ctx.drawText(tr, m.name, lx + 12 + slide, ry + 5,
                    Anim.lerpColor(0xFFA0A0B0, 0xFFFFFFFF, Math.max(en, hv * 0.5f)), true);
            pill(ctx, lx + lw - 32, ry + 3, m, m.isEnabled());
            if (ry + 17 > ltop && ry < lbot) {
                hits.add(new Hit(lx + 4, ry, lw - 8, 17, MOD_SEL, m));
                hits.add(new Hit(lx + lw - 32, ry + 2, 22, 12, MOD_TOG, m));
            }
            ry += 18;
        }
        ctx.disableScissor();

        // ---- settings of the selected module
        ctx.fill(sx, wy + 2, wx + ww, wy + wh, 0xFF111118);
        if (selMod == null) return;
        Mod m = selMod;
        int top = wy + 10, bottom = wy + wh - 4;
        float ss = Anim.to("setSwitch", 1f, 12f, 1f);
        int sl = (int) ((1 - ss) * 12);
        scrollSetT = MathHelper.clamp(scrollSetT, 0, maxSet);
        float scrollSet = Anim.to("scrollSet", scrollSetT, 18f);

        ctx.enableScissor(sx, wy + 2, wx + ww, wy + wh);
        int y = top - Math.round(scrollSet);
        int startY = y;
        int x0 = sx + sl;

        ctx.drawText(tr, m.name, x0 + 10, y, 0xFFFFFFFF, true);
        String st = m.isEnabled() ? "Включён" : "Выключен";
        ctx.drawText(tr, st, wx + ww - tr.getWidth(st) - 10, y,
                Anim.lerpColor(0xFF888899, Util.color(0, false), Anim.to("e:" + m.name, m.isEnabled() ? 1f : 0f, 14f)), true);
        y += 14;
        for (OrderedText line : tr.wrapLines(Text.literal(Lang.module(m.name)), sw - 20)) {
            ctx.drawText(tr, line, x0 + 10, y, 0xFF9AA0B5, false);
            y += 10;
        }
        y += 6;

        ctx.fill(x0 + 8, y, sx + sw - 8, y + 16, 0xFF1A1A26);
        ctx.drawText(tr, "Бинд (клавиша)", x0 + 14, y + 4, 0xFFCCCCCC, false);
        String bk = binding == m ? "нажми клавишу..." : "[" + keyName(m.key) + "]";
        ctx.drawText(tr, bk, sx + sw - 14 - tr.getWidth(bk), y + 4, Util.color(2, false), false);
        if (y + 16 > wy && y < bottom) hits.add(new Hit(sx + 8, y, sw - 16, 16, BIND, m));
        y += 18;

        if (m != ModuleManager.get(Interface.class)) {
            ctx.fill(x0 + 8, y, sx + sw - 8, y + 16, 0xFF1A1A26);
            ctx.drawText(tr, "Показывать в списке", x0 + 14, y + 4, 0xFFCCCCCC, false);
            pill(ctx, sx + sw - 36, y + 3, "vis:" + m.name, m.visible);
            if (y + 16 > wy && y < bottom) hits.add(new Hit(sx + 8, y, sw - 16, 16, VIS, m));
            y += 18;
        }

        if (!m.settings.isEmpty()) {
            ctx.drawText(tr, "Настройки", x0 + 10, y + 2, 0xFF666677, false);
            y += 14;
        }
        for (Setting<?> s : m.settings) {
            String label = Lang.setting(s.name);
            int h;
            if (s instanceof Setting.Bool b) {
                h = 16;
                ctx.fill(x0 + 8, y, sx + sw - 8, y + h, 0xFF1A1A26);
                ctx.drawText(tr, label, x0 + 14, y + 4, 0xFFCCCCCC, false);
                pill(ctx, sx + sw - 36, y + 3, s, b.get());
            } else if (s instanceof Setting.Num n) {
                h = 26;
                ctx.fill(x0 + 8, y, sx + sw - 8, y + h, 0xFF1A1A26);
                ctx.drawText(tr, label, x0 + 14, y + 4, 0xFFCCCCCC, false);
                String v = n.display();
                ctx.drawText(tr, v, sx + sw - 14 - tr.getWidth(v), y + 4, Util.color(4, false), false);
                int tx = x0 + 14, tw = sw - 28;
                float t = (float) ((n.get() - n.min) / (n.max - n.min));
                float shown = Anim.to(s, t, 18f);
                ctx.fill(tx, y + 17, tx + tw, y + 21, 0xFF30303F);
                ctx.fill(tx, y + 17, tx + (int) (tw * shown), y + 21, Util.color(6, false));
                ctx.fill(tx + (int) (tw * shown) - 1, y + 15, tx + (int) (tw * shown) + 2, y + 23, 0xFFFFFFFF);
            } else {
                Setting.Mode md = (Setting.Mode) s;
                h = 16;
                ctx.fill(x0 + 8, y, sx + sw - 8, y + h, 0xFF1A1A26);
                ctx.drawText(tr, label, x0 + 14, y + 4, 0xFFCCCCCC, false);
                String v = "‹ " + Lang.value(md.get()) + " ›";
                ctx.drawText(tr, v, sx + sw - 14 - tr.getWidth(v), y + 4, Util.color(4, false), false);
            }
            if (y + h > wy && y < bottom) {
                if (s instanceof Setting.Num) hits.add(new Hit(sx + 14, y, sw - 28, h, SET, s));
                else hits.add(new Hit(sx + 8, y, sw - 16, h, SET, s));
            }
            y += h + 2;
        }
        maxSet = Math.max(0, (y - startY) + 6 - (bottom - top));
        ctx.disableScissor();
    }

    // ------------------------------------------------------------------ assistant tab
    private void renderAssistant(DrawContext ctx, int mx, int my, float delta, int lx, int sx, int sw, float cs) {
        TextRenderer tr = textRenderer;
        int slide = (int) ((1 - cs) * 16);

        // left column: presets + profiles
        ctx.fill(lx, wy + 2, lx + LIST, wy + wh, 0xFF14141E);
        int y = wy + 10;
        ctx.drawText(tr, "Быстрые пресеты", lx + 10 + slide, y, 0xFFFFFFFF, true);
        y += 14;
        for (int i = 0; i < Advisor.PRESETS.length; i++) {
            button(ctx, lx + 8 + slide, y, LIST - 16, 20, Advisor.PRESETS[i], "pre:" + i, mx, my);
            hits.add(new Hit(lx + 8, y, LIST - 16, 20, PRESET, i));
            y += 24;
        }
        y += 4;
        ctx.drawText(tr, "Профили настроек", lx + 10 + slide, y, 0xFFFFFFFF, true);
        y += 14;
        for (int i = 1; i <= 3; i++) {
            String lab = "Профиль " + i + (Config.profileExists(i) ? " •" : "");
            ctx.drawText(tr, lab, lx + 10 + slide, y + 5, 0xFFCCCCCC, false);
            button(ctx, lx + LIST - 78, y, 34, 18, "Сохр.", "ps:" + i, mx, my);
            hits.add(new Hit(lx + LIST - 78, y, 34, 18, PSAVE, i));
            button(ctx, lx + LIST - 40, y, 32, 18, "Загр.", "pl:" + i, mx, my);
            hits.add(new Hit(lx + LIST - 40, y, 32, 18, PLOAD, i));
            y += 22;
        }

        // right column: tips + chat
        ctx.fill(sx, wy + 2, wx + ww, wy + wh, 0xFF111118);
        long now = System.currentTimeMillis();
        if (now - tipsAt > 1000) { tips = Advisor.tips(); tipsAt = now; }

        int inTop = wy + wh - 24;
        int top = wy + 10, bottom = inTop - 4;
        scrollSetT = MathHelper.clamp(scrollSetT, 0, maxSet);
        if (AiAssistant.chat.size() != lastChat) { lastChat = AiAssistant.chat.size(); scrollSetT = 99999; }
        float scroll = Anim.to("scrollSet", scrollSetT, 18f);

        ctx.enableScissor(sx, wy + 2, wx + ww, bottom);
        int cy = top - Math.round(scroll), startY = cy;
        int tx = sx + 10 + slide, tw = sw - 20;

        ctx.drawText(tr, "Советы по твоей настройке", tx, cy, 0xFFFFFFFF, true);
        cy += 13;
        for (String tip : tips) {
            ctx.fill(tx - 4, cy + 1, tx - 2, cy + 8, Util.color(cy / 8, false));
            for (OrderedText l : tr.wrapLines(Text.literal(tip), tw)) {
                ctx.drawText(tr, l, tx, cy, 0xFFB5BACD, false);
                cy += 10;
            }
            cy += 4;
        }
        cy += 6;
        ctx.drawText(tr, "Помощник Claude", tx, cy, 0xFFFFFFFF, true);
        cy += 13;
        if (!AiAssistant.hasKey() && AiAssistant.chat.isEmpty()) {
            String info = "ИИ-чат включится, когда ты положишь свой API-ключ Anthropic в файл config/aurora_ai_key.txt. "
                    + "Без ключа работают советы и пресеты. Помощник видит твои настройки и подсказывает значения.";
            for (OrderedText l : tr.wrapLines(Text.literal(info), tw)) { ctx.drawText(tr, l, tx, cy, 0xFF8A8FA5, false); cy += 10; }
        }
        for (String[] msg : AiAssistant.chat) {
            boolean user = msg[0].equals("user");
            String pre = user ? "Ты: " : "Claude: ";
            for (OrderedText l : tr.wrapLines(Text.literal(pre + msg[1]), tw)) {
                ctx.drawText(tr, l, tx, cy, user ? Util.color(3, false) : 0xFFFFFFFF, false);
                cy += 10;
                pre = "";
            }
            cy += 4;
        }
        if (AiAssistant.busy) {
            String dots = ".".repeat(1 + (int) ((System.currentTimeMillis() / 350) % 3));
            ctx.drawText(tr, "Claude думает" + dots, tx, cy, 0xFF8A8FA5, false);
            cy += 12;
        }
        if (!AiAssistant.error.isEmpty()) {
            for (OrderedText l : tr.wrapLines(Text.literal(AiAssistant.error), tw)) { ctx.drawText(tr, l, tx, cy, 0xFFFF6677, false); cy += 10; }
        }
        maxSet = Math.max(0, (cy - startY) + 6 - (bottom - top));
        ctx.disableScissor();

        // input row
        ctx.fill(sx, inTop - 3, wx + ww, inTop - 2, 0xFF1E1E2C);
        askField.visible = true;
        askField.setPosition(sx + 8, inTop + 1);
        askField.setWidth(sw - 16 - 58);
        askField.render(ctx, mx, my, delta);
        button(ctx, sx + sw - 8 - 54, inTop, 54, 18, "Спросить", "send", mx, my);
        hits.add(new Hit(sx + sw - 8 - 54, inTop, 54, 18, SEND, null));
    }

    private void send() {
        String q = askField.getText().trim();
        if (q.isEmpty() || AiAssistant.busy) return;
        askField.setText("");
        AiAssistant.ask(q);
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int b = click.button();

        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.in(mx, my)) continue;
            switch (h.kind()) {
                case CAT -> {
                    if (selCat != h.a()) {
                        selCat = (Category) h.a();
                        selMod = null;
                        scrollListT = scrollSetT = 0;
                        Anim.set("scrollList", 0f);
                        Anim.set("scrollSet", 0f);
                        Anim.set("catSwitch", 0f);
                    }
                }
                case MOD_SEL -> {
                    if (selMod != h.a()) { selMod = (Mod) h.a(); scrollSetT = 0; Anim.set("scrollSet", 0f); Anim.set("setSwitch", 0f); }
                }
                case MOD_TOG -> { Mod m = (Mod) h.a(); if (b == 0) m.toggle(); }
                case BIND -> {
                    Mod m = (Mod) h.a();
                    if (b == 0) binding = m; else m.key = -1;
                }
                case VIS -> { Mod m = (Mod) h.a(); if (b == 0) m.visible = !m.visible; }
                case SET -> {
                    Setting<?> s = (Setting<?>) h.a();
                    if (s instanceof Setting.Bool bs && b == 0) bs.toggle();
                    else if (s instanceof Setting.Mode ms) ms.cycle(b == 1 ? -1 : 1);
                    else if (s instanceof Setting.Num && b == 0) { sliding = h; slide(mx); }
                }
                case PRESET -> {
                    int idx = (Integer) h.a();
                    Advisor.applyPreset(idx);
                    tipsAt = 0;
                    Notifications.pushText("Пресет: " + Advisor.PRESETS[idx], true);
                }
                case PSAVE -> {
                    int slot = (Integer) h.a();
                    Config.saveTo(Config.profile(slot));
                    Notifications.pushText("Профиль " + slot + " сохранён", true);
                }
                case PLOAD -> {
                    int slot = (Integer) h.a();
                    if (Config.profileExists(slot)) {
                        Config.loadFrom(Config.profile(slot));
                        tipsAt = 0;
                        Notifications.pushText("Профиль " + slot + " загружен", true);
                    } else Notifications.pushText("Профиль " + slot + " пуст", false);
                }
                case SEND -> send();
                default -> {}
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void slide(double mx) {
        Setting.Num n = (Setting.Num) sliding.a();
        double t = MathHelper.clamp((mx - sliding.x()) / sliding.w(), 0.0, 1.0);
        n.setClamped(n.min + t * (n.max - n.min));
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (sliding != null) { slide(click.x()); return true; }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        sliding = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (mx < wx + SIDE) return true;
        if (selCat != Category.ASSISTANT && mx < wx + SIDE + LIST)
            scrollListT = MathHelper.clamp(scrollListT - (float) vAmount * 24, 0, maxList);
        else scrollSetT = MathHelper.clamp(scrollSetT - (float) vAmount * 24, 0, maxSet);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput ki) {
        int k = ki.key();
        if (binding != null) {
            binding.key = (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_DELETE) ? -1 : k;
            binding = null;
            return true;
        }
        if (askField != null && askField.visible && askField.isFocused()) {
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { send(); return true; }
            if (k == GLFW.GLFW_KEY_ESCAPE) { askField.setFocused(false); return true; }
            return super.keyPressed(ki);
        }
        if (k == GLFW.GLFW_KEY_RIGHT_SHIFT || k == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(ki);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Config.save(); }
}
