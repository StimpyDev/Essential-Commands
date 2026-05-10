package com.fibermc.essentialcommands.teleportation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fibermc.essentialcommands.ECPerms;
import com.fibermc.essentialcommands.access.ServerPlayerEntityAccess;
import com.fibermc.essentialcommands.commands.FlyCommand;
import com.fibermc.essentialcommands.events.PlayerDamageCallback;
import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.playerdata.PlayerDataManager;
import com.fibermc.essentialcommands.text.TextFormatType;
import com.fibermc.essentialcommands.types.MinecraftLocation;

import net.minecraft.ChatFormatting;
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
    private final Map<UUID, Integer> flyCooldownMap;

    private static TeleportManager instance;

    private TeleportManager() {
        instance = this;
        activeTeleportRequests = new ArrayList<>();
        playersOnTeleportCooldown = new ArrayList<>();
        queuedTeleportMap = new ConcurrentHashMap<>();
        flyCooldownMap = new ConcurrentHashMap<>();
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
        if (activeTeleportRequests.size() == 0 && queuedTeleportMap.size() == 0 && playersOnTeleportCooldown.size() == 0 && flyCooldownMap.size() == 0) {
            return;
        }

        if (activeTeleportRequests.size() > 0) {
            activeTeleportRequests.removeIf(request -> {
                request.incrementAgeTicks();
                if (request.getAgeTicks() > CONFIG.TELEPORT_REQUEST_DURATION_TICKS) {
                    request.end();
                    request.getSenderPlayerData().sendMessage("teleport.request.expired.sender", request.getTargetPlayer().getDisplayName());
                    request.getTargetPlayerData().sendMessage("teleport.request.expired.receiver", request.getSenderPlayer().getDisplayName());
                    return true;
                }
                return request.isEnded();
            });
        }

        if (playersOnTeleportCooldown.size() > 0) {
            playersOnTeleportCooldown.removeIf(playerData -> {
                playerData.tickTpCooldown();
                return playerData.getTpCooldown() < 0;
            });
        }

        if (flyCooldownMap.size() > 0) {
            for (UUID uuid : flyCooldownMap.keySet()) {
                flyCooldownMap.compute(uuid, (key, remaining) -> {
                    if (remaining == null) return null;
                    
                    int newVal = remaining - 1;
                    if (newVal <= 0) {
                        ServerPlayer player = server.getPlayerList().getPlayer(key);
                        if (player != null) {
                            player.sendSystemMessage(
                                Component.translatable("cmd.fly.feedback.ready")
                                    .withStyle(ChatFormatting.GREEN)
                            );
                        }
                        return null;
                    }
                    return newVal;
                });
            }
        }

        if (queuedTeleportMap.size() > 0) {
            var shouldInterruptOnMove = CONFIG.TELEPORT_INTERRUPT_ON_MOVE;
            var maxMove = CONFIG.TELEPORT_INTERRUPT_ON_MOVE_AMOUNT;

            queuedTeleportMap.entrySet().removeIf(entry -> {
                QueuedTeleport queuedTeleport = entry.getValue();
                queuedTeleport.tick(server);

                var playerData = queuedTeleport.getPlayerData();
                var player = playerData.getPlayer();
                
                if (player == null || player.isRemoved()) return true;

                long ticksRemaining = queuedTeleport.getTicksRemaining();
                
                if (ticksRemaining > 0 && ticksRemaining % 20 == 0) {
                    int secondsRemaining = (int) (ticksRemaining / 20);
                    var playerAccess = (ServerPlayerEntityAccess) player;
                    var accentStyle = playerAccess.ec$getProfile().getStyle(TextFormatType.Accent);
                    
                    playerData.sendMessage(
                        "teleport.queued",
                        queuedTeleport.getDestName(),
                        Component.literal(String.valueOf(secondsRemaining)).withStyle(accentStyle)
                    );
                }

                if (shouldInterruptOnMove && playerData.hasMovedThisTick()) {
                    if (player.position().distanceTo(queuedTeleport.initialPosition) > maxMove) {
                        if (!PlayerTeleporter.playerHasTpRulesBypass(player, ECPerms.Registry.bypass_teleport_interrupt_on_move)) {
                            playerData.sendError("teleport.interrupted.moved");
                            return true;
                        }
                    }
                }

                if (ticksRemaining < 0) {
                    PlayerTeleporter.teleport(queuedTeleport);
                    return true;
                }

                return false;
            });
        }
    }

    public void onPlayerDamaged(ServerPlayer playerEntity, DamageSource damageSource) {
        if (playerEntity.getAbilities().mayfly) {
            if (damageSource.getEntity() instanceof ServerPlayer) {
                FlyCommand.disableFly(playerEntity);
                
                var playerData = PlayerData.access(playerEntity);
                playerData.sendError("cmd.fly.error.combat");
                
                flyCooldownMap.put(playerEntity.getUUID(), 300);
            }
        }

        if (!CONFIG.TELEPORT_INTERRUPT_ON_DAMAGED) return;

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
        var playerEntity = playerData.getPlayer();
        var playerAccess = ((ServerPlayerEntityAccess) playerEntity);

        QueuedTeleport prevValue = queuedTeleportMap.put(
            playerEntity.getUUID(),
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
