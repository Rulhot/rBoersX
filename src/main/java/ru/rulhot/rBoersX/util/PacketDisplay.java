package ru.rulhot.rBoersX.util;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import lombok.experimental.UtilityClass;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

@UtilityClass
public class PacketDisplay {

    private final int ENTITY_FLAGS = 0;
    private final int TRANSLATION = 11;
    private final int SCALE = 12;
    private final int BRIGHTNESS = 16;
    private final int VIEW_RANGE = 17;
    private final int GLOW_COLOR = 22;
    private final int BLOCK_STATE = 23;

    private final byte FLAG_GLOWING = 0x40;
    private final int BLOCK_LIGHT_SHIFT = 4;
    private final int SKY_LIGHT_SHIFT = 20;

    public int spawn(@NotNull User viewer, @NotNull Location location, @NotNull List<EntityData<?>> state) {
        int entityId = SpigotReflectionUtil.generateEntityId();
        viewer.sendPacket(new WrapperPlayServerSpawnEntity(
                entityId,
                UUID.randomUUID(),
                EntityTypes.BLOCK_DISPLAY,
                new com.github.retrooper.packetevents.protocol.world.Location(location.getX(), location.getY(),
                        location.getZ(), 0.0F, 0.0F),
                0.0F,
                0,
                null));
        viewer.sendPacket(new WrapperPlayServerEntityMetadata(entityId, state));
        return entityId;
    }

    public void destroy(@NotNull User viewer, int @NotNull [] entityIds) {
        viewer.sendPacket(new WrapperPlayServerDestroyEntities(entityIds));
    }

    public @NotNull EntityData<?> glowing(boolean glowing) {
        return new EntityData<>(ENTITY_FLAGS, EntityDataTypes.BYTE, glowing ? FLAG_GLOWING : (byte) 0);
    }

    public @NotNull EntityData<?> translation(float x, float y, float z) {
        return new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(x, y, z));
    }

    public @NotNull EntityData<?> scale(float x, float y, float z) {
        return new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, new Vector3f(x, y, z));
    }

    public @NotNull EntityData<?> brightness(int blockLight, int skyLight) {
        return new EntityData<>(BRIGHTNESS, EntityDataTypes.INT,
                blockLight << BLOCK_LIGHT_SHIFT | skyLight << SKY_LIGHT_SHIFT);
    }

    public @NotNull EntityData<?> viewRange(float range) {
        return new EntityData<>(VIEW_RANGE, EntityDataTypes.FLOAT, range);
    }

    public @NotNull EntityData<?> glowColor(@NotNull Color color) {
        return new EntityData<>(GLOW_COLOR, EntityDataTypes.INT, color.asRGB());
    }

    public @NotNull EntityData<?> block(@NotNull BlockData block) {
        return new EntityData<>(BLOCK_STATE, EntityDataTypes.BLOCK_STATE,
                SpigotConversionUtil.fromBukkitBlockData(block).getGlobalId());
    }
}
