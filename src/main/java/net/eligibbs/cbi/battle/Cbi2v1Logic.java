package net.eligibbs.cbi.battle;

import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.ShowdownPokemon;
import com.cobblemon.mod.common.battles.ShowdownSide;
import kotlin.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Cbi2v1Logic {

    public static boolean is2v1(int side1Actors, int side2Actors) {
        return (side1Actors == 2 && side2Actors == 1) || (side1Actors == 1 && side2Actors == 2);
    }

    public static int calculateSlotsPerActor(int pokemonPerSide, int actorsOnSide) {
        if (pokemonPerSide > 0 && actorsOnSide > 0) {
            return Math.max(1, pokemonPerSide / actorsOnSide);
        }
        return 1;
    }

    public static <T> Pair<List<T>, List<T>> splitTeam(List<T> fullTeam) {
        List<T> team1 = new ArrayList<>();
        List<T> team2 = new ArrayList<>();

        if (fullTeam == null || fullTeam.isEmpty()) {
            return new Pair<>(team1, team2);
        }
        if (fullTeam.size() == 1) {
            team1.add(fullTeam.get(0));
            return new Pair<>(team1, team2);
        }

        // Even indices to team1 (lead 1 at 0, 2, 4...)
        // Odd indices to team2 (lead 2 at 1, 3, 5...)
        for (int i = 0; i < fullTeam.size(); i++) {
            if (i % 2 == 0) {
                team1.add(fullTeam.get(i));
            } else {
                team2.add(fullTeam.get(i));
            }
        }
        return new Pair<>(team1, team2);
    }

    /**
     * Which solo slot (0 or 1) a party member belongs to, mirroring {@link #splitTeam}.
     */
    public static int slotForPartyIndex(int partyIndex) {
        return partyIndex % 2;
    }

    /**
     * 1-based Showdown switch index of a party member inside its sub-team, or -1 if the
     * party member does not belong to the given slot's sub-team.
     */
    public static int switchIndexForSlot(int partyIndex, int slot) {
        if (partyIndex < 0 || slotForPartyIndex(partyIndex) != slot) {
            return -1;
        }
        return partyIndex / 2 + 1;
    }

    /**
     * Whether a Showdown request for a single sub-team actually needs a response written back.
     */
    public static boolean requiresResponse(@Nullable ShowdownActionRequest request) {
        if (request == null || request.getWait()) {
            return false;
        }
        boolean hasActive = request.getActive() != null && !request.getActive().isEmpty();
        boolean hasForceSwitch = request.getForceSwitch().stream().anyMatch(Boolean::booleanValue);
        return hasActive || hasForceSwitch;
    }

    /**
     * A moveset with no moves. The client treats a slot with no usable moves as an automatic pass,
     * which is what we want for a sub-team that Showdown has told to wait.
     */
    public static ShowdownMoveset createPlaceholderMoveset() {
        ShowdownMoveset placeholder = new ShowdownMoveset();
        placeholder.setMoves(new ArrayList<>());
        return placeholder;
    }

    @Nullable
    public static ShowdownSide mergeSides(@Nullable ShowdownSide side0, @Nullable ShowdownSide side1) {
        ShowdownSide base = side0 != null ? side0 : side1;
        if (base == null) {
            return null;
        }
        ShowdownSide merged = new ShowdownSide();
        merged.setName(base.getName());
        merged.setId(base.getId());
        List<ShowdownPokemon> pokemon = new ArrayList<>();
        if (side0 != null) {
            pokemon.addAll(side0.getPokemon());
        }
        if (side1 != null) {
            pokemon.addAll(side1.getPokemon());
        }
        merged.setPokemon(pokemon);
        return merged;
    }

    public static ShowdownActionRequest createMergedRequest(@Nullable ShowdownActionRequest req0, @Nullable ShowdownActionRequest req1) {
        ShowdownActionRequest merged = new ShowdownActionRequest();

        boolean wait0 = req0 != null && req0.getWait();
        boolean wait1 = req1 != null && req1.getWait();
        merged.setWait(wait0 && wait1);

        ShowdownMoveset active0 = req0 != null && req0.getActive() != null && !req0.getActive().isEmpty() ? req0.getActive().get(0) : null;
        ShowdownMoveset active1 = req1 != null && req1.getActive() != null && !req1.getActive().isEmpty() ? req1.getActive().get(0) : null;
        if (active0 != null || active1 != null) {
            // Keep the movesets positionally aligned with the solo actor's active slots. A slot whose
            // sub-team is waiting gets an empty placeholder so the client auto-passes for it.
            List<ShowdownMoveset> activeMoves = new ArrayList<>();
            activeMoves.add(active0 != null ? active0 : createPlaceholderMoveset());
            if (active1 != null) {
                activeMoves.add(active1);
            }
            merged.setActive(activeMoves);
        }

        List<Boolean> forceSwitch = new ArrayList<>();
        boolean fs0 = req0 != null && !req0.getForceSwitch().isEmpty() && req0.getForceSwitch().get(0);
        boolean fs1 = req1 != null && !req1.getForceSwitch().isEmpty() && req1.getForceSwitch().get(0);
        forceSwitch.add(fs0);
        forceSwitch.add(fs1);
        merged.setForceSwitch(forceSwitch);

        // The client resolves each slot's Pokémon through request.side.pokemon, so the merged side
        // must contain the Pokémon of both sub-teams or the second slot gets auto-passed.
        ShowdownSide mergedSide = mergeSides(
                req0 != null ? req0.getSide() : null,
                req1 != null ? req1.getSide() : null
        );
        if (mergedSide != null) {
            merged.setSide(mergedSide);
        }

        boolean noCancel0 = req0 != null && req0.getNoCancel();
        boolean noCancel1 = req1 != null && req1.getNoCancel();
        merged.setNoCancel(noCancel0 || noCancel1);

        return merged;
    }

    public static String translateShowdownMessage(String rawMessage, boolean isSoloSide1) {
        if (rawMessage == null) {
            return null;
        }

        if (rawMessage.startsWith("sideupdate")) {
            if (!isSoloSide1) {
                // Solo is on Side 2 (actor p2). Showdown slot 1 sends sideupdate\np4.
                return rawMessage.replaceFirst("^sideupdate\np4\\b", "sideupdate\np2");
            } else {
                // Solo is on Side 1 (actor p1). Showdown slot 1 sends sideupdate\np3.
                return rawMessage.replaceFirst("^sideupdate\np3\\b", "sideupdate\np1");
            }
        }

        String result = rawMessage;
        if (!isSoloSide1) {
            // Solo is Side 2 (actor p2). Duo is Side 1 (actors p1, p3).
            // In Showdown multi: p1 is duo1, p2 is solo slot 0, p3 is duo2, p4 is solo slot 1.
            // Showdown multi positions are p1a, p2a, p3b, p4b (players 3/4 occupy slot b of their side).
            // Cobblemon: p1a -> duo1 slot a; p3b -> duo2 slot b; p2a -> solo slot a; p2b -> solo slot b.
            result = result.replaceAll("\\bp4[ab]\\b", "p2b");
            result = result.replaceAll("\\bp3a\\b", "p3b");
            result = result.replaceAll("(\\||^)split\\|p4\\b", "$1split|p2");
            result = result.replaceAll("\\bp4:", "p2:");
        } else {
            // Solo is Side 1 (actor p1). Duo is Side 2 (actors p2, p4).
            // In Showdown multi: p1 is solo slot 0, p2 is duo1, p3 is solo slot 1, p4 is duo2.
            // Showdown multi positions are p1a, p2a, p3b, p4b (players 3/4 occupy slot b of their side).
            // Cobblemon: p1a -> solo slot a; p1b -> solo slot b; p2a -> duo1 slot a; p4b -> duo2 slot b.
            result = result.replaceAll("\\bp3[ab]\\b", "p1b");
            result = result.replaceAll("\\bp4a\\b", "p4b");
            result = result.replaceAll("(\\||^)split\\|p3\\b", "$1split|p1");
            result = result.replaceAll("\\bp3:", "p1:");
        }
        return result;
    }
}
