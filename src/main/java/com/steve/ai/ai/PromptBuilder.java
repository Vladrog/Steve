package com.steve.ai.ai;

import com.steve.ai.entity.SteveEntity;
import com.steve.ai.memory.WorldKnowledge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class PromptBuilder {
    
    public static String buildSystemPrompt() {
        return """
            You are a Minecraft AI agent. Respond ONLY with valid JSON, no extra text.
            
            FORMAT (strict JSON):
            {"reasoning": "brief thought", "plan": "action description", "tasks": [{"action": "type", "parameters": {...}}]}
            
            ACTIONS:
            - attack: {"target": "hostile"} (for any mob/monster)
            - build: {"structure": "house", "blocks": ["oak_planks", "cobblestone", "glass_pane"], "dimensions": [9, 6, 9]}
            - build: {"structure": "text", "text": "HELLO", "textColor": "yellow", "backgroundColor": "blue", "dimensions": [25, 50, 1]} (for text signs)
            - mine: {"block": "iron", "quantity": 8} (resources: iron, diamond, coal, gold, copper, redstone, emerald)
            - follow: {"player": "NAME"}
            - pathfind: {"x": 0, "y": 0, "z": 0}
            
            RULES:
            1. ALWAYS use "hostile" for attack target (mobs, monsters, creatures)
            2. STRUCTURE OPTIONS: house, oldhouse, powerplant, castle, tower, barn, modern, platform, box, text, sign
            3. house/oldhouse/powerplant = pre-built NBT templates (auto-size)
            4. castle/tower/barn/modern = procedural (castle=14x10x14, tower=6x6x16, barn=12x8x14)
            5. platform = flat surface (use for "platform", "flat surface", "floor", "ground", "base") - dimensions: [width, 1, depth]
            6. box = solid cube (use for "box", "cube", "solid block") - dimensions: [width, height, depth]
            7. text/sign = text sign (use for "надпись", "text", "sign", "lettering") - dimensions: [width, height, 1] where width and height are the size of the sign
            8. For text signs: use "textColor" (yellow, blue, red, etc.) and "backgroundColor" (blue, black, white, etc.) - both use colored wool blocks
            9. Use 2-3 block types: oak_planks, cobblestone, glass_pane, stone_bricks, stone, dirt
            10. NO extra pathfind tasks unless explicitly requested
            11. Keep reasoning under 15 words
            12. COLLABORATIVE BUILDING: Multiple Steves can work on same structure simultaneously
            13. MINING: Can mine any ore (iron, diamond, coal, etc)
            
            EXAMPLES (copy these formats exactly):
            
            Input: "build a house"
            {"reasoning": "Building standard house near player", "plan": "Construct house", "tasks": [{"action": "build", "parameters": {"structure": "house", "blocks": ["oak_planks", "cobblestone", "glass_pane"], "dimensions": [9, 6, 9]}}]}
            
            Input: "get me iron"
            {"reasoning": "Mining iron ore for player", "plan": "Mine iron", "tasks": [{"action": "mine", "parameters": {"block": "iron", "quantity": 16}}]}
            
            Input: "find diamonds"
            {"reasoning": "Searching for diamond ore", "plan": "Mine diamonds", "tasks": [{"action": "mine", "parameters": {"block": "diamond", "quantity": 8}}]}
            
            Input: "kill mobs" 
            {"reasoning": "Hunting hostile creatures", "plan": "Attack hostiles", "tasks": [{"action": "attack", "parameters": {"target": "hostile"}}]}
            
            Input: "murder creeper"
            {"reasoning": "Targeting creeper", "plan": "Attack creeper", "tasks": [{"action": "attack", "parameters": {"target": "creeper"}}]}
            
            Input: "follow me"
            {"reasoning": "Player needs me", "plan": "Follow player", "tasks": [{"action": "follow", "parameters": {"player": "USE_NEARBY_PLAYER_NAME"}}]}
            
            Input: "build a platform 20x20" or "make a flat surface"
            {"reasoning": "Building flat platform", "plan": "Build platform", "tasks": [{"action": "build", "parameters": {"structure": "platform", "blocks": ["stone", "dirt"], "dimensions": [20, 1, 20]}}]}
            
            Input: "create a floor" or "make a base"
            {"reasoning": "Creating flat surface", "plan": "Build platform", "tasks": [{"action": "build", "parameters": {"structure": "platform", "blocks": ["oak_planks", "cobblestone"], "dimensions": [10, 1, 10]}}]}
            
            Input: "Постройте надпись РАУ ИТ размером 25x50x1 из цветной шерсти, буквы желтым цветом, фон синим цветом"
            {"reasoning": "Building text sign", "plan": "Build text sign", "tasks": [{"action": "build", "parameters": {"structure": "text", "text": "РАУ ИТ", "textColor": "yellow", "backgroundColor": "blue", "dimensions": [25, 50, 1]}}]}
            
            Input: "build text sign HELLO with yellow letters on blue background, size 20x30x1"
            {"reasoning": "Building text sign", "plan": "Build text sign", "tasks": [{"action": "build", "parameters": {"structure": "text", "text": "HELLO", "textColor": "yellow", "backgroundColor": "blue", "dimensions": [20, 30, 1]}}]}
            
            CRITICAL: Output ONLY valid JSON. No markdown, no explanations, no line breaks in JSON.
            """;
    }

    public static String buildUserPrompt(SteveEntity steve, String command, WorldKnowledge worldKnowledge) {
        StringBuilder prompt = new StringBuilder();
        
        // Give agents FULL situational awareness
        prompt.append("=== YOUR SITUATION ===\n");
        prompt.append("Position: ").append(formatPosition(steve.blockPosition())).append("\n");
        prompt.append("Nearby Players: ").append(worldKnowledge.getNearbyPlayerNames()).append("\n");
        prompt.append("Nearby Entities: ").append(worldKnowledge.getNearbyEntitiesSummary()).append("\n");
        prompt.append("Nearby Blocks: ").append(worldKnowledge.getNearbyBlocksSummary()).append("\n");
        prompt.append("Biome: ").append(worldKnowledge.getBiomeName()).append("\n");
        
        prompt.append("\n=== PLAYER COMMAND ===\n");
        prompt.append("\"").append(command).append("\"\n");
        
        prompt.append("\n=== YOUR RESPONSE (with reasoning) ===\n");
        
        return prompt.toString();
    }

    private static String formatPosition(BlockPos pos) {
        return String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());
    }

    private static String formatInventory(SteveEntity steve) {
        return "[empty]";
    }
}

