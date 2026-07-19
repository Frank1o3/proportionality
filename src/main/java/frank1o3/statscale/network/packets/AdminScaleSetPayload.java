package frank1o3.statscale.network.packets;

import frank1o3.statscale.Proportionality;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record AdminScaleSetPayload(UUID target, double scale, boolean frozen) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminScaleSetPayload> TYPE = new CustomPacketPayload.Type<>(
            Proportionality.id("admin_scale_set"));

    public static final StreamCodec<FriendlyByteBuf, AdminScaleSetPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.target());
                buf.writeDouble(payload.scale());
                buf.writeBoolean(payload.frozen());
            },
            buf -> new AdminScaleSetPayload(buf.readUUID(), buf.readDouble(), buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<AdminScaleSetPayload> type() {
        return TYPE;
    }
}