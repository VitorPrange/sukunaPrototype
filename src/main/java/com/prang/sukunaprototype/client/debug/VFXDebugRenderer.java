package com.prang.sukunaprototype.client.debug;

import com.prang.sukunaprototype.SukunaPrototype;
import com.prang.sukunaprototype.client.vfx.VFXManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Debug overlay for VFX performance metrics.
 * Toggle with F3+V to show active effect count, render timing, and culling stats.
 */
@EventBusSubscriber(modid = SukunaPrototype.MODID, value = Dist.CLIENT)
public class VFXDebugRenderer {
    
    private static boolean enabled = false;
    private static long lastRenderTimeNanos = 0;
    private static int lastRenderedCount = 0;
    private static int lastCulledCount = 0;
    
    /**
     * Toggle debug overlay on/off.
     * Called when F3+V is pressed.
     */
    public static void toggle() {
        enabled = !enabled;
        SukunaPrototype.LOGGER.info("[VFXDebug] Performance overlay " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if debug overlay is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Update render timing statistics.
     * Called by VFXManager after rendering each frame.
     * 
     * @param renderTimeNanos Time taken to render VFX in nanoseconds
     * @param renderedCount Number of effects rendered
     * @param culledCount Number of effects culled (not rendered)
     */
    public static void updateStats(long renderTimeNanos, int renderedCount, int culledCount) {
        lastRenderTimeNanos = renderTimeNanos;
        lastRenderedCount = renderedCount;
        lastCulledCount = culledCount;
    }
    
    /**
     * Render debug overlay on screen.
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!enabled) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        // Position in top-left corner, below F3 debug info
        int x = 5;
        int y = screenHeight / 2 + 20; // Below typical F3 debug overlay
        int lineHeight = font.lineHeight + 2;
        int bgPadding = 2;
        
        // Collect metrics
        int activeEffects = VFXManager.getActiveEffectCount();
        double renderTimeMs = lastRenderTimeNanos / 1_000_000.0;
        int totalProcessed = lastRenderedCount + lastCulledCount;
        double cullPercent = totalProcessed > 0 ? (lastCulledCount * 100.0 / totalProcessed) : 0.0;
        
        // Calculate FPS impact (rough estimate based on 60 FPS = 16.67ms per frame)
        double fpsImpact = (renderTimeMs / 16.67) * 100.0;
        
        // Build display lines
        String[] lines = {
            "=== VFX Debug (F3+V) ===",
            String.format("Active Effects: %d", activeEffects),
            String.format("Rendered: %d  Culled: %d (%.1f%%)", lastRenderedCount, lastCulledCount, cullPercent),
            String.format("Render Time: %.3f ms (%.1f%% of frame)", renderTimeMs, fpsImpact),
            String.format("FPS Impact: %s", getFpsImpactLabel(fpsImpact))
        };
        
        // Calculate background dimensions
        int maxWidth = 0;
        for (String line : lines) {
            int width = font.width(line);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        
        int bgX = x - bgPadding;
        int bgY = y - bgPadding;
        int bgWidth = maxWidth + bgPadding * 2;
        int bgHeight = lines.length * lineHeight + bgPadding * 2;
        
        // Draw semi-transparent background
        graphics.fill(bgX, bgY, bgX + bgWidth, bgY + bgHeight, 0x80000000);
        
        // Draw text lines
        for (int i = 0; i < lines.length; i++) {
            int lineY = y + i * lineHeight;
            int color = i == 0 ? 0xFFFFFF00 : getLineColor(i, fpsImpact); // Yellow header
            graphics.drawString(font, lines[i], x, lineY, color, false);
        }
    }
    
    /**
     * Get color for a debug line based on performance impact.
     */
    private static int getLineColor(int lineIndex, double fpsImpact) {
        // Line 4 is FPS impact - color code it
        if (lineIndex == 4) {
            if (fpsImpact < 5.0) {
                return 0xFF00FF00; // Green - minimal impact
            } else if (fpsImpact < 15.0) {
                return 0xFFFFFF00; // Yellow - noticeable impact
            } else {
                return 0xFFFF0000; // Red - significant impact
            }
        }
        return 0xFFFFFFFF; // White for other lines
    }
    
    /**
     * Get human-readable FPS impact label.
     */
    private static String getFpsImpactLabel(double fpsImpact) {
        if (fpsImpact < 5.0) {
            return "Minimal";
        } else if (fpsImpact < 10.0) {
            return "Low";
        } else if (fpsImpact < 15.0) {
            return "Moderate";
        } else if (fpsImpact < 25.0) {
            return "High";
        } else {
            return "Very High";
        }
    }
}
