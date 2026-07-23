package com.prang.sukunaprototype.client.vfx;

import com.prang.sukunaprototype.Config;
import com.prang.sukunaprototype.SukunaPrototype;
import com.prang.sukunaprototype.SukunaPrototypeClient;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.ArrayList;

/**
 * Straight, thin sword slash plane, billboard-style.
 * - Each spawn picks a random 2D angle (0-360) directly in the camera's
 *   screen plane (built from the camera's right/up vectors). The slash's
 *   length runs along that angle and its width is perpendicular to it, so
 *   it can land as "-", "|", "\", "/", or anything in between with equal
 *   probability - not just "sideways relative to a mostly-horizontal tangent"
 *   like the old yaw+roll approach, which could never actually go vertical.
 * - Rendered with a custom RenderType (POSITION_COLOR, no normal at all,
 *   normal alpha blending for a solid/opaque look, targets MAIN_TARGET
 *   explicitly), so there's neither directional diffuse shading nor risk
 *   of landing on an uncomposited target.
 * - Wider in middle, tapered to a point at both ends (quadratic taper)
 * - 15 ticks (0.5s): instant appear, brief hold, fast fade
 */
@OnlyIn(Dist.CLIENT)
public class SlashEffect extends VFXInstance {
    private static final Logger LOGGER = LoggerFactory.getLogger("SlashEffect");
    
    private static final int SEGMENTS = 16;
    private static final Random RANDOM = new Random();
    
    // Store camera basis at spawn time so slash stays fixed in world space
    private Vector3f spawnRight;
    private Vector3f spawnUp;
    private Vector3f spawnViewDir;
    private Vec3 spawnPos;
    
    // Distance scaling: keep a constant apparent (screen) size no matter the range.
    // Tighter band so the slash stays LONG up close (higher min) and THIN far
    // away (lower max) instead of shrinking to nothing in your face or ballooning
    // out at range.
    private static final float SIZE_REF_DIST = 12.0f;   // distance (blocks) where scale == 1.0
    private static final float SIZE_MIN_SCALE = 0.85f;  // closest clamp (up in your face) - thinned
    private static final float SIZE_MAX_SCALE = 1.15f;  // farthest clamp - barely thicker, not bloated
    // Slight 3D tilt of the blade out of the screen plane (radians). This makes the
    // slash's length run partly along the view axis, so different parts sit at
    // different depths -> real PARTIAL occlusion when it meshes into a block/mob
    // (instead of a flat billboard's all-or-nothing depth behaviour).
    private static final float BLADE_TILT = 0.18f;
    
    // Color scheme
    public static class ColorScheme {
        final int mainColor;
        final int outlineColor;
        
        ColorScheme(int mainColor, int outlineColor) {
            this.mainColor = mainColor;
            this.outlineColor = outlineColor;
        }
    }
    public static final ColorScheme BLACK_WHITE = new ColorScheme(0xFF000000, 0xFFFFFFFF); // Solid black, white outline
    public static final ColorScheme BLACK_RED   = new ColorScheme(0xFF000000, 0xFFFF0000); // Solid black, red outline
    public static final ColorScheme RED_WHITE   = new ColorScheme(0xFFFF0000, 0xFFFFFFFF); // Solid red, white outline
    public static final ColorScheme RED_BLACK   = new ColorScheme(0xFFFF0000, 0xFF000000); // Solid red, black outline
    public static final ColorScheme WHITE_BLACK = new ColorScheme(0xFFFFFFFF, 0xFF000000); // Solid white, black outline
    public static final ColorScheme WHITE_RED   = new ColorScheme(0xFFFFFFFF, 0xFFFF0000); // Solid white, red outline
    
    // Re-added default (used by G key + debug marker + VFXManager.spawnSlashAtEntity).
    public static final ColorScheme CLASSIC     = BLACK_RED; // Solid black, red outline
    
    // Every available scheme, for the "random all colors" key.
    public static final ColorScheme[] ALL_SCHEMES = {
            BLACK_WHITE, BLACK_RED, RED_WHITE, RED_BLACK, WHITE_BLACK, WHITE_RED
    };
    
    // Slash parameters
        private final float length;
        private final float maxWidth;
        private final float sliceAngle; // 2D angle (radians) of the cut, in the camera's screen plane
        private final ColorScheme colors;
        private final boolean debugMode;
        private final float damage; // hearts of damage (half-hearts = HP)

        // Animation
        private float currentAlpha = 1.0f;
        private float currentScale = 1.0f;
        private float flash = 1.0f; // 1.0 = normal, >1 brightens edge toward white (separation flash)

        // Pre-computed parametric offsets along the blade's length. Direction is
        // applied at render time (billboard), so these are scalars, not vectors.
        // Thickness is NOT baked: it's read live from the slashThickness gamerule each
        // frame in render(), so /gamerule slashThickness updates even spawned slashes.
        private float[] lengthOffsets;

        // Track entities already hit by this slash so each only takes damage once.
        private final Set<UUID> hitEntities = new HashSet<>();
    
    // Two RenderTypes share the blade geometry. BOTH are depth-tested (LEQUAL)
    // so the slash PUNCTURES into blocks/mobs: the part in front of the geometry
    // draws, the part behind is hidden (like it went inside).
    //  - SLASH_RENDER_TYPE (core): normal alpha blend, depth-tested. Solid fill.
    //  - SLASH_OUTLINE_TYPE (rim): additive (neon glow) + depth-tested, so the
    //    glow also clips into geometry instead of x-raying through it.
    private static final RenderType SLASH_RENDER_TYPE = RenderType.create(
        "sukunaprototype_slash",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLE_STRIP,
        256,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderType.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setWriteMaskState(RenderType.COLOR_WRITE)
            .setCullState(RenderType.NO_CULL)
            .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
            .setOutputState(RenderType.MAIN_TARGET)
            .createCompositeState(false)
    );

    private static final RenderType SLASH_OUTLINE_TYPE = RenderType.create(
        "sukunaprototype_slash_outline",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLE_STRIP,
        256,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderType.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderType.LIGHTNING_TRANSPARENCY) // additive glow
            .setWriteMaskState(RenderType.COLOR_WRITE)
            .setCullState(RenderType.NO_CULL)
            .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
            .setOutputState(RenderType.MAIN_TARGET)
            .createCompositeState(false)
    );
    
    public SlashEffect(Vec3 position, int maxAge, float length, float maxWidth, ColorScheme colors, boolean debugMode) {
            this(position, RANDOM.nextFloat() * (float) (Math.PI * 2.0), maxAge, length, maxWidth, colors, debugMode, 0.0f);
        }

        public SlashEffect(Vec3 position, float sliceAngle, int maxAge, float length, float maxWidth,
                            ColorScheme colors, boolean debugMode) {
            this(position, sliceAngle, maxAge, length, maxWidth, colors, debugMode, 0.0f);
        }

        // 15 ticks = 0.5s default. Length/thickness read from config so the
        // slashLength / slashThickness options take effect live. Length also gets
        // per-spawn jitter (thickness stays constant).
        public SlashEffect(Vec3 position, ColorScheme colors) {
            this(position, RANDOM.nextFloat() * (float) (Math.PI * 2.0), 15,
                    (float) Config.SLASH_LENGTH.get().doubleValue()
                            * (1.0f + (RANDOM.nextFloat() * 2.0f - 1.0f) * (float) Config.SLASH_LENGTH_JITTER.get().doubleValue()),
                    (float) Config.SLASH_THICKNESS.get().doubleValue(),
                    colors, false, 0.0f);
        }

        // Backward-compatible with the old (position, yaw, maxAge, length, maxWidth,
        // height, colors) signature still used in VFXManager.spawnSlashAtEntity.
        // yaw/height are ignored now since angle is fully random and height no
        // longer affects the flat billboard blade.
        public SlashEffect(Vec3 position, float legacyYaw, int maxAge, float length, float maxWidth, float legacyHeight, ColorScheme colors) {
            this(position, RANDOM.nextFloat() * (float) (Math.PI * 2.0), maxAge, length, maxWidth, colors, false, 0.0f);
        }

        // Full constructor with damage parameter
        public SlashEffect(Vec3 position, float sliceAngle, int maxAge, float length, float maxWidth,
                            ColorScheme colors, boolean debugMode, float damage) {
            super(position, maxAge);
            this.sliceAngle = sliceAngle;
            this.length = length;
            this.maxWidth = maxWidth;
            this.colors = colors;
            this.debugMode = debugMode;
            this.damage = damage;
            this.billboard = false;
            this.fullBright = true;
            this.renderLayer = debugMode ? 2000 : 1000;

            setCullingRadius(length * 0.6f + length * 0.5f * Mth.sin(BLADE_TILT));
            computeBlade();
            
            // Capture camera basis at spawn time so slash stays fixed in world space
            Minecraft mc = Minecraft.getInstance();
            Camera camera = mc.gameRenderer.getMainCamera();
            if (camera != null) {
                Vector3f viewDir = camera.getLookVector();
                Vector3f worldUp = new Vector3f(0, 1, 0);
                Vector3f right = new Vector3f(viewDir).cross(worldUp);
                if (right.lengthSquared() < 0.0001f) {
                    right.set(1, 0, 0);
                } else {
                    right.normalize();
                }
                Vector3f up = new Vector3f(right).cross(viewDir).normalize();
                
                this.spawnRight = right;
                this.spawnUp = up;
                this.spawnViewDir = viewDir;
                this.spawnPos = mc.player.getEyePosition(1.0f);
            }
        }
    
    private void computeBlade() {
        lengthOffsets = new float[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = (float) i / SEGMENTS;
            lengthOffsets[i] = (t - 0.5f) * length;
        }
    }

    // Drives the separation-flash brightness (1.0 normal; >1 = white-hot edge).
    // Drives the separation-flash brightness (1.0 normal; >1 = white-hot edge).
    private void setFlash(float f) { this.flash = f; }

    /**
     * Applies damage to entities intersecting the slash AABB.
     * Called once at age==1 so damage is applied on the first real tick after spawn.
     * Only targets hostile mobs (Monster category) with direct line of sight from player.
     */
    private void applyDamage(ClientLevel level) {
        if (damage <= 0.0f) {
            LOGGER.info("[SlashEffect.applyDamage] SKIP: damage <= 0 ({})", damage);
            return; // No damage configured
        }

        // Reconstruct the same geometry used in render() for the AABB
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) {
            LOGGER.info("[SlashEffect.applyDamage] SKIP: camera is null");
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        Vec3 renderPos = getRenderPosition(0); // no partial tick for damage calc
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 lookDir = mc.player.getViewVector(1.0f);

        // Camera-facing 2D basis (same as render)
        Vector3f viewDir = camera.getLookVector();
        Vector3f worldUp = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(viewDir).cross(worldUp);
        if (right.lengthSquared() < 0.0001f) {
            right.set(1, 0, 0);
        } else {
            right.normalize();
        }
        Vector3f up = new Vector3f(right).cross(viewDir).normalize();

        float cosA = Mth.cos(sliceAngle);
        float sinA = Mth.sin(sliceAngle);
        Vector3f planeLen = new Vector3f(right).mul(cosA).add(new Vector3f(up).mul(sinA));
        Vector3f planeWid = new Vector3f(right).mul(-sinA).add(new Vector3f(up).mul(cosA));
        Vector3f lengthDir = new Vector3f(planeLen).mul(Mth.cos(BLADE_TILT)).add(new Vector3f(viewDir).mul(Mth.sin(BLADE_TILT)));
        Vector3f widthDir = planeWid;

        // Live thickness from gamerule (same as render)
        float thick = (float) SukunaPrototypeClient.gameruleMilli(SukunaPrototype.SLASH_THICKNESS, (int)(Config.SLASH_THICKNESS.get() * 1000));

        // Build AABB covering the full blade length + max thickness (with small padding)
        float halfLen = length * 0.5f;
        float halfThick = thick * 0.5f;
        Vec3 min = renderPos.subtract(
            lengthDir.x * halfLen + widthDir.x * halfThick,
            lengthDir.y * halfLen + widthDir.y * halfThick,
            lengthDir.z * halfLen + widthDir.z * halfThick
        );
        Vec3 max = renderPos.add(
            lengthDir.x * halfLen + widthDir.x * halfThick,
            lengthDir.y * halfLen + widthDir.y * halfThick,
            lengthDir.z * halfLen + widthDir.z * halfThick
        );
        AABB box = new AABB(min, max).inflate(0.5); // small pad for edge cases

        LOGGER.info("[SlashEffect.applyDamage] AABB: min={} max={} length={} thick={} pos={}", min, max, length, thick, renderPos);

        // Find living entities in the box
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box);
        
        // Filter: target mobs and other players (not self, not items/XP orbs)
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity e : candidates) {
            if (e == mc.player) {
                LOGGER.info("[SlashEffect.applyDamage] SKIP: target is self");
                continue;
            }
            // Target mobs AND other players - excludes MISC (items, XP orbs)
            if (e.getType().getCategory() == MobCategory.MISC) {
                LOGGER.info("[SlashEffect.applyDamage] SKIP: MISC category - {} ({})", e.getName().getString(), e.getType().getCategory());
                continue;
            }
            
            // Line of sight check: raycast from player eye to entity hitbox center
            Vec3 targetCenter = e.getBoundingBox().getCenter();
            ClipContext context = new ClipContext(
                eyePos,
                targetCenter,
                ClipContext.Block.COLLIDER, // blocks block the ray
                ClipContext.Fluid.NONE,
                mc.player
            );
            HitResult hitResult = level.clip(context);
            if (hitResult.getType() != HitResult.Type.MISS) {
                LOGGER.info("[SlashEffect.applyDamage] SKIP: no line of sight to {} (hit: {})", e.getName().getString(), hitResult.getType());
                continue;
            }
            
            // Also check if any other entity blocks the view (including other mobs)
            // Use a slightly thicker ray to be more forgiving
            AABB rayBox = new AABB(eyePos, targetCenter).inflate(0.1);
            List<Entity> blockingEntities = level.getEntities(mc.player, rayBox, entity -> 
                entity != mc.player && entity != e && entity.isAlive() && entity.getType().getCategory() != MobCategory.MISC
            );
            if (!blockingEntities.isEmpty()) {
                LOGGER.info("[SlashEffect.applyDamage] SKIP: entity {} blocks line of sight to {}", blockingEntities.get(0).getName().getString(), e.getName().getString());
                continue;
            }

            targets.add(e);
        }

        LOGGER.info("[SlashEffect.applyDamage] Found {} valid targets after filtering", targets.size());
        for (LivingEntity e : targets) {
            LOGGER.info("[SlashEffect.applyDamage]   Target: {} pos={} uuid={}", e.getName().getString(), e.position(), e.getUUID());
        }

        if (targets.isEmpty()) {
            return;
        }

        // damage field is in HEARTS; hurt() expects HALF-HEARTS (HP). Convert: hearts * 2.
        float damageAmount = damage * 2.0f; 
        LOGGER.info("[SlashEffect.applyDamage] Damage amount: {} HP (hearts={})", damageAmount, damage);

        // Send slash damage packet to server for authoritative damage application
        // Server validates position, angle, damage amount, and target entities
        UUID slashId = UUID.randomUUID();
        Vec3 direction = new Vec3(lengthDir.x, lengthDir.y, lengthDir.z).normalize();
        List<UUID> targetUuids = targets.stream().map(Entity::getUUID).toList();
        
        SlashDamagePacket packet = new SlashDamagePacket(
            slashId,
            renderPos,
            direction,
            length,
            thick,
            damage, // damage in hearts
            targetUuids
        );

        LOGGER.info("[SlashEffect.applyDamage] Sending SlashDamagePacket: slashId={} targets={} pos={} dir={} len={} width={} dmg={}", 
            slashId, targetUuids.size(), renderPos, direction, length, thick, damage);
        
        // Send packet to server (works for both integrated and dedicated servers)
        PacketDistributor.sendToServer(packet);

        // Track hit entities locally to prevent duplicate client-side processing
        // (server is authoritative, but this prevents visual double-hit if packet is re-sent)
        for (LivingEntity target : targets) {
            hitEntities.add(target.getUUID());
        }
    }

    @Override
    public RenderType getRenderType() {
        return SLASH_RENDER_TYPE;
    }

    @Override
    protected boolean onTick(ClientLevel level) {
        if (debugMode) {
            currentAlpha = 1.0f;
            currentScale = 1.0f;
            return false;
        }

        float progress = getAgeRatio();

        // SEPARATION FLASH — the Dismantle "ignites": the cut line pops to
        // white-hot on the first ~2 ticks (flash > 1 -> edge lerps to white),
        // then settles to 1.0. No particles — just the line itself flashing.
        float flash = age < 2 ? 1.6f : Math.max(1.0f, 1.6f - (float)(age - 2) / 6.0f * 0.6f);
        setFlash(flash);

        // Apply damage on the first real tick (age == 1, after spawn)
        if (age == 1) {
            applyDamage(level);
        }

        // Instant appear at full alpha, hold, then fade out over the last 40%.
        // No fade-IN (looks like a flicker) - the slash just shows up and dies.
        if (progress < 0.6f) {            // full show
            currentScale = 1.0f;
            currentAlpha = 1.0f;
        } else {                           // last 40%: quick fade out
            float f = (progress - 0.6f) / 0.4f;
            currentAlpha = (1.0f - f) * (1.0f - f);
            currentScale = 1.0f - f * 0.3f;
        }

        return false;
    }
    
    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, Camera camera) {
        if (currentAlpha <= 0.01f || currentScale <= 0.01f) return;
        
        poseStack.pushPose();
        
        Vec3 cameraPos = camera.getPosition();
        Vec3 renderPos = getRenderPosition(partialTick);
        poseStack.translate(
            renderPos.x - cameraPos.x,
            renderPos.y - cameraPos.y,
            renderPos.z - cameraPos.z
        );
        
        // Use stored camera basis from spawn time so slash stays fixed in world space
        // Falls back to current camera if spawn basis wasn't captured
        Vector3f viewDir = this.spawnViewDir != null ? this.spawnViewDir : camera.getLookVector();
        Vector3f right = this.spawnRight != null ? this.spawnRight : new Vector3f(viewDir).cross(new Vector3f(0, 1, 0));
        Vector3f up = this.spawnUp != null ? this.spawnUp : new Vector3f(right).cross(viewDir);
        
        if (right.lengthSquared() < 0.0001f) {
            right.set(1, 0, 0);
        }
        right.normalize();
        up.normalize();
        
        Matrix4f matrix = poseStack.last().pose();
        
        int mainColor = colors.mainColor;
        int outlineColor = colors.outlineColor;
        float alpha = currentAlpha;
        
        // Apparent-size lock: scale world size by distance so the slash
        // subtends a constant angle on screen. Up close -> small, far -> big.
        float dist = (float) cameraPos.distanceTo(renderPos);
        float distScale = Mth.clamp(dist / SIZE_REF_DIST, SIZE_MIN_SCALE, SIZE_MAX_SCALE);
        float sizeScale = currentScale * distScale;
        
        float cosA = Mth.cos(sliceAngle);
        float sinA = Mth.sin(sliceAngle);
        // lengthDir / widthDir start in the camera-facing plane (right/up basis).
        Vector3f planeLen = new Vector3f(right).mul(cosA).add(new Vector3f(up).mul(sinA));
        Vector3f planeWid = new Vector3f(right).mul(-sinA).add(new Vector3f(up).mul(cosA));
        // Tilt the blade slightly out of the screen plane along the view axis so its
        // length gains depth variation -> it can be occluded PARTIALLY by blocks/mobs.
        Vector3f lengthDir = new Vector3f(planeLen).mul(Mth.cos(BLADE_TILT)).add(new Vector3f(viewDir).mul(Mth.sin(BLADE_TILT)));
        Vector3f widthDir = planeWid;

        // LIVE tuning: thickness + outline read from gamerules every frame, so
        // /gamerule slashThickness ... / slashOutline ... apply INSTANTLY, even to
        // slashes already on screen (no /reload, no respawn). Stored in milliblocks
        // (×1000) since GameRules has no double type; defaults match Config.
        float thick = (float) SukunaPrototypeClient.gameruleMilli(SukunaPrototype.SLASH_THICKNESS, (int)(Config.SLASH_THICKNESS.get() * 1000));
        float outlineW = (float) SukunaPrototypeClient.gameruleMilli(SukunaPrototype.SLASH_OUTLINE, (int)(Config.SLASH_OUTLINE.get() * 1000));
        // Per-segment width taper (pointed ends, widest at center), scaled live.
        // t-based so thickness is never baked into stored geometry.
        java.util.function.Function<Integer, Float> segWidth = (i) -> {
            float t = (float) i / SEGMENTS;
            float d = Math.abs(t - 0.5f) * 2.0f;
            return thick * (1.0f - d * d);
        };
        
        // 1. OUTLINE — neon glow. Two additive passes (soft outer halo + tight
        //    bright edge). minRim keeps the rim visible all the way to the sharp
        //    tip, so the whole slash reads as a glowing blade, not just its middle.
        float vib = (float) Config.SLASH_OUTLINE_VIBRANCY.get().doubleValue();
        float outlineAlpha = Math.min(1.0f, alpha * vib);
        if (outlineAlpha > 0.01f && outlineW > 0.001f) {
            VertexConsumer vc = bufferSource.getBuffer(SLASH_OUTLINE_TYPE);
            float or = ((outlineColor >> 16) & 0xFF) / 255.0f;
            float og = ((outlineColor >> 8) & 0xFF) / 255.0f;
            float ob = (outlineColor & 0xFF) / 255.0f;
            // Separation flash: lerp the edge color toward white-hot when flash > 1.
            float fk = Math.max(0f, flash - 1.0f) * 2.0f; // 0..1 as flash 1..1.5
            if (fk > 0.001f) {
                or = or + (1.0f - or) * fk; og = og + (1.0f - og) * fk; ob = ob + (1.0f - ob) * fk;
            }
            // EDGE-ONLY GLOW — tight halo + sharp edge, surgical. No fat middle
            // bloom: a cursed-energy line, not a glow stick. outlineW = edge tightness.
            renderBlade(vc, matrix, or, og, ob, outlineAlpha * 0.5f,  sizeScale, outlineW * 2.2f, 0.0f, segWidth, lengthDir, widthDir); // tight halo
            renderBlade(vc, matrix, or, og, ob, outlineAlpha,         sizeScale, outlineW,       0.30f, segWidth, lengthDir, widthDir); // sharp edge
        }

        // 2. MAIN BLADE (solid fill). Depth-tested like the outline, so it
        // punctures into blocks/mobs together with the rim (no x-ray).
        if (alpha > 0.01f) {
            VertexConsumer vc = bufferSource.getBuffer(SLASH_RENDER_TYPE);
            float mr = ((mainColor >> 16) & 0xFF) / 255.0f;
            float mg = ((mainColor >> 8) & 0xFF) / 255.0f;
            float mb = (mainColor & 0xFF) / 255.0f;
            renderBlade(vc, matrix, mr, mg, mb, alpha, sizeScale, 0f, 0f, segWidth, lengthDir, widthDir);
        }
        
        poseStack.popPose();
    }
    
    /**
     * Submits the blade as a single triangle strip (NO_CULL = double-sided).
     * @param extraWidth outline thickness ADDED on top of the taper; it is
     *   itself multiplied by the blade's taper so the outline pinches to a
     *   point at both ends exactly like the main blade does.
     */
    private void renderBlade(VertexConsumer vc, Matrix4f matrix, float r, float g, float b, float alpha,
                              float scale, float extraWidth, float minRim,
                              java.util.function.Function<Integer, Float> segWidth, Vector3f lengthDir, Vector3f widthDir) {
        for (int i = 0; i <= SEGMENTS; i++) {
            float lenOffset = lengthOffsets[i] * scale;
            // taper (1 - dist^2) shapes the pointy ends.
            float taper = 1.0f - Math.abs((float) i / SEGMENTS - 0.5f) * 2.0f;
            taper = taper * taper;
            // Outline rim keeps a minimum fraction (minRim) at the tips so the
            // neon edge stays visible to the very point; the solid core (extraWidth=0)
            // still tapers to a true point.
            float rim = extraWidth * (minRim + (1.0f - minRim) * taper);
            float w = segWidth.apply(i) * scale + rim;
            
            Vector3f center = new Vector3f(lengthDir).mul(lenOffset);
            Vector3f halfWidth = new Vector3f(widthDir).mul(w * 0.5f);
            
            Vector3f left = new Vector3f(center).sub(halfWidth);
            Vector3f right = new Vector3f(center).add(halfWidth);
            
            vc.addVertex(matrix, left.x, left.y, left.z).setColor(r, g, b, alpha);
            vc.addVertex(matrix, right.x, right.y, right.z).setColor(r, g, b, alpha);
        }
    }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
            private Vec3 position = Vec3.ZERO;
            private Entity target = null;
            private int maxAge = 15;
            private float length = (float) Config.SLASH_LENGTH.get().doubleValue();
            private float maxWidth = (float) Config.SLASH_THICKNESS.get().doubleValue();
            private ColorScheme colors = CLASSIC;
            private boolean debugMode = false;
            private float damage = (float) Config.SLASH_DAMAGE.get().doubleValue();
        
        public Builder at(Entity target) { this.target = target; if (target!=null) this.position = target.position(); return this; }
        public Builder at(Vec3 p) { this.position = p; this.target = null; return this; }
        // yaw() kept as a no-op-compatible shim for old call sites; the slash's
        // on-screen angle is now fully randomized per spawn (see build()), not
        // derived from player/entity yaw.
        public Builder yaw(float y) { return this; }
        public Builder maxAge(int a) { this.maxAge = a; return this; }
        public Builder length(float l) { this.length = l; return this; }
        public Builder maxWidth(float w) { this.maxWidth = w; return this; }
        public Builder height(float h) { return this; } // kept for call-site compatibility, unused now
        public Builder colors(ColorScheme c) { this.colors = c; return this; }
        public Builder debugMode(boolean d) { this.debugMode = d; return this; }
        public Builder damage(float d) { this.damage = d; return this; }
        
        public SlashEffect build() {
            Vec3 pos;
            if (target != null) {
                // target.position() is the entity's FEET, not its center - that's
                // why the slash always appeared "under" the target. Raise it to
                // roughly chest/eye height instead.
                pos = target.position().add(0, target.getBbHeight() * 0.6, 0);
            } else {
                pos = position;
            }
            
            // Random jitter so it's not the exact same spot every single time,
            // while staying centered on/near the target.
            float jitterX = (RANDOM.nextFloat() - 0.5f) * 1.0f;
            float jitterY = (RANDOM.nextFloat() - 0.5f) * 0.6f;
            float jitterZ = (RANDOM.nextFloat() - 0.5f) * 1.0f;
            pos = pos.add(jitterX, jitterY, jitterZ);
            
            // 1. DIAGONAL BIAS — Sukuna's Dismantle cuts are deliberate ／ lines,
            // not uniform 360° swooshes. Snap toward discrete diagonal/vertical
            // slots with a small wobble so each cut reads intentional.
            float[] CUT_SLOTS = {
                (float) (Math.PI * 0.25f),  // ／
                (float) (Math.PI * 0.75f),  // ＼
                (float) (Math.PI * 0.50f),  // │
                (float) (Math.PI * 0.00f),  // ─
                (float) (Math.PI * 1.00f)   // ─
            };
            float sliceAngle = CUT_SLOTS[RANDOM.nextInt(CUT_SLOTS.length)]
                    + (RANDOM.nextFloat() - 0.5f) * 0.40f; // ±~11° wobble
            
            // Per-spawn length randomness: thickness stays constant (maxWidth),
            // only the length varies. finalLen = length * (1 ± jitter).
            float jit = (float) Config.SLASH_LENGTH_JITTER.get().doubleValue();
            float lenMul = 1.0f + (RANDOM.nextFloat() * 2.0f - 1.0f) * jit;
            float finalLen = length * lenMul;
            
            return new SlashEffect(pos, sliceAngle, maxAge, finalLen, maxWidth, colors, debugMode, damage);
        }
        public void spawn() { VFXManager.spawn(build()); }
    }
}
++ import com.prang.sukunaprototype.network.SlashDamagePacket;
++ import net.neoforged.neoforge.network.PacketDistributor;
