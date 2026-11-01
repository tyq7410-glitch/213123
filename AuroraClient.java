package dev.aurora;

import dev.aurora.core.Config;
import dev.aurora.core.ModuleManager;
import dev.aurora.module.Visuals.Crosshair;
import dev.aurora.module.Visuals.Zoom;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

public class AuroraClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        Config.load();
        Runtime.getRuntime().addShutdownHook(new Thread(Config::save));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player != null && mc.world != null) ModuleManager.tick();
        });

        // widgets, list, notifications...
        HudElementRegistry.addLast(Identifier.of("aurora", "hud"),
                (ctx, tickCounter) -> ModuleManager.renderHud(ctx, tickCounter.getTickProgress(false)));

        // custom crosshair replaces the vanilla one when the module is on
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (ctx, tickCounter) -> {
            Crosshair c = ModuleManager.get(Crosshair.class);
            if (c != null && c.isEnabled()) c.draw(ctx);
            else original.render(ctx, tickCounter);
        });

        // hit events (WTap, combo, effects...)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && entity instanceof LivingEntity) dev.aurora.core.Events.onAttack(entity);
            return ActionResult.PASS;
        });

        // don't leave a zoomed FOV in options.txt
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            Zoom z = ModuleManager.get(Zoom.class);
            if (z != null) z.restore();
            Config.save();
        });
    }
}
