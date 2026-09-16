/*
 * Armed Pillagers - pillagers that carry firearms and know how to use them.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.nfx.armedpillagers.mixin;

import com.nfx.rangedweapons.api.RangedWeapons;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Pillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A protocol gun uses the game's crossbow hold signal, which Fresh Animations
 * understands. This does not fake item use or change the attack goal. All
 * non-protocol items keep Pillager's own pose selection.
 */
@Mixin(Pillager.class)
public abstract class PillagerMixin {
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private void armedpillagers$gunHold(CallbackInfoReturnable<AbstractIllager.IllagerArmPose> ci) {
        Pillager pillager = (Pillager) (Object) this;
        if (RangedWeapons.isWeapon(pillager.getMainHandItem())) {
            ci.setReturnValue(AbstractIllager.IllagerArmPose.CROSSBOW_HOLD);
        }
    }
}
