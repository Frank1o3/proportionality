package frank1o3.statscale.network.packets;

import frank1o3.statscale.Proportionality;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * @param found false if the queried player name did not resolve to a known
 *              UUID (never played / typo); the other fields are meaningless
 *              in that case and the admin screen should show an error.
 */
public record AdminScaleInfoPayload(
        boolean found, UUID target, String name, double scale, double serverMaxScale, boolean frozen)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminScaleInfoPayload> TYPE = new CustomPacketPayload.Type<>(
            Proportionality.id("admin_scale_info"));

    public static final StreamCodec<FriendlyByteBuf, AdminScaleInfoPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.found());
                buf.writeUUID(payload.target());
                buf.writeUtf(payload.name());
                buf.writeDouble(payload.scale());
                buf.writeDouble(payload.serverMaxScale());
                buf.writeBoolean(payload.frozen());
            },
            buf -> new AdminScaleInfoPayload(
                    buf.readBoolean(), buf.readUUID(), buf.readUtf(), buf.readDouble(), buf.readDouble(),
                    buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<AdminScaleInfoPayload> type() {
        return TYPE;
    }
}