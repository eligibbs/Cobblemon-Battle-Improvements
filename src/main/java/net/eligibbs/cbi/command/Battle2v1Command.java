package net.eligibbs.cbi.command;

import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.eligibbs.cbi.battle.CbiBattleBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;

public class Battle2v1Command {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("battle2v1")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("teamPlayer1", EntityArgument.player())
                                .then(Commands.argument("teamPlayer2", EntityArgument.player())
                                        .then(Commands.argument("soloPlayer", EntityArgument.player())
                                                .executes(Battle2v1Command::executeStart2v1Battle)
                                        )
                                )
                        )
        );

        dispatcher.register(
                Commands.literal("cbi")
                        .then(Commands.literal("battle2v1")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("teamPlayer1", EntityArgument.player())
                                        .then(Commands.argument("teamPlayer2", EntityArgument.player())
                                                .then(Commands.argument("soloPlayer", EntityArgument.player())
                                                        .executes(Battle2v1Command::executeStart2v1Battle)
                                                )
                                        )
                                )
                        )
        );
    }

    private static int executeStart2v1Battle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer p1 = EntityArgument.getPlayer(context, "teamPlayer1");
        ServerPlayer p2 = EntityArgument.getPlayer(context, "teamPlayer2");
        ServerPlayer solo = EntityArgument.getPlayer(context, "soloPlayer");

        if (p1.getUUID().equals(p2.getUUID()) || p1.getUUID().equals(solo.getUUID()) || p2.getUUID().equals(solo.getUUID())) {
            context.getSource().sendFailure(Component.literal("All 3 players in a 2v1 battle must be distinct."));
            return 0;
        }

        List<ServerPlayer> team = Arrays.asList(p1, p2);
        BattleStartResult result = CbiBattleBuilder.pvp2v1(team, solo, BattleFormat.Companion.getGEN_9_MULTI());

        if (result instanceof SuccessfulBattleStart) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Started 2v1 battle: [" + p1.getScoreboardName() + " & " + p2.getScoreboardName() + "] vs [" + solo.getScoreboardName() + "]"
            ), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to start 2v1 battle. Check player status and healthy Pokémon."));
            return 0;
        }
    }
}
