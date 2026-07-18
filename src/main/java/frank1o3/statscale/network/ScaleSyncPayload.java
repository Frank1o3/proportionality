package frank1o3.statscale.network;

import frank1o3.statscale.Proportionality;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → Client packet.
 *
 * <p>Sent in two situations:
 * <ol>
 *   <li>When a player joins the server – delivers their saved scale and the server's maximum
 *       so the client GUI can initialise its slider range correctly.</li>
 *   <li>After the server successfully applies a scale change – confirms the committed value
 *       back to the client so the GUI stays in sync even if the server clamped the request.</li>
 * </ol>
 *
 * @param currentScale The player's active (and now persisted) scale value.
 * @param serverMaxScale The maximum scale this server permits. Used to cap the slider range
 *                       on the client so players can never request a value the server rejects.
 */
public record ScaleSyncPayload(float currentScale, float serverMaxScale) implements CustomPacketPayload {

    /** Unique identifier registered on both sides during mod initialisation. */
    public static final CustomPacketPayload.Type<ScaleSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(Proportionality.id("scale_sync"));

    /** Wire codec – two floats, 8 bytes total. */
    public static final StreamCodec<FriendlyByteBuf, ScaleSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeFloat(payload.currentScale);
                        buf.writeFloat(payload.serverMaxScale);
                    },
                    buf -> new ScaleSyncPayload(buf.readFloat(), buf.readFloat())
            );

    @Override
    public CustomPacketPayload.Type<ScaleSyncPayload> type() {
        return TYPE;
    }
}
