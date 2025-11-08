package com.steve.ai.action.actions;

import com.steve.ai.SteveMod;
import com.steve.ai.action.ActionResult;
import com.steve.ai.action.CollaborativeBuildManager;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.memory.StructureRegistry;
import com.steve.ai.structure.StructureTemplateLoader;
import com.steve.ai.structure.TextRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class BuildStructureAction extends BaseAction {
    private static class BlockPlacement {
        BlockPos pos;
        Block block;
        
        BlockPlacement(BlockPos pos, Block block) {
            this.pos = pos;
            this.block = block;
        }
    }
    
    private String structureType;
    private List<BlockPlacement> buildPlan;
    private int currentBlockIndex;
    private List<Block> buildMaterials;
    private int ticksRunning;
    private CollaborativeBuildManager.CollaborativeBuild collaborativeBuild; // For multi-Steve collaboration
    private boolean isCollaborative;
    private static final int MAX_TICKS = 120000;
    private static final int BLOCKS_PER_TICK = 1;
    private static final double BUILD_SPEED_MULTIPLIER = 1.5;

    public BuildStructureAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        structureType = task.getStringParameter("structure").toLowerCase();
        currentBlockIndex = 0;
        ticksRunning = 0;
        collaborativeBuild = CollaborativeBuildManager.findActiveBuild(structureType);
        if (collaborativeBuild != null) {
            isCollaborative = true;
            
            steve.setFlying(true);
            
            SteveMod.LOGGER.info("Steve '{}' JOINING collaborative build of '{}' ({}% complete) - FLYING & INVULNERABLE ENABLED", 
                steve.getSteveName(), structureType, collaborativeBuild.getProgressPercentage());
            
            buildMaterials = new ArrayList<>();
            buildMaterials.add(Blocks.OAK_PLANKS); // Default material
            buildMaterials.add(Blocks.COBBLESTONE);
            buildMaterials.add(Blocks.GLASS_PANE);
            
            return; // Skip structure generation, just join the existing build
        }
        
        isCollaborative = false;
        
        buildMaterials = new ArrayList<>();
        Object blocksParam = task.getParameter("blocks");
        if (blocksParam instanceof List) {
            List<?> blocksList = (List<?>) blocksParam;
            for (Object blockObj : blocksList) {
                Block block = parseBlock(blockObj.toString());
                if (block != Blocks.AIR) {
                    buildMaterials.add(block);
                }
            }
        }
        
        if (buildMaterials.isEmpty()) {
            String materialName = task.getStringParameter("material", "oak_planks");
            Block block = parseBlock(materialName);
            buildMaterials.add(block != Blocks.AIR ? block : Blocks.OAK_PLANKS);
        }
        
        SteveMod.LOGGER.info("Build materials: {} (count: {})", buildMaterials, buildMaterials.size());
        
        Object dimensionsParam = task.getParameter("dimensions");
        int width = 9;  // Increased from 5
        int height = 6; // Increased from 4
        int depth = 9;  // Increased from 5
        
        if (dimensionsParam instanceof List) {
            List<?> dims = (List<?>) dimensionsParam;
            if (dims.size() >= 3) {
                width = ((Number) dims.get(0)).intValue();
                height = ((Number) dims.get(1)).intValue();
                depth = ((Number) dims.get(2)).intValue();
            }
        } else {
            width = task.getIntParameter("width", 5);
            height = task.getIntParameter("height", 4);
            depth = task.getIntParameter("depth", 5);
        }
        
        net.minecraft.world.entity.player.Player nearestPlayer = findNearestPlayer();
        BlockPos groundPos;
        
        if (nearestPlayer != null) {
            net.minecraft.world.phys.Vec3 eyePos = nearestPlayer.getEyePosition(1.0F);
            net.minecraft.world.phys.Vec3 lookVec = nearestPlayer.getLookAngle();
            
            net.minecraft.world.phys.Vec3 targetPos = eyePos.add(lookVec.scale(12));
            
            BlockPos lookTarget = new BlockPos(
                (int)Math.floor(targetPos.x),
                (int)Math.floor(targetPos.y),
                (int)Math.floor(targetPos.z)
            );
            
            groundPos = findGroundLevel(lookTarget);
            
            if (groundPos == null) {
                groundPos = findGroundLevel(nearestPlayer.blockPosition().offset(
                    (int)Math.round(lookVec.x * 10),
                    0,
                    (int)Math.round(lookVec.z * 10)
                ));
            }
            
            SteveMod.LOGGER.info("Building in player's field of view at {} (looking from {} towards {})", 
                groundPos, eyePos, targetPos);
        } else {
            BlockPos buildPos = steve.blockPosition().offset(2, 0, 2);
            groundPos = findGroundLevel(buildPos);
        }
        
        if (groundPos == null) {
            result = ActionResult.failure("Cannot find suitable ground for building in your field of view");
            return;
        }
        
        SteveMod.LOGGER.info("Found ground at Y={} (Build starting at {})", groundPos.getY(), groundPos);
        
        BlockPos clearPos = groundPos;
        
        buildPlan = tryLoadFromTemplate(structureType, clearPos);
        SteveMod.LOGGER.info("tryLoadFromTemplate returned: {} (null: {})", 
            buildPlan != null ? buildPlan.size() + " blocks" : "null", buildPlan == null);
        
        if (buildPlan == null || buildPlan.isEmpty()) {
            // Special handling for text structures
            if ("text".equals(structureType) || "sign".equals(structureType)) {
                String text = task.getStringParameter("text", "HELLO");
                String textColor = task.getStringParameter("textColor", "yellow");
                String backgroundColor = task.getStringParameter("backgroundColor", "blue");
                
                Block textBlock = parseColoredWool(textColor);
                Block backgroundBlock = parseColoredWool(backgroundColor);
                
                SteveMod.LOGGER.info("Building text sign: '{}' with text color: {}, background color: {}", 
                    text, textColor, backgroundColor);
                
                buildPlan = buildText(text, clearPos, width, height, depth, textBlock, backgroundBlock);
            } else {
                // Fall back to procedural generation
                SteveMod.LOGGER.info("NBT template not found or empty for '{}', using procedural generation", structureType);
                buildPlan = generateBuildPlan(structureType, clearPos, width, height, depth);
            }
            
            if (buildPlan != null && !buildPlan.isEmpty()) {
                SteveMod.LOGGER.info("Generated procedural '{}' with {} blocks (dimensions: {}x{}x{})", 
                    structureType, buildPlan.size(), width, height, depth);
            } else {
                SteveMod.LOGGER.error("Procedural generation returned null or empty for '{}'", structureType);
            }
        } else {
            SteveMod.LOGGER.info("Loaded '{}' from NBT template with {} blocks", structureType, buildPlan.size());
        }
        
        if (buildPlan == null || buildPlan.isEmpty()) {
            SteveMod.LOGGER.error("Cannot generate build plan for: {} (buildPlan is null: {}, empty: {})", 
                structureType, buildPlan == null, buildPlan != null && buildPlan.isEmpty());
            result = ActionResult.failure("Cannot generate build plan for: " + structureType);
            return;
        }
        
        StructureRegistry.register(clearPos, width, height, depth, structureType);
        
        collaborativeBuild = CollaborativeBuildManager.findActiveBuild(structureType);
        
        if (collaborativeBuild != null) {
            isCollaborative = true;
            SteveMod.LOGGER.info("Steve '{}' JOINING existing {} collaborative build at {}", 
                steve.getSteveName(), structureType, collaborativeBuild.startPos);
        } else {
            List<CollaborativeBuildManager.BlockPlacement> collaborativeBlocks = new ArrayList<>();
            for (BlockPlacement bp : buildPlan) {
                collaborativeBlocks.add(new CollaborativeBuildManager.BlockPlacement(bp.pos, bp.block));
            }
            
            collaborativeBuild = CollaborativeBuildManager.registerBuild(structureType, collaborativeBlocks, clearPos);
            isCollaborative = true;
            SteveMod.LOGGER.info("Steve '{}' CREATED new {} collaborative build at {}", 
                steve.getSteveName(), structureType, clearPos);
        }
        
        steve.setFlying(true);
        
        SteveMod.LOGGER.info("Steve '{}' starting COLLABORATIVE build of {} at {} with {} blocks using materials: {} [FLYING ENABLED]", 
            steve.getSteveName(), structureType, clearPos, buildPlan.size(), buildMaterials);
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        
        if (ticksRunning > MAX_TICKS) {
            steve.setFlying(false); // Disable flying on timeout
            result = ActionResult.failure("Building timeout");
            return;
        }
        
        if (isCollaborative && collaborativeBuild != null) {
            if (collaborativeBuild.isComplete()) {
                CollaborativeBuildManager.completeBuild(collaborativeBuild.structureId);
                steve.setFlying(false);
                result = ActionResult.success("Built " + structureType + " collaboratively!");
                return;
            }
            
            int blocksPlacedThisTick = 0;
            int maxAttempts = BLOCKS_PER_TICK * 10; // Try more times to find available blocks
            int attempts = 0;
            
            while (blocksPlacedThisTick < BLOCKS_PER_TICK && attempts < maxAttempts) {
                attempts++;
                
                CollaborativeBuildManager.BlockPlacement placement = 
                    CollaborativeBuildManager.getNextBlock(collaborativeBuild, steve.getSteveName());
                
                if (placement == null) {
                    // Check if build is actually complete
                    if (collaborativeBuild.isComplete()) {
                        CollaborativeBuildManager.completeBuild(collaborativeBuild.structureId);
                        steve.setFlying(false);
                        result = ActionResult.success("Built " + structureType + " collaboratively!");
                        SteveMod.LOGGER.info("Steve '{}' completed building {}! Total blocks: {}/{}", 
                            steve.getSteveName(), structureType, 
                            collaborativeBuild.getBlocksPlaced(), collaborativeBuild.getTotalBlocks());
                        return;
                    }
                    
                    // Build not complete, but no blocks available right now
                    // Try again next tick - don't break immediately
                    if (attempts >= maxAttempts && ticksRunning % 20 == 0) {
                        SteveMod.LOGGER.info("Steve '{}' waiting for available blocks. Build {}% complete ({}/{})", 
                            steve.getSteveName(), collaborativeBuild.getProgressPercentage(),
                            collaborativeBuild.getBlocksPlaced(), collaborativeBuild.getTotalBlocks());
                    }
                    break; // Exit loop, will try again next tick
                }
                
                BlockPos pos = placement.pos;
                
                // Check if block is already placed correctly
                BlockState existingState = steve.level().getBlockState(pos);
                if (existingState.getBlock() == placement.block) {
                    // Block already placed correctly, skip it and try next
                    SteveMod.LOGGER.debug("Block at {} already placed correctly, skipping", pos);
                    continue;
                }
                
                double distance = Math.sqrt(steve.blockPosition().distSqr(pos));
                if (distance > 5) {
                    steve.teleportTo(pos.getX() + 2, pos.getY(), pos.getZ() + 2);
                    SteveMod.LOGGER.info("Steve '{}' teleported to block at {}", steve.getSteveName(), pos);
                }
                
                steve.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                
                steve.swing(InteractionHand.MAIN_HAND, true);
                
                BlockState blockState = placement.block.defaultBlockState();
                steve.level().setBlock(pos, blockState, 3);
                blocksPlacedThisTick++;
                
                SteveMod.LOGGER.info("Steve '{}' PLACED BLOCK at {} - Total: {}/{}", 
                    steve.getSteveName(), pos, collaborativeBuild.getBlocksPlaced(), 
                    collaborativeBuild.getTotalBlocks());
                
                // Particles and sound
                if (steve.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        15, 0.4, 0.4, 0.4, 0.15
                    );
                    
                    var soundType = blockState.getSoundType(steve.level(), pos, steve);
                    steve.level().playSound(null, pos, soundType.getPlaceSound(), 
                        SoundSource.BLOCKS, 1.0f, soundType.getPitch());
                }
            }
            
            if (ticksRunning % 100 == 0 && collaborativeBuild.getBlocksPlaced() > 0) {
                int percentComplete = collaborativeBuild.getProgressPercentage();
                SteveMod.LOGGER.info("{} build progress: {}/{} ({}%) - {} Steves working", 
                    structureType, 
                    collaborativeBuild.getBlocksPlaced(), 
                    collaborativeBuild.getTotalBlocks(), 
                    percentComplete,
                    collaborativeBuild.participatingSteves.size());
            }
        } else {
            steve.setFlying(false); // Disable flying on error
            result = ActionResult.failure("Build system error: not in collaborative mode");
        }
    }

    @Override
    protected void onCancel() {
        steve.setFlying(false); // Disable flying when cancelled
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        return "Build " + structureType + " (" + currentBlockIndex + "/" + (buildPlan != null ? buildPlan.size() : 0) + ")";
    }

    private List<BlockPlacement> generateBuildPlan(String type, BlockPos start, int width, int height, int depth) {
        SteveMod.LOGGER.info("Generating build plan for type: '{}', dimensions: {}x{}x{}, materials: {}", 
            type, width, height, depth, buildMaterials);
        
        if (buildMaterials == null || buildMaterials.isEmpty()) {
            SteveMod.LOGGER.error("Build materials are empty! Cannot generate build plan.");
            return null;
        }
        
        List<BlockPlacement> result = switch (type.toLowerCase()) {
            case "house", "home" -> {
                SteveMod.LOGGER.info("Building advanced house");
                yield buildAdvancedHouse(start, width, height, depth);
            }
            case "castle", "catle", "fort" -> {
                SteveMod.LOGGER.info("Building castle");
                yield buildCastle(start, width, height, depth);
            }
            case "tower" -> {
                SteveMod.LOGGER.info("Building tower");
                yield buildAdvancedTower(start, width, height);
            }
            case "wall" -> {
                SteveMod.LOGGER.info("Building wall");
                yield buildWall(start, width, height);
            }
            case "platform" -> {
                SteveMod.LOGGER.info("Building platform");
                yield buildPlatform(start, width, depth);
            }
            case "barn", "shed" -> {
                SteveMod.LOGGER.info("Building barn");
                yield buildBarn(start, width, height, depth);
            }
            case "modern", "modern_house" -> {
                SteveMod.LOGGER.info("Building modern house");
                yield buildModernHouse(start, width, height, depth);
            }
            case "box", "cube" -> {
                SteveMod.LOGGER.info("Building box");
                yield buildBox(start, width, height, depth);
            }
            default -> {
                SteveMod.LOGGER.warn("Unknown structure type '{}', building advanced house", type);
                yield buildAdvancedHouse(start, Math.max(5, width), Math.max(4, height), Math.max(5, depth));
            }
        };
        
        if (result != null) {
            SteveMod.LOGGER.info("Generated {} blocks for structure type '{}'", result.size(), type);
        } else {
            SteveMod.LOGGER.error("Build plan generation returned null for type '{}'", type);
        }
        
        return result;
    }
    
    private Block getMaterial(int index) {
        return buildMaterials.get(index % buildMaterials.size());
    }

    private List<BlockPlacement> buildHouse(BlockPos start, int width, int height, int depth) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block floorMaterial = getMaterial(0);
        Block wallMaterial = getMaterial(1);
        Block roofMaterial = getMaterial(2);
        
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, 0, z), floorMaterial));
            }
        }
        
        for (int y = 1; y < height; y++) {
            for (int x = 0; x < width; x++) {
                blocks.add(new BlockPlacement(start.offset(x, y, 0), wallMaterial)); // Front wall
                blocks.add(new BlockPlacement(start.offset(x, y, depth - 1), wallMaterial)); // Back wall
            }
            for (int z = 1; z < depth - 1; z++) {
                blocks.add(new BlockPlacement(start.offset(0, y, z), wallMaterial)); // Left wall
                blocks.add(new BlockPlacement(start.offset(width - 1, y, z), wallMaterial)); // Right wall
            }
        }
        
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, height, z), roofMaterial));
            }
        }
        
        return blocks;
    }

    private List<BlockPlacement> buildWall(BlockPos start, int width, int height) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block material = getMaterial(0);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                blocks.add(new BlockPlacement(start.offset(x, y, 0), material));
            }
        }
        return blocks;
    }

    private List<BlockPlacement> buildTower(BlockPos start, int width, int height) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block material = getMaterial(0);
        Block accentMaterial = getMaterial(1);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    // Hollow tower with accent corners
                    if (x == 0 || x == width - 1 || z == 0 || z == width - 1) {
                        boolean isCorner = (x == 0 || x == width - 1) && (z == 0 || z == width - 1);
                        Block blockToUse = isCorner ? accentMaterial : material;
                        blocks.add(new BlockPlacement(start.offset(x, y, z), blockToUse));
                    }
                }
            }
        }
        return blocks;
    }

    private List<BlockPlacement> buildPlatform(BlockPos start, int width, int depth) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block material = getMaterial(0);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, 0, z), material));
            }
        }
        return blocks;
    }

    private List<BlockPlacement> buildBox(BlockPos start, int width, int height, int depth) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block material = getMaterial(0);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    blocks.add(new BlockPlacement(start.offset(x, y, z), material));
                }
            }
        }
        return blocks;
    }
    
    private List<BlockPlacement> buildAdvancedHouse(BlockPos start, int width, int height, int depth) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block floorMaterial = getMaterial(0);
        Block wallMaterial = getMaterial(1);
        Block roofMaterial = getMaterial(2);
        Block windowMaterial = Blocks.GLASS_PANE;
        Block doorMaterial = Blocks.OAK_DOOR;
        
        if (roofMaterial == Blocks.GLASS || roofMaterial == Blocks.GLASS_PANE) {
            roofMaterial = Blocks.OAK_PLANKS; // Force wood roof if glass was selected
        }
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, 0, z), floorMaterial));
            }
        }
        for (int y = 1; y <= height; y++) {
            for (int x = 0; x < width; x++) {
                if (x == width / 2 && y <= 2) {
                    blocks.add(new BlockPlacement(start.offset(x, y, 0), doorMaterial));
                } else if (y >= 2 && y <= height - 1 && (x == 2 || x == width - 3)) {
                    // Windows on front wall (taller windows)
                    blocks.add(new BlockPlacement(start.offset(x, y, 0), windowMaterial));
                } else {
                    blocks.add(new BlockPlacement(start.offset(x, y, 0), wallMaterial));
                }
                
                // BACK WALL - Multiple windows
                if (y >= 2 && y <= height - 1 && (x == 2 || x == width / 2 || x == width - 3)) {
                    blocks.add(new BlockPlacement(start.offset(x, y, depth - 1), windowMaterial));
                } else {
                    blocks.add(new BlockPlacement(start.offset(x, y, depth - 1), wallMaterial));
                }
            }
            for (int z = 1; z < depth - 1; z++) {
                // Left and right walls with multiple windows
                if (y >= 2 && y <= height - 1 && (z % 3 == 1)) {
                    blocks.add(new BlockPlacement(start.offset(0, y, z), windowMaterial));
                    blocks.add(new BlockPlacement(start.offset(width - 1, y, z), windowMaterial));
                } else {
                    blocks.add(new BlockPlacement(start.offset(0, y, z), wallMaterial));
                    blocks.add(new BlockPlacement(start.offset(width - 1, y, z), wallMaterial));
                }
            }
        }
        int roofStartHeight = height + 1;
        int roofLayers = Math.max(width, depth) / 2 + 1;
        
        for (int layer = 0; layer < roofLayers; layer++) {
            int currentHeight = roofStartHeight + layer;
            int inset = layer;
            
            for (int x = inset; x < width - inset; x++) {
                for (int z = inset; z < depth - inset; z++) {
                    if (x == inset || x == width - 1 - inset || 
                        z == inset || z == depth - 1 - inset) {
                        blocks.add(new BlockPlacement(start.offset(x, currentHeight, z), roofMaterial));
                    }
                }
            }
            
            if (width - 2 * inset <= 1 || depth - 2 * inset <= 1) {
                break;
            }
        }
        
        return blocks;
    }
    
    private List<BlockPlacement> buildCastle(BlockPos start, int width, int height, int depth) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block stoneMaterial = Blocks.STONE_BRICKS;
        Block wallMaterial = Blocks.COBBLESTONE;
        Block accentMaterial = getMaterial(2); // Use third material for accent
        Block windowMaterial = Blocks.GLASS_PANE;
        
        for (int y = 0; y <= height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    boolean isEdge = (x == 0 || x == width - 1 || z == 0 || z == depth - 1);
                    boolean isCorner = (x <= 2 || x >= width - 3) && (z <= 2 || z >= depth - 3);
                    
                    if (y == 0) {
                        // Solid stone floor
                        blocks.add(new BlockPlacement(start.offset(x, y, z), stoneMaterial));
                    } else if (isEdge && !isCorner) {
                        if (x == width / 2 && z == 0 && y <= 3) {
                            if (y >= 1 && y <= 3 && x >= width / 2 - 1 && x <= width / 2 + 1) {
                                blocks.add(new BlockPlacement(start.offset(x, y, 0), Blocks.AIR));
                            }
                        } else if (y % 4 == 2 && !isCorner) {
                            // Arrow slit windows
                            blocks.add(new BlockPlacement(start.offset(x, y, z), windowMaterial));
                        } else {
                            // Thick stone walls
                            blocks.add(new BlockPlacement(start.offset(x, y, z), wallMaterial));
                        }
                    }
                }
            }
        }
        
        int towerHeight = height + 6; // Much taller towers
        int towerSize = 3;
        int[][] corners = {{0, 0}, {width - towerSize, 0}, {0, depth - towerSize}, {width - towerSize, depth - towerSize}};
        
        for (int[] corner : corners) {
            for (int y = 0; y <= towerHeight; y++) {
                for (int dx = 0; dx < towerSize; dx++) {
                    for (int dz = 0; dz < towerSize; dz++) {
                        boolean isTowerEdge = (dx == 0 || dx == towerSize - 1 || dz == 0 || dz == towerSize - 1);
                        
                        if (y == 0 || isTowerEdge) {
                            // Solid base and hollow center
                            blocks.add(new BlockPlacement(start.offset(corner[0] + dx, y, corner[1] + dz), stoneMaterial));
                        }
                        
                        // Windows on towers
                        if (y % 5 == 3 && isTowerEdge && (dx == towerSize / 2 || dz == towerSize / 2)) {
                            blocks.add(new BlockPlacement(start.offset(corner[0] + dx, y, corner[1] + dz), windowMaterial));
                        }
                    }
                }
            }
            for (int dx = 0; dx < towerSize; dx++) {
                for (int dz = 0; dz < towerSize; dz++) {
                    if (dx % 2 == 0 || dz % 2 == 0) {
                        blocks.add(new BlockPlacement(start.offset(corner[0] + dx, towerHeight + 1, corner[1] + dz), stoneMaterial));
                    }
                }
            }
        }
        for (int x = 0; x < width; x += 2) {
            blocks.add(new BlockPlacement(start.offset(x, height + 1, 0), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(x, height + 2, 0), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(x, height + 1, depth - 1), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(x, height + 2, depth - 1), stoneMaterial));
        }
        for (int z = 0; z < depth; z += 2) {
            blocks.add(new BlockPlacement(start.offset(0, height + 1, z), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(0, height + 2, z), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(width - 1, height + 1, z), stoneMaterial));
            blocks.add(new BlockPlacement(start.offset(width - 1, height + 2, z), stoneMaterial));
        }
        
        return blocks;
    }
    
    private List<BlockPlacement> buildModernHouse(BlockPos start, int width, int height, int depth) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block wallMaterial = Blocks.QUARTZ_BLOCK;
        Block floorMaterial = Blocks.SMOOTH_STONE;
        Block glassMaterial = Blocks.GLASS;
        Block roofMaterial = Blocks.DARK_OAK_PLANKS;
        
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, 0, z), floorMaterial));
            }
        }
        
        // Modern design with lots of glass
        for (int y = 1; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Front - mostly glass
                if (x % 2 == 0 || y > 1) {
                    blocks.add(new BlockPlacement(start.offset(x, y, 0), glassMaterial));
                } else {
                    blocks.add(new BlockPlacement(start.offset(x, y, 0), wallMaterial));
                }
                
                blocks.add(new BlockPlacement(start.offset(x, y, depth - 1), wallMaterial));
            }
            
            for (int z = 1; z < depth - 1; z++) {
                // Side walls with some glass
                if (z % 3 == 1 && y == 2) {
                    blocks.add(new BlockPlacement(start.offset(0, y, z), glassMaterial));
                    blocks.add(new BlockPlacement(start.offset(width - 1, y, z), glassMaterial));
                } else {
                    blocks.add(new BlockPlacement(start.offset(0, y, z), wallMaterial));
                    blocks.add(new BlockPlacement(start.offset(width - 1, y, z), wallMaterial));
                }
            }
        }
        
        // Flat modern roof
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, height, z), roofMaterial));
            }
        }
        
        return blocks;
    }
    
    private List<BlockPlacement> buildBarn(BlockPos start, int width, int height, int depth) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block woodMaterial = Blocks.OAK_PLANKS;
        Block logMaterial = Blocks.OAK_LOG;
        Block roofMaterial = Blocks.SPRUCE_PLANKS;
        
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, 0, z), woodMaterial));
            }
        }
        
        for (int y = 1; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean isSupport = (x == 0 || x == width - 1 || x == width / 2);
                Block material = isSupport ? logMaterial : woodMaterial;
                
                // Large door opening in front
                if (x >= width / 3 && x <= 2 * width / 3 && y <= 2) {
                    continue; // Skip for large opening
                }
                
                blocks.add(new BlockPlacement(start.offset(x, y, 0), material));
                blocks.add(new BlockPlacement(start.offset(x, y, depth - 1), material));
            }
            
            for (int z = 1; z < depth - 1; z++) {
                blocks.add(new BlockPlacement(start.offset(0, y, z), logMaterial));
                blocks.add(new BlockPlacement(start.offset(width - 1, y, z), logMaterial));
            }
        }
        
        // Tall peaked roof
        int roofPeakHeight = height + width / 2;
        for (int x = 0; x < width; x++) {
            int distFromCenter = Math.abs(x - width / 2);
            int roofY = roofPeakHeight - distFromCenter;
            
            for (int z = 0; z < depth; z++) {
                blocks.add(new BlockPlacement(start.offset(x, roofY, z), roofMaterial));
            }
        }
        
        return blocks;
    }
    
    private List<BlockPlacement> buildAdvancedTower(BlockPos start, int width, int height) {
        List<BlockPlacement> blocks = new ArrayList<>();
        Block wallMaterial = Blocks.STONE_BRICKS;
        Block accentMaterial = Blocks.CHISELED_STONE_BRICKS;
        Block windowMaterial = Blocks.GLASS_PANE;
        Block roofMaterial = Blocks.DARK_OAK_STAIRS;
        
        // Main tower body
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    boolean isEdge = (x == 0 || x == width - 1 || z == 0 || z == width - 1);
                    boolean isCorner = (x == 0 || x == width - 1) && (z == 0 || z == width - 1);
                    
                    if (y == 0) {
                        blocks.add(new BlockPlacement(start.offset(x, y, z), wallMaterial));
                    } else if (isEdge) {
                        // Windows every few levels
                        if (y % 3 == 2 && !isCorner && (x == width / 2 || z == width / 2)) {
                            blocks.add(new BlockPlacement(start.offset(x, y, z), windowMaterial));
                        } else if (isCorner) {
                            blocks.add(new BlockPlacement(start.offset(x, y, z), accentMaterial));
                        } else {
                            blocks.add(new BlockPlacement(start.offset(x, y, z), wallMaterial));
                        }
                    }
                }
            }
        }
        
        for (int i = 0; i < width / 2 + 1; i++) {
            for (int x = i; x < width - i; x++) {
                for (int z = i; z < width - i; z++) {
                    if (x == i || x == width - 1 - i || z == i || z == width - 1 - i) {
                        blocks.add(new BlockPlacement(start.offset(x, height + i, z), roofMaterial));
                    }
                }
            }
        }
        
        return blocks;
    }

    private Block parseBlock(String blockName) {
        blockName = blockName.toLowerCase().replace(" ", "_");
        if (!blockName.contains(":")) {
            blockName = "minecraft:" + blockName;
        }
        ResourceLocation resourceLocation = new ResourceLocation(blockName);
        Block block = BuiltInRegistries.BLOCK.get(resourceLocation);
        return block != null ? block : Blocks.AIR;
    }
    
    /**
     * Parse color name to colored wool block
     */
    private Block parseColoredWool(String colorName) {
        colorName = colorName.toLowerCase().trim();
        String woolName = "minecraft:" + colorName + "_wool";
        ResourceLocation resourceLocation = new ResourceLocation(woolName);
        Block block = BuiltInRegistries.BLOCK.get(resourceLocation);
        if (block != null && block != Blocks.AIR) {
            return block;
        }
        // Fallback to yellow wool if color not found
        SteveMod.LOGGER.warn("Color '{}' not found, using yellow_wool", colorName);
        return Blocks.YELLOW_WOOL;
    }
    
    /**
     * Build text sign using TextRenderer
     */
    private List<BlockPlacement> buildText(String text, BlockPos start, int width, int height, int thickness, 
                                           Block textBlock, Block backgroundBlock) {
        List<TextRenderer.BlockPlacement> textBlocks = TextRenderer.renderText(
            text, start, width, height, thickness, textBlock, backgroundBlock);
        
        // Convert TextRenderer.BlockPlacement to BuildStructureAction.BlockPlacement
        List<BlockPlacement> result = new ArrayList<>();
        for (TextRenderer.BlockPlacement tp : textBlocks) {
            result.add(new BlockPlacement(tp.pos, tp.block));
        }
        
        return result;
    }
    
    /**
     * Find the actual ground level from a starting position
     * Scans downward to find solid ground, or upward if underground
     */
    private BlockPos findGroundLevel(BlockPos startPos) {
        int maxScanDown = 20; // Scan up to 20 blocks down
        int maxScanUp = 10;   // Scan up to 10 blocks up if we're underground
        
        // First, try scanning downward to find ground
        for (int i = 0; i < maxScanDown; i++) {
            BlockPos checkPos = startPos.below(i);
            BlockPos belowPos = checkPos.below();
            
            if (steve.level().getBlockState(checkPos).isAir() && 
                isSolidGround(belowPos)) {
                return checkPos; // This is ground level
            }
        }
        
        // Scan upward to find the surface
        for (int i = 1; i < maxScanUp; i++) {
            BlockPos checkPos = startPos.above(i);
            BlockPos belowPos = checkPos.below();
            
            if (steve.level().getBlockState(checkPos).isAir() && 
                isSolidGround(belowPos)) {
                return checkPos;
            }
        }
        
        // but make sure there's something solid below
        BlockPos fallbackPos = startPos;
        while (!isSolidGround(fallbackPos.below()) && fallbackPos.getY() > -64) {
            fallbackPos = fallbackPos.below();
        }
        
        return fallbackPos;
    }
    
    /**
     * Check if a position has solid ground suitable for building
     */
    private boolean isSolidGround(BlockPos pos) {
        var blockState = steve.level().getBlockState(pos);
        var block = blockState.getBlock();
        
        // Not solid if it's air or liquid
        if (blockState.isAir() || block == Blocks.WATER || block == Blocks.LAVA) {
            return false;
        }
        
        return blockState.isSolid();
    }
    
    /**
     * Find a suitable building site with flat, clear ground
     * Searches for an area that is:
     * - Relatively flat (max 2 block height difference)
     * - Clear of obstructions (trees, rocks, etc.)
     * - Has enough vertical space for the structure
     */
    private BlockPos findSuitableBuildingSite(BlockPos startPos, int width, int height, int depth) {
        int maxSearchRadius = 10;
        int searchStep = 3; // Small steps to stay nearby
        
        if (isAreaSuitable(startPos, width, height, depth)) {
            return startPos;
        }        // Search in expanding circles
        for (int radius = searchStep; radius < maxSearchRadius; radius += searchStep) {
            for (int angle = 0; angle < 360; angle += 45) { // Check every 45 degrees
                double radians = Math.toRadians(angle);
                int offsetX = (int) (Math.cos(radians) * radius);
                int offsetZ = (int) (Math.sin(radians) * radius);
                
                BlockPos testPos = new BlockPos(
                    startPos.getX() + offsetX,
                    startPos.getY(),
                    startPos.getZ() + offsetZ
                );
                
                BlockPos groundPos = findGroundLevel(testPos);
                if (groundPos != null && isAreaSuitable(groundPos, width, height, depth)) {
                    SteveMod.LOGGER.info("Found suitable flat ground at {} ({}m away)", groundPos, radius);
                    return groundPos;
                }
            }
        }
        
        SteveMod.LOGGER.warn("Could not find suitable flat ground within {}m", maxSearchRadius);
        return null;
    }
    
    /**
     * Check if an area is suitable for building
     * - Must be relatively flat (max 2 block height variation)
     * - Must be clear of obstructions above ground
     * - Must have solid ground below
     */
    private boolean isAreaSuitable(BlockPos startPos, int width, int height, int depth) {
        // Sample key points in the build area to check terrain
        int samples = 0;
        int maxSamples = 9; // Check 9 points (corners + center + midpoints)
        int unsuitable = 0;
        
        BlockPos[] checkPoints = {
            startPos,                                    // Front-left corner
            startPos.offset(width - 1, 0, 0),           // Front-right corner
            startPos.offset(0, 0, depth - 1),           // Back-left corner
            startPos.offset(width - 1, 0, depth - 1),   // Back-right corner
            startPos.offset(width / 2, 0, depth / 2),   // Center
            startPos.offset(width / 2, 0, 0),           // Front-center
            startPos.offset(width / 2, 0, depth - 1),   // Back-center
            startPos.offset(0, 0, depth / 2),           // Left-center
            startPos.offset(width - 1, 0, depth / 2)    // Right-center
        };
        
        int minY = startPos.getY();
        int maxY = startPos.getY();
        
        for (BlockPos checkPos : checkPoints) {
            samples++;
            
            if (!isSolidGround(checkPos.below())) {
                unsuitable++;
                continue;
            }
            
            BlockPos actualGround = findGroundLevel(checkPos);
            if (actualGround != null) {
                minY = Math.min(minY, actualGround.getY());
                maxY = Math.max(maxY, actualGround.getY());
            }
            
            for (int y = 1; y <= Math.min(height, 3); y++) {
                BlockPos abovePos = checkPos.above(y);
                var blockState = steve.level().getBlockState(abovePos);
                
                if (!blockState.isAir()) {
                    Block block = blockState.getBlock();
                    if (block != Blocks.GRASS && block != Blocks.TALL_GRASS && 
                        block != Blocks.FERN && block != Blocks.DEAD_BUSH &&
                        block != Blocks.DANDELION && block != Blocks.POPPY) {
                        unsuitable++;
                        break;
                    }
                }
            }
        }
        
        int heightVariation = maxY - minY;
        if (heightVariation > 2) {
            SteveMod.LOGGER.debug("Area at {} too uneven ({}m height difference)", startPos, heightVariation);
            return false;
        }
        
        // Area is suitable if less than 30% of samples are problematic
        boolean suitable = unsuitable < (maxSamples * 0.3);
        
        if (!suitable) {
            SteveMod.LOGGER.debug("Area at {} has too many obstructions ({}/{})", startPos, unsuitable, samples);
        }
        
        return suitable;
    }
    
    /**
     * Try to load structure from NBT template file
     * Returns null if no template found (falls back to procedural generation)
     */
    private List<BlockPlacement> tryLoadFromTemplate(String structureName, BlockPos startPos) {
        if (!(steve.level() instanceof ServerLevel serverLevel)) {
            SteveMod.LOGGER.debug("Level is not ServerLevel, cannot load template");
            return null;
        }
        
        SteveMod.LOGGER.info("Attempting to load NBT template for '{}'", structureName);
        var template = StructureTemplateLoader.loadFromNBT(serverLevel, structureName);
        if (template == null) {
            SteveMod.LOGGER.info("Template '{}' not found in NBT files", structureName);
            return null;
        }
        
        if (template.blocks == null || template.blocks.isEmpty()) {
            SteveMod.LOGGER.warn("Template '{}' loaded but has no blocks", structureName);
            return null;
        }
        
        List<BlockPlacement> blocks = new ArrayList<>();
        for (var templateBlock : template.blocks) {
            BlockPos worldPos = startPos.offset(templateBlock.relativePos);
            Block block = templateBlock.blockState.getBlock();
            blocks.add(new BlockPlacement(worldPos, block));
        }
        
        SteveMod.LOGGER.info("Successfully loaded template '{}' with {} blocks", structureName, blocks.size());
        return blocks;
    }
    
    /**
     * Find the nearest player to build in front of
     */
    private net.minecraft.world.entity.player.Player findNearestPlayer() {
        java.util.List<? extends net.minecraft.world.entity.player.Player> players = steve.level().players();
        
        if (players.isEmpty()) {
            return null;
        }
        
        net.minecraft.world.entity.player.Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (net.minecraft.world.entity.player.Player player : players) {
            if (!player.isAlive() || player.isRemoved() || player.isSpectator()) {
                continue;
            }
            
            double distance = steve.distanceTo(player);
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        
        return nearest;
    }
    
}

