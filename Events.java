package dev.aurora.core;

import dev.aurora.module.*;
import dev.aurora.module.Pvp.*;
import dev.aurora.module.Movement.*;
import dev.aurora.module.Visuals.*;
import dev.aurora.module.Pve.*;
import dev.aurora.module.Cosmetics.*;
import dev.aurora.module.Misc.*;
import net.minecraft.entity.Entity;

/** Called when the local player attacks an entity. */
public final class Events {
    private Events() {}

    public static void onAttack(Entity e) {
        WTap w = ModuleManager.get(WTap.class);
        if (w != null && w.isEnabled()) w.hit();
        HitEffects h = ModuleManager.get(HitEffects.class);
        if (h != null && h.isEnabled()) h.burst(e);
        ComboCounter c = ModuleManager.get(ComboCounter.class);
        if (c != null && c.isEnabled()) c.hit();
        ReachDisplay r = ModuleManager.get(ReachDisplay.class);
        if (r != null && r.isEnabled()) r.hit(e);
    }
}
