package net.eligibbs.cbi;

import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.ShowdownPokemon;
import com.cobblemon.mod.common.battles.ShowdownSide;
import kotlin.Pair;
import net.eligibbs.cbi.battle.Cbi2v1Logic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CbiBattleLogicTest {

    @Test
    public void test2v1SlotAllocation() {
        int pokemonPerSide = 2;

        // Side 1: Team of 2 players
        int teamSideActors = 2;
        int teamMemberSlots = Cbi2v1Logic.calculateSlotsPerActor(pokemonPerSide, teamSideActors);
        assertEquals(1, teamMemberSlots, "Each player in the 2-player team should have exactly 1 active slot (goes once per turn)");

        // Side 2: Solo player
        int soloSideActors = 1;
        int soloMemberSlots = Cbi2v1Logic.calculateSlotsPerActor(pokemonPerSide, soloSideActors);
        assertEquals(2, soloMemberSlots, "The solo player should have exactly 2 active slots (goes twice per turn)");
    }

    @Test
    public void testStandardBattleSlotAllocationUnchanged() {
        // 1v1 Singles format: pokemonPerSide = 1, actors = 1
        assertEquals(1, Cbi2v1Logic.calculateSlotsPerActor(1, 1));

        // 1v1 Doubles format: pokemonPerSide = 2, actors = 1
        assertEquals(2, Cbi2v1Logic.calculateSlotsPerActor(2, 1));

        // 2v2 Multi format: pokemonPerSide = 2, actors = 2
        assertEquals(1, Cbi2v1Logic.calculateSlotsPerActor(2, 2));
    }

    @Test
    public void test2v1TeamSizeDetection() {
        assertTrue(Cbi2v1Logic.is2v1(2, 1));
        assertTrue(Cbi2v1Logic.is2v1(1, 2));
        assertFalse(Cbi2v1Logic.is2v1(2, 2));
        assertFalse(Cbi2v1Logic.is2v1(1, 1));
    }

    @Test
    public void testSoloTeamSplitting() {
        // Test with 6 elements
        List<String> dummyTeam = Arrays.asList("P0", "P1", "P2", "P3", "P4", "P5");

        Pair<List<String>, List<String>> split = Cbi2v1Logic.splitTeam(dummyTeam);
        assertEquals(Arrays.asList("P0", "P2", "P4"), split.getFirst(), "Team 1 (primary slot) should have indices 0, 2, 4");
        assertEquals(Arrays.asList("P1", "P3", "P5"), split.getSecond(), "Team 2 (secondary slot) should have indices 1, 3, 5");

        // Test with 2 elements
        List<String> twoPkmnTeam = Arrays.asList("P0", "P1");
        Pair<List<String>, List<String>> split2 = Cbi2v1Logic.splitTeam(twoPkmnTeam);
        assertEquals(Collections.singletonList("P0"), split2.getFirst());
        assertEquals(Collections.singletonList("P1"), split2.getSecond());

        // Test with 3 elements
        List<String> threePkmnTeam = Arrays.asList("P0", "P1", "P2");
        Pair<List<String>, List<String>> split3 = Cbi2v1Logic.splitTeam(threePkmnTeam);
        assertEquals(Arrays.asList("P0", "P2"), split3.getFirst());
        assertEquals(Collections.singletonList("P1"), split3.getSecond());
    }

    @Test
    public void testRequestMergingForBothSlots() {
        ShowdownActionRequest req0 = new ShowdownActionRequest();
        req0.setActive(new ArrayList<>(Collections.singletonList(new ShowdownMoveset())));
        req0.setForceSwitch(Collections.singletonList(false));
        req0.setWait(false);
        req0.setSide(side("p2", UUID.randomUUID()));

        ShowdownActionRequest req1 = new ShowdownActionRequest();
        req1.setActive(new ArrayList<>(Collections.singletonList(new ShowdownMoveset())));
        req1.setForceSwitch(Collections.singletonList(false));
        req1.setWait(false);

        ShowdownActionRequest merged = Cbi2v1Logic.createMergedRequest(req0, req1);

        assertNotNull(merged.getActive());
        assertEquals(2, merged.getActive().size(), "Merged request should contain active movesets for both slots");
        assertEquals(2, merged.getForceSwitch().size());
        assertFalse(merged.getForceSwitch().get(0));
        assertFalse(merged.getForceSwitch().get(1));
        assertFalse(merged.getWait());
    }

    @Test
    public void testMergedRequestSideContainsBothSubTeams() {
        // The client looks up each slot's Pokémon in request.side.pokemon; if the second
        // sub-team is missing the second slot is silently auto-passed ("health tank" bug).
        UUID lead0 = UUID.randomUUID();
        UUID bench0 = UUID.randomUUID();
        UUID lead1 = UUID.randomUUID();

        ShowdownActionRequest req0 = new ShowdownActionRequest();
        req0.setActive(new ArrayList<>(Collections.singletonList(new ShowdownMoveset())));
        req0.setForceSwitch(Collections.singletonList(false));
        req0.setSide(side("p2", lead0, bench0));

        ShowdownActionRequest req1 = new ShowdownActionRequest();
        req1.setActive(new ArrayList<>(Collections.singletonList(new ShowdownMoveset())));
        req1.setForceSwitch(Collections.singletonList(false));
        req1.setSide(side("p4", lead1));

        ShowdownActionRequest merged = Cbi2v1Logic.createMergedRequest(req0, req1);

        assertNotNull(merged.getSide());
        assertEquals("p2", merged.getSide().getId(), "Merged side keeps the primary showdown id");
        List<UUID> uuids = merged.getSide().getPokemon().stream().map(ShowdownPokemon::getUuid).toList();
        assertEquals(Arrays.asList(lead0, bench0, lead1), uuids, "Merged side must list the Pokémon of both sub-teams");
    }

    @Test
    public void testMergedActiveMovesetsStayAlignedWhenSlot0Waits() {
        ShowdownMoveset slot1Moves = new ShowdownMoveset();

        ShowdownActionRequest req0 = new ShowdownActionRequest();
        req0.setWait(true);
        req0.setSide(side("p2", UUID.randomUUID()));

        ShowdownActionRequest req1 = new ShowdownActionRequest();
        req1.setActive(new ArrayList<>(Collections.singletonList(slot1Moves)));
        req1.setForceSwitch(Collections.singletonList(false));
        req1.setSide(side("p4", UUID.randomUUID()));

        ShowdownActionRequest merged = Cbi2v1Logic.createMergedRequest(req0, req1);

        assertNotNull(merged.getActive());
        assertEquals(2, merged.getActive().size(), "Slot 0 gets a placeholder so slot 1's moveset stays at index 1");
        assertSame(slot1Moves, merged.getActive().get(1));
        assertTrue(merged.getActive().get(0).getMoves().isEmpty(), "Placeholder moveset has no moves");
        assertFalse(merged.getWait());
    }

    @Test
    public void testSwitchIndexIsRelativeToSlotSubTeam() {
        // Party indices 0,2,4 -> slot 0 sub-team (Showdown positions 1,2,3)
        assertEquals(1, Cbi2v1Logic.switchIndexForSlot(0, 0));
        assertEquals(2, Cbi2v1Logic.switchIndexForSlot(2, 0));
        assertEquals(3, Cbi2v1Logic.switchIndexForSlot(4, 0));
        // Party indices 1,3,5 -> slot 1 sub-team (Showdown positions 1,2,3)
        assertEquals(1, Cbi2v1Logic.switchIndexForSlot(1, 1));
        assertEquals(2, Cbi2v1Logic.switchIndexForSlot(3, 1));
        assertEquals(3, Cbi2v1Logic.switchIndexForSlot(5, 1));
        // Cross sub-team switches are not possible in Showdown
        assertEquals(-1, Cbi2v1Logic.switchIndexForSlot(1, 0));
        assertEquals(-1, Cbi2v1Logic.switchIndexForSlot(2, 1));
        assertEquals(-1, Cbi2v1Logic.switchIndexForSlot(-1, 0));
    }

    @Test
    public void testRequiresResponse() {
        assertFalse(Cbi2v1Logic.requiresResponse(null));

        ShowdownActionRequest waiting = new ShowdownActionRequest();
        waiting.setWait(true);
        assertFalse(Cbi2v1Logic.requiresResponse(waiting), "A waiting side must not be answered");

        ShowdownActionRequest moving = new ShowdownActionRequest();
        moving.setActive(new ArrayList<>(Collections.singletonList(new ShowdownMoveset())));
        moving.setForceSwitch(Collections.singletonList(false));
        assertTrue(Cbi2v1Logic.requiresResponse(moving));

        ShowdownActionRequest switching = new ShowdownActionRequest();
        switching.setForceSwitch(Collections.singletonList(true));
        assertTrue(Cbi2v1Logic.requiresResponse(switching));

        ShowdownActionRequest empty = new ShowdownActionRequest();
        assertFalse(Cbi2v1Logic.requiresResponse(empty));
    }

    private static ShowdownSide side(String id, UUID... pokemonIds) {
        ShowdownSide side = new ShowdownSide();
        side.setId(id);
        side.setName(UUID.randomUUID());
        List<ShowdownPokemon> pokemon = new ArrayList<>();
        for (UUID uuid : pokemonIds) {
            ShowdownPokemon sp = new ShowdownPokemon();
            sp.setIdent(id + ": Mon");
            sp.setDetails("Mon, " + uuid + ", L50, M");
            sp.setCondition("100/100");
            pokemon.add(sp);
        }
        side.setPokemon(pokemon);
        return side;
    }

    @Test
    public void testRequestMergingWithForceSwitchOnOneSlot() {
        ShowdownActionRequest req0 = new ShowdownActionRequest();
        req0.setForceSwitch(Collections.singletonList(false));
        req0.setWait(true);

        ShowdownActionRequest req1 = new ShowdownActionRequest();
        req1.setForceSwitch(Collections.singletonList(true));
        req1.setWait(false);

        ShowdownActionRequest merged = Cbi2v1Logic.createMergedRequest(req0, req1);

        assertEquals(2, merged.getForceSwitch().size());
        assertFalse(merged.getForceSwitch().get(0));
        assertTrue(merged.getForceSwitch().get(1), "Slot 1 should require a forced switch");
        assertFalse(merged.getWait());
    }

    @Test
    public void testTranslateShowdownMessageSoloSide2() {
        // Solo player is on Side 2 (Dev). Duo is on Side 1 (Player 1 & Player 2).
        String raw = "|switch|p1a: Pikachu|Pikachu, L50, M|100/100\n" +
                "|switch|p2a: Charizard|Charizard, L50, M|100/100\n" +
                "|switch|p3a: Blastoise|Blastoise, L50, M|100/100\n" +
                "|switch|p4a: Venusaur|Venusaur, L50, M|100/100\n" +
                "|move|p4a: Venusaur|Giga Drain|p1a: Pikachu\n" +
                "|split|p4\n" +
                "|-heal|p4a: Venusaur|100/100\n" +
                "|-sidestart|p4: Reflect";

        String translated = Cbi2v1Logic.translateShowdownMessage(raw, false);

        assertTrue(translated.contains("|switch|p1a: Pikachu"));
        assertTrue(translated.contains("|switch|p2a: Charizard"));
        assertTrue(translated.contains("|switch|p3b: Blastoise"), "Duo 2 on Side 1 should map from p3a to p3b");
        assertTrue(translated.contains("|switch|p2b: Venusaur"), "Solo 2nd slot on Side 2 should map from p4a to p2b");
        assertTrue(translated.contains("|move|p2b: Venusaur|Giga Drain|p1a: Pikachu"));
        assertTrue(translated.contains("|split|p2"));
        assertTrue(translated.contains("|-sidestart|p2: Reflect"));
    }

    @Test
    public void testTranslateShowdownMessageSoloSide1() {
        // Solo player is on Side 1. Duo is on Side 2.
        String raw = "|switch|p1a: Charizard|Charizard, L50, M|100/100\n" +
                "|switch|p2a: Pikachu|Pikachu, L50, M|100/100\n" +
                "|switch|p3a: Venusaur|Venusaur, L50, M|100/100\n" +
                "|switch|p4a: Blastoise|Blastoise, L50, M|100/100\n" +
                "|split|p3\n" +
                "|-sidestart|p3: Light Screen";

        String translated = Cbi2v1Logic.translateShowdownMessage(raw, true);

        assertTrue(translated.contains("|switch|p1a: Charizard"));
        assertTrue(translated.contains("|switch|p2a: Pikachu"));
        assertTrue(translated.contains("|switch|p1b: Venusaur"), "Solo 2nd slot on Side 1 should map from p3a to p1b");
        assertTrue(translated.contains("|switch|p4b: Blastoise"), "Duo 2 on Side 2 should map from p4a to p4b");
        assertTrue(translated.contains("|split|p1"));
        assertTrue(translated.contains("|-sidestart|p1: Light Screen"));
    }

    @Test
    public void testTranslateRealShowdownMultiPositionsSoloSide2() {
        // Real Showdown multi output: players 3 and 4 occupy slot "b" of their side (p3b / p4b).
        String raw = "|switch|p1a: Pikachu|Pikachu, L50, M|100/100\n" +
                "|switch|p2a: Charizard|Charizard, L50, M|100/100\n" +
                "|switch|p3b: Blastoise|Blastoise, L50, M|100/100\n" +
                "|switch|p4b: Venusaur|Venusaur, L50, M|100/100\n" +
                "|move|p4b: Venusaur|Giga Drain|p3b: Blastoise\n" +
                "|-damage|p3b: Blastoise|50/100|[from] move: Giga Drain|[of] p4b: Venusaur\n" +
                "|faint|p4b: Venusaur";

        String translated = Cbi2v1Logic.translateShowdownMessage(raw, false);

        assertFalse(translated.contains("p4b"), "p4b must never reach the client (no actor p4 on solo side 2)");
        assertFalse(translated.contains("p4a"));
        assertTrue(translated.contains("|switch|p2b: Venusaur"));
        assertTrue(translated.contains("|switch|p3b: Blastoise"));
        assertTrue(translated.contains("|move|p2b: Venusaur|Giga Drain|p3b: Blastoise"));
        assertTrue(translated.contains("[of] p2b: Venusaur"));
        assertTrue(translated.contains("|faint|p2b: Venusaur"));
    }

    @Test
    public void testTranslateRealShowdownMultiPositionsSoloSide1() {
        String raw = "|switch|p1a: Charizard|Charizard, L50, M|100/100\n" +
                "|switch|p2a: Pikachu|Pikachu, L50, M|100/100\n" +
                "|switch|p3b: Venusaur|Venusaur, L50, M|100/100\n" +
                "|switch|p4b: Blastoise|Blastoise, L50, M|100/100\n" +
                "|move|p3b: Venusaur|Giga Drain|p4b: Blastoise";

        String translated = Cbi2v1Logic.translateShowdownMessage(raw, true);

        assertFalse(translated.contains("p3b"), "p3b must never reach the client (no actor p3 on solo side 1)");
        assertTrue(translated.contains("|switch|p1b: Venusaur"));
        assertTrue(translated.contains("|switch|p4b: Blastoise"));
        assertTrue(translated.contains("|move|p1b: Venusaur|Giga Drain|p4b: Blastoise"));
    }

    @Test
    public void testTranslateSideUpdateRequest() {
        // Solo player on Side 2 receives sideupdate for slot 1 (p4 in Showdown)
        String sideUpdateSoloSide2 = "sideupdate\np4\n|request|{\"side\":{\"id\":\"p4\"}}";
        String translatedSide2 = Cbi2v1Logic.translateShowdownMessage(sideUpdateSoloSide2, false);
        assertTrue(translatedSide2.startsWith("sideupdate\np2\n"), "p4 sideupdate should route to p2 actor on server");

        // Solo player on Side 1 receives sideupdate for slot 1 (p3 in Showdown)
        String sideUpdateSoloSide1 = "sideupdate\np3\n|request|{\"side\":{\"id\":\"p3\"}}";
        String translatedSide1 = Cbi2v1Logic.translateShowdownMessage(sideUpdateSoloSide1, true);
        assertTrue(translatedSide1.startsWith("sideupdate\np1\n"), "p3 sideupdate should route to p1 actor on server");
    }
}
