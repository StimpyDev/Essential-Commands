package com.fibermc.essentialcommands.commands;

import java.util.Optional;

import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.teleportation.TeleportRequest;
import com.fibermc.essentialcommands.text.ECText;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class TeleportDenyCommand extends TeleportResponseCommand {
    
    public TeleportDenyCommand() {}

    @Override
    protected int exec(CommandContext<CommandSourceStack> context, ServerPlayer respondingPlayer, ServerPlayer requesterPlayer) {
        var respondingPlayerData = PlayerData.access(respondingPlayer);

        if (requesterPlayer == null) {
            respondingPlayer.sendSystemMessage(
                ECText.getInstance().getText("cmd.tpa_reply.error.no_request_from_target")
                    .withStyle(ChatFormatting.RED)
            );
            return -1;
        }

        var requesterPlayerData = PlayerData.access(requesterPlayer);

        Optional<TeleportRequest> teleportRequest = requesterPlayerData.getSentTeleportRequests()
            .getRequestToPlayer(respondingPlayerData);

        if (teleportRequest.isEmpty()) {
            respondingPlayer.sendSystemMessage(
                ECText.getInstance().getText("cmd.tpa_reply.error.no_request_from_target")
                    .withStyle(ChatFormatting.RED)
            );
            return -1;
        }
        
        requesterPlayer.sendSystemMessage(
            ECText.getInstance().getText("cmd.tpdeny.feedback", respondingPlayer.getDisplayName())
                .withStyle(ChatFormatting.RED)
        );
        
        respondingPlayer.sendSystemMessage(
            ECText.getInstance().getText("cmd.tpdeny.success", requesterPlayer.getDisplayName())
                .withStyle(ChatFormatting.GREEN)
        );

        teleportRequest.get().end();
        
        return Command.SINGLE_SUCCESS;
    }
}
