package com.steve.ai.event;

import com.steve.ai.SteveMod;
import com.steve.ai.entity.SteveManager;
import com.steve.ai.memory.StructureRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SteveMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {
    private static boolean stevesSpawned = false;

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!stevesSpawned) {
                // Clear structure registry for fresh spatial awareness
                StructureRegistry.clear();
                
                // Регистрируем всех существующих Стивов, загруженных из сохранения
                if (player.level() instanceof ServerLevel level) {
                    SteveManager manager = SteveMod.getSteveManager();
                    manager.tick(level);
                    SteveMod.LOGGER.info("Registered existing Steves on world load");
                }
                
                // Убрано автоматическое создание Стивов - теперь они создаются только через команду /steve spawn
                // Если нужно автоматически создавать Стивов, раскомментируйте код ниже:
                /*
                ServerLevel level = (ServerLevel) player.level();
                SteveManager manager = SteveMod.getSteveManager();
                manager.clearAllSteves();
                
                // Remove ALL SteveEntity instances from the world (including ones loaded from NBT)
                for (var entity : level.getAllEntities()) {
                    if (entity instanceof SteveEntity) {
                        entity.discard();
                    }
                }
                
                Vec3 playerPos = player.position();
                Vec3 lookVec = player.getLookAngle();
                
                String[] names = {"Steve", "Alex", "Bob", "Charlie"};
                
                for (int i = 0; i < 4; i++) {
                    double offsetX = lookVec.x * 5 + (lookVec.z * (i - 1.5) * 2);
                    double offsetZ = lookVec.z * 5 + (-lookVec.x * (i - 1.5) * 2);
                    
                    Vec3 spawnPos = new Vec3(
                        playerPos.x + offsetX,
                        playerPos.y,
                        playerPos.z + offsetZ
                    );
                    
                    manager.spawnSteve(level, spawnPos, names[i]);
                }
                */
                
                stevesSpawned = true;
            }
        }
    }
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // Вызываем tick для всех загруженных уровней сервера
        // Это регистрирует незарегистрированных Стивов и очищает мертвых
        var server = event.getServer();
        if (server != null) {
            SteveManager manager = SteveMod.getSteveManager();
            for (ServerLevel level : server.getAllLevels()) {
                manager.tick(level);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        stevesSpawned = false;
    }
}

