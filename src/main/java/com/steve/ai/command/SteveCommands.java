package com.steve.ai.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.steve.ai.SteveMod;
import com.steve.ai.ai.LMStudioClient;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.entity.SteveManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class SteveCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("steve")
            .then(Commands.literal("spawn")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(SteveCommands::spawnSteve)))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(SteveCommands::removeSteve)))
            .then(Commands.literal("list")
                .executes(SteveCommands::listSteves))
            .then(Commands.literal("stop")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(SteveCommands::stopSteve)))
            .then(Commands.literal("tell")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("command", StringArgumentType.greedyString())
                        .executes(SteveCommands::tellSteve))))
            .then(Commands.literal("test-lmstudio")
                .executes(SteveCommands::testLMStudio))
        );
    }

    private static int spawnSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        
        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("Command must be run on server"));
            return 0;
        }

        SteveManager manager = SteveMod.getSteveManager();
        
        Vec3 sourcePos = source.getPosition();
        if (source.getEntity() != null) {
            Vec3 lookVec = source.getEntity().getLookAngle();
            sourcePos = sourcePos.add(lookVec.x * 3, 0, lookVec.z * 3);
        } else {
            sourcePos = sourcePos.add(3, 0, 0);
        }
        Vec3 spawnPos = sourcePos;
        
        SteveEntity steve = manager.spawnSteve(serverLevel, spawnPos, name);
        if (steve != null) {
            source.sendSuccess(() -> Component.literal("Spawned Steve: " + name), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to spawn Steve. Name may already exist or max limit reached."));
            return 0;
        }
    }

    private static int removeSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        
        SteveManager manager = SteveMod.getSteveManager();
        if (manager.removeSteve(name)) {
            source.sendSuccess(() -> Component.literal("Removed Steve: " + name), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Steve not found: " + name));
            return 0;
        }
    }

    private static int listSteves(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        SteveManager manager = SteveMod.getSteveManager();
        
        var names = manager.getSteveNames();
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active Steves"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Active Steves (" + names.size() + "): " + String.join(", ", names)), false);
        }
        return 1;
    }

    private static int stopSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        
        SteveManager manager = SteveMod.getSteveManager();
        SteveEntity steve = manager.getSteve(name);
        
        if (steve != null) {
            steve.getActionExecutor().stopCurrentAction();
            steve.getMemory().clearTaskQueue();
            source.sendSuccess(() -> Component.literal("Stopped Steve: " + name), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Steve not found: " + name));
            return 0;
        }
    }

    private static int tellSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String command = StringArgumentType.getString(context, "command");
        CommandSourceStack source = context.getSource();
        
        SteveManager manager = SteveMod.getSteveManager();
        SteveEntity steve = manager.getSteve(name);
        
        if (steve != null) {
            // Disabled command feedback message
            // source.sendSuccess(() -> Component.literal("Instructing " + name + ": " + command), true);
            
            new Thread(() -> {
                steve.getActionExecutor().processNaturalLanguageCommand(command);
            }).start();
            
            return 1;
        } else {
            source.sendFailure(Component.literal("Steve not found: " + name));
            return 0;
        }
    }

    private static int testLMStudio(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        source.sendSuccess(() -> Component.literal("Тестирование подключения к LM Studio..."), false);
        
        new Thread(() -> {
            try {
                String apiUrl = SteveConfig.LMSTUDIO_API_URL.get();
                String model = SteveConfig.LMSTUDIO_MODEL.get();
                String provider = SteveConfig.AI_PROVIDER.get();
                
                source.sendSuccess(() -> Component.literal("Провайдер: " + provider), false);
                source.sendSuccess(() -> Component.literal("URL: " + apiUrl), false);
                source.sendSuccess(() -> Component.literal("Модель: " + (model.isEmpty() ? "(не указана)" : model)), false);
                
                if (!"lmstudio".equalsIgnoreCase(provider)) {
                    source.sendFailure(Component.literal("Ошибка: Провайдер установлен на '" + provider + "', а не 'lmstudio'"));
                    source.sendFailure(Component.literal("Измените [ai].provider на 'lmstudio' в config/steve-common.toml"));
                    return;
                }
                
                LMStudioClient client = new LMStudioClient();
                String response = client.sendRequest(
                    "You are a helpful assistant. Respond briefly.",
                    "Say 'Hello from LM Studio'"
                );
                
                if (response != null && !response.isEmpty()) {
                    source.sendSuccess(() -> Component.literal("✓ Успешно! LM Studio отвечает."), true);
                    source.sendSuccess(() -> Component.literal("Ответ: " + response.substring(0, Math.min(100, response.length()))), false);
                } else {
                    source.sendFailure(Component.literal("✗ Ошибка: LM Studio не ответил"));
                    source.sendFailure(Component.literal("Проверьте логи игры для подробностей"));
                }
            } catch (Exception e) {
                source.sendFailure(Component.literal("✗ Ошибка: " + e.getMessage()));
                SteveMod.LOGGER.error("Ошибка при тестировании LM Studio", e);
            }
        }).start();
        
        return 1;
    }
}

