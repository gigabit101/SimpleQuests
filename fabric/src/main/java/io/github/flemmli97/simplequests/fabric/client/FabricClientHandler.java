package io.github.flemmli97.simplequests.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class FabricClientHandler {

    public static void sendNotificationPkt(CustomPacketPayload pkt) {
        ClientPlayNetworking.send(pkt);
    }
}
