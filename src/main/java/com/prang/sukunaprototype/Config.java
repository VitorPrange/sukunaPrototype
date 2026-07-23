package com.prang.sukunaprototype;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Sukuna Prototype mod.
 * 
 * <p><b>Important:</b> GameRules take precedence over config values at runtime.
 * Config values serve as defaults, but live gameplay uses gamerule values which
 * can be changed with /gamerule or /sukunaprototype commands without restarting.
 * See {@link SukunaPrototype} for gamerule definitions.</p>
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- Slash VFX tuning ---
    public static final ModConfigSpec.DoubleValue SLASH_LENGTH = BUILDER
            .comment("Slash length in blocks (world size). Bigger = longer slash.")
            .defineInRange("slashLength", 12.0, 1.0, 40.0);

    public static final ModConfigSpec.DoubleValue SLASH_THICKNESS = BUILDER
            .comment("Slash thickness in blocks (world size). Smaller = thinner slash.")
            .defineInRange("slashThickness", 0.16, 0.02, 3.0);

    // Slash outline rim thickness (blocks). 0 = none. Drawn additive + depth-tested
    // so it glows in open air but vanishes where the slash enters a block/mob.
    public static final ModConfigSpec.DoubleValue SLASH_OUTLINE = BUILDER
            .comment("Slash outline rim thickness in blocks (0 = none). Vibrant additive edge.")
            .defineInRange("slashOutline", 0.12, 0.0, 1.0);

    // How bright/punchy the outline glow is. Additive, so >1 ramps the rim
    // up to a hard glowing edge. 1.0 = strong, 2.0+ = blown-out neon.
    public static final ModConfigSpec.DoubleValue SLASH_OUTLINE_VIBRANCY = BUILDER
            .comment("Outline glow intensity (additive). Higher = more vibrant neon rim.")
            .defineInRange("slashOutlineVibrancy", 1.8, 0.2, 3.0);

    // Per-spawn length randomness: final length = configLength * (1 ± jitter).
    // 0.0 = always exactly config length; 0.5 = anywhere from half to 1.5x.
    public static final ModConfigSpec.DoubleValue SLASH_LENGTH_JITTER = BUILDER
            .comment("Random length variation per slash (0 = none, 0.5 = half to 1.5x). Thickness is unaffected.")
            .defineInRange("slashLengthJitter", 0.35, 0.0, 1.0);

    // Slash damage in hearts (half-hearts = 1 HP). Applied once per slash at spawn via AABB sweep.
    // 0.0 = no damage. 10.0 = 5 hearts (10 HP). 20.0 = 10 hearts (20 HP).
    public static final ModConfigSpec.DoubleValue SLASH_DAMAGE = BUILDER
            .comment("Slash damage in hearts (half-hearts = 1 HP). Applied once per slash at spawn via AABB sweep. 0 = no damage.")
            .defineInRange("slashDamage", 6.0, 0.0, 100.0);

    // Enable debug logging for VFX system. When true, verbose debug/info logs are printed.
    // Default false to avoid log spam in production.
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING = BUILDER
            .comment("Enable verbose debug logging for VFX system (default false to avoid log spam).")
            .define("enableDebugLogging", false);

    // --- Damage Meter HUD Configuration ---
    
    public static final ModConfigSpec.EnumValue<DamageMeterAnchor> DAMAGE_METER_ANCHOR = BUILDER
            .comment("Anchor position for damage meter HUD (TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP_CENTER, BOTTOM_CENTER)")
            .defineEnum("damageMeterAnchor", DamageMeterAnchor.TOP_RIGHT);
    
    public static final ModConfigSpec.IntValue DAMAGE_METER_X_OFFSET = BUILDER
            .comment("Horizontal offset in pixels from anchor position (can be negative)")
            .defineInRange("damageMeterXOffset", 0, -1000, 1000);
    
    public static final ModConfigSpec.IntValue DAMAGE_METER_Y_OFFSET = BUILDER
            .comment("Vertical offset in pixels from anchor position (can be negative)")
            .defineInRange("damageMeterYOffset", 0, -1000, 1000);

    // --- Cleave Auto-Adjust Configuration ---
    
    public static final ModConfigSpec.DoubleValue CLEAVE_BASE_DAMAGE = BUILDER
            .comment("Base damage for Cleave ability in hearts (default 4.0)")
            .defineInRange("cleaveBaseDamage", 4.0, 0.0, 100.0);
    
    public static final ModConfigSpec.DoubleValue CLEAVE_HEALTH_SCALING = BUILDER
            .comment("Damage scaling per target max health (default 0.1 = 10% of max HP)")
            .defineInRange("cleaveHealthScaling", 0.1, 0.0, 1.0);
    
    public static final ModConfigSpec.DoubleValue CLEAVE_ARMOR_SCALING = BUILDER
            .comment("Damage scaling per target armor point (default 0.05)")
            .defineInRange("cleaveArmorScaling", 0.05, 0.0, 1.0);

    static final ModConfigSpec SPEC = BUILDER.build();
    
    /**
     * Anchor positions for damage meter HUD.
     */
    public enum DamageMeterAnchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        TOP_CENTER,
        BOTTOM_CENTER
    }
}

