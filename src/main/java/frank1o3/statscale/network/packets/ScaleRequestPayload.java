package frank1o3.statscale.network.packets;

import frank1o3.statscale.Proportionality;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → Server packet.
 *
 * <p>
 * Sent when the player commits a new scale value from the
 * {@link frank1o3.statscale.client.gui.screen.ScaleScreen}.
 * The server validates the value against the server-configured maximum, applies
 * it through
 * the existing attribute pipeline, and persists it via
 * {@link frank1o3.statscale.server.storage.ScaleStorage}.
 *
 * @param scale The requested logical scale value (e.g. 1.0 – 16.0). The server
 *              will clamp
 *              this to its own configured maximum, so the client never needs to
 *              enforce the cap.
 */
public record ScaleRequestPayload(double scale) implements CustomPacketPayload {

        /** Unique identifier registered on both sides during mod initialisation. */
        public static final CustomPacketPayload.Type<ScaleRequestPayload> TYPE = new CustomPacketPayload.Type<>(
                        Proportionality.id("scale_request"));

        /**
         * Codec used by Fabric's packet registration to serialise / deserialise this
         * payload
         * over the network wire. Floats are 4 bytes; the packet is intentionally
         * minimal.
         */
        public static final StreamCodec<FriendlyByteBuf, ScaleRequestPayload> CODEC = StreamCodec.of(
                        (buf, payload) -> buf.writeDouble(payload.scale),
                        buf -> new ScaleRequestPayload(buf.readDouble()));

        @Override
        public CustomPacketPayload.Type<ScaleRequestPayload> type() {
                return TYPE;
        }
}
