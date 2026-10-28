package egg.client.liteapi;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LiteApiPayload(byte[] data) implements CustomPayload {
    public static final CustomPayload.Id<LiteApiPayload> ID = new CustomPayload.Id<>(Identifier.of("liteapi", "feature-control"));

    public static final PacketCodec<PacketByteBuf, LiteApiPayload> CODEC = CustomPayload.codecOf(
            (value, buf) -> buf.writeBytes(value.data()),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new LiteApiPayload(bytes);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
