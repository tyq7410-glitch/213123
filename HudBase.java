package dev.aurora.module;

import dev.aurora.core.*;
import dev.aurora.util.Util;
import net.minecraft.client.gui.DrawContext;

/** Base for on-screen widgets: position (in % of the screen) and background are configurable. */
public abstract class HudBase extends Mod {
    protected final Setting.Num posX;
    protected final Setting.Num posY;
    protected final Setting.Bool bg = add(new Setting.Bool("Фон", true));

    private final int defX, defY;

    protected HudBase(String name, int x, int y) { this(name, x, y, Category.VISUAL); }

    protected HudBase(String name, int x, int y, Category cat) {
        super(name, cat);
        defX = x; defY = y;
        posX = add(new Setting.Num("Позиция X (%)", x, 0, 100, 1));
        posY = add(new Setting.Num("Позиция Y (%)", y, 0, 100, 1));
    }

    public double px() { return posX.get(); }
    public double py() { return posY.get(); }
    public void resetPosition() { posX.setClamped(defX); posY.setClamped(defY); }

    protected int ox(DrawContext c, int w) { return (int) ((c.getScaledWindowWidth() - w) * posX.get() / 100.0); }
    protected int oy(DrawContext c, int h) { return (int) ((c.getScaledWindowHeight() - h) * posY.get() / 100.0); }

    protected void drawLines(DrawContext c, java.util.List<String> l) {
        if (l.isEmpty()) return;
        var tr = mc.textRenderer;
        int w = 0;
        for (String s : l) w = Math.max(w, tr.getWidth(s));
        w += 10;
        int h = l.size() * 11 + 4;
        int x = ox(c, w), y = oy(c, h);
        box(c, x, y, w, h);
        for (int i = 0; i < l.size(); i++) c.drawText(tr, l.get(i), x + 5, y + 3 + i * 11, 0xFFFFFFFF, true);
    }

    protected void box(DrawContext c, int x, int y, int w, int h) {
        if (bg.get()) c.fill(x, y, x + w, y + h, 0x90101018);
        c.fill(x, y, x + w, y + 1, Util.color(x / 10, false));
    }
}
