package com.prang.sukunaprototype;

import com.prang.sukunaprototype.client.vfx.SlashEffect;
import com.prang.sukunaprototype.client.vfx.VFXManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@EventBusSubscriber(modid = SukunaPrototype.MODID, value = Dist.CLIENT)
public class SukunaPrototypeClient {
    public static KeyMapping SLASH_KEY;
    public static KeyMapping SLASH_VOID_KEY;
    public static KeyMapping SLASH_BLOOD_KEY;
    public static KeyMapping SLASH_HOLY_KEY;
    public static KeyMapping SLASH_TOXIC_KEY;
    public static KeyMapping SLASH_ELECTRIC_KEY;
    public static KeyMapping SLASH_RANDOM_KEY;
    public static KeyMapping SLASH_DEBUG_KEY;
    private static final Logger LOGGER = LoggerFactory.getLogger("SukunaPrototypeSlash");
    private static final Random RANDOM = new Random();
    
    // G key scanCode = 20 (layout-independent)
    private static final int SLASH_SCANCODE = 20;
    // Z key scanCode = 44 (layout-independent) - DEBUG MODE
    private static final int SLASH_DEBUG_SCANCODE = 44;
    // X key scanCode = 45 - RANDOM ALL COLORS (spam for variety)
    private static final int SLASH_RANDOM_SCANCODE = 45;
    // T key scanCode = 17 - VOID (moved off X so X = random-all)
    private static final int SLASH_VOID_SCANCODE = 17;
    // C key scanCode = 46 - BLOOD
    private static final int SLASH_BLOOD_SCANCODE = 46;
    // V key scanCode = 47 - HOLY
    private static final int SLASH_HOLY_SCANCODE = 47;
    // B key scanCode = 48 - TOXIC
    private static final int SLASH_TOXIC_SCANCODE = 48;
    // N key scanCode = 49 - ELECTRIC
    private static final int SLASH_ELECTRIC_SCANCODE = 49;
    
    // Soft-lock targeting: how far (in blocks) we search for a target along
    // the player's look direction, and how far off-center (in blocks) an
    // entity can be from that aim line and still count as "targeted".
    // Tune these to taste - bigger tolerance = easier to hit, bigger range =
    // works from farther away.
    private static final double SOFT_LOCK_RANGE = 50.0;
    private static final double SOFT_LOCK_TOLERANCE = 2.5;

    public SukunaPrototypeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        LOGGER.info("[SlashVFX] SukunaPrototypeClient constructor called");
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        SukunaPrototype.LOGGER.info("[SlashVFX] HELLO FROM CLIENT SETUP");
        SukunaPrototype.LOGGER.info("[SlashVFX] MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        
        event.enqueueWork(() -> {
            LOGGER.info("[SlashVFX] Initializing VFXManager...");
            VFXManager.init();
            LOGGER.info("[SlashVFX] VFXManager initialized successfully");
        });
    }

    @SubscribeEvent
    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        LOGGER.info("[SlashVFX] Registering key mappings...");
        
        // CLASSIC (G) - Purple main, dark red outline
        SLASH_KEY = new KeyMapping(
            "key.sukunaprototype.slash.classic",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            "key.categories.sukunaprototype"
        );
        event.register(SLASH_KEY);
        
        // VOID (T) - Blue violet, dark purple
        SLASH_VOID_KEY = new KeyMapping(
            "key.sukunaprototype.slash.void",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_T,
            "key.categories.sukunaprototype"
        );
        event.register(SLASH_VOID_KEY);
        
        // BLOOD (C) - Red, dark red
        SLASH_BLOOD_KEY = new KeyMapping(
            "key.sukunaprototype.slash.blood",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_C,
            "key.categories.sukunaprototype"
        );
        event.register(SLASH_BLOOD_KEY);
        
        // HOLY (V) - Gold, orange
        SLASH_HOLY_KEY = new KeyMapping(
            "key.sukunaprototype.slash.holy",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_V,
            "key.categories.sukunaprototype"
        );
        event.register(SLASH_HOLY_KEY);
        
        // TOXIC (B) - Neon green, dark green
        SLASH_TOXIC_KEY = new KeyMapping(
            "key.sukunaprototype.slash.toxic",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_B,
            "key.categories.sukunaprototype"
        );
        event.register(SLASH_TOXIC_KEY);
        
        // ELECTRIC (N) - Cyan, dark blue
        SLASH_ELECTRIC_KEY = new KeyMapping(
            "key.sukunaprototype.slash.electric",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_N,
            "key.categories.sukunaprototype"
        );
        event.register(SLASH_ELECTRIC_KEY);
        
        // RANDOM ALL (X) - Spawns a random ColorScheme from the full set.
        SLASH_RANDOM_KEY = new KeyMapping(
            "key.sukunaprototype.slash.random",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_X,
            "key.categories.sukunaprototype"
        );
        event.register(SLASH_RANDOM_KEY);

        // DEBUG (Z) - Persistent debug marker
        SLASH_DEBUG_KEY = new KeyMapping(
            "key.sukunaprototype.slash.debug",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_Z,
            "key.categories.sukunaprototype"
        );
        event.register(SLASH_DEBUG_KEY);
        
        LOGGER.info("[SlashVFX] Key bindings registered: G=Classic, X=RandomAll, T=Void, C=Blood, V=Holy, B=Toxic, N=Electric, Z=Debug");
    }
    
    @SubscribeEvent
    static void onKeyInput(InputEvent.Key event) {
        // Log ALL key events for debugging
        LOGGER.debug("[SlashVFX] Key event: key={}, scanCode={}, action={}, modifiers={}", 
            event.getKey(), event.getScanCode(), event.getAction(), event.getModifiers());
        
        // Only handle key press (action 1 = GLFW_PRESS)
        if (event.getAction() != 1) {
            if (isOurKey(event.getScanCode())) {
                LOGGER.debug("[SlashVFX] Our key but not press action: {}", event.getAction());
            }
            return;
        }
        
        int scanCode = event.getScanCode();
        
        if (scanCode == SLASH_SCANCODE) {
            LOGGER.info("[SlashVFX] ===== CLASSIC SLASH (G) =====");
            spawnSlashAtTarget(SlashEffect.CLASSIC, "CLASSIC");
        } else if (scanCode == SLASH_VOID_SCANCODE) {
            LOGGER.info("[SlashVFX] ===== VOID SLASH (T) =====");
            spawnSlashAtTarget(SlashEffect.BLACK_RED, "VOID");
        } else if (scanCode == SLASH_BLOOD_SCANCODE) {
            LOGGER.info("[SlashVFX] ===== BLOOD SLASH (C) =====");
            spawnSlashAtTarget(SlashEffect.BLACK_WHITE, "BLOOD");
        } else if (scanCode == SLASH_HOLY_SCANCODE) {
            LOGGER.info("[SlashVFX] ===== HOLY SLASH (V) =====");
            spawnSlashAtTarget(SlashEffect.RED_BLACK, "HOLY");
        } else if (scanCode == SLASH_TOXIC_SCANCODE) {
            LOGGER.info("[SlashVFX] ===== TOXIC SLASH (B) =====");
            spawnSlashAtTarget(SlashEffect.RED_WHITE, "TOXIC");
        } else if (scanCode == SLASH_ELECTRIC_SCANCODE) {
            LOGGER.info("[SlashVFX] ===== ELECTRIC SLASH (N) =====");
            spawnSlashAtTarget(SlashEffect.WHITE_BLACK, "ELECTRIC");
        } else if (scanCode == SLASH_RANDOM_SCANCODE) {
            LOGGER.info("[SlashVFX] ===== RANDOM-SCHEME SLASH (X) =====");
            spawnRandomSlashAtTarget();
        } else if (scanCode == SLASH_DEBUG_SCANCODE) {
            LOGGER.info("[SlashVFX] ===== DEBUG MARKER (Z) =====");
            spawnDebugMarker();
        } else {
            LOGGER.debug("[SlashVFX] Key pressed but not our key: {}", scanCode);
            return;
        }
    }
    
    private static boolean isOurKey(int scanCode) {
        return scanCode == SLASH_SCANCODE || scanCode == SLASH_VOID_SCANCODE || 
               scanCode == SLASH_BLOOD_SCANCODE || scanCode == SLASH_HOLY_SCANCODE ||
               scanCode == SLASH_TOXIC_SCANCODE || scanCode == SLASH_ELECTRIC_SCANCODE ||
               scanCode == SLASH_RANDOM_SCANCODE || scanCode == SLASH_DEBUG_SCANCODE;
    }
    
    private static void spawnRandomSlashAtTarget() {
        SlashEffect.ColorScheme scheme = SlashEffect.ALL_SCHEMES[
                RANDOM.nextInt(SlashEffect.ALL_SCHEMES.length)];
        spawnSlashAtTarget(scheme, "RANDOM(" + schemeName(scheme) + ")");
    }
    
    private static String schemeName(SlashEffect.ColorScheme scheme) {
        if (scheme == SlashEffect.BLACK_WHITE) return "BLACK_WHITE";
        if (scheme == SlashEffect.BLACK_RED) return "BLACK_RED";
        if (scheme == SlashEffect.RED_WHITE) return "RED_WHITE";
        if (scheme == SlashEffect.RED_BLACK) return "RED_BLACK";
        if (scheme == SlashEffect.WHITE_BLACK) return "WHITE_BLACK";
        if (scheme == SlashEffect.WHITE_RED) return "WHITE_RED";
        return "?";
    }
    
    private static void spawnSlashAtTarget(SlashEffect.ColorScheme scheme, String schemeName) {
        Minecraft mc = Minecraft.getInstance();
        LOGGER.info("[SlashVFX] Player: {}", mc.player);
        LOGGER.info("[SlashVFX] Level: {}", mc.level);
        LOGGER.info("[SlashVFX] Game mode: {}", mc.gameMode);
        LOGGER.info("[SlashVFX] Screen: {}", mc.screen);
        
        if (mc.player == null) {
            LOGGER.warn("[SlashVFX] No player - aborting");
            return;
        }
        if (mc.level == null) {
            LOGGER.warn("[SlashVFX] No level - aborting");
            return;
        }
        
        // Soft-lock targeting instead of the vanilla exact-crosshair hitResult:
        // finds the closest entity to the player's aim LINE (not just what's
        // directly under the crosshair), within SOFT_LOCK_RANGE blocks and
        // SOFT_LOCK_TOLERANCE blocks of "wiggle room" off-center. This lets you
        // hit from far away and without needing pixel-perfect aim.
        Entity target = findSoftTarget(mc, SOFT_LOCK_RANGE, SOFT_LOCK_TOLERANCE);
        
        if (target == null) {
            LOGGER.warn("[SlashVFX] No target found within {} blocks (tolerance {}) - aborting", 
                SOFT_LOCK_RANGE, SOFT_LOCK_TOLERANCE);
            return;
        }
        
        LOGGER.info("[SlashVFX] SOFT-LOCK TARGET! Target: {} (UUID: {}, pos: {})", 
            target.getName().getString(), target.getUUID(), target.position());
        
        LOGGER.info("[SlashVFX] Spawning {} slash effect...", schemeName);
        
        SlashEffect.builder()
            .at(target)
            .colors(scheme)
            .spawn();
        
        LOGGER.info("[SlashVFX] {} slash spawned! Active effects: {}", 
            schemeName, VFXManager.getActiveEffectCount());
    }
    
    /**
     * Finds the best entity to target along the player's look direction, with
     * tolerance so aim doesn't need to be pixel-perfect and works from far away.
     *
     * Instead of a hard raycast (which only hits whatever's exactly under the
     * crosshair, out to a short vanilla reach distance), this scans every
     * nearby entity and measures its perpendicular distance from the aim
     * line. Anything within `tolerance` blocks of that line (plus a little
     * extra allowance for the entity's own size) counts as a valid target;
     * the closest one to the line wins.
     *
     * @param maxRange  how far along the aim line to search, in blocks
     * @param tolerance how far off-center (in blocks) an entity can be and
     *                  still count as targeted
     */
    private static Entity findSoftTarget(Minecraft mc, double maxRange, double tolerance) {
        if (mc.player == null || mc.level == null) return null;
        
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);
        
        AABB searchBox = mc.player.getBoundingBox().inflate(maxRange);
        List<Entity> candidates = mc.level.getEntities(mc.player, searchBox, e -> e != mc.player && e.isAlive());
        
        Entity best = null;
        double bestPerpDist = Double.MAX_VALUE;
        
        for (Entity candidate : candidates) {
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 toEntity = center.subtract(eyePos);
            
            // How far along the aim line the entity's center projects to.
            double t = toEntity.dot(look);
            if (t <= 0 || t > maxRange) {
                continue; // behind the player, or farther than our search range
            }
            
            Vec3 closestPointOnRay = eyePos.add(look.scale(t));
            double perpDist = center.distanceTo(closestPointOnRay);
            
            // Bigger entities get a bit more allowance so large mobs (like a
            // Warden) aren't harder to hit near their edges than small ones.
            double entityRadius = Math.max(candidate.getBbWidth(), candidate.getBbHeight()) / 2.0;
            double allowedDist = tolerance + entityRadius;
            
            if (perpDist <= allowedDist && perpDist < bestPerpDist) {
                bestPerpDist = perpDist;
                best = candidate;
            }
        }
        
        return best;
    }
    
    private static void spawnDebugMarker() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.hitResult == null) return;
        
        if (mc.hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) mc.hitResult;
            Entity target = entityHit.getEntity();
            
            LOGGER.info("[SlashVFX] ===== DEBUG MARKER AT ENTITY =====");
            LOGGER.info("[SlashVFX] Target: {} (pos: {})", target.getName().getString(), target.position());
            
            // Spawn a persistent debug slash
            SlashEffect.builder()
                .at(target)
                .colors(SlashEffect.CLASSIC)
                .debugMode(true)
                .spawn();
            
            LOGGER.info("[SlashVFX] Debug marker spawned! Active effects: {}", VFXManager.getActiveEffectCount());
        }
    }
}