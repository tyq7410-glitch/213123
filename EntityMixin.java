package dev.aurora.mixin;

import dev.aurora.module.Visuals.ESP;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void aurora$glow(CallbackInfoReturnable<Boolean> cir) {
        if (ESP.shouldGlow((Entity) (Object) this)) cir.setReturnValue(true);
    }
}
