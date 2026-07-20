package com.prang.sukunaprototype;

import com.prang.sukunaprototype.client.vfx.SlashEffect;
import com.prang.sukunaprototype.client.vfx.VFXManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
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
    private static final Logger LOGGER = LoggerFactory.getLogger("SukunaPrototypeSlash");
    private static final Random RANDOM = new Random();

    // X key scanCode = 45 - tap: one random slash. hold: consecutive slashes that accelerate.
    // Hold-ramp tuning (in ticks @ 20tps): starts at HOLD_SLOW, eases (quadratic)
    // up to the gamerule cap (slashMaxRate) over HOLD_RAMP ticks. Short ramp so
    // the rate actually reaches the gamerule value instead of plateauing below it.
    private static final int HOLD_SLOW = 10;     // start interval: 2 slashes/sec
    private static final int HOLD_RAMP = 20;     // ticks to reach the slashMaxRate cap (1s held)

    // Hold state
    private static boolean holding = false;
    private static int holdTicks = 0;
    private static int nextSlashTick = 0;

    // Soft-lock targeting: how far (in blocks) we search for a target along
    // the player's look direction, and how far off-center (in blocks) an
    // entity can be from that aim line and still count as "targeted".
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

        SLASH_KEY = new KeyMapping(
            "key.sukunaprototype.slash.random",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_X,
            "key.categories.sukunaprototype"
        );
        event.register(SLASH_KEY);

        LOGGER.info("[SlashVFX] Key bindings registered: X=Random All (tap) / hold to ramp");
    }

    @SubscribeEvent
    static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != 1) return;          // only initial press fires
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
            .spawn();

        LOGGER.info("[SlashVFX] {} slash spawned! Active effects: {}", schemeName, VFXManager.getActiveEffectCount());
    }

    /**
     * Finds the best entity to target along the player's look direction, with
     * tolerance so aim doesn't need to be pixel-perfect and works from far away.
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

            double t = toEntity.dot(look);
            if (t <= 0 || t > maxRange) continue; // behind player or out of range

            Vec3 closestPointOnRay = eyePos.add(look.scale(t));
            double perpDist = center.distanceTo(closestPointOnRay);

            double entityRadius = Math.max(candidate.getBbWidth(), candidate.getBbHeight()) / 2.0;
            double allowedDist = tolerance + entityRadius;

            if (perpDist <= allowedDist && perpDist < bestPerpDist) {
                bestPerpDist = perpDist;
                best = candidate;
            }
        }

        return best;
    }
}
