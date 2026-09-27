package net.eligibbs.cbi.mixin;

import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.battles.ChallengeManager;
import com.cobblemon.mod.common.net.messages.client.battle.BattleChallengeNotificationPacket;
import net.eligibbs.cbi.battle.Cbi2v1Challenge;
import net.eligibbs.cbi.battle.CbiBattleBuilder;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

/**
 * Teaches Cobblemon's {@link ChallengeManager} about {@link Cbi2v1Challenge}: validation on
 * accept, starting the asymmetric battle, and notifying every member of the team side.
 */
@Mixin(value = ChallengeManager.class, remap = false)
public abstract class ChallengeManagerMixin {

    @Shadow @Final private static Map<UUID, UUID> selectedLead;

    @Shadow
    private boolean validateDimension(Collection<? extends ServerPlayer> players) {
        return false;
    }

    @Shadow
    private ServerPlayer validateProximity(Collection<? extends ServerPlayer> players) {
        return null;
    }

    @Inject(
            method = "notificationPacket(Lcom/cobblemon/mod/common/battles/ChallengeManager$BattleChallenge;)Lcom/cobblemon/mod/common/api/net/NetworkPacket;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cbi$notificationPacket(ChallengeManager.BattleChallenge request, CallbackInfoReturnable<NetworkPacket<?>> cir) {
        if (request instanceof Cbi2v1Challenge challenge) {
            // The client keys pending challenges by the challenging *players*, so a team sender must
            // list both members (the default would list the team id, which no player entity has).
            cir.setReturnValue(new BattleChallengeNotificationPacket(
                    challenge.getRequestID(),
                    challenge.getSenderID(),
                    challenge.getSenderPlayerIds(),
                    challenge.getBattleFormat(),
                    challenge.getExpiryTime()
            ));
        }
    }

    @Inject(
            method = "canAccept(Lcom/cobblemon/mod/common/battles/ChallengeManager$BattleChallenge;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cbi$canAccept(ChallengeManager.BattleChallenge request, CallbackInfoReturnable<Boolean> cir) {
        if (!(request instanceof Cbi2v1Challenge challenge)) {
            return;
        }
        cir.setReturnValue(cbi$validate2v1(challenge));
    }

    @Inject(
            method = "onAccept(Lcom/cobblemon/mod/common/battles/ChallengeManager$BattleChallenge;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cbi$onAccept(ChallengeManager.BattleChallenge request, CallbackInfo ci) {
        if (!(request instanceof Cbi2v1Challenge challenge)) {
            return;
        }
        List<ServerPlayer> team = challenge.getTeamPlayers();
        ServerPlayer solo = challenge.getSoloPlayer();

        List<UUID> leads = new ArrayList<>();
        for (ServerPlayer player : team) {
            leads.add(selectedLead.get(player.getUUID()));
        }
        leads.add(selectedLead.get(solo.getUUID()));

        // pvp2v1 reports start errors to the players itself.
        CbiBattleBuilder.pvp2v1(team, solo, leads, challenge.getBattleFormat());
        ci.cancel();
    }

    private boolean cbi$validate2v1(Cbi2v1Challenge challenge) {
        // The team may have changed since the challenge was sent.
        if (!Cbi2v1Challenge.isFullTeam(challenge.getTeam()) || !Cbi2v1Challenge.isPairing(challenge.getSender(), challenge.getReceiver())) {
            challenge.notifySender(true, "error.missing_team");
            challenge.notifyReceiver(true, "error.missing_team");
            return false;
        }

        List<ServerPlayer> allPlayers = challenge.getAllPlayers();

        // The solo player fields two Pokémon at once; each team member fields one.
        if (Cbi2v1Challenge.countAlivePokemon(challenge.getSoloPlayer()) < 2) {
            challenge.notifySender(true, "error.insufficient_pokemon");
            challenge.notifyReceiver(true, "error.insufficient_pokemon");
            return false;
        }
        for (ServerPlayer member : challenge.getTeamPlayers()) {
            if (Cbi2v1Challenge.countAlivePokemon(member) < 1) {
                challenge.notifySender(true, "error.insufficient_pokemon");
                challenge.notifyReceiver(true, "error.insufficient_pokemon");
                return false;
            }
        }

        if (validateDimension(allPlayers)) {
            challenge.notifySender(true, "error.player_different_dimension");
            challenge.notifyReceiver(true, "error.player_different_dimension");
            return false;
        }

        ServerPlayer farAwayPlayer = validateProximity(allPlayers);
        if (farAwayPlayer != null) {
            challenge.notifySender(true, "error.player_distance", farAwayPlayer.getName().copy());
            challenge.notifyReceiver(true, "error.player_distance", farAwayPlayer.getName().copy());
            return false;
        }

        return true;
    }
}
