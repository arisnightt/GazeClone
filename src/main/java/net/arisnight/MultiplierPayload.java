package net.arisnight;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MultiplierPayload(int advancements, boolean looking) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MultiplierPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GazeClone.MOD_ID, "multiplier"));

    public static final StreamCodec<ByteBuf, MultiplierPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MultiplierPayload::advancements,
            ByteBufCodecs.BOOL, MultiplierPayload::looking,
            MultiplierPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}