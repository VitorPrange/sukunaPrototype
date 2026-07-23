package com.prang.sukunaprototype.network;

import com.prang.sukunaprototype.SukunaPrototype;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers custom packet payload handlers for client-server communication.
 * Runs on the mod event bus (both client and server).
 */
@EventBusSubscriber(modid = SukunaPrototype.MODID)
public class NetworkHandler {

    /**
     * Registers payload handlers for the play phase.
     * Called on both client and server during mod loading.
     */
    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(SukunaPrototype.MODID + ":1");

        // Register client-to-server packet handler
        // Server-side handler runs on the server thread
        registrar.playToServer(
            SlashDamagePacket.TYPE,
            SlashDamagePacket.STREAM_CODEC,
            SlashDamagePacket::handleOnServer
        );

        SukunaPrototype.LOGGER.info("[NetworkHandler] Registered SlashDamagePacket handler");
    }
}