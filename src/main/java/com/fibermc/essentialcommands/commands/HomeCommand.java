package com.fibermc.essentialcommands.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fibermc.essentialcommands.access.ServerPlayerEntityAccess;
import com.fibermc.essentialcommands.commands.suggestions.ListSuggestion;
import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.teleportation.PlayerTeleporter;
import com.fibermc.essentialcommands.text.ECText;
import com.fibermc.essentialcommands.text.TextFormatType;
import com.fibermc.essentialcommands.types.MinecraftLocation;
import com.fibermc.essentialcommands.types.NamedMinecraftLocation;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class HomeCommand implements Command<CommandSourceStack> {

    public HomeCommand() {}

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PlayerData senderPlayerData = getTargetPlayerData(context);
        return exec(senderPlayerData, StringArgumentType.getString(context, "home_name"));
    }

    private static PlayerData getTargetPlayerData(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return ((ServerPlayerEntityAccess) context.getSource().getPlayerOrException()).ec$getPlayerData();
    }

    public static String getSoleHomeName(PlayerData playerData) throws CommandSyntaxException {
        Set<String> homeNames = playerData.getHomeNames();
        if (homeNames.isEmpty()) {
            throw CommandUtil.createSimpleException(
                ECText.access(playerData.getPlayer()).getText("cmd.home.tp.error.shortcut_none_exist", TextFormatType.Error));
        }
        
        if (homeNames.size() > 1) {
            throw CommandUtil.createSimpleException(
                ECText.access(playerData.getPlayer()).getText("cmd.home.tp.error.shortcut_more_than_one", TextFormatType.Error));
        }

        return homeNames.iterator().next(); // iterator().next() is sneller dan stream().findAny().get()
    }

    public int runDefault(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PlayerData playerData = getTargetPlayerData(context);
        return exec(playerData, getSoleHomeName(playerData));
    }

    private static int exec(PlayerData senderPlayerData, String homeName) throws CommandSyntaxException {
        return exec(senderPlayerData, senderPlayerData, homeName);
    }

    public static int exec(PlayerData senderPlayerData, PlayerData targetPlayerData, String homeName) throws CommandSyntaxException {
        MinecraftLocation loc = targetPlayerData.getHomeLocation(homeName);
        ServerPlayer player = senderPlayerData.getPlayer();
        ECText ecText = ECText.access(player);
        
        if (loc == null) {
            Message msg = ecText.getText(
                "cmd.home.tp.error.not_found",
                TextFormatType.Error,
                Component.literal("'" + homeName + "'").withStyle(ChatFormatting.YELLOW));
            throw new CommandSyntaxException(new SimpleCommandExceptionType(msg), msg);
        }

        if (senderPlayerData.isInCombat()) {
            throw CommandUtil.createSimpleException(
                ecText.getText("teleport.error.in_combat", TextFormatType.Error));
        }

        MutableComponent homeNameText = (MutableComponent) ecText.getText(
            "cmd.home.location_name",
            TextFormatType.Default,
            ecText.accent(homeName));

        PlayerTeleporter.requestTeleport(senderPlayerData, loc, homeNameText);
        
        return SINGLE_SUCCESS;
    }

    public static class Suggestion {
        public static final SuggestionProvider<CommandSourceStack> LIST_SUGGESTION_PROVIDER
            = ListSuggestion.ofContext(Suggestion::getSuggestionsList);

        public static List<String> getSuggestionsList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
            return new ArrayList<>(HomeCommand.getTargetPlayerData(context).getHomeNames());
        }

        public static Set<Map.Entry<String, NamedMinecraftLocation>> getSuggestionEntries(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
            return HomeCommand.getTargetPlayerData(context).getHomeEntries();
        }
    }
}
