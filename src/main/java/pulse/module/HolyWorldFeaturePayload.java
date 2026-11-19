package pulse.module;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** UTF-8 JSON payload for HolyWorld's liteapi:feature-control channel. */
public record HolyWorldFeaturePayload(byte[] data) implements CustomPayload {
   public static final Id<HolyWorldFeaturePayload> ID = new Id<>(Identifier.of("liteapi", "feature-control"));
   public static final PacketCodec<RegistryByteBuf, HolyWorldFeaturePayload> CODEC = PacketCodec.of(
      (payload, buffer) -> buffer.writeBytes(payload.data()),
      buffer -> {
         byte[] data = new byte[buffer.readableBytes()];
         buffer.readBytes(data);
         return new HolyWorldFeaturePayload(data);
      }
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
