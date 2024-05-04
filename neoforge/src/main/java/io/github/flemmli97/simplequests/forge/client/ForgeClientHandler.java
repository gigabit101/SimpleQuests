package io.github.flemmli97.simplequests.forge.client;

import io.github.flemmli97.simplequests.client.ClientHandler;
import io.github.flemmli97.simplequests.network.C2SNotify;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public class ForgeClientHandler {

    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientHandler.onLogin();
    }

    public static void sendNotifyPkt() {
        Minecraft.getInstance().getConnection().send(C2SNotify.INSTANCE);
    }
}
