package com.fibermc.essentialcommands.commands;

import java.util.Optional;

import com.fibermc.essentialcommands.access.ServerPlayerEntityAccess;
import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.teleportation.TeleportRequest;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class TeleportAcceptCommand extends TeleportResponseCommand {
    
    @Override
    protected int exec(CommandContext<CommandSourceStack> context, ServerPlayer respondingPlayer, ServerPlayer requesterPlayer) {
        var respondingPlayerData = PlayerData.access(respondingPlayer);

        if (requesterPlayer == null) {
            respondingPlayerData.sendError("cmd.tpa_reply.error.no_request_from_target");
            return -1;
        }

        var requesterPlayerData = ((ServerPlayerEntityAccess) requesterPlayer).ec$getPlayerData();

        Optional<TeleportRequest> teleportRequest = requesterPlayerData.getSentTeleportRequests()
            .getRequestToPlayer(respondingPlayerData);

        if (teleportRequest.isPresent() && teleportRequest.get().getTargetPlayer().equals(respondingPlayer)) {

            requesterPlayerData.sendMessage(
                "cmd.tpaccept.feedback",
                respondingPlayer.getDisplayName()
            );

            teleportRequest.get().queue();

            respondingPlayerData.sendMessage(
                "cmd.tpaccept.feedback",
                requesterPlayer.getDisplayName()
            );

            teleportRequest.get().end();

            return SINGLE_SUCCESS;
        } else {
            respondingPlayerData.sendError("cmd.tpa_reply.error.no_request_from_target");
            return -1;
        }
    }
}
