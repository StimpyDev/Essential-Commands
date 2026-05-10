package com.fibermc.essentialcommands.commands;

import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.teleportation.TeleportRequest;
import com.fibermc.essentialcommands.text.ECText;
import com.fibermc.essentialcommands.text.TextFormatType;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType; // TOEGEVOEGD
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component; // TOEGEVOEGD
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.UUID;

public abstract class TeleportResponseCommand implements Command<CommandSourceStack> {

    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "target_player");
        
        ServerPlayer targetPlayer = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);

        if (targetPlayer == null) {
            context.getSource().sendFailure(Component.literal("Speler '" + targetName + "' is niet online."));
            return 0;
        }

        return exec(
            context,
            context.getSource().getPlayerOrException(),
            targetPlayer
        );
    }

    public int runDefault(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var respondingPlayer = context.getSource().getPlayerOrException();
        var respondingPlayerData = PlayerData.access(respondingPlayer);
        var ecText = ECText.access(respondingPlayer);
        LinkedHashMap<UUID, TeleportRequest> incomingTeleportRequests = respondingPlayerData.getIncomingTeleportRequests();

        if (incomingTeleportRequests.size() > 1) {
            throw CommandUtil.createSimpleException(
                ecText.getText("cmd.tpa_reply.error.shortcut_more_than_one", TextFormatType.Error));
        } else if (incomingTeleportRequests.size() == 0) {
            throw CommandUtil.createSimpleException(
                ecText.getText("cmd.tpa_reply.error.shortcut_none_exist", TextFormatType.Error));
        }

        // Get the request entry safely
        var entry = incomingTeleportRequests.entrySet().stream().findFirst().get();
        UUID senderUuid = entry.getKey();
        TeleportRequest anyRequest = entry.getValue();
        ServerPlayer teleportRequestSender = anyRequest.getSenderPlayer();

        // Handle the case where the sender is offline
        if (teleportRequestSender == null) {
            incomingTeleportRequests.remove(senderUuid);
            
            throw CommandUtil.createSimpleException(
                ecText.getText("cmd.tpa_reply.error.no_request_from_target", TextFormatType.Error));
        }

        return exec(context, respondingPlayer, teleportRequestSender);
    }

    abstract int exec(CommandContext<CommandSourceStack> context, ServerPlayer respondingPlayer, ServerPlayer requesterPlayer);
}
