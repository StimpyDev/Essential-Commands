package com.fibermc.essentialcommands.commands;

import java.util.Optional;

import com.fibermc.essentialcommands.access.ServerPlayerEntityAccess;
import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.teleportation.TeleportRequest;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class TeleportAcceptCommand extends TeleportResponseCommand {
    protected int exec(CommandContext<CommandSourceStack> context, ServerPlayer respondingPlayer, ServerPlayer requesterPlayer) {
        var senderPlayerData = PlayerData.access(respondingPlayer);

        // Check if the requester is still online
        if (requesterPlayer == null) {
            senderPlayerData.sendError("cmd.tpa_reply.error.no_request_from_target");
            return -1;
        }

        var targetPlayerData = ((ServerPlayerEntityAccess) requesterPlayer).ec$getPlayerData();

        // Identify if target player did indeed request to teleport.
        Optional<TeleportRequest> teleportRequest = targetPlayerData.getSentTeleportRequests()
            .getRequestToPlayer(senderPlayerData);

        if (teleportRequest.isPresent() && teleportRequest.get().getTargetPlayer().equals(respondingPlayer)) {

            // Inform target player that teleport has been accepted
            targetPlayerData.sendMessage("cmd.tpaccept.feedback");

            // Conduct teleportation
            teleportRequest.get().queue();

            // Send message to command sender confirming acceptance
            senderPlayerData.sendMessage("cmd.tpaccept.feedback");

            // Remove the tp request
            teleportRequest.get().end();

            return SINGLE_SUCCESS;
        } else {
            senderPlayerData.sendError("cmd.tpa_reply.error.no_request_from_target");
            return -1;
        }
    }
}
