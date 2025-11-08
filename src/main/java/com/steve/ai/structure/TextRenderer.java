package com.steve.ai.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders text as blocks in Minecraft.
 * Creates a bitmap representation of text and converts it to block placements.
 */
public class TextRenderer {
    
    /**
     * Simple 5x7 pixel font for characters
     * Each character is represented as a 5x7 grid of bits
     * Using Map to support Unicode characters (Cyrillic, etc.)
     */
    private static final Map<Character, int[]> FONT_5x7 = new HashMap<>();
    
    static {
        // Initialize font data for common characters
        // Format: 5 columns, 7 rows (top to bottom)
        // 1 = pixel on, 0 = pixel off
        
        // Space
        FONT_5x7.put(' ', new int[]{0, 0, 0, 0, 0, 0, 0});
        
        // A
        FONT_5x7.put('A', new int[]{
            0b01110,  //  ###
            0b10001,  // #   #
            0b10001,  // #   #
            0b11111,  // #####
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001   // #   #
        });
        
        // B
        FONT_5x7.put('B', new int[]{
            0b11110,  // ####
            0b10001,  // #   #
            0b10001,  // #   #
            0b11110,  // ####
            0b10001,  // #   #
            0b10001,  // #   #
            0b11110   // ####
        });
        
        // E
        FONT_5x7.put('E', new int[]{
            0b11111,  // #####
            0b10000,  // #
            0b10000,  // #
            0b11110,  // ####
            0b10000,  // #
            0b10000,  // #
            0b11111   // #####
        });
        
        // H
        FONT_5x7.put('H', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b11111,  // #####
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001   // #   #
        });
        
        // I
        FONT_5x7.put('I', new int[]{
            0b11111,  // #####
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b11111   // #####
        });
        
        // P
        FONT_5x7.put('P', new int[]{
            0b11110,  // ####
            0b10001,  // #   #
            0b10001,  // #   #
            0b11110,  // ####
            0b10000,  // #
            0b10000,  // #
            0b10000   // #
        });
        
        // R
        FONT_5x7.put('R', new int[]{
            0b11110,  // ####
            0b10001,  // #   #
            0b10001,  // #   #
            0b11110,  // ####
            0b10100,  // # #
            0b10010,  // #  #
            0b10001   // #   #
        });
        
        // Complete English alphabet (remaining letters) - must be before Cyrillic references
        FONT_5x7.put('C', new int[]{
            0b01110,  //  ###
            0b10001,  // #   #
            0b10000,  // #
            0b10000,  // #
            0b10000,  // #
            0b10001,  // #   #
            0b01110   //  ###
        });
        
        FONT_5x7.put('D', new int[]{
            0b11110,  // ####
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b11110   // ####
        });
        
        FONT_5x7.put('F', new int[]{
            0b11111,  // #####
            0b10000,  // #
            0b10000,  // #
            0b11110,  // ####
            0b10000,  // #
            0b10000,  // #
            0b10000   // #
        });
        
        FONT_5x7.put('G', new int[]{
            0b01110,  //  ###
            0b10001,  // #   #
            0b10000,  // #
            0b10111,  // # ###
            0b10001,  // #   #
            0b10001,  // #   #
            0b01110   //  ###
        });
        
        FONT_5x7.put('J', new int[]{
            0b01111,  //  ####
            0b00001,  //     #
            0b00001,  //     #
            0b00001,  //     #
            0b10001,  // #   #
            0b10001,  // #   #
            0b01110   //  ###
        });
        
        FONT_5x7.put('K', new int[]{
            0b10001,  // #   #
            0b10010,  // #  #
            0b10100,  // # #
            0b11000,  // ##
            0b10100,  // # #
            0b10010,  // #  #
            0b10001   // #   #
        });
        
        FONT_5x7.put('L', new int[]{
            0b10000,  // #
            0b10000,  // #
            0b10000,  // #
            0b10000,  // #
            0b10000,  // #
            0b10000,  // #
            0b11111   // #####
        });
        
        FONT_5x7.put('M', new int[]{
            0b10001,  // #   #
            0b11011,  // ## ##
            0b10101,  // # # #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001   // #   #
        });
        
        FONT_5x7.put('N', new int[]{
            0b10001,  // #   #
            0b11001,  // ##  #
            0b10101,  // # # #
            0b10011,  // #  ##
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001   // #   #
        });
        
        FONT_5x7.put('O', new int[]{
            0b01110,  //  ###
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b01110   //  ###
        });
        
        FONT_5x7.put('Q', new int[]{
            0b01110,  //  ###
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10101,  // # # #
            0b10010,  // #  #
            0b01101   //  ## #
        });
        
        FONT_5x7.put('S', new int[]{
            0b01110,  //  ###
            0b10001,  // #   #
            0b10000,  // #
            0b01110,  //  ###
            0b00001,  //     #
            0b10001,  // #   #
            0b01110   //  ###
        });
        
        FONT_5x7.put('T', new int[]{
            0b11111,  // #####
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b00100   //   #
        });
        
        FONT_5x7.put('U', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b01110   //  ###
        });
        
        FONT_5x7.put('V', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b01010,  //  # #
            0b01010,  //  # #
            0b00100   //   #
        });
        
        FONT_5x7.put('W', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10101,  // # # #
            0b10101,  // # # #
            0b11011,  // ## ##
            0b10001   // #   #
        });
        
        FONT_5x7.put('X', new int[]{
            0b10001,  // #   #
            0b01010,  //  # #
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b01010,  //  # #
            0b10001   // #   #
        });
        
        FONT_5x7.put('Y', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b01010,  //  # #
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b00100   //   #
        });
        
        FONT_5x7.put('Z', new int[]{
            0b11111,  // #####
            0b00001,  //     #
            0b00010,  //    #
            0b00100,  //   #
            0b01000,  //  #
            0b10000,  // #
            0b11111   // #####
        });
        
        // Cyrillic characters (after all Latin letters are added)
        FONT_5x7.put('А', FONT_5x7.get('A')); // Cyrillic А
        FONT_5x7.put('Р', FONT_5x7.get('P')); // Cyrillic Р
        FONT_5x7.put('У', new int[]{      // Cyrillic У
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b01010,  //  # #
            0b00100,  //   #
            0b00100,  //   #
            0b11000   // ##
        });
        
        FONT_5x7.put('Т', new int[]{      // Cyrillic Т
            0b11111,  // #####
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b00100   //   #
        });
        
        FONT_5x7.put('И', new int[]{      // Cyrillic И
            0b10001,  // #   #
            0b10001,  // #   #
            0b10011,  // #  ##
            0b10101,  // # # #
            0b11001,  // ##  #
            0b10001,  // #   #
            0b10001   // #   #
        });
        
        // More numbers
        FONT_5x7.put('6', new int[]{
            0b01110,  //  ###
            0b10000,  // #
            0b10000,  // #
            0b11110,  // ####
            0b10001,  // #   #
            0b10001,  // #   #
            0b01110   //  ###
        });
        
        FONT_5x7.put('7', new int[]{
            0b11111,  // #####
            0b00001,  //     #
            0b00010,  //    #
            0b00100,  //   #
            0b01000,  //  #
            0b10000,  // #
            0b10000   // #
        });
        
        FONT_5x7.put('8', new int[]{
            0b01110,  //  ###
            0b10001,  // #   #
            0b10001,  // #   #
            0b01110,  //  ###
            0b10001,  // #   #
            0b10001,  // #   #
            0b01110   //  ###
        });
        
        FONT_5x7.put('9', new int[]{
            0b01110,  //  ###
            0b10001,  // #   #
            0b10001,  // #   #
            0b01111,  //  ####
            0b00001,  //     #
            0b00001,  //     #
            0b01110   //  ###
        });
        
        // Numbers
        FONT_5x7.put('0', new int[]{
            0b01110,  //  ###
            0b10001,  // #   #
            0b10011,  // #  ##
            0b10101,  // # # #
            0b11001,  // ##  #
            0b10001,  // #   #
            0b01110   //  ###
        });
        
        FONT_5x7.put('1', new int[]{
            0b00100,  //   #
            0b01100,  //  ##
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b00100,  //   #
            0b01110   //  ###
        });
        
        FONT_5x7.put('2', new int[]{
            0b01110,  //  ###
            0b10001,  // #   #
            0b00001,  //     #
            0b00110,  //   ##
            0b01000,  //  #
            0b10000,  // #
            0b11111   // #####
        });
        
        FONT_5x7.put('3', new int[]{
            0b11110,  // ####
            0b00001,  //     #
            0b00001,  //     #
            0b01110,  //  ###
            0b00001,  //     #
            0b00001,  //     #
            0b11110   // ####
        });
        
        FONT_5x7.put('4', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b11111,  // #####
            0b00001,  //     #
            0b00001,  //     #
            0b00001   //     #
        });
        
        FONT_5x7.put('5', new int[]{
            0b11111,  // #####
            0b10000,  // #
            0b10000,  // #
            0b11110,  // ####
            0b00001,  //     #
            0b00001,  //     #
            0b11110   // ####
        });
        
        // Complete Cyrillic alphabet (all Latin letters are already added above)
        FONT_5x7.put('Б', new int[]{
            0b11110,  // ####
            0b10000,  // #
            0b10000,  // #
            0b11110,  // ####
            0b10001,  // #   #
            0b10001,  // #   #
            0b11110   // ####
        });
        
        FONT_5x7.put('В', FONT_5x7.get('B')); // Cyrillic В = Latin B
        
        FONT_5x7.put('Г', new int[]{
            0b11111,  // #####
            0b10000,  // #
            0b10000,  // #
            0b10000,  // #
            0b10000,  // #
            0b10000,  // #
            0b10000   // #
        });
        
        FONT_5x7.put('Д', new int[]{
            0b00110,  //   ##
            0b01010,  //  # #
            0b01010,  //  # #
            0b10001,  // #   #
            0b10001,  // #   #
            0b11111,  // #####
            0b10001   // #   #
        });
        
        FONT_5x7.put('Е', FONT_5x7.get('E')); // Cyrillic Е = Latin E
        
        FONT_5x7.put('Ё', FONT_5x7.get('E')); // Cyrillic Ё = Latin E
        
        FONT_5x7.put('Ж', new int[]{
            0b10101,  // # # #
            0b10101,  // # # #
            0b01010,  //  # #
            0b11111,  // #####
            0b01010,  //  # #
            0b10101,  // # # #
            0b10101   // # # #
        });
        
        FONT_5x7.put('З', new int[]{
            0b11110,  // ####
            0b00001,  //     #
            0b00001,  //     #
            0b01110,  //  ###
            0b00001,  //     #
            0b00001,  //     #
            0b11110   // ####
        });
        
        FONT_5x7.put('Й', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b11111,  // #####
            0b10001,  // #   #
            0b10001,  // #   #
            0b01010   //  # #
        });
        
        FONT_5x7.put('К', FONT_5x7.get('K')); // Cyrillic К = Latin K
        
        FONT_5x7.put('Л', new int[]{
            0b00111,  //   ###
            0b01001,  //  #  #
            0b01001,  //  #  #
            0b01001,  //  #  #
            0b01001,  //  #  #
            0b10001,  // #   #
            0b10001   // #   #
        });
        
        FONT_5x7.put('М', FONT_5x7.get('M')); // Cyrillic М = Latin M
        
        FONT_5x7.put('Н', FONT_5x7.get('H')); // Cyrillic Н = Latin H
        
        FONT_5x7.put('О', FONT_5x7.get('O')); // Cyrillic О = Latin O
        
        FONT_5x7.put('П', new int[]{
            0b11111,  // #####
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001   // #   #
        });
        
        FONT_5x7.put('С', FONT_5x7.get('C')); // Cyrillic С = Latin C
        
        FONT_5x7.put('Ф', new int[]{
            0b00100,  //   #
            0b01110,  //  ###
            0b10101,  // # # #
            0b10101,  // # # #
            0b01110,  //  ###
            0b00100,  //   #
            0b00100   //   #
        });
        
        FONT_5x7.put('Х', FONT_5x7.get('X')); // Cyrillic Х = Latin X
        
        FONT_5x7.put('Ц', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b11111,  // #####
            0b00001   //     #
        });
        
        FONT_5x7.put('Ч', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b01111,  //  ####
            0b00001,  //     #
            0b00001,  //     #
            0b00001   //     #
        });
        
        FONT_5x7.put('Ш', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b11111   // #####
        });
        
        FONT_5x7.put('Щ', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b11111,  // #####
            0b00001   //     #
        });
        
        FONT_5x7.put('Ъ', new int[]{
            0b11000,  // ##
            0b01000,  //  #
            0b01000,  //  #
            0b01110,  //  ###
            0b01001,  //  #  #
            0b01001,  //  #  #
            0b01110   //  ###
        });
        
        FONT_5x7.put('Ы', new int[]{
            0b10001,  // #   #
            0b10001,  // #   #
            0b10001,  // #   #
            0b11101,  // ### #
            0b10011,  // #  ##
            0b10011,  // #  ##
            0b11101   // ### #
        });
        
        FONT_5x7.put('Ь', new int[]{
            0b10000,  // #
            0b10000,  // #
            0b10000,  // #
            0b11110,  // ####
            0b10001,  // #   #
            0b10001,  // #   #
            0b11110   // ####
        });
        
        FONT_5x7.put('Э', new int[]{
            0b11110,  // ####
            0b00001,  //     #
            0b00001,  //     #
            0b01111,  //  ####
            0b00001,  //     #
            0b00001,  //     #
            0b11110   // ####
        });
        
        FONT_5x7.put('Ю', new int[]{
            0b10010,  // #  #
            0b10101,  // # # #
            0b10101,  // # # #
            0b11001,  // ##  #
            0b10101,  // # # #
            0b10101,  // # # #
            0b10010   // #  #
        });
        
        FONT_5x7.put('Я', new int[]{
            0b01111,  //  ####
            0b10001,  // #   #
            0b10001,  // #   #
            0b01111,  //  ####
            0b00101,  //   # #
            0b01001,  //  #  #
            0b10001   // #   #
        });
    }
    
    /**
     * Renders text as blocks with scalable font
     * @param text Text to render
     * @param startPos Starting position (bottom-left corner of text)
     * @param width Total width of the sign area
     * @param height Total height of the sign area
     * @param thickness Thickness (depth) of the sign
     * @param textBlock Block type for text (letters)
     * @param backgroundBlock Block type for background
     * @return List of block placements
     */
    public static List<BlockPlacement> renderText(
            String text,
            BlockPos startPos,
            int width,
            int height,
            int thickness,
            Block textBlock,
            Block backgroundBlock) {
        
        List<BlockPlacement> blocks = new ArrayList<>();
        
        // Convert text to uppercase for simplicity
        text = text.toUpperCase();
        
        // Base font dimensions (in pixels)
        int baseCharWidth = 5;  // 5 pixels per character
        int baseCharHeight = 7; // 7 pixels per character
        int baseCharSpacing = 1; // 1 pixel between characters
        
        // Margins from edges (2-3 blocks, but adaptive based on build size)
        // For larger builds, use smaller relative margins to maximize text area
        // For very large builds, use minimal margins (2 blocks)
        int margin = Math.max(2, Math.min(3, (int)(Math.min(width, height) / 20.0)));
        
        // Available area for text (after margins)
        int availableWidth = width - 2 * margin;
        int availableHeight = height - 2 * margin;
        
        // Calculate scale factor to fit text proportionally
        // Find maximum scale that fits both width and height
        int textLength = text.length();
        
        // Calculate base text width
        int baseTextWidth = textLength * (baseCharWidth + baseCharSpacing) - baseCharSpacing;
        
        // Find maximum scale by trying different scales and checking if text fits
        // Calculate theoretical maximums using double precision
        double maxScaleByHeight = (double) availableHeight / baseCharHeight;
        double maxScaleByWidth = baseTextWidth > 0 ? (double) availableWidth / baseTextWidth : maxScaleByHeight;
        int theoreticalMaxScale = (int) Math.floor(Math.min(maxScaleByHeight, maxScaleByWidth));
        
        // Find the actual maximum scale that fits (try from max down to 1)
        int scale = 1;
        for (int testScale = Math.max(1, theoreticalMaxScale + 1); testScale >= 1; testScale--) {
            int testScaledHeight = baseCharHeight * testScale;
            int testScaledWidth = baseTextWidth * testScale;
            
            if (testScaledHeight <= availableHeight && testScaledWidth <= availableWidth) {
                scale = testScale;
                break; // Found the maximum scale that fits
            }
        }
        
        // Log scale calculation for debugging
        com.steve.ai.SteveMod.LOGGER.info("Text rendering: text='{}', size={}x{}, margin={}, available={}x{}, baseTextWidth={}, scale={} (maxHeight={}, maxWidth={})",
            text, width, height, margin, availableWidth, availableHeight, baseTextWidth, scale, 
            String.format("%.2f", (double) availableHeight / baseCharHeight), 
            baseTextWidth > 0 ? String.format("%.2f", (double) availableWidth / baseTextWidth) : "N/A");
        
        // Recalculate with actual scale
        int scaledCharWidth = baseCharWidth * scale;
        int scaledCharHeight = baseCharHeight * scale;
        int scaledCharSpacing = baseCharSpacing * scale;
        
        // Calculate how many characters actually fit with this scale
        int maxChars = (availableWidth + scaledCharSpacing) / (scaledCharWidth + scaledCharSpacing);
        int charsToRender = Math.min(text.length(), maxChars);
        
        // Calculate text dimensions in blocks
        int textBlockWidth = charsToRender * (scaledCharWidth + scaledCharSpacing) - scaledCharSpacing;
        int textBlockHeight = scaledCharHeight;
        
        // Center text in available area
        int textStartX = margin + (availableWidth - textBlockWidth) / 2;
        int textStartY = margin + (availableHeight - textBlockHeight) / 2;
        
        // First, fill entire area with background
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < thickness; z++) {
                    BlockPos pos = startPos.offset(x, y, z);
                    blocks.add(new BlockPlacement(pos, backgroundBlock));
                }
            }
        }
        
        // Then, render scaled text on top of background
        int currentCharX = textStartX;
        for (int charIndex = 0; charIndex < charsToRender; charIndex++) {
            char ch = text.charAt(charIndex);
            int[] charData = FONT_5x7.get(ch);
            
            if (charData == null) {
                // Unknown character, skip
                currentCharX += scaledCharWidth + scaledCharSpacing;
                continue;
            }
            
            // Render character with scaling
            for (int pixelRow = 0; pixelRow < baseCharHeight; pixelRow++) {
                int rowData = charData[pixelRow];
                
                for (int pixelCol = 0; pixelCol < baseCharWidth; pixelCol++) {
                    // Check if pixel should be on
                    if ((rowData & (1 << (baseCharWidth - 1 - pixelCol))) != 0) {
                        // Calculate block positions for this scaled pixel
                        int blockStartX = currentCharX + pixelCol * scale;
                        int blockStartY = textStartY + (baseCharHeight - 1 - pixelRow) * scale;
                        
                        // Place scale x scale blocks for this pixel
                        for (int blockX = 0; blockX < scale; blockX++) {
                            for (int blockY = 0; blockY < scale; blockY++) {
                                int finalX = blockStartX + blockX;
                                int finalY = blockStartY + blockY;
                                
                                // Check bounds
                                if (finalX >= 0 && finalX < width && finalY >= 0 && finalY < height) {
                                    BlockPos pos = startPos.offset(finalX, finalY, 0);
                                    // Replace background block with text block
                                    blocks.removeIf(bp -> bp.pos.equals(pos));
                                    blocks.add(new BlockPlacement(pos, textBlock));
                                }
                            }
                        }
                    }
                }
            }
            
            currentCharX += scaledCharWidth + scaledCharSpacing;
        }
        
        return blocks;
    }
    
    /**
     * Block placement for text rendering
     */
    public static class BlockPlacement {
        public final BlockPos pos;
        public final Block block;
        
        public BlockPlacement(BlockPos pos, Block block) {
            this.pos = pos;
            this.block = block;
        }
    }
}

