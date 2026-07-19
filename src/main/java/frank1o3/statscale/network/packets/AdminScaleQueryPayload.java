package frank1o3.statscale.network.packets;

import frank1o3.statscale.Proportionality;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AdminScaleQueryPayload(String targetName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminScaleQueryPayload> TYPE = new CustomPacketPayload.Type<>(
            Proportionality.id("admin_scale_query"));

    public static final StreamCodec<FriendlyByteBuf, AdminScaleQueryPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.targetName()),
            buf -> new AdminScaleQueryPayload(buf.readUtf()));

    @Override
    public CustomPacketPayload.Type<AdminScaleQueryPayload> type() {
        return TYPE;
    }
}