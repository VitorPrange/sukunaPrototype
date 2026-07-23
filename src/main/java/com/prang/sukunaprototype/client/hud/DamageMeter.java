package com.prang.sukunaprototype.client.hud;

import com.prang.sukunaprototype.Config;
import com.prang.sukunaprototype.SukunaPrototype;
import com.prang.sukunaprototype.SukunaPrototypeClient;
import static com.prang.sukunaprototype.VFXConstants.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks and displays accumulated slash damage on the HUD.
 * Shows total damage dealt, stays visible for configurable duration,
 * timer resets when new damage is dealt.
 */
@EventBusSubscriber(modid = SukunaPrototype.MODID, value = Dist.CLIENT)
public class DamageMeter {
    private static final Logger LOGGER = LoggerFactory.getLogger("SukunaDamageMeter");
    
    // Accumulated damage in hearts (not HP)
    private static float totalDamage = 0.0f;
    
    // Time remaining in ticks before meter disappears
    private static int ticksRemaining = 0;
    
    // Whether the meter is currently active
    private static boolean active = false;
    
    /**
     * Call this when slash damage is dealt to update the meter.
     * Adds to accumulated damage and resets the display timer.
     * 
     * @param damageInHearts Damage amount in hearts (half-hearts = 1 HP)
     */
    public static void addDamage(float damageInHearts) {
        if (damageInHearts <= 0) return;
        
        totalDamage += damageInHearts;
        
        // Get display duration from gamerule (in seconds)
        int durationSeconds = getDurationSeconds();
        ticksRemaining = durationSeconds * 20; // Convert seconds to ticks
        active = true;
        
        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            LOGGER.info("[DamageMeter] Added {} hearts, total now: {} hearts, timer reset to {} ticks",
                damageInHearts, totalDamage, ticksRemaining);
        }
    }
    
    /**
     * Manually reset the damage meter (clears accumulated damage and hides display).
     */
    public static void reset() {
        totalDamage = 0.0f;
        ticksRemaining = 0;
        active = false;
        
        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            LOGGER.info("[DamageMeter] Reset");
        }
    }
    
    /**
     * Get the configured display duration in seconds from gamerule.
     * Falls back to 5 seconds if gamerule can't be read.
     */
    private static int getDurationSeconds() {
        try {
            Minecraft mc = Minecraft.getInstance();
            var server = mc.getSingleplayerServer();
            if (server != null) {
                return server.getGameRules().getInt(SukunaPrototype.DAMAGE_METER_DURATION);
            }
            if (mc.level != null) {
                return mc.level.getGameRules().getInt(SukunaPrototype.DAMAGE_METER_DURATION);
            }
        } catch (Exception e) {
            // Fall back to default
        }
        return 5; // Default 5 seconds
    }
    
    /**
     * Tick the damage meter - decrements timer and deactivates when expired.
     * Called from client tick event.
     */
    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (!active) return;
        
        ticksRemaining--;
        
        if (ticksRemaining <= 0) {
            active = false;
            totalDamage = 0.0f;
            
            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                LOGGER.info("[DamageMeter] Timer expired, meter hidden");
            }
        }
    }
    
    /**
     * Render the damage meter on the HUD.
     * Shows accumulated damage in hearts with fade-out effect.
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!active || totalDamage <= 0) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        
        // Format damage text: "DMG: 123.5 ♥"
        String damageText = String.format("DMG: %.1f ♥", totalDamage);
        
        // Calculate alpha for fade-out effect (last 1 second fades)
        int fadeStartTicks = DAMAGE_METER_FADE_START_TICKS;
        float alpha = 1.0f;
        if (ticksRemaining < fadeStartTicks) {
            alpha = (float) ticksRemaining / fadeStartTicks;
        }
        
        // Position: calculate from config anchor + offsets
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int textWidth = font.width(damageText);
        
        // Get anchor and offsets from config
        Config.DamageMeterAnchor anchor = Config.DAMAGE_METER_ANCHOR.get();
        int xOffset = Config.DAMAGE_METER_X_OFFSET.get();
        int yOffset = Config.DAMAGE_METER_Y_OFFSET.get();
        
        // Calculate base position from anchor
        int x, y;
        switch (anchor) {
            case TOP_LEFT:
                x = DAMAGE_METER_SCREEN_PADDING;
                y = DAMAGE_METER_SCREEN_PADDING;
                break;
            case TOP_RIGHT:
                x = screenWidth - textWidth - DAMAGE_METER_SCREEN_PADDING;
                y = DAMAGE_METER_SCREEN_PADDING;
                break;
            case BOTTOM_LEFT:
                x = DAMAGE_METER_SCREEN_PADDING;
                y = screenHeight - 30;
                break;
            case BOTTOM_RIGHT:
                x = screenWidth - textWidth - DAMAGE_METER_SCREEN_PADDING;
                y = screenHeight - 30;
                break;
            case TOP_CENTER:
                x = (screenWidth - textWidth) / 2;
                y = DAMAGE_METER_SCREEN_PADDING;
                break;
            case BOTTOM_CENTER:
                x = (screenWidth - textWidth) / 2;
                y = screenHeight - 30;
                break;
            default:
                x = screenWidth - textWidth - DAMAGE_METER_SCREEN_PADDING;
                y = DAMAGE_METER_SCREEN_PADDING;
        }
        
        // Apply offsets
        x += xOffset;
        y += yOffset;
        
        // Color: gradient based on damage amount
        // Low damage = yellow (0xFFFF00), high damage = red (0xFF0000)
        int color = getDamageColor(totalDamage);
        int colorWithAlpha = applyAlpha(color, alpha);
        
        // Draw text with shadow for better visibility
        graphics.drawString(font, damageText, x, y, colorWithAlpha, true);
        
        // Draw timer bar below text
        drawTimerBar(graphics, x, y + DAMAGE_METER_TIMER_BAR_OFFSET, textWidth, ticksRemaining, getDurationSeconds() * 20, alpha);
    }
    
    /**
     * Get color for damage display based on amount.
     * Interpolates from yellow (low) to orange to red (high).
     */
    private static int getDamageColor(float damage) {
        // 0-50 hearts: yellow to orange
        // 50-100 hearts: orange to red
        // 100+ hearts: pure red
        
        if (damage >= DAMAGE_COLOR_THRESHOLD_HIGH) {
            return COLOR_RED;
        } else if (damage >= DAMAGE_COLOR_THRESHOLD_LOW) {
            // Orange to red
            float t = (damage - DAMAGE_COLOR_THRESHOLD_LOW) / DAMAGE_COLOR_THRESHOLD_LOW;
            return interpolateColor(COLOR_ORANGE, COLOR_RED, t);
        } else {
            // Yellow to orange
            float t = damage / DAMAGE_COLOR_THRESHOLD_LOW;
            return interpolateColor(COLOR_YELLOW, COLOR_ORANGE, t);
        }
    }
    
    /**
     * Interpolate between two RGB colors.
     */
    private static int interpolateColor(int color1, int color2, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t)); // Clamp to [0, 1]
        
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Apply alpha transparency to a color.
     */
    private static int applyAlpha(int rgb, float alpha) {
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        int a = (int) (alpha * 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
    
    /**
     * Draw a horizontal timer bar showing time remaining.
     */
    private static void drawTimerBar(GuiGraphics graphics, int x, int y, int width, int currentTicks, int maxTicks, float alpha) {
        float progress = (float) currentTicks / maxTicks;
        int barWidth = (int) (width * progress);
        
        // Background (dark gray with alpha)
        int bgColor = applyAlpha(COLOR_DAMAGE_METER_BG, alpha * DAMAGE_METER_BG_ALPHA);
        graphics.fill(x, y, x + width, y + DAMAGE_METER_TIMER_BAR_HEIGHT, bgColor);
        
        // Foreground (white with alpha)
        int fgColor = applyAlpha(COLOR_DAMAGE_METER_FG, alpha);
        graphics.fill(x, y, x + barWidth, y + DAMAGE_METER_TIMER_BAR_HEIGHT, fgColor);
    }
}
