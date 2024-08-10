package io.github.flemmli97.simplequests.network;

import io.github.flemmli97.simplequests.SimpleQuests;
import io.github.flemmli97.simplequests.player.PlayerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Packet to notify the server this player has SimpleQuests on the client
 */
public class C2SNotify implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SNotify> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "c2s_notify"));

    public static final C2SNotify INSTANCE = new C2SNotify();

    public static final StreamCodec<FriendlyByteBuf, C2SNotify> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(C2SNotify pkt, ServerPlayer sender) {
        if (sender != null) {
            PlayerData.get(sender).hasClient = true;
        }
    }

    private C2SNotify() {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
