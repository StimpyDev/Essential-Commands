package com.fibermc.essentialcommands.commands;

import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.teleportation.TeleportRequest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import dev.jpcode.eccore.util.TextUtil;

import java.util.List;
import java.util.stream.Collectors;

public class TeleportCancelCommand implements Command<CommandSourceStack> {

    public TeleportCancelCommand() {}

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer senderPlayer = context.getSource().getPlayerOrException();
        var senderPlayerData = PlayerData.access(senderPlayer);

        var existingTeleportRequests = senderPlayerData.getSentTeleportRequests();

        if (existingTeleportRequests.size() == 0) {
            senderPlayerData.sendCommandError("cmd.tpcancel.error.no_exists");
            return 0;
        }
    
        List<Component> targetNames = existingTeleportRequests.stream()
            .map(request -> {
                ServerPlayer p = request.getTargetPlayer();
                return p != null ? p.getDisplayName() : Component.literal("Offline Player");
            })
            .collect(Collectors.toList());

        List<TeleportRequest> requestsList = existingTeleportRequests.stream().collect(Collectors.toList());
        for (TeleportRequest request : requestsList) {
            request.end(); 
        }
        
        existingTeleportRequests.clear();

        senderPlayer.sendSystemMessage(
            Component.translatable(
                "cmd.tpcancel.feedback",
                TextUtil.join(targetNames, Component.literal(", "))
            ).withStyle(ChatFormatting.GREEN)
        );

        return Command.SINGLE_SUCCESS;
    }
}
