package com.prang.sukunaprototype.client.vfx;

import com.prang.sukunaprototype.Config;
import com.prang.sukunaprototype.SukunaPrototype;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages visual effects - spawning, ticking, and rendering.
 * Thread-safe for multi-threaded packet handling.
 */
@EventBusSubscriber(modid = SukunaPrototype.MODID, value = Dist.CLIENT)
public class VFXManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SukunaPrototypeVFX");
    
    private static final ConcurrentLinkedQueue<VFXInstance> pendingEffects = new ConcurrentLinkedQueue<>();
    private static final List<VFXInstance> activeEffects = new ArrayList<>();
    private static final List<VFXInstance> toRemove = new ArrayList<>();
    private static final int MAX_EFFECTS = 10000;
    
    public static void init() {
        LOGGER.info("VFX Manager initialized - ready for effects");
    }
    
    public static void spawn(VFXInstance effect) {
        LOGGER.info("[VFXManager] spawn() called for effect: {} (class: {})", effect, effect.getClass().getSimpleName());
        LOGGER.info("[VFXManager] Effect details - pos: {}, age: {}, maxAge: {}, color: 0x{:08X}", 
            effect.getPosition(), effect.getAge(), effect.getMaxAge(), effect.getColor());
        
        if (activeEffects.size() + pendingEffects.size() >= MAX_EFFECTS) {
            LOGGER.warn("[VFXManager] Max effects reached ({}), removing oldest", MAX_EFFECTS);
            if (!activeEffects.isEmpty()) {
                activeEffects.remove(0);
            }
        }
        pendingEffects.add(effect);
        LOGGER.info("[VFXManager] Effect added to pending queue. Pending: {}, Active: {}", pendingEffects.size(), activeEffects.size());
    }
    
    public static void spawnSlashAtEntity(Entity target) {
        LOGGER.info("[VFXManager] spawnSlashAtEntity called for: {}", target);
        if (target == null || target.level() == null || !(target.level() instanceof ClientLevel)) {
            LOGGER.warn("[VFXManager] Invalid target or level");
            return;
        }
        
        ClientLevel level = (ClientLevel) target.level();
        Vec3 targetPos = target.position();
        
        SlashEffect slash = new SlashEffect(
            targetPos,
            target.getYRot(),
            15,
            (float) Config.SLASH_LENGTH.get().doubleValue(),
            (float) Config.SLASH_THICKNESS.get().doubleValue(),
            1.5f,
            SlashEffect.CLASSIC
        );
        
        spawn(slash);
    }
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        VFXInstance effect;
        int processed = 0;
        while ((effect = pendingEffects.poll()) != null) {
            activeEffects.add(effect);
            processed++;
            LOGGER.info("[VFXManager] Moved effect from pending to active: {} (total active: {})", 
                effect.getClass().getSimpleName(), activeEffects.size());
        }
        if (processed > 0) {
            LOGGER.debug("[VFXManager] Processed {} pending effects", processed);
        }
        
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            toRemove.clear();
            for (VFXInstance activeEffect : activeEffects) {
                if (activeEffect.tick(level)) {
                    toRemove.add(activeEffect);
                    LOGGER.debug("[VFXManager] Effect marked for removal: {} (age: {}/{})", 
                        activeEffect.getClass().getSimpleName(), activeEffect.getAge(), activeEffect.getMaxAge());
                }
            }
            if (!toRemove.isEmpty()) {
                LOGGER.info("[VFXManager] Removing {} expired effects", toRemove.size());
                activeEffects.removeAll(toRemove);
            }
        }
        
        activeEffects.sort(Comparator.comparingInt(VFXInstance::getRenderLayer));
    }
    
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        
        if (activeEffects.isEmpty()) {
            return;
        }
        
        LOGGER.debug("[VFXManager] Rendering {} active effects at AFTER_PARTICLES stage", activeEffects.size());
        
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Camera camera = mc.gameRenderer.getMainCamera();
        DeltaTracker partialTick = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = mc.renderBuffers().bufferSource();
        Frustum frustum = event.getFrustum();
        
        if (level == null || camera == null || frustum == null) {
            LOGGER.warn("[VFXManager] Missing render dependencies - level: {}, camera: {}, frustum: {}", 
                level, camera, frustum);
            return;
        }
        
        float partialTickValue = (float) partialTick.getGameTimeDeltaTicks();
        
        int rendered = 0;
        int culled = 0;
        for (VFXInstance activeEffect : activeEffects) {
            if (!activeEffect.shouldCull(frustum, partialTickValue)) {
                LOGGER.debug("[VFXManager] Rendering effect: {} at pos: {}", 
                    activeEffect.getClass().getSimpleName(), activeEffect.getRenderPosition(partialTickValue));
                activeEffect.render(poseStack, bufferSource, partialTickValue, camera);
                rendered++;
            } else {
                culled++;
            }
        }
        
        if (rendered > 0 || culled > 0) {
            LOGGER.info("[VFXManager] Render pass complete - rendered: {}, culled: {}, total active: {}", 
                rendered, culled, activeEffects.size());
        }
    }
    
    public static int getActiveEffectCount() {
        return activeEffects.size();
    }
    
    public static void clearAll() {
        LOGGER.info("[VFXManager] Clearing all effects (active: {}, pending: {})", activeEffects.size(), pendingEffects.size());
        activeEffects.clear();
        pendingEffects.clear();
    }
}