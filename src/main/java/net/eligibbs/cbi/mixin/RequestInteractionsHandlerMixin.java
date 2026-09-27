package net.eligibbs.cbi.mixin;

import com.cobblemon.mod.common.battles.TeamManager;
import com.cobblemon.mod.common.net.messages.client.PlayerInteractOptionsPacket;
import com.cobblemon.mod.common.net.messages.server.RequestPlayerInteractionsPacket;
import com.cobblemon.mod.common.net.serverhandling.RequestInteractionsHandler;
import net.eligibbs.cbi.battle.Cbi2v1Challenge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.EnumMap;
import java.util.UUID;

/**
 * Adds the "Multi" battle option to the player interaction wheel for a team-vs-solo pairing.
 * Cobblemon only offers it when both players are in (different) teams, so without this a team
 * could never challenge a single player through the normal UI.
 */
@Mixin(value = RequestInteractionsHandler.class, remap = false)
public abstract class RequestInteractionsHandlerMixin {

    @Redirect(
            method = "handle(Lcom/cobblemon/mod/common/net/messages/server/RequestPlayerInteractionsPacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/util/EnumMap;Ljava/util/UUID;ILjava/util/UUID;)Lcom/cobblemon/mod/common/net/messages/client/PlayerInteractOptionsPacket;"
            )
    )
    private PlayerInteractOptionsPacket cbi$addMultiBattleFor2v1(
            EnumMap<PlayerInteractOptionsPacket.Options, PlayerInteractOptionsPacket.OptionStatus> options,
            UUID targetId,
            int numericTargetId,
            UUID selectedPokemonId,
            RequestPlayerInteractionsPacket packet,
            MinecraftServer server,
            ServerPlayer player
    ) {
        Player target = player.level().getPlayerByUUID(targetId);
        if (target instanceof ServerPlayer serverTarget) {
            cbi$offerMultiBattle(options, player, serverTarget);
        }
        return new PlayerInteractOptionsPacket(options, targetId, numericTargetId, selectedPokemonId);
    }

    private static void cbi$offerMultiBattle(
            EnumMap<PlayerInteractOptionsPacket.Options, PlayerInteractOptionsPacket.OptionStatus> options,
            ServerPlayer player,
            ServerPlayer target
    ) {
        // Only when the regular battle checks (distance, line of sight, not already battling) passed.
        if (options.get(PlayerInteractOptionsPacket.Options.SINGLE_BATTLE) != PlayerInteractOptionsPacket.OptionStatus.AVAILABLE) {
            return;
        }
        if (options.containsKey(PlayerInteractOptionsPacket.Options.MULTI_BATTLE)
                || !Cbi2v1Challenge.isPairing(player, target)) {
            return;
        }

        TeamManager.MultiBattleTeam team = TeamManager.INSTANCE.getTeam(player);
        ServerPlayer solo = team != null ? target : player;
        TeamManager.MultiBattleTeam pairTeam = team != null ? team : TeamManager.INSTANCE.getTeam(target);
        if (pairTeam == null) {
            return;
        }

        boolean enoughPokemon = Cbi2v1Challenge.countAlivePokemon(solo) >= 2
                && pairTeam.getTeamPlayers().stream().allMatch(member -> Cbi2v1Challenge.countAlivePokemon(member) >= 1);
        options.put(
                PlayerInteractOptionsPacket.Options.MULTI_BATTLE,
                enoughPokemon ? PlayerInteractOptionsPacket.OptionStatus.AVAILABLE : PlayerInteractOptionsPacket.OptionStatus.INSUFFICIENT_POKEMON
        );
    }
}
