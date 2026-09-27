package net.eligibbs.cbi.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import kotlin.Pair;
import net.eligibbs.cbi.battle.Cbi2v1Helper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PokemonBattle.class, remap = false)
public abstract class PokemonBattleMixin {

    @Inject(method = "getActor(Ljava/lang/String;)Lcom/cobblemon/mod/common/api/battles/model/actor/BattleActor;", at = @At("HEAD"), cancellable = true, remap = false)
    private void cbi$getActor(String showdownId, CallbackInfoReturnable<BattleActor> cir) {
        PokemonBattle battle = (PokemonBattle) (Object) this;
        if (Cbi2v1Helper.is2v1Battle(battle)) {
            BattleActor actor = Cbi2v1Helper.getActorForShowdownId(battle, showdownId);
            if (actor != null) {
                cir.setReturnValue(actor);
            }
        }
    }

    @Inject(method = "getActorAndActiveSlotFromPNX", at = @At("HEAD"), cancellable = true, remap = false)
    private void cbi$getActorAndActiveSlotFromPNX(String pnx, CallbackInfoReturnable<Pair<BattleActor, ActiveBattlePokemon>> cir) {
        PokemonBattle battle = (PokemonBattle) (Object) this;
        if (Cbi2v1Helper.is2v1Battle(battle)) {
            Pair<BattleActor, ActiveBattlePokemon> pair = Cbi2v1Helper.getActorAndActiveSlotFromPNX(battle, pnx);
            if (pair != null) {
                cir.setReturnValue(pair);
            }
        }
    }

    @Inject(method = "getBattlePokemon", at = @At("HEAD"), cancellable = true, remap = false)
    private void cbi$getBattlePokemon(String pnx, String pokemonID, CallbackInfoReturnable<BattlePokemon> cir) {
        PokemonBattle battle = (PokemonBattle) (Object) this;
        if (Cbi2v1Helper.is2v1Battle(battle)) {
            BattlePokemon pokemon = Cbi2v1Helper.getBattlePokemon(battle, pnx, pokemonID);
            if (pokemon != null) {
                cir.setReturnValue(pokemon);
            }
        }
    }
}
