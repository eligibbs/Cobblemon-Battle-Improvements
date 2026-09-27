package net.eligibbs.cbi.mixin.client;

import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection.SwitchTile;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Pair;
import net.eligibbs.cbi.battle.Cbi2v1Logic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * In a 2v1 battle the solo player's party is split into two Showdown sub-teams (one per active
 * slot). The switch screen only offers the Pokémon that can actually be sent out in the slot
 * currently being chosen for, so the player can't pick an option Showdown would reject.
 */
@Mixin(value = BattleSwitchPokemonSelection.class, remap = false)
public abstract class BattleSwitchPokemonSelectionMixin {

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void cbi$filterTilesToSlotSubTeam(BattleGUI battleGUI, SingleActionRequest request, CallbackInfo ci) {
        BattleSwitchPokemonSelection self = (BattleSwitchPokemonSelection) (Object) this;
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        ClientBattleActor actor = battleGUI.getActor();
        if (battle == null || actor == null || !cbi$isSoloIn2v1(battle, actor)) {
            return;
        }

        int slot = actor.getActivePokemon().indexOf(request.getActivePokemon());
        if (slot < 0) {
            return;
        }

        List<Pokemon> party = actor.getPokemon();
        List<SwitchTile> tiles = self.getTiles();
        List<SwitchTile> kept = new ArrayList<>();
        for (SwitchTile tile : tiles) {
            int partyIndex = cbi$indexOf(party, tile.getPokemon().getUuid());
            if (partyIndex >= 0 && Cbi2v1Logic.slotForPartyIndex(partyIndex) == slot) {
                kept.add(tile);
            }
        }

        tiles.clear();
        for (int i = 0; i < kept.size(); i++) {
            SwitchTile tile = kept.get(i);
            Pair<Float, Float> pos = self.getSlotPosition(i);
            tiles.add(new SwitchTile(self, pos.getFirst(), pos.getSecond(), tile.getPokemon(), tile.getShowdownPokemon(), tile.isFainted(), tile.isCurrentlyInBattle()));
        }

        // Mirror Cobblemon's own handling: nothing valid left for a forced switch means we must pass.
        if (request.getForceSwitch() && !self.isReviving()
                && tiles.stream().allMatch(tile -> tile.isFainted() || tile.isCurrentlyInBattle())) {
            battleGUI.selectAction(request, PassActionResponse.INSTANCE);
        }
    }

    private static boolean cbi$isSoloIn2v1(ClientBattle battle, ClientBattleActor actor) {
        return battle.getSide1().getActors().size() == 1
                && battle.getSide2().getActors().size() == 2
                && battle.getSide1().getActors().contains(actor)
                && actor.getActivePokemon().size() == 2;
    }

    private static int cbi$indexOf(List<Pokemon> party, UUID uuid) {
        for (int i = 0; i < party.size(); i++) {
            if (party.get(i).getUuid().equals(uuid)) {
                return i;
            }
        }
        return -1;
    }
}
