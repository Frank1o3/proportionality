package frank1o3.statscale.network.packets;

import frank1o3.statscale.Proportionality;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RangeSyncPayload(double minScale) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RangeSyncPayload> TYPE = new CustomPacketPayload.Type<>(
            Proportionality.id("range_sync"));

    public static final StreamCodec<FriendlyByteBuf, RangeSyncPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.minScale);
            },
            buf -> new RangeSyncPayload(buf.readDouble()));

    @Override
    public CustomPacketPayload.Type<RangeSyncPayload> type() {
        return TYPE;
    }

}
