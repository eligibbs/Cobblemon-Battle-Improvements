package net.eligibbs.cbi.compat.cobbledex;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.FormDexRecord;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Writes discoveries into a player's Cobblemon Pokédex ({@link PokedexManager}).
 * <p>
 * This deliberately goes through {@link PokedexManager#encounter(Pokemon)} /
 * {@link PokedexManager#obtain(Pokemon)} with a reference Pokémon of the right form and shiny
 * state instead of poking the records directly, so Cobblemon records genders/shiny states the
 * same way it does for a real capture, fires its {@code POKEDEX_DATA_CHANGED} events and
 * pushes the incremental update to the client.
 * <p>
 * Only Cobblemon classes are referenced here; nothing in this class requires Cobbledex.
 */
public final class CobblemonPokedexUnlocker {

    private CobblemonPokedexUnlocker() {
    }

    /**
     * Unlocks {@code form} in {@code player}'s Cobblemon Pokédex.
     *
     * @param caught {@code true} to mark the form as owned, {@code false} to mark it as seen
     * @return whether the Pokédex actually changed (nothing is written if it already knew as much)
     */
    public static boolean unlock(@NotNull ServerPlayer player, @NotNull FormData form, boolean shiny, boolean caught) {
        PokedexManager dex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);

        SpeciesDexRecord speciesRecord = dex.getSpeciesRecord(form.getSpecies().getResourceIdentifier());
        FormDexRecord formRecord = speciesRecord == null ? null : speciesRecord.getFormRecord(form.getName());
        int currentKnowledge = formRecord == null ? CobbledexSyncLogic.UNREGISTERED : formRecord.getKnowledge().ordinal();
        boolean seenShinyState = formRecord != null && formRecord.hasSeenShinyState(shiny);
        if (!CobbledexSyncLogic.needsUnlock(currentKnowledge, seenShinyState, caught)) {
            return false;
        }

        Pokemon reference = createReferencePokemon(form, shiny);
        if (caught) {
            dex.obtain(reference);
        } else {
            dex.encounter(reference);
        }
        return true;
    }

    /** Persists the player's Pokédex; call once after a batch of {@link #unlock} calls. */
    public static void save(@NotNull ServerPlayer player) {
        PokedexManager dex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);
        Cobblemon.INSTANCE.getPlayerDataManager().saveSingle(dex, PlayerInstancedDataStoreTypes.INSTANCE.getPOKEDEX());
    }

    /**
     * A throw-away Pokémon used only as the source of species/form/shiny information for the
     * Pokédex; it is never stored or spawned. Cobbledex does not track levels, so level 1 is
     * used and Cobblemon simply keeps whatever highest level it already knows.
     */
    @NotNull
    private static Pokemon createReferencePokemon(@NotNull FormData form, boolean shiny) {
        PokemonProperties properties = new PokemonProperties();
        properties.setSpecies(form.getSpecies().getResourceIdentifier().toString());
        properties.setShiny(shiny);
        properties.setLevel(1);
        Pokemon pokemon = properties.create();
        // Assign the exact FormData rather than matching by name; Cobblemon versions differ in
        // whether properties match forms by display name or showdown id.
        if (pokemon.getForm() != form) {
            pokemon.setForm(form);
        }
        return pokemon;
    }
}
