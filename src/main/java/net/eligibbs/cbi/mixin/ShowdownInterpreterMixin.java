package net.eligibbs.cbi.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.util.DistributionUtilsKt;
import kotlin.Unit;
import net.eligibbs.cbi.battle.Cbi2v1Helper;
import net.eligibbs.cbi.battle.Cbi2v1Logic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ShowdownInterpreter.class, remap = false)
public abstract class ShowdownInterpreterMixin {

    @Inject(
            method = "interpretMessage",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void cbi$interpretMessage(UUID battleId, String message, CallbackInfo ci) {
        if (message != null && !message.startsWith("{\"winner\":\"")) {
            PokemonBattle battle = BattleRegistry.getBattle(battleId);
            if (battle != null && Cbi2v1Helper.is2v1Battle(battle)) {
                ci.cancel();
                String translated = Cbi2v1Logic.translateShowdownMessage(message, Cbi2v1Helper.isSoloSide1(battle));
                DistributionUtilsKt.runOnServer(() -> {
                    battle.getShowdownMessages().add(translated);
                    ShowdownInterpreter.INSTANCE.interpret(battle, translated);
                    return Unit.INSTANCE;
                });
            }
        }
    }
}
