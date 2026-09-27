package net.eligibbs.cbi.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import net.eligibbs.cbi.battle.Cbi2v1Helper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleRegistry.class, remap = false)
public abstract class BattleRegistryMixin {

    @Inject(method = "startShowdown", at = @At("HEAD"), cancellable = true, remap = false)
    private void cbi$startShowdown(PokemonBattle battle, CallbackInfo ci) {
        if (Cbi2v1Helper.is2v1Battle(battle)) {
            Cbi2v1Helper.startShowdown2v1(battle);
            ci.cancel();
        }
    }

    @Inject(method = "closeBattle", at = @At("HEAD"), remap = false)
    private static void cbi$closeBattle(PokemonBattle battle, CallbackInfo ci) {
        if (battle != null) {
            Cbi2v1Helper.cleanBattle(battle.getBattleId());
        }
    }
}
