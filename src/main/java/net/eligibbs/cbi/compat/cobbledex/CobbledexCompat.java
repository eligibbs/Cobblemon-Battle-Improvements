package net.eligibbs.cbi.compat.cobbledex;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.platform.events.PlatformEvents;
import com.cobblemon.mod.common.platform.events.ServerPlayerEvent;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import com.rafacasari.mod.cobbledex.Cobbledex;
import com.rafacasari.mod.cobbledex.api.CobbledexCoopDiscovery;
import com.rafacasari.mod.cobbledex.api.CobbledexDiscovery;
import com.rafacasari.mod.cobbledex.api.CobbledexEvents;
import com.rafacasari.mod.cobbledex.api.classes.DiscoveryRegister;
import com.rafacasari.mod.cobbledex.api.events.DiscoveryEvent;
import net.eligibbs.cbi.CbiMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Bridges Cobbledex (https://github.com/Rafacasari/cobbledex) into the Cobblemon Pokédex:
 * whenever a Pokémon is unlocked in a player's Cobbledex it is unlocked in their Cobblemon
 * Pokédex as well.
 * <ul>
 *   <li>Live: Cobbledex's {@code NEW_FORM_DISCOVERED} / {@code NEW_FORM_CAUGHT} events mark the
 *       form as seen / owned in the Cobblemon Pokédex of the discovering player.</li>
 *   <li>Login: the player's whole Cobbledex collection is replayed into their Cobblemon Pokédex
 *       so progress made before this integration (or while offline in co-op mode) is picked up.</li>
 *   <li>Co-op mode: Cobbledex shares one collection between everyone, so discoveries are
 *       mirrored to every online player and the shared collection is used for the login sync.</li>
 * </ul>
 * This class references Cobbledex classes and must only be loaded when the {@value #MOD_ID} mod
 * is present (see {@code CbiMod#onCommonSetup}).
 */
public final class CobbledexCompat {

    public static final String MOD_ID = "cobbledex";

    private CobbledexCompat() {
    }

    /** Must run after all mods are constructed (e.g. from common setup) so Cobbledex's event buses exist. */
    public static void init() {
        // Explicit Consumer types pick Cobblemon's Java-friendly subscribe overload over the Kotlin one.
        Consumer<DiscoveryEvent.OnFormDiscoveryEvent> onDiscovered = CobbledexCompat::onFormDiscovered;
        Consumer<ServerPlayerEvent.Login> onLogin = login -> syncPlayer(login.getPlayer());
        CobbledexEvents.NEW_FORM_DISCOVERED.subscribe(Priority.LOWEST, onDiscovered);
        CobbledexEvents.NEW_FORM_CAUGHT.subscribe(Priority.LOWEST, onDiscovered);
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe(Priority.LOWEST, onLogin);
        CbiMod.LOGGER.info("Cobbledex detected: Cobbledex discoveries will also unlock Cobblemon Pokédex entries.");
    }

    private static void onFormDiscovered(@NotNull DiscoveryEvent.OnFormDiscoveryEvent event) {
        DiscoveryRegister register = event.getRegister();
        boolean caught = register.getStatus() == DiscoveryRegister.RegisterType.CAUGHT;
        boolean shiny = register.isShiny();

        if (isCoopMode()) {
            // The shared collection just changed for everyone who is online; offline players catch up at login.
            MinecraftServer server = event.getPlayer().getServer();
            List<ServerPlayer> players = server != null ? server.getPlayerList().getPlayers() : List.of(event.getPlayer());
            for (ServerPlayer player : players) {
                unlockAndSave(player, event.getForm(), shiny, caught);
            }
        } else {
            unlockAndSave(event.getPlayer(), event.getForm(), shiny, caught);
        }
    }

    private static void unlockAndSave(@NotNull ServerPlayer player, @NotNull FormData form, boolean shiny, boolean caught) {
        try {
            if (CobblemonPokedexUnlocker.unlock(player, form, shiny, caught)) {
                CobblemonPokedexUnlocker.save(player);
            }
        } catch (Exception e) {
            CbiMod.LOGGER.error("Failed to mirror Cobbledex discovery of {} into {}'s Pokédex", form.showdownId(), player.getGameProfile().getName(), e);
        }
    }

    /** Replays the collection the player sees in Cobbledex (personal or shared) into their Cobblemon Pokédex. */
    public static void syncPlayer(@NotNull ServerPlayer player) {
        Map<String, Map<String, DiscoveryRegister>> registers;
        try {
            registers = collectionFor(player);
        } catch (Exception e) {
            CbiMod.LOGGER.error("Failed to read {}'s Cobbledex collection", player.getGameProfile().getName(), e);
            return;
        }
        if (registers == null || registers.isEmpty()) {
            return;
        }

        Map<String, Species> speciesByShowdownId = speciesByShowdownId();
        int unlocked = 0;
        // Iterate over snapshots: unlocking fires Cobblemon events that other mods may react to.
        for (Map.Entry<String, Map<String, DiscoveryRegister>> speciesEntry : new ArrayList<>(registers.entrySet())) {
            Species species = speciesByShowdownId.get(speciesEntry.getKey());
            if (species == null || speciesEntry.getValue() == null) {
                continue;
            }
            for (Map.Entry<String, DiscoveryRegister> formEntry : new ArrayList<>(speciesEntry.getValue().entrySet())) {
                FormData form = findForm(species, formEntry.getKey());
                DiscoveryRegister register = formEntry.getValue();
                if (form == null || register == null) {
                    continue;
                }
                boolean caught = register.getStatus() == DiscoveryRegister.RegisterType.CAUGHT;
                try {
                    if (CobblemonPokedexUnlocker.unlock(player, form, register.isShiny(), caught)) {
                        unlocked++;
                    }
                } catch (Exception e) {
                    CbiMod.LOGGER.error("Failed to mirror Cobbledex entry {} into {}'s Pokédex", form.showdownId(), player.getGameProfile().getName(), e);
                }
            }
        }

        if (unlocked > 0) {
            CobblemonPokedexUnlocker.save(player);
            CbiMod.LOGGER.info("Unlocked {} Cobblemon Pokédex form(s) for {} from their Cobbledex collection", unlocked, player.getGameProfile().getName());
        }
    }

    private static boolean isCoopMode() {
        try {
            return Cobbledex.INSTANCE.getConfig().getCoopMode();
        } catch (Exception e) {
            // Config not initialised yet; treat as the default (personal collections).
            return false;
        }
    }

    @Nullable
    private static Map<String, Map<String, DiscoveryRegister>> collectionFor(@NotNull ServerPlayer player) {
        if (isCoopMode()) {
            CobbledexCoopDiscovery coop = CobbledexCoopDiscovery.Companion.getDiscovery();
            return coop != null ? coop.getRegisters() : null;
        }
        return CobbledexDiscovery.Companion.getPlayerData(player).getRegisters();
    }

    /** Cobbledex keys its collection by {@code Species.showdownId()}. */
    @NotNull
    private static Map<String, Species> speciesByShowdownId() {
        Map<String, Species> map = new HashMap<>();
        for (Species species : PokemonSpecies.getSpecies()) {
            map.put(species.showdownId(), species);
        }
        return map;
    }

    /** Cobbledex keys forms by {@code FormData.formOnlyShowdownId()}. */
    @Nullable
    private static FormData findForm(@NotNull Species species, @NotNull String formOnlyShowdownId) {
        if (species.getStandardForm().formOnlyShowdownId().equals(formOnlyShowdownId)) {
            return species.getStandardForm();
        }
        for (FormData form : species.getForms()) {
            if (form.formOnlyShowdownId().equals(formOnlyShowdownId)) {
                return form;
            }
        }
        return null;
    }
}
