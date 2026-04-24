package com.fibermc.essentialcommands.commands;

import java.util.Optional;

import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.teleportation.TeleportRequest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class TeleportDenyCommand extends TeleportResponseCommand {
    
    @Override
    protected int exec(CommandContext<CommandSourceStack> context, ServerPlayer respondingPlayer, ServerPlayer requesterPlayer) {
        var respondingPlayerData = PlayerData.access(respondingPlayer);

        if (requesterPlayer == null) {
            respondingPlayerData.sendError("cmd.tpa_reply.error.no_request_from_target");
            return -1;
        }

        var requesterPlayerData = PlayerData.access(requesterPlayer);

        Optional<TeleportRequest> teleportRequest = requesterPlayerData.getSentTeleportRequests()
            .getRequestToPlayer(respondingPlayerData);

        if (teleportRequest.isEmpty()) {
            respondingPlayerData.sendCommandError("cmd.tpa_reply.error.no_request_from_target");
            return -1;
        }

        requesterPlayerData.sendMessage(
            "cmd.tpdeny.feedback",
            respondingPlayer.getDisplayName()
        );
        
        respondingPlayerData.sendMessage(
            "cmd.tpdeny.feedback",
            requesterPlayer.getDisplayName()
        );

        teleportRequest.get().end();
        
        return Command.SINGLE_SUCCESS;
    }
}
