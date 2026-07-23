package com.prang.sukunaprototype;

import com.prang.sukunaprototype.client.vfx.SlashEffect;
import com.prang.sukunaprototype.client.vfx.VFXManager;
import static com.prang.sukunaprototype.VFXConstants.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@EventBusSubscriber(modid = SukunaPrototype.MODID, value = Dist.CLIENT)
public class SukunaPrototypeClient {
    public static KeyMapping SLASH_KEY;
    public static KeyMapping CLEAVE_KEY;
    private static final Logger LOGGER = LoggerFactory.getLogger("SukunaPrototypeSlash");
    private static final Random RANDOM = new Random();

    // X key scanCode = 45 - tap: one random slash. hold: consecutive slashes that accelerate.
    // Hold-ramp tuning (in ticks @ 20tps): starts at HOLD_SLOW, eases (quadratic)
    // up to the gamerule cap (slashMaxRate) over HOLD_RAMP ticks. Short ramp so
    // the rate actually reaches the gamerule value instead of plateauing below it.
    // Constants imported from VFXConstants: HOLD_SLOW = 10, HOLD_RAMP = 20

    //Hold state
    private static boolean holding = false;
    private static int holdTicks = 0;
    private static int nextSlashTick = 0;
    private static boolean wasKeyDown = false;
    
    // Target visualization
    private static Entity currentTarget = null;

    // Soft-lock targeting: how far (in blocks) we search for a target along
    // the player's look direction, and how far off-center (in blocks) an
    // entity can be from that aim line and still count as "targeted".
    // Constants imported from VFXConstants: SOFT_LOCK_RANGE = 50.0, SOFT_LOCK_TOLERANCE = 2.5

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

        SLASH_KEY = new KeyMapping(
            "key.sukunaprototype.slash.random",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_X,
            "key.categories.sukunaprototype"
        );
        event.register(SLASH_KEY);

        CLEAVE_KEY = new KeyMapping(
            "key.sukunaprototype.cleave",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_C,
            "key.categories.sukunaprototype"
        );
        event.register(CLEAVE_KEY);

        LOGGER.info("[SlashVFX] Key bindings registered: X=Slash (tap/hold) | C=Cleave");
    }

    // Detect key release on focus loss / Esc / alt-tab by tracking previous state in Pre tick
    // Also clear hold state when GUI opens or window loses focus
    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        
        // Clear hold if GUI opened or window lost focus
        if (holding && (mc.screen != null || !mc.isWindowActive())) {
            holding = false;
            holdTicks = 0;
            nextSlashTick = 0;
        }
        
        boolean isDown = SLASH_KEY.isDown();
        if (wasKeyDown && !isDown) {
            keyReleased();
        }
        wasKeyDown = isDown;
    }

    private static void keyReleased() {
        if (holding) {
            holding = false;
            holdTicks = 0;
            nextSlashTick = 0;
        }
    }

    @SubscribeEvent
    static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != 1) return;          // only initial press fires
        
        // F3+V toggle for debug overlay
        if (event.getKey() == InputConstants.KEY_V) {
            Minecraft mc = Minecraft.getInstance();
            // Check if F3 is held
            if (InputConstants.isKeyDown(mc.getWindow().getWindow(), InputConstants.KEY_F3)) {
                com.prang.sukunaprototype.client.debug.VFXDebugRenderer.toggle();
                return;
            }
        }
        
        // Cleave Auto-Adjust handler (C key by default)
        if (CLEAVE_KEY.isDown()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            
            // Check if Cleave is enabled via gamerule
            if (!mc.level.getGameRules().getBoolean(SukunaPrototype.CLEAVE_ENABLED)) {
                return;
            }
            
            // Find target using existing soft-lock targeting
            Entity target = findSoftTarget(mc, SOFT_LOCK_RANGE, SOFT_LOCK_TOLERANCE);
            if (target == null || !(target instanceof net.minecraft.world.entity.LivingEntity)) {
                return;
            }
            
            net.minecraft.world.entity.LivingEntity livingTarget = (net.minecraft.world.entity.LivingEntity) target;
            
            // Calculate Cleave damage: baseDamage + (maxHealth * healthScaling) + (armor * armorScaling)
            float baseDamage = Config.CLEAVE_BASE_DAMAGE.get().floatValue();
            float healthScaling = Config.CLEAVE_HEALTH_SCALING.get().floatValue();
            float armorScaling = Config.CLEAVE_ARMOR_SCALING.get().floatValue();
            
            float targetMaxHealth = livingTarget.getMaxHealth();
            float targetArmor = livingTarget.getArmorValue();
            
            float cleaveDamage = baseDamage + (targetMaxHealth * healthScaling) + (targetArmor * armorScaling);
            
            LOGGER.info("[Cleave] Target: {} | MaxHP: {} | Armor: {} | Calculated Damage: {} hearts",
                livingTarget.getName().getString(), targetMaxHealth, targetArmor, cleaveDamage);
            
            // Spawn Cleave slash with calculated damage using builder pattern
            SlashEffect.builder()
                .at(livingTarget)
                .colors(SlashEffect.BLACK_RED)  // Red/black color for Cleave distinction
                .damage(cleaveDamage)
                .spawn();
            
            LOGGER.info("[Cleave] Slash spawned with {} hearts damage", cleaveDamage);
            
            return;  // Prevent SLASH_KEY from also firing
        }
        
        if (!SLASH_KEY.isDown()) return;              // respects the key bound in Controls

        if (!holding) {
            spawnRandomSlashAtTarget();               // immediate first slash (no tick latency)
            holdTicks = 0;
            nextSlashTick = slashInterval(0);
            holding = true;
        }
    }

    // Drives the consecutive-slash ramp while the bound key is held.
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        
        // Update target visualization while key is held
        if (SLASH_KEY.isDown() && mc.player != null && mc.level != null) {
            currentTarget = findSoftTarget(mc, SOFT_LOCK_RANGE, SOFT_LOCK_TOLERANCE);
        } else {
            currentTarget = null;
        }
        
        if (!SLASH_KEY.isDown()) {                    // key released (or unbound): stop
            if (holding) {
                holding = false;
                holdTicks = 0;
                nextSlashTick = 0;
            }
            return;
        }
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) return;

        holdTicks++;
        if (holdTicks >= nextSlashTick) {
            spawnRandomSlashAtTarget();
            nextSlashTick = holdTicks + slashInterval(holdTicks);
        }
    }

    // Interval in ticks between consecutive hold-slashes. Starts at HOLD_SLOW,
    // eases (quadratic) down to the gamerule cap (slashMaxRate -> ticks) as the hold lengthens.
    private static int slashInterval(int held) {
        int capTicks = gameruleCapTicks();           // fastest allowed interval (smaller = faster)
        float p = Math.min(1f, (float) held / HOLD_RAMP);
        float eased = p * p;
        return Math.max(capTicks, Math.round(HOLD_SLOW - (HOLD_SLOW - capTicks) * eased));
    }

    // gamerule slashMaxRate (slashes/sec) -> interval in ticks, clamped to a sane floor.
    // In single-player the authoritative value lives on the integrated SERVER
    // (your /gamerule command updates it there); the client's copy is only set at
    // login and never synced, so reading mc.level would always return the default.
    // Prefer the SP server's rules; fall back to the client copy in multiplayer.
    private static int gameruleCapTicks() {
        Minecraft mc = Minecraft.getInstance();
        int rate = 7;
        try {
            MinecraftServer srv = mc.getSingleplayerServer();
            if (srv != null) {
                rate = srv.getGameRules().getInt(SukunaPrototype.SLASH_MAX_RATE);
            } else if (mc.level != null) {
                rate = mc.level.getGameRules().getInt(SukunaPrototype.SLASH_MAX_RATE);
            }
        } catch (Exception ignored) { /* fall back to default 7 */ }
        rate = Math.max(1, Math.min(60, rate));
        // ticks per slash = 20 / rate, min 1 tick (20/s ceiling)
        return Math.max(1, Math.round(20f / rate));
    }

    // Reads an IntegerValue gamerule holding MILLIBLOCKS (×1000) and returns
    // blocks (value / 1000.0). Prefers the SP server (authoritative, synced by
    // your /gamerule edits), falls back to the client copy in multiplayer.
    // Used for live slash thickness / outline so changes apply instantly, no reload.
    public static double gameruleMilli(GameRules.Key<GameRules.IntegerValue> key, int fallbackMilli) {
        Minecraft mc = Minecraft.getInstance();
        try {
            MinecraftServer srv = mc.getSingleplayerServer();
            if (srv != null) return srv.getGameRules().getInt(key) / 1000.0;
            if (mc.level != null) return mc.level.getGameRules().getInt(key) / 1000.0;
        } catch (Exception ignored) { /* fall back */ }
        return fallbackMilli / 1000.0;
    }

    // Reads an IntegerValue gamerule holding MILLIHEARTS (×1000) and returns
    // hearts (value / 1000.0). Prefers the SP server (authoritative, synced by
    // your /gamerule edits), falls back to the client copy in multiplayer.
    // Used for live slash damage so changes apply instantly, no reload.
    public static float gameruleMilliHearts(GameRules.Key<GameRules.IntegerValue> key, int fallbackMilli) {
        Minecraft mc = Minecraft.getInstance();
        try {
            MinecraftServer srv = mc.getSingleplayerServer();
            if (srv != null) return srv.getGameRules().getInt(key) / 1000.0f;
            if (mc.level != null) return mc.level.getGameRules().getInt(key) / 1000.0f;
        } catch (Exception ignored) { /* fall back */ }
        return fallbackMilli / 1000.0f;
    }

    private static void spawnRandomSlashAtTarget() {
        SlashEffect.ColorScheme scheme = SlashEffect.ALL_SCHEMES[
                RANDOM.nextInt(SlashEffect.ALL_SCHEMES.length)];
        spawnSlashAtTarget(scheme, "RANDOM(" + schemeName(scheme) + ")");
    }

    private static final java.util.Map<SlashEffect.ColorScheme, String> SCHEME_NAMES = 
            java.util.Map.of(
                SlashEffect.BLACK_WHITE, "BLACK_WHITE",
                SlashEffect.BLACK_RED, "BLACK_RED",
                SlashEffect.RED_WHITE, "RED_WHITE",
                SlashEffect.RED_BLACK, "RED_BLACK",
                SlashEffect.WHITE_BLACK, "WHITE_BLACK",
                SlashEffect.WHITE_RED, "WHITE_RED"
            );

    private static String schemeName(SlashEffect.ColorScheme scheme) {
        return SCHEME_NAMES.getOrDefault(scheme, "?");
    }

    // Reads the live slash damage from gamerule (millihearts/1000) or config fallback.
    // Returns damage in hearts (half-hearts = HP). Enforces a minimum of 6.0 hearts for testing.
    private static float currentSlashDamage() {
        float dmg = gameruleMilliHearts(SukunaPrototype.SLASH_DAMAGE, (int)(Config.SLASH_DAMAGE.get() * 1000));
        if (dmg < 6.0f) {
            LOGGER.warn("[SlashVFX] currentSlashDamage read {} hearts (below 6.0), clamping to 6.0", dmg);
            dmg = 6.0f;
        }
        return dmg;
    }

    private static void spawnSlashAtTarget(SlashEffect.ColorScheme scheme, String schemeName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            LOGGER.warn("[SlashVFX] No player/level - aborting");
            return;
        }

        Entity target = findSoftTarget(mc, SOFT_LOCK_RANGE, SOFT_LOCK_TOLERANCE);
        if (target == null) {
            LOGGER.warn("[SlashVFX] No target found within {} blocks (tolerance {}) - aborting",
                SOFT_LOCK_RANGE, SOFT_LOCK_TOLERANCE);
            return;
        }

        LOGGER.info("[SlashVFX] SOFT-LOCK TARGET! {} (pos: {})", target.getName().getString(), target.position());
        LOGGER.info("[SlashVFX] Spawning {} slash effect...", schemeName);

        SlashEffect.builder()
            .at(target)
            .colors(scheme)
            .damage(currentSlashDamage())
            .spawn();

        LOGGER.info("[SlashVFX] {} slash spawned! Active effects: {}", schemeName, VFXManager.getActiveEffectCount());
    }

    /**
     * Finds the best entity to target along the player's look direction, with
     * tolerance so aim doesn't need to be pixel-perfect and works from far away.
     * Includes line-of-sight check - target must be directly visible.
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
            // Target mobs and other players - excludes MISC (items, XP orbs)
            if (candidate.getType().getCategory() == MobCategory.MISC) continue;

            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 toEntity = center.subtract(eyePos);

            double t = toEntity.dot(look);
            if (t <= 0 || t > maxRange) continue; // behind player or out of range

            Vec3 closestPointOnRay = eyePos.add(look.scale(t));
            double perpDist = center.distanceTo(closestPointOnRay);

            double entityRadius = Math.max(candidate.getBbWidth(), candidate.getBbHeight()) / 2.0;
            double allowedDist = tolerance + entityRadius;

            if (perpDist <= allowedDist && perpDist < bestPerpDist) {
                // Line of sight check - ensure nothing blocks the view to the entity's hitbox
                // Use COLLIDER instead of OUTLINE so grass/flowers/non-solid blocks don't block
                ClipContext context = new ClipContext(
                    eyePos,
                    center,
                    ClipContext.Block.COLLIDER, // only blocks with collision boxes
                    ClipContext.Fluid.NONE,
                    mc.player
                );
                HitResult result = mc.level.clip(context);
                if (result.getType() != HitResult.Type.MISS) {
                    // Something blocks line of sight
                    continue;
                }

                bestPerpDist = perpDist;
                best = candidate;
            }
        }

        return best;
    }
    
    /**
     * Render translucent outline around the currently targeted entity.
     * Shows which entity will be hit when slash key is pressed.
     */
    @SubscribeEvent
    static void onRenderLevel(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        
        if (currentTarget == null || !currentTarget.isAlive()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        
        // Render translucent bounding box around target
        var poseStack = event.getPoseStack();
        var camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        AABB box = currentTarget.getBoundingBox().inflate(0.1);
        var buffer = mc.renderBuffers().bufferSource();
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(
            poseStack,
            buffer.getBuffer(net.minecraft.client.renderer.RenderType.lines()),
            box,
            1.0f, 0.0f, 0.0f, 0.6f  // Red with 60% opacity
        );
        buffer.endBatch();
        
        poseStack.popPose();
    }
}