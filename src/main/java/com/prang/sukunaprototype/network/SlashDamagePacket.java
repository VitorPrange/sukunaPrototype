package com.prang.sukunaprototype.network;

import com.prang.sukunaprototype.SukunaPrototype;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.PayloadContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Client-to-server packet requesting slash damage application.
 * Server validates position, angle, damage amount, and target entities before applying damage.
 * 
 * Packet structure:
 * - slashId (UUID): Unique identifier for this slash effect
 * - position (Vec3): World position of the slash center
 * - direction (Vec3): Normalized direction vector of the slash plane
 * - length (float): Length of the slash in blocks
 * - width (float): Width/thickness of the slash in blocks
 * - damage (float): Damage amount in hearts (half-hearts = HP)
 * - hitEntities (List<UUID>): UUIDs of entities the client detected as hit
 */
public record SlashDamagePacket(
    UUID slashId,
    Vec3 position,
    Vec3 direction,
    float length,
    float width,
    float damage,
    List<UUID> hitEntities
) implements CustomPacketPayload {

    public static final Type<SlashDamagePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(SukunaPrototype.MODID, "slash_damage")
    );

    // Vec3 stream codec: x, y, z as doubles
    private static final StreamCodec<ByteBuf, Vec3> VEC3_STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE,
        Vec3::x,
        ByteBufCodecs.DOUBLE,
        Vec3::y,
        ByteBufCodecs.DOUBLE,
        Vec3::z,
        Vec3::new
    );

    // UUID stream codec using Mojang's UUIDUtil
    private static final StreamCodec<ByteBuf, UUID> UUID_STREAM_CODEC = UUIDUtil.STREAM_CODEC;

    // List<UUID> stream codec with max size limit
    private static final StreamCodec<ByteBuf, List<UUID>> UUID_LIST_STREAM_CODEC = 
        ByteBufCodecs.collection(
            java.util.ArrayList::new,
            UUID_STREAM_CODEC,
            64 // max 64 entities per slash
        );

    public static final StreamCodec<ByteBuf, SlashDamagePacket> STREAM_CODEC = StreamCodec.composite(
        UUID_STREAM_CODEC,
        SlashDamagePacket::slashId,
        VEC3_STREAM_CODEC,
        SlashDamagePacket::position,
        VEC3_STREAM_CODEC,
        SlashDamagePacket::direction,
        ByteBufCodecs.FLOAT,
        SlashDamagePacket::length,
        ByteBufCodecs.FLOAT,
        SlashDamagePacket::width,
        ByteBufCodecs.FLOAT,
        SlashDamagePacket::damage,
        UUID_LIST_STREAM_CODEC,
        SlashDamagePacket::hitEntities,
        SlashDamagePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Validates packet data on the server side.
     * @param serverLevel The server level to validate against
     * @param player The sending player
     * @param configDamageMillihearts Configured damage in millihearts (from gamerule)
     * @return true if packet is valid, false if rejected
     */
    public boolean validate(ServerLevel serverLevel, 
                            ServerPlayer player,
                            int configDamageMillihearts) {
        // Validate slash ID is not null
        if (slashId == null) {
            SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Rejected: null slashId from player {}", player.getName().getString());
            return false;
        }

        // Validate position is finite and within reasonable range of player
        if (position == null || !isFinite(position) || position.distanceTo(player.position()) > 64.0) {
            SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Rejected: invalid position {} from player {} (dist={})", 
                position, player.getName().getString(), position != null ? position.distanceTo(player.position()) : -1);
            return false;
        }

        // Validate direction is normalized (approximately)
        if (direction == null || !isFinite(direction) || Math.abs(direction.length() - 1.0) > 0.01) {
            SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Rejected: invalid direction {} from player {}", 
                direction, player.getName().getString());
            return false;
        }

        // Validate length and width are positive and within config bounds
        if (length <= 0 || length > 50.0f || width <= 0 || width > 5.0f) {
            SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Rejected: invalid length={} or width={} from player {}", 
                length, width, player.getName().getString());
            return false;
        }

        // Validate damage matches configured gamerule (within tolerance for floating point)
        float configDamageHearts = configDamageMillihearts / 1000.0f;
        if (Math.abs(damage - configDamageHearts) > 0.5f) { // 0.5 heart tolerance
            SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Rejected: damage mismatch: packet={} config={} from player {}", 
                damage, configDamageHearts, player.getName().getString());
            return false;
        }

        // Validate hit entities list size
        if (hitEntities == null || hitEntities.isEmpty() || hitEntities.size() > 64) {
            SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Rejected: invalid hitEntities size={} from player {}", 
                hitEntities != null ? hitEntities.size() : -1, player.getName().getString());
            return false;
        }

        // Validate all entity UUIDs exist and are valid targets on server
        for (UUID uuid : hitEntities) {
            if (uuid == null) {
                SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Rejected: null UUID in hitEntities from player {}", 
                    player.getName().getString());
                return false;
            }
            Entity entity = serverLevel.getEntity(uuid);
            if (entity == null || !entity.isAlive()) {
                SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Rejected: entity {} not found or dead from player {}", 
                    uuid, player.getName().getString());
                return false;
            }
            if (!(entity instanceof LivingEntity)) {
                SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Rejected: entity {} is not a LivingEntity from player {}", 
                    uuid, player.getName().getString());
                return false;
            }
            if (entity.getType().getCategory() == MobCategory.MISC) {
                SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Rejected: entity {} is MISC category from player {}", 
                    uuid, player.getName().getString());
                return false;
            }
            // Check distance from slash position to entity (rough AABB check)
            if (entity.position().distanceTo(position) > length * 0.6 + width) {
                SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Rejected: entity {} too far from slash pos from player {}", 
                    uuid, player.getName().getString());
                return false;
            }
        }

        return true;
    }

    private static boolean isFinite(Vec3 vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }

    /**
     * Server-side packet handler. Validates the packet and applies damage to valid targets.
     * Runs on the server thread via context.enqueueWork().
     */
    public static void handleOnServer(SlashDamagePacket packet, PayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player();
            if (player == null) {
                SukunaPrototype.LOGGER.warn("[SlashDamagePacket] No player in context");
                return;
            }

            ServerLevel serverLevel = player.serverLevel();
            // Get configured damage from gamerule (millihearts)
            int configDamageMillihearts = serverLevel.getGameRules().getInt(SukunaPrototype.SLASH_DAMAGE);

            // Validate packet
            if (!packet.validate(serverLevel, player, configDamageMillihearts)) {
                return; // Validation failed, logged in validate()
            }

            // Apply damage to validated entities
            float damageAmount = packet.damage() * 2.0f; // hearts -> half-hearts (HP)
            
            // Check gamerule for invulnerability frame handling
            boolean ignoreInvulnerable = serverLevel.getGameRules().getBoolean(SukunaPrototype.SLASH_IGNORE_INVULNERABLE);
            
            for (UUID uuid : packet.hitEntities()) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity instanceof LivingEntity target) {
                    // Double-check entity is still valid
                    if (!target.isAlive() || target.getType().getCategory() == MobCategory.MISC) {
                        continue;
                    }
                    
                    // Handle invulnerability frames per gamerule
                    if (ignoreInvulnerable) {
                        target.invulnerableTime = 0;
                    }
                    
                    // Use player as damage source if available, fallback to magic
                    DamageSource source = serverLevel.damageSources().playerAttack(player);
                    boolean hurtResult = target.hurt(source, damageAmount);
                    
                    SukunaPrototype.LOGGER.info("[SlashDamagePacket] Server hurt target={} uuid={} damage={} result={}", 
                        target.getName().getString(), uuid, damageAmount, hurtResult);
                } else {
                    SukunaPrototype.LOGGER.warn("[SlashDamagePacket] Entity {} not found or not living on server", uuid);
                }
            }
        });
    }
}