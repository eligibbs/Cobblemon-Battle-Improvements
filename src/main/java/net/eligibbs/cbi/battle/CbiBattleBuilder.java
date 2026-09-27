package net.eligibbs.cbi.battle;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class CbiBattleBuilder {

    public static BattleStartResult pvp2v1(
            @NotNull List<ServerPlayer> teamPlayers,
            @NotNull ServerPlayer soloPlayer,
            @Nullable BattleFormat battleFormat
    ) {
        return pvp2v1(teamPlayers, soloPlayer, Collections.emptyList(), battleFormat);
    }

    /**
     * @param leadingPokemon lead Pokémon uuids in the order [team player 1, team player 2, solo player];
     *                       entries may be {@code null} (or missing) to let the party order decide.
     */
    public static BattleStartResult pvp2v1(
            @NotNull List<ServerPlayer> teamPlayers,
            @NotNull ServerPlayer soloPlayer,
            @NotNull List<UUID> leadingPokemon,
            @Nullable BattleFormat battleFormat
    ) {
        return pvp2v1(
                teamPlayers,
                soloPlayer,
                leadingPokemon,
                battleFormat != null ? battleFormat : BattleFormat.Companion.getGEN_9_MULTI(),
                false,
                false,
                PlayerExtensionsKt::party
        );
    }

    public static BattleStartResult pvp2v1(
            @NotNull List<ServerPlayer> teamPlayers,
            @NotNull ServerPlayer soloPlayer,
            @NotNull List<UUID> leadingPokemon,
            @NotNull BattleFormat battleFormat,
            boolean heal,
            boolean didSleep,
            @NotNull Function<ServerPlayer, PartyStore> partyAccessor
    ) {
        if (teamPlayers.size() != 2) {
            throw new IllegalArgumentException("teamPlayers must contain exactly 2 players for a 2v1 battle");
        }

        List<ServerPlayer> allPlayers = new ArrayList<>();
        allPlayers.addAll(teamPlayers);
        allPlayers.add(soloPlayer);

        int adjustLevel = battleFormat.getAdjustLevel();

        // 1. Build team player 1 actor
        ServerPlayer p1 = teamPlayers.get(0);
        UUID lead1 = !leadingPokemon.isEmpty() ? leadingPokemon.get(0) : null;
        List<BattlePokemon> p1Team = partyAccessor.apply(p1).toBattleTeam(heal, didSleep, lead1);
        if (adjustLevel > 0) {
            for (BattlePokemon bp : p1Team) {
                bp.getEffectedPokemon().setLevel(adjustLevel);
            }
        }
        PlayerBattleActor actor1 = new PlayerBattleActor(p1.getUUID(), p1Team);
        actor1.setBattleTheme(PlayerExtensionsKt.getBattleTheme(soloPlayer));

        // 2. Build team player 2 actor
        ServerPlayer p2 = teamPlayers.get(1);
        UUID lead2 = leadingPokemon.size() > 1 ? leadingPokemon.get(1) : null;
        List<BattlePokemon> p2Team = partyAccessor.apply(p2).toBattleTeam(heal, didSleep, lead2);
        if (adjustLevel > 0) {
            for (BattlePokemon bp : p2Team) {
                bp.getEffectedPokemon().setLevel(adjustLevel);
            }
        }
        PlayerBattleActor actor2 = new PlayerBattleActor(p2.getUUID(), p2Team);
        actor2.setBattleTheme(PlayerExtensionsKt.getBattleTheme(soloPlayer));

        // 3. Build solo player actor
        UUID leadSolo = leadingPokemon.size() > 2 ? leadingPokemon.get(2) : null;
        List<BattlePokemon> soloTeam = partyAccessor.apply(soloPlayer).toBattleTeam(heal, didSleep, leadSolo);
        if (adjustLevel > 0) {
            for (BattlePokemon bp : soloTeam) {
                bp.getEffectedPokemon().setLevel(adjustLevel);
            }
        }
        PlayerBattleActor soloActor = new PlayerBattleActor(soloPlayer.getUUID(), soloTeam);
        soloActor.setBattleTheme(PlayerExtensionsKt.getBattleTheme(p1));

        // Validate party requirements:
        // Team player 1 needs at least 1 alive Pokémon
        // Team player 2 needs at least 1 alive Pokémon
        // Solo player needs at least 2 alive Pokémon (as they control 2 active slots simultaneously)
        long alive1 = p1Team.stream().filter(bp -> !bp.getEffectedPokemon().isFainted()).count();
        long alive2 = p2Team.stream().filter(bp -> !bp.getEffectedPokemon().isFainted()).count();
        long aliveSolo = soloTeam.stream().filter(bp -> !bp.getEffectedPokemon().isFainted()).count();

        BattleActorErrors actorErrors = new BattleActorErrors();
        if (alive1 < 1) {
            Set<BattleStartError> errors = new HashSet<>();
            errors.add(BattleStartError.Companion.insufficientPokemon(p1, (int) alive1, 1));
            actorErrors.put(actor1, errors);
        }
        if (alive2 < 1) {
            Set<BattleStartError> errors = new HashSet<>();
            errors.add(BattleStartError.Companion.insufficientPokemon(p2, (int) alive2, 1));
            actorErrors.put(actor2, errors);
        }
        if (aliveSolo < 2) {
            Set<BattleStartError> errors = new HashSet<>();
            errors.add(BattleStartError.Companion.insufficientPokemon(soloPlayer, (int) aliveSolo, 2));
            actorErrors.put(soloActor, errors);
        }

        // Check if any player is already in battle
        for (ServerPlayer player : allPlayers) {
            if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
                BattleActor matchedActor = player.getUUID().equals(p1.getUUID()) ? actor1 :
                        player.getUUID().equals(p2.getUUID()) ? actor2 : soloActor;
                Set<BattleStartError> errors = actorErrors.getOrDefault(matchedActor, new HashSet<>());
                errors.add(BattleStartError.Companion.alreadyInBattle(player));
                actorErrors.put(matchedActor, errors);
            }
        }

        if (!actorErrors.isEmpty()) {
            ErroredBattleStart errorResult = new ErroredBattleStart(Collections.emptySet(), actorErrors);
            errorResult.sendTo(allPlayers, comp -> comp);
            return errorResult;
        }

        BattleSide side1 = new BattleSide(actor1, actor2);
        BattleSide side2 = new BattleSide(soloActor);

        return BattleRegistry.startBattle(battleFormat, side1, side2, true);
    }
}
