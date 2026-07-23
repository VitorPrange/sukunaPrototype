package com.prang.sukunaprototype;

/**
 * Centralized constants for VFX system tuning and configuration.
 * All magic numbers extracted here for easier maintenance and tuning.
 */
public final class VFXConstants {
    
    // Prevent instantiation
    private VFXConstants() {}
    
    // ==================== Soft-Lock Targeting ====================
    
    /**
     * Maximum distance (in blocks) to search for targets along the player's look direction.
     * Entities beyond this range cannot be targeted.
     */
    public static final double SOFT_LOCK_RANGE = 50.0;
    
    /**
     * Tolerance distance (in blocks) from the aim line for soft-lock targeting.
     * Entities within this perpendicular distance from the look vector can be targeted.
     */
    public static final double SOFT_LOCK_TOLERANCE = 2.5;
    
    // ==================== Key Hold Mechanics ====================
    
    /**
     * Starting interval between slashes when holding the key (in ticks @ 20 TPS).
     * 10 ticks = 2 slashes per second initially.
     */
    public static final int HOLD_SLOW = 10;
    
    /**
     * Number of ticks over which the hold ramp accelerates to the gamerule cap.
     * 20 ticks = 1 second to reach maximum slash rate.
     */
    public static final int HOLD_RAMP = 20;
    
    // ==================== VFX Manager ====================
    
    /**
     * Maximum number of active VFX instances allowed simultaneously.
     * Oldest effects are removed when this limit is reached.
     */
    public static final int MAX_EFFECTS = 1000;
    
    // ==================== Slash Effect Rendering ====================
    
    /**
     * Number of segments used to tessellate the slash geometry.
     * Higher values create smoother curves but increase vertex count.
     */
    public static final int SLASH_SEGMENTS = 16;
    
    /**
     * Reference distance (in blocks) where slash scale equals 1.0.
     * Slashes scale relative to this distance to maintain apparent screen size.
     */
    public static final float SIZE_REF_DIST = 12.0f;
    
    /**
     * Minimum scale factor for slashes when very close to camera.
     * Prevents slashes from becoming too large up close.
     */
    public static final float SIZE_MIN_SCALE = 0.85f;
    
    /**
     * Maximum scale factor for slashes when far from camera.
     * Prevents slashes from becoming too small at distance.
     */
    public static final float SIZE_MAX_SCALE = 1.15f;
    
    /**
     * Slight 3D tilt angle of the blade out of screen plane (in radians).
     * Creates partial occlusion effect when slash intersects geometry.
     * ~10.3 degrees.
     */
    public static final float BLADE_TILT = 0.18f;
    
    /**
     * Default slash lifetime in ticks (15 ticks = 0.75 seconds @ 20 TPS).
     */
    public static final int SLASH_DEFAULT_LIFETIME = 15;
    
    // ==================== Damage Meter ====================
    
    /**
     * Number of ticks before damage meter begins fading (1 second @ 20 TPS).
     */
    public static final int DAMAGE_METER_FADE_START_TICKS = 20;
    
    /**
     * Padding in pixels from screen edges for damage meter positioning.
     */
    public static final int DAMAGE_METER_SCREEN_PADDING = 10;
    
    /**
     * Vertical offset in pixels for damage meter timer bar below text.
     */
    public static final int DAMAGE_METER_TIMER_BAR_OFFSET = 12;
    
    /**
     * Height in pixels of the damage meter timer bar.
     */
    public static final int DAMAGE_METER_TIMER_BAR_HEIGHT = 2;
    
    /**
     * Damage threshold (in hearts) for yellow-to-orange color transition.
     */
    public static final float DAMAGE_COLOR_THRESHOLD_LOW = 50.0f;
    
    /**
     * Damage threshold (in hearts) for orange-to-red color transition.
     */
    public static final float DAMAGE_COLOR_THRESHOLD_HIGH = 100.0f;
    
    // ==================== Colors ====================
    
    /**
     * Color value for yellow damage display.
     */
    public static final int COLOR_YELLOW = 0xFFFF00;
    
    /**
     * Color value for orange damage display.
     */
    public static final int COLOR_ORANGE = 0xFFA500;
    
    /**
     * Color value for red damage display.
     */
    public static final int COLOR_RED = 0xFF0000;
    
    /**
     * Color value for damage meter background.
     */
    public static final int COLOR_DAMAGE_METER_BG = 0x404040;
    
    /**
     * Color value for damage meter foreground.
     */
    public static final int COLOR_DAMAGE_METER_FG = 0xFFFFFF;
    
    /**
     * Background alpha multiplier for damage meter.
     */
    public static final float DAMAGE_METER_BG_ALPHA = 0.5f;
}
