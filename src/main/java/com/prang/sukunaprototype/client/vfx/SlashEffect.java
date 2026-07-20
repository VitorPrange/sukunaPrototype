package com.prang.sukunaprototype.client.vfx;

import com.prang.sukunaprototype.Config;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

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
    
    private static final int SEGMENTS = 16;
    private static final Random RANDOM = new Random();
    
    // Distance scaling: keep a constant apparent (screen) size no matter the range.
    // Tighter band so the slash stays LONG up close (higher min) and THIN far
    // away (lower max) instead of shrinking to nothing in your face or ballooning
    // out at range.
    private static final float SIZE_REF_DIST = 12.0f;   // distance (blocks) where scale == 1.0
    private static final float SIZE_MIN_SCALE = 1.0f;   // closest clamp (up in your face) - >=1 so never shrinks up close
    private static final float SIZE_MAX_SCALE = 1.6f;   // farthest clamp - lowered so not too thick
    private static final float OUTLINE_EXTRA = 0.06f;   // outline thickness added on top of blade
    
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
    
    // Animation
    private float currentAlpha = 1.0f;
    private float currentScale = 1.0f;
    
    // Pre-computed parametric offsets along the blade's length + taper widths.
    // Direction is applied at render time (billboard), so these are scalars, not vectors.
    private float[] lengthOffsets;
    private float[] widths;
    
    // Custom RenderType: POSITION_COLOR (no normal at all, so no directional
    // diffuse shading), targeting MAIN_TARGET explicitly so it's guaranteed to
    // be composited to screen. Uses normal alpha blending (not additive) so
    // full-alpha colors look solid/opaque instead of glowy and see-through.
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
    
    public SlashEffect(Vec3 position, int maxAge, float length, float maxWidth, ColorScheme colors, boolean debugMode) {
        this(position, RANDOM.nextFloat() * (float) (Math.PI * 2.0), maxAge, length, maxWidth, colors, debugMode);
    }
    
    public SlashEffect(Vec3 position, float sliceAngle, int maxAge, float length, float maxWidth,
                        ColorScheme colors, boolean debugMode) {
        super(position, maxAge);
        this.sliceAngle = sliceAngle;
        this.length = length;
        this.maxWidth = maxWidth;
        this.colors = colors;
        this.debugMode = debugMode;
        this.billboard = false;
        this.fullBright = true;
        this.renderLayer = debugMode ? 2000 : 1000;
        
        setCullingRadius(length * 0.6f);
        computeBlade();
    }
    
    // 15 ticks = 0.5s default. Length/thickness read from config so the
    // slashLength / slashThickness options take effect live. Length also gets
    // per-spawn jitter (thickness stays constant).
    public SlashEffect(Vec3 position, ColorScheme colors) {
        this(position, 15,
                (float) Config.SLASH_LENGTH.get().doubleValue()
                        * (1.0f + (RANDOM.nextFloat() * 2.0f - 1.0f) * (float) Config.SLASH_LENGTH_JITTER.get().doubleValue()),
                (float) Config.SLASH_THICKNESS.get().doubleValue(),
                colors, false);
    }
    
    // Backward-compatible with the old (position, yaw, maxAge, length, maxWidth,
    // height, colors) signature still used in VFXManager.spawnSlashAtEntity.
    // yaw/height are ignored now since angle is fully random and height no
    // longer affects the flat billboard blade.
    public SlashEffect(Vec3 position, float legacyYaw, int maxAge, float length, float maxWidth, float legacyHeight, ColorScheme colors) {
        this(position, maxAge, length, maxWidth, colors, false);
    }
    
    private void computeBlade() {
        lengthOffsets = new float[SEGMENTS + 1];
        widths = new float[SEGMENTS + 1];
        
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = (float) i / SEGMENTS;
            lengthOffsets[i] = (t - 0.5f) * length;
            
            // Width: pointed at both ends, widest at center. No floor -> true taper to a point.
            float distFromCenter = Math.abs(t - 0.5f) * 2.0f;
            widths[i] = maxWidth * (1.0f - distFromCenter * distFromCenter);
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
        // Pure translation only - no rotation here. All orientation is baked
        // directly into lengthDir/widthDir below, computed from the camera's
        // own screen-space basis, so the slash is always flat-on to the
        // viewer no matter which way sliceAngle points it.
        Matrix4f matrix = poseStack.last().pose();
        
        int mainColor = colors.mainColor;
        int outlineColor = colors.outlineColor;
        float alpha = currentAlpha;
        
        // Apparent-size lock: scale world size by distance so the slash
        // subtends a constant angle on screen. Up close -> small, far -> big.
        float dist = (float) cameraPos.distanceTo(renderPos);
        float distScale = Mth.clamp(dist / SIZE_REF_DIST, SIZE_MIN_SCALE, SIZE_MAX_SCALE);
        float sizeScale = currentScale * distScale;
        
        // Camera-facing 2D basis (right/up in the plane facing the viewer).
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
        // lengthDir / widthDir: perpendicular, both inside the camera-facing plane.
        Vector3f lengthDir = new Vector3f(right).mul(cosA).add(new Vector3f(up).mul(sinA));
        Vector3f widthDir = new Vector3f(right).mul(-sinA).add(new Vector3f(up).mul(cosA));
        
        // 1. OUTLINE (wider, behind, drawn first). Its extra width tapers to 0
        // at the tips so it converges to a sharp point (looks like < not >).
        float outlineAlpha = Math.min(1.0f, alpha * 1.2f);
        if (outlineAlpha > 0.01f) {
            VertexConsumer vc = bufferSource.getBuffer(getRenderType());
            float or = ((outlineColor >> 16) & 0xFF) / 255.0f;
            float og = ((outlineColor >> 8) & 0xFF) / 255.0f;
            float ob = (outlineColor & 0xFF) / 255.0f;
            renderBlade(vc, matrix, or, og, ob, outlineAlpha, sizeScale, OUTLINE_EXTRA, lengthDir, widthDir);
        }
        
        // 2. MAIN BLADE (solid fill).
        if (alpha > 0.01f) {
            VertexConsumer vc = bufferSource.getBuffer(getRenderType());
            float mr = ((mainColor >> 16) & 0xFF) / 255.0f;
            float mg = ((mainColor >> 8) & 0xFF) / 255.0f;
            float mb = (mainColor & 0xFF) / 255.0f;
            renderBlade(vc, matrix, mr, mg, mb, alpha, sizeScale, 0f, lengthDir, widthDir);
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
                              float scale, float extraWidth, Vector3f lengthDir, Vector3f widthDir) {
        for (int i = 0; i <= SEGMENTS; i++) {
            float lenOffset = lengthOffsets[i] * scale;
            // taper (1 - dist^2) at the blade also scales the outline's extra width,
            // so tips pinch to a true point on BOTH layers -> "<" not ">".
            float taper = 1.0f - Math.abs((float) i / SEGMENTS - 0.5f) * 2.0f;
            taper = taper * taper;
            float w = widths[i] * scale + extraWidth * taper;
            
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
            
            // Fully random on-screen angle (0-360°): horizontal, vertical,
            // diagonal, anything - all equally likely.
            float sliceAngle = RANDOM.nextFloat() * (float) (Math.PI * 2.0);
            
            // Per-spawn length randomness: thickness stays constant (maxWidth),
            // only the length varies. finalLen = length * (1 ± jitter).
            float jit = (float) Config.SLASH_LENGTH_JITTER.get().doubleValue();
            float lenMul = 1.0f + (RANDOM.nextFloat() * 2.0f - 1.0f) * jit;
            float finalLen = length * lenMul;
            
            return new SlashEffect(pos, sliceAngle, maxAge, finalLen, maxWidth, colors, debugMode);
        }
        public void spawn() { VFXManager.spawn(build()); }
    }
}