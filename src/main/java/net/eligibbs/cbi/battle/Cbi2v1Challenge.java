package net.eligibbs.cbi.battle;

import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleTypes;
import com.cobblemon.mod.common.battles.ChallengeManager;
import com.cobblemon.mod.common.battles.TeamManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A battle challenge between a {@link TeamManager.MultiBattleTeam} of two players and a single
 * player who is not in a team. Cobblemon's own {@link ChallengeManager.MultiBattleChallenge}
 * requires both parties to be teams, so this fills the gap for the 2v1 format.
 * <p>
 * The team side is addressed by its team id (so either team member can respond and team
 * disbanding cancels the request, exactly like a 2v2 challenge) while the solo side is
 * addressed by the player's uuid.
 */
public final class Cbi2v1Challenge extends ChallengeManager.BattleChallenge {

    public static final int EXPIRY_SECONDS = 20;

    private final ServerPlayer sender;
    private final ServerPlayer receiver;
    private final UUID selectedPokemonId;
    private final BattleFormat battleFormat;
    private final TeamManager.MultiBattleTeam team;
    private final boolean senderIsTeam;

    private Cbi2v1Challenge(
            @NotNull ServerPlayer sender,
            @NotNull ServerPlayer receiver,
            @NotNull UUID selectedPokemonId,
            @NotNull BattleFormat battleFormat,
            @NotNull TeamManager.MultiBattleTeam team,
            boolean senderIsTeam
    ) {
        this.sender = sender;
        this.receiver = receiver;
        this.selectedPokemonId = selectedPokemonId;
        this.battleFormat = battleFormat;
        this.team = team;
        this.senderIsTeam = senderIsTeam;
    }

    /**
     * Creates a 2v1 challenge if exactly one of the two players belongs to a full team and the
     * other belongs to none. Returns {@code null} for any other pairing so Cobblemon's default
     * handling (1v1 or 2v2) applies.
     */
    @Nullable
    public static Cbi2v1Challenge tryCreate(
            @NotNull ServerPlayer sender,
            @NotNull ServerPlayer receiver,
            @NotNull UUID selectedPokemonId,
            @NotNull BattleFormat battleFormat
    ) {
        TeamManager.MultiBattleTeam senderTeam = TeamManager.INSTANCE.getTeam(sender);
        TeamManager.MultiBattleTeam receiverTeam = TeamManager.INSTANCE.getTeam(receiver);
        if (isFullTeam(senderTeam) && receiverTeam == null) {
            return new Cbi2v1Challenge(sender, receiver, selectedPokemonId, battleFormat, senderTeam, true);
        }
        if (senderTeam == null && isFullTeam(receiverTeam)) {
            return new Cbi2v1Challenge(sender, receiver, selectedPokemonId, battleFormat, receiverTeam, false);
        }
        return null;
    }

    public static boolean isFullTeam(@Nullable TeamManager.MultiBattleTeam team) {
        return team != null && team.getTeamPlayers().size() == TeamManager.MAX_TEAM_MEMBER_COUNT;
    }

    /** Whether the two players form a team-vs-solo pairing that can start a 2v1 battle. */
    public static boolean isPairing(@NotNull ServerPlayer a, @NotNull ServerPlayer b) {
        TeamManager.MultiBattleTeam teamA = TeamManager.INSTANCE.getTeam(a);
        TeamManager.MultiBattleTeam teamB = TeamManager.INSTANCE.getTeam(b);
        return (isFullTeam(teamA) && teamB == null) || (teamA == null && isFullTeam(teamB));
    }

    public static boolean isMultiFormat(@NotNull BattleFormat format) {
        return format.getBattleType().getName().equals(BattleTypes.INSTANCE.getMULTI().getName());
    }

    public static long countAlivePokemon(@NotNull ServerPlayer player) {
        long count = 0;
        for (Pokemon pokemon : PlayerExtensionsKt.party(player)) {
            if (pokemon != null && !pokemon.isFainted()) {
                count++;
            }
        }
        return count;
    }

    @NotNull
    public TeamManager.MultiBattleTeam getTeam() {
        return team;
    }

    @NotNull
    public List<ServerPlayer> getTeamPlayers() {
        return new ArrayList<>(team.getTeamPlayers());
    }

    @NotNull
    public ServerPlayer getSoloPlayer() {
        return senderIsTeam ? receiver : sender;
    }

    @NotNull
    public List<ServerPlayer> getSenderPlayers() {
        return senderIsTeam ? getTeamPlayers() : Collections.singletonList(sender);
    }

    @NotNull
    public List<ServerPlayer> getReceiverPlayers() {
        return senderIsTeam ? Collections.singletonList(receiver) : getTeamPlayers();
    }

    @NotNull
    public List<ServerPlayer> getAllPlayers() {
        List<ServerPlayer> players = getTeamPlayers();
        players.add(getSoloPlayer());
        return players;
    }

    @NotNull
    public List<UUID> getSenderPlayerIds() {
        return getSenderPlayers().stream().map(ServerPlayer::getUUID).toList();
    }

    @NotNull
    @Override
    public String getKey() {
        // Re-use Cobblemon's "multi" challenge lang entries (sent/received/accept/decline/...).
        return "challenge.multi";
    }

    @NotNull
    @Override
    public ServerPlayer getSender() {
        return sender;
    }

    @NotNull
    @Override
    public ServerPlayer getReceiver() {
        return receiver;
    }

    @NotNull
    @Override
    public UUID getSelectedPokemonId() {
        return selectedPokemonId;
    }

    @NotNull
    @Override
    public BattleFormat getBattleFormat() {
        return battleFormat;
    }

    @Override
    public int getExpiryTime() {
        return EXPIRY_SECONDS;
    }

    @NotNull
    @Override
    public UUID getSenderID() {
        return senderIsTeam ? team.getTeamID() : sender.getUUID();
    }

    @NotNull
    @Override
    public UUID getReceiverID() {
        return senderIsTeam ? receiver.getUUID() : team.getTeamID();
    }

    @Override
    public void sendToSender(@NotNull NetworkPacket<?> packet) {
        CobblemonNetwork.INSTANCE.sendPacketToPlayers(getSenderPlayers(), packet);
    }

    @Override
    public void sendToReceiver(@NotNull NetworkPacket<?> packet) {
        CobblemonNetwork.INSTANCE.sendPacketToPlayers(getReceiverPlayers(), packet);
    }

    @Override
    public void notifySender(boolean error, @NotNull String langKey, @NotNull Object... params) {
        for (ServerPlayer player : getSenderPlayers()) {
            notify(player, error, getKey() + "." + langKey, params);
        }
    }

    @Override
    public void notifyReceiver(boolean error, @NotNull String langKey, @NotNull Object... params) {
        for (ServerPlayer player : getReceiverPlayers()) {
            notify(player, error, getKey() + "." + langKey, params);
        }
    }
}
