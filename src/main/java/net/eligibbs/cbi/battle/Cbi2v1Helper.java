package net.eligibbs.cbi.battle;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.exception.IllegalActionChoiceException;
import com.cobblemon.mod.common.net.messages.client.battle.BattleQueueRequestPacket;
import kotlin.Pair;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Cbi2v1Helper {

    private static final Map<UUID, SoloSlotState> BATTLE_STATES = new ConcurrentHashMap<>();

    public static class SoloSlotState {
        public ShowdownActionRequest slot0Request;
        public ShowdownActionRequest slot1Request;
    }

    public static boolean is2v1Battle(@Nullable PokemonBattle battle) {
        if (battle == null || battle.getSide1() == null || battle.getSide2() == null) {
            return false;
        }
        BattleActor[] s1 = battle.getSide1().getActors();
        BattleActor[] s2 = battle.getSide2().getActors();
        if (s1 == null || s2 == null) {
            return false;
        }
        return Cbi2v1Logic.is2v1(s1.length, s2.length);
    }

    public static boolean isSoloSide1(@NotNull PokemonBattle battle) {
        return battle.getSide1().getActors().length == 1;
    }

    public static boolean isSoloActor(@NotNull PokemonBattle battle, @NotNull BattleActor actor) {
        BattleActor solo = getSoloActor(battle);
        return solo != null && solo.getUuid().equals(actor.getUuid());
    }

    @Nullable
    public static BattleActor getSoloActor(@NotNull PokemonBattle battle) {
        if (!is2v1Battle(battle)) {
            return null;
        }
        if (isSoloSide1(battle)) {
            return battle.getSide1().getActors()[0];
        } else {
            return battle.getSide2().getActors()[0];
        }
    }

    @NotNull
    public static String getPrimaryShowdownId(@NotNull PokemonBattle battle) {
        return isSoloSide1(battle) ? "p1" : "p2";
    }

    @NotNull
    public static String getSecondaryShowdownId(@NotNull PokemonBattle battle) {
        return isSoloSide1(battle) ? "p3" : "p4";
    }

    public static void startShowdown2v1(@NotNull PokemonBattle battle) {
        boolean soloSide1 = isSoloSide1(battle);
        BattleActor soloActor = getSoloActor(battle);
        if (soloActor == null) {
            return;
        }

        BattleActor duo1;
        BattleActor duo2;
        if (soloSide1) {
            soloActor.setShowdownId("p1");
            duo1 = battle.getSide2().getActors()[0];
            duo2 = battle.getSide2().getActors()[1];
            duo1.setShowdownId("p2");
            duo2.setShowdownId("p4");

            // Solo actor gets 2 active slots
            soloActor.getActivePokemon().add(new ActiveBattlePokemon(soloActor, null));
            soloActor.getActivePokemon().add(new ActiveBattlePokemon(soloActor, null));

            // Duo actors get 1 active slot each
            duo1.getActivePokemon().add(new ActiveBattlePokemon(duo1, null));
            duo2.getActivePokemon().add(new ActiveBattlePokemon(duo2, null));
        } else {
            duo1 = battle.getSide1().getActors()[0];
            duo2 = battle.getSide1().getActors()[1];
            duo1.setShowdownId("p1");
            duo2.setShowdownId("p3");
            soloActor.setShowdownId("p2");

            // Duo actors get 1 active slot each
            duo1.getActivePokemon().add(new ActiveBattlePokemon(duo1, null));
            duo2.getActivePokemon().add(new ActiveBattlePokemon(duo2, null));

            // Solo actor gets 2 active slots
            soloActor.getActivePokemon().add(new ActiveBattlePokemon(soloActor, null));
            soloActor.getActivePokemon().add(new ActiveBattlePokemon(soloActor, null));
        }

        for (BattleActor actor : battle.getActors()) {
            for (BattlePokemon bp : actor.getPokemonList()) {
                if (bp.getEntity() != null) {
                    bp.getEntity().setBattleId(battle.getBattleId());
                }
            }
        }

        // Split solo team into 2 teams for Showdown registration
        Pair<List<BattlePokemon>, List<BattlePokemon>> soloSplit = Cbi2v1Logic.splitTeam(soloActor.getPokemonList());
        List<BattlePokemon> soloTeam1 = soloSplit.getFirst();
        List<BattlePokemon> soloTeam2 = soloSplit.getSecond();

        List<String> messages = new ArrayList<>();
        messages.add(">start { \"format\": " + battle.getFormat().toFormatJSON() + " }");

        if (soloSide1) {
            messages.add(">player p1 {\"name\":\"" + soloActor.getUuid() + "\",\"team\":\"" + BattleRegistry.INSTANCE.packTeam(soloTeam1) + "\"}");
            messages.add(">player p2 {\"name\":\"" + duo1.getUuid() + "\",\"team\":\"" + BattleRegistry.INSTANCE.packTeam(duo1.getPokemonList()) + "\"}");
            messages.add(">player p3 {\"name\":\"" + soloActor.getUuid() + "\",\"team\":\"" + BattleRegistry.INSTANCE.packTeam(soloTeam2) + "\"}");
            messages.add(">player p4 {\"name\":\"" + duo2.getUuid() + "\",\"team\":\"" + BattleRegistry.INSTANCE.packTeam(duo2.getPokemonList()) + "\"}");

            messages.add(">p1 team " + soloTeam1.size());
            messages.add(">p2 team " + duo1.getPokemonList().size());
            messages.add(">p3 team " + soloTeam2.size());
            messages.add(">p4 team " + duo2.getPokemonList().size());
        } else {
            messages.add(">player p1 {\"name\":\"" + duo1.getUuid() + "\",\"team\":\"" + BattleRegistry.INSTANCE.packTeam(duo1.getPokemonList()) + "\"}");
            messages.add(">player p2 {\"name\":\"" + soloActor.getUuid() + "\",\"team\":\"" + BattleRegistry.INSTANCE.packTeam(soloTeam1) + "\"}");
            messages.add(">player p3 {\"name\":\"" + duo2.getUuid() + "\",\"team\":\"" + BattleRegistry.INSTANCE.packTeam(duo2.getPokemonList()) + "\"}");
            messages.add(">player p4 {\"name\":\"" + soloActor.getUuid() + "\",\"team\":\"" + BattleRegistry.INSTANCE.packTeam(soloTeam2) + "\"}");

            messages.add(">p1 team " + duo1.getPokemonList().size());
            messages.add(">p2 team " + soloTeam1.size());
            messages.add(">p3 team " + duo2.getPokemonList().size());
            messages.add(">p4 team " + soloTeam2.size());
        }

        BATTLE_STATES.put(battle.getBattleId(), new SoloSlotState());
        ShowdownService.Companion.getService().startBattle(battle, messages.toArray(new String[0]));
    }

    @Nullable
    public static BattleActor getActorForShowdownId(@NotNull PokemonBattle battle, @NotNull String showdownId) {
        if (!is2v1Battle(battle)) {
            return null;
        }
        String p1Id = getPrimaryShowdownId(battle);
        String p2Id = getSecondaryShowdownId(battle);
        if (showdownId.equals(p1Id) || showdownId.equals(p2Id)) {
            return getSoloActor(battle);
        }

        for (BattleActor actor : battle.getActors()) {
            if (actor.getShowdownId().equals(showdownId)) {
                return actor;
            }
        }
        return null;
    }

    @Nullable
    public static Pair<BattleActor, ActiveBattlePokemon> getActorAndActiveSlotFromPNX(@NotNull PokemonBattle battle, @NotNull String pnx) {
        if (!is2v1Battle(battle) || pnx.length() < 3) {
            return null;
        }
        String pid = pnx.substring(0, 2);
        char letter = pnx.charAt(2);
        int slotIndex = letter - 'a';

        BattleActor solo = getSoloActor(battle);
        if (solo == null) {
            return null;
        }

        String primId = getPrimaryShowdownId(battle);
        String secId = getSecondaryShowdownId(battle);

        // If pnx uses the solo actor's showdownId (e.g. p2a, p2b or p1a, p1b)
        if (pid.equals(solo.getShowdownId())) {
            if (slotIndex >= 0 && slotIndex < solo.getActivePokemon().size()) {
                return new Pair<>(solo, solo.getActivePokemon().get(slotIndex));
            }
        }

        // If pnx uses raw primary or secondary showdown ID (e.g. p1 or p3/p4)
        if (pid.equals(primId) && solo.getActivePokemon().size() > 0) {
            return new Pair<>(solo, solo.getActivePokemon().get(0));
        } else if (pid.equals(secId) && solo.getActivePokemon().size() > 1) {
            return new Pair<>(solo, solo.getActivePokemon().get(1));
        }

        // Duo side actors
        for (BattleActor actor : battle.getActors()) {
            if (actor.getShowdownId().equals(pid) && !actor.getUuid().equals(solo.getUuid())) {
                if (actor.getActivePokemon().size() > 0) {
                    return new Pair<>(actor, actor.getActivePokemon().get(0));
                }
            }
        }
        return null;
    }

    @Nullable
    public static BattlePokemon getBattlePokemon(@NotNull PokemonBattle battle, @NotNull String pnx, @NotNull String pokemonID) {
        if (!is2v1Battle(battle)) {
            return null;
        }
        BattleActor actor = getActorForShowdownId(battle, pnx.substring(0, 2));
        if (actor == null) {
            return null;
        }
        for (BattlePokemon bp : actor.getPokemonList()) {
            if (bp.getUuid().toString().equalsIgnoreCase(pokemonID)
                    || (bp.getEffectedPokemon() != null && bp.getEffectedPokemon().getUuid().toString().equalsIgnoreCase(pokemonID))) {
                return bp;
            }
        }
        return null;
    }

    public static void handleSoloRequestInstruction(@NotNull PokemonBattle battle, @NotNull BattleActor soloActor, @NotNull BattleMessage message) {
        if (message.getRawMessage().contains("teamPreview")) {
            return;
        }

        ShowdownActionRequest request = BattleRegistry.INSTANCE.getGson().fromJson(
                message.getRawMessage().split("\\|request\\|")[1],
                ShowdownActionRequest.class
        );
        if (request == null || request.getSide() == null) {
            return;
        }

        SoloSlotState state = BATTLE_STATES.computeIfAbsent(battle.getBattleId(), k -> new SoloSlotState());
        String reqSideId = request.getSide().getId();

        String primId = getPrimaryShowdownId(battle);
        String secId = getSecondaryShowdownId(battle);

        if (reqSideId.equals(primId)) {
            state.slot0Request = request;
        } else if (reqSideId.equals(secId)) {
            state.slot1Request = request;
        }

        ShowdownActionRequest merged = Cbi2v1Logic.createMergedRequest(state.slot0Request, state.slot1Request);
        merged.sanitize(battle, soloActor);

        battle.dispatchGo(() -> {
            soloActor.sendUpdate(new BattleQueueRequestPacket(merged));
            soloActor.setRequest(merged);
            soloActor.getResponses().clear();
            return Unit.INSTANCE;
        });
    }

    /**
     * Ensures every switch chosen by the solo player targets a Pokémon that belongs to the
     * sub-team registered with Showdown for that slot. Cobblemon catches the thrown
     * exception and re-sends the request so the player can pick again.
     */
    public static void validateSoloResponses(@NotNull PokemonBattle battle, @NotNull BattleActor soloActor, @NotNull List<ShowdownActionResponse> responses) {
        List<BattlePokemon> party = soloActor.getPokemonList();
        for (int slot = 0; slot < responses.size() && slot < soloActor.getActivePokemon().size(); slot++) {
            if (!(responses.get(slot) instanceof SwitchActionResponse switchResponse)) {
                continue;
            }
            int partyIndex = indexOfPokemon(party, switchResponse.getNewPokemonId());
            if (partyIndex >= 0 && Cbi2v1Logic.switchIndexForSlot(partyIndex, slot) < 0) {
                BattlePokemon target = party.get(partyIndex);
                throw new IllegalActionChoiceException(
                        soloActor,
                        target.getName().getString() + " can only be sent out in the "
                                + (Cbi2v1Logic.slotForPartyIndex(partyIndex) == 0 ? "left" : "right") + " battle slot."
                );
            }
        }
    }

    public static void writeSoloShowdownResponse(@NotNull PokemonBattle battle, @NotNull BattleActor soloActor) {
        ShowdownActionRequest req = soloActor.getRequest();
        if (req == null) {
            return;
        }

        List<ShowdownActionResponse> responses = soloActor.getResponses();
        String[] showdownIds = { getPrimaryShowdownId(battle), getSecondaryShowdownId(battle) };
        SoloSlotState state = BATTLE_STATES.computeIfAbsent(battle.getBattleId(), k -> new SoloSlotState());
        ShowdownActionRequest[] slotRequests = { state.slot0Request, state.slot1Request };

        List<ActiveBattlePokemon> activePokemon = soloActor.getActivePokemon();
        List<ShowdownMoveset> activeMoves = req.getActive();

        List<String> toSend = new ArrayList<>();

        for (int slot = 0; slot < 2 && slot < responses.size() && slot < activePokemon.size(); slot++) {
            // Only answer the sub-teams Showdown is actually waiting on; a "wait" side rejects any input.
            if (!Cbi2v1Logic.requiresResponse(slotRequests[slot])) {
                continue;
            }
            ShowdownActionResponse response = responses.get(slot);
            ShowdownMoveset moveset = activeMoves != null && activeMoves.size() > slot ? activeMoves.get(slot) : null;
            String action = toSlotShowdownString(soloActor, response, activePokemon.get(slot), moveset, slot);
            toSend.add(">" + showdownIds[slot] + " " + action);
        }

        responses.clear();
        soloActor.setRequest(null);
        soloActor.getExpectingPassActions().clear();
        state.slot0Request = null;
        state.slot1Request = null;

        if (!toSend.isEmpty()) {
            battle.writeShowdownAction(toSend.toArray(new String[0]));
        }
    }

    private static String toSlotShowdownString(
            @NotNull BattleActor soloActor,
            @NotNull ShowdownActionResponse response,
            @NotNull ActiveBattlePokemon activePokemon,
            @Nullable ShowdownMoveset moveset,
            int slot
    ) {
        if (response instanceof SwitchActionResponse switchResponse) {
            // Showdown indexes switches within the sub-team registered for this slot, not the full party.
            int partyIndex = indexOfPokemon(soloActor.getPokemonList(), switchResponse.getNewPokemonId());
            int switchIndex = Cbi2v1Logic.switchIndexForSlot(partyIndex, slot);
            return switchIndex > 0 ? "switch " + switchIndex : "default";
        }
        return response.toShowdownString(activePokemon, moveset);
    }

    private static int indexOfPokemon(@NotNull List<BattlePokemon> party, @NotNull UUID pokemonId) {
        for (int i = 0; i < party.size(); i++) {
            if (party.get(i).getUuid().equals(pokemonId)) {
                return i;
            }
        }
        return -1;
    }

    public static void cleanBattle(@NotNull UUID battleId) {
        BATTLE_STATES.remove(battleId);
    }
}
