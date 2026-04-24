package com.fibermc.essentialcommands.teleportation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fibermc.essentialcommands.ECPerms;
import com.fibermc.essentialcommands.access.ServerPlayerEntityAccess;
import com.fibermc.essentialcommands.events.PlayerDamageCallback;
import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.playerdata.PlayerDataManager;
import com.fibermc.essentialcommands.text.TextFormatType;
import com.fibermc.essentialcommands.types.MinecraftLocation;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import dev.jpcode.eccore.util.TimeUtil;

import static com.fibermc.essentialcommands.EssentialCommands.CONFIG;

public final class TeleportManager {
    private final List<TeleportRequest> activeTeleportRequests;
    private final List<PlayerData> playersOnTeleportCooldown;
    private final Map<UUID, QueuedTeleport> queuedTeleportMap;

    private static TeleportManager instance;

    private TeleportManager() {
        instance = this;
        activeTeleportRequests = new ArrayList<>();
        playersOnTeleportCooldown = new ArrayList<>();
        queuedTeleportMap = new ConcurrentHashMap<>();
    }

    public static TeleportManager getInstance() {
        if (instance == null) {
            instance = new TeleportManager();
        }
        return instance;
    }

    public static void init() {
        getInstance();
        PlayerDamageCallback.EVENT.register((ServerPlayer playerEntity, DamageSource source) -> instance.onPlayerDamaged(playerEntity, source));
        PlayerDataManager.TICK_EVENT.register(((playerDataManager, server) -> instance.tick(server)));
    }

    public void tick(MinecraftServer server) {
        if (activeTeleportRequests.size() == 0 && queuedTeleportMap.size() == 0 && playersOnTeleportCooldown.size() == 0) {
            return;
        }

        if (activeTeleportRequests.size() > 0) {
            Iterator<TeleportRequest> requestIter = activeTeleportRequests.iterator();
            while (requestIter.hasNext()) {
                TeleportRequest request = requestIter.next();
                request.incrementAgeTicks();

                if (request.getAgeTicks() > CONFIG.TELEPORT_REQUEST_DURATION_TICKS) {
                    request.end();
                    request.getSenderPlayerData().sendMessage("teleport.request.expired.sender", request.getTargetPlayer().getDisplayName());
                    request.getTargetPlayerData().sendMessage("teleport.request.expired.receiver", request.getSenderPlayer().getDisplayName());
                    requestIter.remove();
                    continue;
                }
                
                if (request.isEnded()) {
                    requestIter.remove();
                }
            }
        }

        if (playersOnTeleportCooldown.size() > 0) {
            ListIterator<PlayerData> tpCooldownIterator = playersOnTeleportCooldown.listIterator();
            while (tpCooldownIterator.hasNext()) {
                PlayerData playerData = tpCooldownIterator.next();
                playerData.tickTpCooldown();
                if (playerData.getTpCooldown() < 0) {
                    tpCooldownIterator.remove();
                }
            }
        }

        if (queuedTeleportMap.size() > 0) {
            var shouldInterruptTeleportOnMove = CONFIG.TELEPORT_INTERRUPT_ON_MOVE;
            var maxMoveBeforeInterrupt = CONFIG.TELEPORT_INTERRUPT_ON_MOVE_AMOUNT;
            Iterator<Map.Entry<UUID, QueuedTeleport>> tpQueueIter = queuedTeleportMap.entrySet().iterator();
            
            while (tpQueueIter.hasNext()) {
                Map.Entry<UUID, QueuedTeleport> entry = tpQueueIter.next();
                QueuedTeleport queuedTeleport = entry.getValue();
                queuedTeleport.tick(server);

                var playerData = queuedTeleport.getPlayerData();
                var player = playerData.getPlayer();
                var playerAccess = (ServerPlayerEntityAccess) player;
                
                long ticksRemaining = queuedTeleport.getTicksRemaining();
                
                if (ticksRemaining > 0 && ticksRemaining % 20 == 0) {
                    int secondsRemaining = (int) (ticksRemaining / 20);
                    var accentStyle = playerAccess.ec$getProfile().getStyle(TextFormatType.Accent);
                    
                    playerData.sendMessage(
                        "teleport.queued",
                        queuedTeleport.getDestName(),
                        Component.literal(String.valueOf(secondsRemaining)).withStyle(accentStyle)
                    );
                }

                if (shouldInterruptTeleportOnMove
                    && playerData.hasMovedThisTick()
                    && player.position().distanceTo(queuedTeleport.initialPosition) > maxMoveBeforeInterrupt
                    && !PlayerTeleporter.playerHasTpRulesBypass(player, ECPerms.Registry.bypass_teleport_interrupt_on_move)
                ) {
                    playerData.sendError("teleport.interrupted.moved");
                    tpQueueIter.remove();
                    continue;
                }

                // Voer teleportatie uit
                if (ticksRemaining < 0) {
                    tpQueueIter.remove();
                    PlayerTeleporter.teleport(queuedTeleport);
                }
            }
        }
    }

    public void onPlayerDamaged(ServerPlayer playerEntity, DamageSource damageSource) {
        if (!CONFIG.TELEPORT_INTERRUPT_ON_DAMAGED) {
            return;
        }
        var playerAccess = ((ServerPlayerEntityAccess) playerEntity);
        if (playerAccess.ec$getQueuedTeleport() != null
            && !PlayerTeleporter.playerHasTpRulesBypass(playerEntity, ECPerms.Registry.bypass_teleport_interrupt_on_damaged)
        ) {
            playerAccess.ec$endQueuedTeleport();
            queuedTeleportMap.remove(playerEntity.getUUID());
            playerAccess.ec$getPlayerData().sendError("teleport.interrupted.damage");
        }
    }

    public boolean startTpRequest(ServerPlayer requestSender, ServerPlayer targetPlayer, TeleportRequest.Type requestType) {
        var senderPlayerData = PlayerData.access(requestSender);
        var targetPlayerData = PlayerData.access(targetPlayer);

        if (requestSender.getUUID().equals(targetPlayer.getUUID())) {
            senderPlayerData.sendError("teleport.error.self_teleport");
            return false;
        }

        var teleportRequest = new TeleportRequest(requestSender, targetPlayer, requestType);
        senderPlayerData.addSentTeleportRequest(teleportRequest);
        targetPlayerData.addIncomingTeleportRequest(teleportRequest);
        activeTeleportRequests.add(teleportRequest);
        return true;
    }

    public void startTpCooldown(ServerPlayer player) {
        final int teleportCooldownTicks = (int) (CONFIG.TELEPORT_COOLDOWN * TimeUtil.TPS);
        var playerData = PlayerData.access(player);

        playerData.setTpCooldown(teleportCooldownTicks);
        playersOnTeleportCooldown.add(playerData);
    }

    void queueTeleport(ServerPlayer player, MinecraftLocation dest, MutableComponent destName) {
        queueTeleport(new QueuedLocationTeleport(PlayerData.access(player), dest, destName));
    }

    void queueTeleport(QueuedTeleport queuedTeleport) {
        var playerData = queuedTeleport.getPlayerData();
        var playerAccess = ((ServerPlayerEntityAccess) playerData.getPlayer());

        QueuedTeleport prevValue = queuedTeleportMap.put(
            playerData.getPlayer().getUUID(),
            queuedTeleport
        );
        
        if (prevValue != null) {
            var profile = playerAccess.ec$getProfile();
            var styleUpdater = profile.nonOverwritingColorUpdater(TextFormatType.Accent);
            prevValue.getPlayerData().sendMessage(
                "teleport.request.canceled_by_new",
                prevValue.getDestName().withStyle(styleUpdater),
                queuedTeleport.getDestName().withStyle(styleUpdater)
            );
        }

        playerAccess.ec$setQueuedTeleport(queuedTeleport);
        
        var accentStyle = playerAccess.ec$getProfile().getStyle(TextFormatType.Accent);
        int initialSeconds = (int) (CONFIG.TELEPORT_DELAY_TICKS / 20);

        playerData.sendMessage(
            "teleport.queued",
            queuedTeleport.getDestName(),
            Component.literal(String.valueOf(initialSeconds)).withStyle(accentStyle)
        );
    }
}
