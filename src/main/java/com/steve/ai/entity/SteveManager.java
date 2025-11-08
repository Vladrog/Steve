package com.steve.ai.entity;

import com.steve.ai.SteveMod;
import com.steve.ai.config.SteveConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SteveManager {
    private final Map<String, SteveEntity> activeSteves;
    private final Map<UUID, SteveEntity> stevesByUUID;

    public SteveManager() {
        this.activeSteves = new ConcurrentHashMap<>();
        this.stevesByUUID = new ConcurrentHashMap<>();
    }

    public SteveEntity spawnSteve(ServerLevel level, Vec3 position, String name) {        SteveMod.LOGGER.info("Current active Steves: {}", activeSteves.size());
        
        if (activeSteves.containsKey(name)) {
            SteveMod.LOGGER.warn("Steve name '{}' already exists", name);
            return null;
        }        int maxSteves = SteveConfig.MAX_ACTIVE_STEVES.get();        if (activeSteves.size() >= maxSteves) {
            SteveMod.LOGGER.warn("Max Steve limit reached: {}", maxSteves);
            return null;
        }        SteveEntity steve;
        try {            SteveMod.LOGGER.info("EntityType: {}", SteveMod.STEVE_ENTITY.get());
            steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), level);        } catch (Throwable e) {
            SteveMod.LOGGER.error("Failed to create Steve entity", e);
            SteveMod.LOGGER.error("Exception class: {}", e.getClass().getName());
            SteveMod.LOGGER.error("Exception message: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }

        try {            steve.setSteveName(name);            steve.setPos(position.x, position.y, position.z);            boolean added = level.addFreshEntity(steve);            if (added) {
                activeSteves.put(name, steve);
                stevesByUUID.put(steve.getUUID(), steve);
                SteveMod.LOGGER.info("Successfully spawned Steve: {} with UUID {} at {}", name, steve.getUUID(), position);                return steve;
            } else {
                SteveMod.LOGGER.error("Failed to add Steve entity to world (addFreshEntity returned false)");
                SteveMod.LOGGER.error("=== SPAWN ATTEMPT FAILED ===");
            }
        } catch (Throwable e) {
            SteveMod.LOGGER.error("Exception during spawn setup", e);
            SteveMod.LOGGER.error("=== SPAWN ATTEMPT FAILED WITH EXCEPTION ===");
            e.printStackTrace();
        }

        return null;
    }

    public SteveEntity getSteve(String name) {
        return activeSteves.get(name);
    }

    public SteveEntity getSteve(UUID uuid) {
        return stevesByUUID.get(uuid);
    }

    public boolean removeSteve(String name) {
        SteveEntity steve = activeSteves.remove(name);
        if (steve != null) {
            stevesByUUID.remove(steve.getUUID());
            steve.discard();            return true;
        }
        return false;
    }

    public void clearAllSteves() {
        SteveMod.LOGGER.info("Clearing {} Steve entities", activeSteves.size());
        for (SteveEntity steve : activeSteves.values()) {
            steve.discard();
        }
        activeSteves.clear();
        stevesByUUID.clear();    }

    public Collection<SteveEntity> getAllSteves() {
        return Collections.unmodifiableCollection(activeSteves.values());
    }

    public List<String> getSteveNames() {
        return new ArrayList<>(activeSteves.keySet());
    }

    public int getActiveCount() {
        return activeSteves.size();
    }

    /**
     * Регистрирует уже существующего Стива (например, загруженного из сохранения)
     * в менеджере, чтобы он был виден в командах /steve list и /steve remove
     */
    public boolean registerExistingSteve(SteveEntity steve) {
        if (steve == null || !steve.isAlive() || steve.isRemoved()) {
            return false;
        }
        
        String name = steve.getSteveName();
        if (name == null || name.isEmpty()) {
            name = "Steve_" + steve.getUUID().toString().substring(0, 8);
            steve.setSteveName(name);
            SteveMod.LOGGER.info("Assigned default name to unregistered Steve: {}", name);
        }
        
        // Проверяем, не зарегистрирован ли уже Стив с таким именем
        if (activeSteves.containsKey(name)) {
            SteveEntity existing = activeSteves.get(name);
            // Если это другой Стив (другой UUID), нужно переименовать нового
            if (!existing.getUUID().equals(steve.getUUID())) {
                name = name + "_" + steve.getUUID().toString().substring(0, 8);
                steve.setSteveName(name);
                SteveMod.LOGGER.warn("Renamed Steve to avoid name conflict: {}", name);
            } else {
                // Это тот же Стив, уже зарегистрирован
                return false;
            }
        }
        
        // Проверяем, не зарегистрирован ли уже Стив с таким UUID
        if (stevesByUUID.containsKey(steve.getUUID())) {
            return false;
        }
        
        activeSteves.put(name, steve);
        stevesByUUID.put(steve.getUUID(), steve);
        SteveMod.LOGGER.info("Registered existing Steve: {} with UUID {}", name, steve.getUUID());
        return true;
    }

    public void tick(ServerLevel level) {
        // Регистрируем незарегистрированных Стивов в мире
        for (var entity : level.getAllEntities()) {
            if (entity instanceof SteveEntity steve) {
                if (!stevesByUUID.containsKey(steve.getUUID())) {
                    registerExistingSteve(steve);
                }
            }
        }
        
        // Clean up dead or removed Steves
        Iterator<Map.Entry<String, SteveEntity>> iterator = activeSteves.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SteveEntity> entry = iterator.next();
            SteveEntity steve = entry.getValue();
            
            if (!steve.isAlive() || steve.isRemoved()) {
                iterator.remove();
                stevesByUUID.remove(steve.getUUID());
                SteveMod.LOGGER.info("Cleaned up Steve: {}", entry.getKey());
            }
        }
    }
}

