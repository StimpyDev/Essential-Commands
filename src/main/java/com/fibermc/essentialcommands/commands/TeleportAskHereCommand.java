package com.fibermc.essentialcommands.commands;

import com.fibermc.essentialcommands.ManagerLocator;
import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.teleportation.TeleportManager;
import com.fibermc.essentialcommands.teleportation.TeleportRequest;
import com.fibermc.essentialcommands.text.ChatConfirmationPrompt;
import com.fibermc.essentialcommands.text.ECText;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class TeleportAskHereCommand implements Command<CommandSourceStack> {

    public TeleportAskHereCommand() {}

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        TeleportManager tpMgr = ManagerLocator.getInstance().getTpManager();
        ServerPlayer senderPlayer = context.getSource().getPlayerOrException();
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "target_player");
        var senderPlayerData = PlayerData.access(senderPlayer);
        var targetPlayerData = PlayerData.access(targetPlayer);
        
        var existingTeleportRequest = senderPlayerData.getSentTeleportRequests()
            .getRequestToPlayer(targetPlayerData);
        if (existingTeleportRequest.isPresent()) {
            senderPlayerData.sendCommandError(
                "cmd.tpask.error.exists",
                existingTeleportRequest.get().getTargetPlayer().getDisplayName());
            return 0;
        }

        boolean success = tpMgr.startTpRequest(senderPlayer, targetPlayer, TeleportRequest.Type.TPA_HERE);

        if (!success) {
            return 0;
        }
        
        targetPlayerData.sendMessage(
            Component.translatable("cmd.tpaskhere.receive", senderPlayer.getDisplayName())
                .withStyle(ChatFormatting.GREEN)
        );

        String senderName = senderPlayer.getGameProfile().name();
        
        new ChatConfirmationPrompt(
            targetPlayer,
            "/tpaccept " + senderName,
            "/tpdeny " + senderName,
            Component.literal("[" + ECText.getInstance().getString("generic.accept") + "]")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
            Component.literal("[" + ECText.getInstance().getString("generic.deny") + "]")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        ).send();

        senderPlayerData.sendMessage(
            Component.translatable("cmd.tpask.send", targetPlayer.getDisplayName())
                .withStyle(ChatFormatting.GREEN)
        );

        return Command.SINGLE_SUCCESS;
    }
}
