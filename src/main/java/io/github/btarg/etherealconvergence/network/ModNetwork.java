package io.github.btarg.etherealconvergence.network;

import io.github.btarg.etherealconvergence.EtherealConvergence;
import io.github.btarg.etherealconvergence.item.ModComponents;
import io.github.btarg.etherealconvergence.util.ItemHelpers;
import io.github.btarg.etherealconvergence.util.LinkHelpers;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

import static io.github.btarg.etherealconvergence.util.LinkHelpers.findLinkedItem;

public class ModNetwork {

    public record PlayerPressButtonOnStackPayload(int slot) implements CustomPacketPayload {
        public static final Type<PlayerPressButtonOnStackPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(EtherealConvergence.MODID, "player_slot"));

        public static final StreamCodec<ByteBuf, PlayerPressButtonOnStackPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.INT,
                PlayerPressButtonOnStackPayload::slot,
                PlayerPressButtonOnStackPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1"); // protocol version string

        registrar.playToServer(
                PlayerPressButtonOnStackPayload.TYPE,
                PlayerPressButtonOnStackPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        // Handle packet here
                        int slot = payload.slot();
                        Player player = context.player(); // we have the MC player here

                        ItemStack stack = player.getInventory().getItem(slot);
                        LinkHelpers.unlinkBoth(stack, player);

                    });
                }
        );
    }

}
