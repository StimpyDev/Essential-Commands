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

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class TeleportAskCommand implements Command<CommandSourceStack> {

    public TeleportAskCommand() {}

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

        boolean success = tpMgr.startTpRequest(senderPlayer, targetPlayer, TeleportRequest.Type.TPA_TO);

        if (!success) {
            return 0;
        }

        var targetPlayerEcText = ECText.access(targetPlayer);
        targetPlayerData.sendMessage(
            "cmd.tpask.receive",
            senderPlayer.getDisplayName()
        );

        String senderName = senderPlayer.getGameProfile().name();
        new ChatConfirmationPrompt(
            targetPlayer,
            "/tpaccept " + senderName,
            "/tpdeny " + senderName,
            targetPlayerEcText.accent("[" + ECText.getInstance().getString("generic.accept") + "]"),
            targetPlayerEcText.error("[" + ECText.getInstance().getString("generic.deny") + "]")
        ).send();


        senderPlayerData.sendMessage("cmd.tpask.send", targetPlayer.getDisplayName());

        return Command.SINGLE_SUCCESS;
    }
}
