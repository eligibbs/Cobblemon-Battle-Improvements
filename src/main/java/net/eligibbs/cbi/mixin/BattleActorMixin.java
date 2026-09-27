package net.eligibbs.cbi.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import net.eligibbs.cbi.battle.Cbi2v1Helper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = BattleActor.class, remap = false)
public abstract class BattleActorMixin {

    @Inject(method = "setActionResponses", at = @At("HEAD"), remap = false)
    private void cbi$validateActionResponses(List<ShowdownActionResponse> responses, CallbackInfo ci) {
        BattleActor actor = (BattleActor) (Object) this;
        PokemonBattle battle = actor.getBattle();
        if (battle != null && Cbi2v1Helper.is2v1Battle(battle) && Cbi2v1Helper.isSoloActor(battle, actor)) {
            Cbi2v1Helper.validateSoloResponses(battle, actor, responses);
        }
    }

    @Inject(method = "writeShowdownResponse", at = @At("HEAD"), cancellable = true, remap = false)
    private void cbi$writeShowdownResponse(CallbackInfo ci) {
        BattleActor actor = (BattleActor) (Object) this;
        PokemonBattle battle = actor.getBattle();
        if (battle != null && Cbi2v1Helper.is2v1Battle(battle) && Cbi2v1Helper.isSoloActor(battle, actor)) {
            Cbi2v1Helper.writeSoloShowdownResponse(battle, actor);
            ci.cancel();
        }
    }
}
