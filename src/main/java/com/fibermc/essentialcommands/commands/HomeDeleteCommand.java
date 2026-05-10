package com.fibermc.essentialcommands.commands;

import com.fibermc.essentialcommands.access.ServerPlayerEntityAccess;
import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.text.ECText;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class HomeDeleteCommand implements Command<CommandSourceStack> {

    public HomeDeleteCommand() {}

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer senderPlayer = source.getPlayerOrException();
        PlayerData senderPlayerData = ((ServerPlayerEntityAccess) senderPlayer).ec$getPlayerData();
        String homeName = StringArgumentType.getString(context, "home_name");
        
        if (senderPlayerData.getHomeLocation(homeName) == null) {
            var homeNameText = ECText.access(senderPlayer).accent(homeName);
            senderPlayerData.sendCommandError("cmd.home.delete.error", homeNameText);
            return 0;
        }

        String confirmCommand = "/home delete_confirm " + homeName;
        var textAccess = ECText.access(senderPlayer);

        MutableComponent message = Component.empty()
            .append(textAccess.getText("cmd.home.delete.confirm_question", textAccess.accent(homeName)))
            .append(Component.literal("\n"))
            .append(Component.literal("Typ ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(confirmCommand).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" om te bevestigen.").withStyle(ChatFormatting.GRAY));

        senderPlayer.sendSystemMessage(message);

        return SINGLE_SUCCESS;
    }

    public int runConfirm(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer senderPlayer = context.getSource().getPlayerOrException();
        PlayerData senderPlayerData = ((ServerPlayerEntityAccess) senderPlayer).ec$getPlayerData();
        String homeName = StringArgumentType.getString(context, "home_name");

        boolean wasSuccessful = senderPlayerData.removeHome(homeName);
        var homeNameText = ECText.access(senderPlayer).accent(homeName);

        if (wasSuccessful) {
            senderPlayerData.sendCommandFeedback("cmd.home.delete.feedback", homeNameText);
            return SINGLE_SUCCESS;
        }

        senderPlayerData.sendCommandError("cmd.home.delete.error", homeNameText);
        return 0;
    }
}
