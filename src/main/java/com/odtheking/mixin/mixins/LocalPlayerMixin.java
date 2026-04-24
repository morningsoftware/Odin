package com.odtheking.mixin.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.odtheking.odin.features.impl.skyblock.AutoSprint;
import com.odtheking.odin.features.impl.skyblock.QuickWarp;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"))
    private boolean odin$autoSprint(boolean original) {
        return original || AutoSprint.INSTANCE.getEnabled();
    }

    @Inject(
            method = "swing",
            at = @At("HEAD"),
            cancellable = true
    ) private void cancelSwing(InteractionHand interactionHand, CallbackInfo ci) {
        if (QuickWarp.INSTANCE.shouldSuppressLeftClick())
            ci.cancel();
    }
}
