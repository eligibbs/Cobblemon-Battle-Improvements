package net.eligibbs.cbi.mixin;

import com.cobblemon.mod.common.battles.ChallengeManager;
import com.cobblemon.mod.common.net.messages.server.BattleChallengePacket;
import com.cobblemon.mod.common.net.serverhandling.ChallengeHandler;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import net.eligibbs.cbi.battle.Cbi2v1Challenge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes a "Multi" challenge sent between a team member and a solo player into a
 * {@link Cbi2v1Challenge}. Cobblemon would otherwise try to build a
 * {@link ChallengeManager.MultiBattleChallenge}, which throws when either side has no team.
 */
@Mixin(value = ChallengeHandler.class, remap = false)
public abstract class ChallengeHandlerMixin {

    @Inject(
            method = "handle(Lcom/cobblemon/mod/common/net/messages/server/BattleChallengePacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cbi$send2v1Challenge(BattleChallengePacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (!Cbi2v1Challenge.isMultiFormat(packet.getBattleFormat())) {
            return;
        }
        Entity targeted = player.level().getEntity(packet.getTargetedEntityId());
        if (!(targeted instanceof ServerPlayer target)) {
            return;
        }

        Pokemon lead = PlayerExtensionsKt.party(player).get(packet.getSelectedPokemonId());
        if (lead == null) {
            // Same as Cobblemon: an invalid lead id means the request is dropped.
            ci.cancel();
            return;
        }

        Cbi2v1Challenge challenge = Cbi2v1Challenge.tryCreate(player, target, lead.getUuid(), packet.getBattleFormat());
        if (challenge == null) {
            return;
        }

        ChallengeManager.INSTANCE.setLead(player, lead.getUuid());
        ChallengeManager.INSTANCE.sendRequest(challenge);
        ci.cancel();
    }
}
