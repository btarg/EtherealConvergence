package io.github.btarg.etherealconvergence.util;

import io.github.btarg.etherealconvergence.EtherealConvergence;
import io.github.btarg.etherealconvergence.item.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

public class LinkHelpers {

    public static void unlink(ItemStack stack, Player player) {
        ModComponents.LinkData data = stack.get(ModComponents.LINK.get());
        if (data == null) {
            return;
        }

        ItemHelpers.removeComponent(stack, player, ModComponents.LINK.get());
        String linkedPlayerName = data.name();
        player.displayClientMessage(Component.translatable("etherealconvergence.message.unlinked", linkedPlayerName).withStyle(ChatFormatting.YELLOW), true);
    }

    public static void unlinkBoth(ItemStack stack, Player player) {
        ModComponents.LinkData data = stack.get(ModComponents.LINK.get());

        if (data == null) {
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Player linkedPlayer = Objects.requireNonNull(serverPlayer.getServer()).getPlayerList().getPlayer(UUID.fromString(data.linkedUUID()));

        ArrayList<Player> message_players = new ArrayList<>();
        message_players.add(player);

        if (linkedPlayer != null) {
            ItemStack linkedStack = findLinkedItem(linkedPlayer, serverPlayer.getUUID());
            ItemHelpers.removeComponent(linkedStack, linkedPlayer, ModComponents.LINK.get());
            message_players.add(linkedPlayer);
        }

        for (var p : message_players) {
            p.displayClientMessage(Component.translatable("etherealconvergence.message.link_broken", player.getDisplayName()).withStyle(ChatFormatting.YELLOW), true);
        }

        ItemHelpers.removeComponent(stack, player, ModComponents.LINK.get());
    }

    public static ItemStack findLinkInHand(Player player) {
        if (player.getMainHandItem().is(EtherealConvergence.AKASHIC_LINK.get())) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(EtherealConvergence.AKASHIC_LINK.get())) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findLinkedItem(Player player, UUID matchingUuid) {
        String requesterId = matchingUuid.toString();
        for (ItemStack s : ItemHelpers.getAllItemStacks(player)) {
            var data = s.get(ModComponents.LINK.get());
            if (data != null && requesterId.equals(data.linkedUUID())) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }
}
