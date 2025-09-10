package io.github.btarg.etherealconvergence.util;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.Stream;

public class ItemHelpers {
    public static <T> void removeComponent(ItemStack stack, Player player, DataComponentType<T> component) {
        if (player.isCreative()) {
            ItemStack newStack = stack.copy();

            if (!player.level().isClientSide) {
                newStack.remove(component);
            }
            int slot = player.getInventory().findSlotMatchingItem(stack);

            if (slot > -1) {
                player.getInventory().setItem(slot, newStack);
            }

        } else if (!player.level().isClientSide) {
            stack.remove(component);
        }
    }

    /**
     * Helper function to set a component on an ItemStack.
     * In creative mode, replaces the ItemStack entirely to ensure proper syncing.
     */
    public static <T> void setComponent(ItemStack stack, Player player, DataComponentType<T> component, T value) {
        if (player.isCreative()) {
            ItemStack newStack = stack.copy();

            // Only try to set components server-side
            if (!player.level().isClientSide) {
                newStack.set(component, value);
            }
            // Update the item on both sides
            int slot = player.getInventory().findSlotMatchingItem(stack);
            player.getInventory().setItem(slot, newStack);

        } else if (!player.level().isClientSide) {
            stack.set(component, value);
        }
    }

    public static List<ItemStack> getAllItemStacks(Player player) {
        return Stream.concat(
                player.getInventory().items.stream().filter(s -> !s.isEmpty()),
                player.getInventory().offhand.stream().filter(s -> !s.isEmpty())
        ).toList();
    }
}
