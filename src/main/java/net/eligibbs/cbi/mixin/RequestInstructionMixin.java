package net.eligibbs.cbi.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.interpreter.instructions.RequestInstruction;
import net.eligibbs.cbi.battle.Cbi2v1Helper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RequestInstruction.class, remap = false)
public abstract class RequestInstructionMixin {

    @Shadow @Final private BattleActor battleActor;
    @Shadow @Final private BattleMessage message;

    @Inject(method = "invoke", at = @At("HEAD"), cancellable = true, remap = false)
    private void cbi$invoke(PokemonBattle battle, CallbackInfo ci) {
        if (Cbi2v1Helper.is2v1Battle(battle) && Cbi2v1Helper.isSoloActor(battle, this.battleActor)) {
            Cbi2v1Helper.handleSoloRequestInstruction(battle, this.battleActor, this.message);
            ci.cancel();
        }
    }
}
