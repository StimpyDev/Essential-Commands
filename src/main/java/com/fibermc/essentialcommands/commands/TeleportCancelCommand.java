package com.fibermc.essentialcommands.commands;

import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.teleportation.TeleportRequest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import dev.jpcode.eccore.util.TextUtil;

import java.util.List;

public class TeleportCancelCommand implements Command<CommandSourceStack> {

    public TeleportCancelCommand() {}

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // Store command sender
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
                if (p != null) {
                    return p.getDisplayName();
                }
                // Fallback to a literal "Offline Player" if we can't find a name method
                return Component.literal("Offline Player");
            })
            .toList();

        // Now clear the requests
        existingTeleportRequests.clear();

        senderPlayerData.sendCommandFeedback(
            "cmd.tpcancel.feedback",
            TextUtil.join(targetNames, Component.literal(", "))
        );

        return SINGLE_SUCCESS;
    }
}
