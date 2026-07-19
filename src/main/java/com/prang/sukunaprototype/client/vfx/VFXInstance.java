package com.prang.sukunaprototype.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Base class for visual effect instances that can be rendered in the world.
 * Supports hundreds of simultaneous instances with minimal overhead.
 */
@OnlyIn(Dist.CLIENT)
public abstract class VFXInstance {
    /** Position in world coordinates */
    protected Vec3 position;
    /** Current age in ticks */
    protected int age = 0;
    /** Maximum lifetime in ticks */
    protected int maxAge = 10;
    /** Whether this effect has been marked for removal */
    protected boolean removed = false;
    /** Scale factor for rendering */
    protected float scale = 1.0f;
    /** Color tint (ARGB) */
    protected int color = 0xFFFFFFFF;
    /** Rotation in degrees */
    protected float yaw = 0.0f;
    protected float pitch = 0.0f;
    protected float roll = 0.0f;
    /** Whether this effect should face the camera (billboard) */
    protected boolean billboard = true;
    /** Whether this effect ignores lighting */
    protected boolean fullBright = false;
    /** Render layer priority (lower = rendered first) */
    protected int renderLayer = 0;
    
    // Frustum culling bounds (overridden by subclasses for large effects)
    protected float cullingRadiusX = 0.5f;
    protected float cullingRadiusY = 0.5f;
    protected float cullingRadiusZ = 0.5f;
    
    protected VFXInstance() {}
    
    public VFXInstance(Vec3 position, int maxAge) {
        this.position = position;
        this.maxAge = maxAge;
        this.age = 0;
    }
    
    /**
     * Called each tick to update the effect.
     * @param level The client level
     * @return true if the effect should be removed
     */
    public boolean tick(ClientLevel level) {
        age++;
        if (age >= maxAge) {
            return true;
        }
        return onTick(level);
    }
    
    /**
     * Override for custom tick logic.
     * @return true if the effect should be removed
     */
    protected boolean onTick(ClientLevel level) {
        return false;
    }
    
    /**
     * Render this effect.
     * @param poseStack The pose stack
     * @param bufferSource The buffer source
     * @param partialTick Partial tick for interpolation
     * @param camera The camera
     */
    public abstract void render(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, Camera camera);
    
    /**
     * Get the interpolated position for rendering.
     */
    public Vec3 getRenderPosition(float partialTick) {
        return position;
    }
    
    /**
     * Get the render type to use for this effect.
     */
    public abstract RenderType getRenderType();
    
    /**
     * Check if this effect should be culled based on camera frustum.
     * Uses customizable culling bounds for large effects.
     */
    public boolean shouldCull(Frustum frustum, float partialTick) {
        if (!billboard) return false;
        Vec3 renderPos = getRenderPosition(partialTick);
        // Create AABB using customizable culling radii
        AABB box = new AABB(renderPos.x - cullingRadiusX, renderPos.y - cullingRadiusY, renderPos.z - cullingRadiusZ,
                           renderPos.x + cullingRadiusX, renderPos.y + cullingRadiusY, renderPos.z + cullingRadiusZ);
        return !frustum.isVisible(box);
    }
    
    /**
     * Apply billboard transformation to face the camera.
     */
    protected void applyBillboard(PoseStack poseStack, Camera camera, float partialTick) {
        if (!billboard) return;
        
        // Get camera rotation
        float yaw = camera.getYRot();
        float pitch = camera.getXRot();
        
        // Apply inverse camera rotation to face camera
        poseStack.mulPose(new Quaternionf().rotateY(-yaw * Mth.DEG_TO_RAD));
        poseStack.mulPose(new Quaternionf().rotateX(pitch * Mth.DEG_TO_RAD));
    }
    
    /**
     * Apply entity-relative billboard (faces camera but rotates around Y only).
     */
    protected void applyEntityBillboard(PoseStack poseStack, Camera camera, float partialTick) {
        if (!billboard) return;
        
        float yaw = camera.getYRot();
        poseStack.mulPose(new Quaternionf().rotateY(-yaw * Mth.DEG_TO_RAD));
    }
    
    /**
     * Get the packed light coordinate for fullbright rendering.
     */
    protected int getPackedLight() {
        return fullBright ? LightTexture.FULL_BRIGHT : LightTexture.pack(15, 15);
    }
    
    /**
     * Get the overlay coordinate.
     */
    protected int getPackedOverlay() {
        return 0xF000F0; // No overlay
    }
    
    // Getters and setters
    public Vec3 getPosition() { return position; }
    public void setPosition(Vec3 position) { this.position = position; }
    public int getAge() { return age; }
    public int getMaxAge() { return maxAge; }
    public float getAgeRatio() { return maxAge > 0 ? (float) age / maxAge : 0.0f; }
    public boolean isRemoved() { return removed; }
    public void remove() { this.removed = true; }
    public float getScale() { return scale; }
    public void setScale(float scale) { this.scale = scale; }
    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }
    public float getRoll() { return roll; }
    public void setRoll(float roll) { this.roll = roll; }
    public boolean isBillboard() { return billboard; }
    public void setBillboard(boolean billboard) { this.billboard = billboard; }
    public boolean isFullBright() { return fullBright; }
    public void setFullBright(boolean fullBright) { this.fullBright = fullBright; }
    public int getRenderLayer() { return renderLayer; }
    public void setRenderLayer(int renderLayer) { this.renderLayer = renderLayer; }
    
    // Culling bounds setters
    public void setCullingRadius(float radius) {
        this.cullingRadiusX = radius;
        this.cullingRadiusY = radius;
        this.cullingRadiusZ = radius;
    }
    
    public void setCullingRadius(float x, float y, float z) {
        this.cullingRadiusX = x;
        this.cullingRadiusY = y;
        this.cullingRadiusZ = z;
    }
}