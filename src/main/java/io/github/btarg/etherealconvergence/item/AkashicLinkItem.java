package io.github.btarg.etherealconvergence.item;

import io.github.btarg.etherealconvergence.EtherealConvergence;
import io.github.btarg.etherealconvergence.config.EtherealConvergenceConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class AkashicLinkItem extends Item {

    public AkashicLinkItem(Properties props) {
        super(props);
    }

    /**
     * Helper function to set a component on an ItemStack.
     * In creative mode, replaces the ItemStack entirely to ensure proper syncing.
     */
    private static <T> void setComponent(ItemStack stack, Player player, DataComponentType<T> component, T value) {
        if (player.isCreative()) {
            ItemStack newStack = stack.copy();

            if (!player.level().isClientSide) {
                newStack.set(component, value);
            }
            int slot = player.getInventory().findSlotMatchingItem(stack);
            player.getInventory().setItem(slot, newStack);

        } else if (!player.level().isClientSide) {
            stack.set(component, value);
        }
    }

    private static <T> void removeComponent(ItemStack stack, Player player, DataComponentType<T> component) {
        if (player.isCreative()) {
            ItemStack newStack = stack.copy();

            if (!player.level().isClientSide) {
                newStack.remove(component);
            }
            int slot = player.getInventory().findSlotMatchingItem(stack);
            player.getInventory().setItem(slot, newStack);

        } else if (!player.level().isClientSide) {
            stack.remove(component);
        }
    }

    private static ItemStack findLinkInHand(Player player) {
        if (player.getMainHandItem().is(EtherealConvergence.AKASHIC_LINK.get())) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(EtherealConvergence.AKASHIC_LINK.get())) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findLinkedItem(Player player, UUID requesterUUID) {
        String requesterId = requesterUUID.toString();
        for (ItemStack s : allStacks(player)) {
            var data = s.get(ModComponents.LINK.get());
            if (data != null && requesterId.equals(data.linkedUUID())) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static List<ItemStack> allStacks(Player player) {
        return Stream.concat(
                player.getInventory().items.stream().filter(s -> !s.isEmpty()),
                player.getInventory().offhand.stream().filter(s -> !s.isEmpty())
        ).toList();
    }

    private static long getCurrentTick() {
        // For now, convert current time to approximate ticks (50ms per tick)
        return System.currentTimeMillis() / 50;
    }

    private static void checkAndRemoveSelfLink(ItemStack stack, Player player) {
        ModComponents.LinkData link = stack.get(ModComponents.LINK.get());

        if (link != null && link.linkedUUID().equals(player.getUUID().toString())) {
            removeComponent(stack, player, ModComponents.LINK.get());
        }
    }

    private boolean hasValidRequestTicksClient(ItemStack stack, long currentTick) {
        ModComponents.RequestData req = stack.get(ModComponents.INCOMING_REQUEST.get());
        if (req == null) return false;
        return currentTick - req.time() <= EtherealConvergenceConfig.getRequestTimeoutTicks();
    }

    @Override
    public boolean isEnchantable(@Nonnull ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return hasValidRequestTicksClient(stack, getCurrentTick());
    }

    @Override
    @Nonnull
    public Component getName(@Nonnull ItemStack stack) {
        boolean hasIncomingRequest = hasValidRequestTicksClient(stack, getCurrentTick());
        ModComponents.LinkData link = stack.get(ModComponents.LINK.get());

        ChatFormatting style;
        if (link == null) {
            style = ChatFormatting.WHITE;
        } else {
            style = hasIncomingRequest ? ChatFormatting.DARK_PURPLE : ChatFormatting.AQUA;
        }

        return Component.translatable("item.etherealconvergence.akashic_link").withStyle(style);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context, @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag tooltipFlag) {

        if (hasValidRequestTicksClient(stack, getCurrentTick())) {
            tooltipComponents.add(Component.translatable("item.etherealconvergence.incoming").withStyle(ChatFormatting.DARK_PURPLE));
        }

        ModComponents.LinkData link = stack.get(ModComponents.LINK.get());
        boolean noLink = link == null || link.linkedUUID().isEmpty();
        MutableComponent linkName = noLink ?
                Component.translatable("item.etherealconvergence.unlinked")
                : Component.translatable("etherealconvergence.message.linked", link.name());

        tooltipComponents.add(linkName.withStyle(ChatFormatting.GRAY));

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    // Right-click entity to bind/unbind
    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player, @Nonnull LivingEntity entity, @Nonnull InteractionHand hand) {
        if (!(entity instanceof Player target))
            return InteractionResult.PASS;

        // Check for self-link and remove it first
        checkAndRemoveSelfLink(stack, player);

//        // Handle shift-click for request cancellation/unlinking
//        if (player.isShiftKeyDown()) {
//            if (player instanceof ServerPlayer serverPlayer) {
//                removeComponent(stack, player, ModComponents.LINK.get());
//                sendStatusMessage(serverPlayer, Component.translatable("etherealconvergence.message.unlinked", ""), ChatFormatting.YELLOW);
//            }
//
//            return InteractionResult.sidedSuccess(player.level().isClientSide);
//        }

        ModComponents.LinkData currentLink = stack.get(ModComponents.LINK.get());
        if (currentLink == null) {
            return createLink(stack, player, target);
        }

        // Handle Unlink
        if (currentLink.linkedUUID().equals(target.getUUID().toString())) {
            removeComponent(stack, player, ModComponents.LINK.get());
            sendStatusMessage(player, Component.translatable("etherealconvergence.message.unlinked", target.getGameProfile().getName()), ChatFormatting.YELLOW);

            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }

        // Handle Relink
        ModComponents.ConfirmationData confirmation = stack.get(ModComponents.CONFIRMATION.get());
        if (confirmation != null && confirmation.targetUUID().equals(target.getUUID().toString()) && (System.currentTimeMillis() - confirmation.time() <= EtherealConvergenceConfig.getConfirmationTimeout())) {
            removeComponent(stack, player, ModComponents.CONFIRMATION.get());
            return createLink(stack, player, target);
        } else {
            setComponent(stack, player, ModComponents.CONFIRMATION.get(), new ModComponents.ConfirmationData(target.getUUID().toString(), System.currentTimeMillis()));
            sendStatusMessage(player, Component.translatable("etherealconvergence.message.confirm_relink", target.getGameProfile().getName()), ChatFormatting.YELLOW);

            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }

    }


    private InteractionResult createLink(ItemStack playerItemStack, Player player, Player target) {
        ItemStack targetLink = findLinkInHand(target);

        if (targetLink.isEmpty()) {
            sendStatusMessage(player, Component.translatable("etherealconvergence.message.no_valid_link"), ChatFormatting.RED);
            return InteractionResult.FAIL;
        }

        // Player
        setComponent(playerItemStack, player, ModComponents.LINK.get(), new ModComponents.LinkData(target.getUUID().toString(), target.getGameProfile().getName()));
        sendStatusMessage(player, Component.translatable("etherealconvergence.message.linked", target.getGameProfile().getName()), ChatFormatting.GREEN);

        // Target
        setComponent(targetLink, target, ModComponents.LINK.get(), new ModComponents.LinkData(player.getUUID().toString(), player.getGameProfile().getName()));
        sendStatusMessage(target, Component.translatable("etherealconvergence.message.linked", player.getGameProfile().getName()), ChatFormatting.GREEN);


        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    // Right-click air to send TP request
    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Check for self-link and remove it first
        checkAndRemoveSelfLink(stack, player);

//        if (player.isShiftKeyDown()) {
//            return handleShiftClick(stack, player);
//        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        ModComponents.RequestData req = stack.get(ModComponents.INCOMING_REQUEST.get());

        // If we don't have a request then send one
        if (!checkRequestValid(req)) return sendRequest(stack, player);

        // Bring the requesting player here
        if (req.type().equals(ModComponents.ERequestType.BRING_REQUESTER_HERE)) {
            return acceptTeleportHereRequest(req, serverPlayer, stack, serverPlayer.serverLevel(), player.blockPosition())
                    ? InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide)
                    : InteractionResultHolder.fail(stack);
        }

        // Go to the requesting player
        return acceptTeleportToRequest(req, player, stack, level)
                ? InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide)
                : InteractionResultHolder.fail(stack);

    }

    // Right-click block to accept request and teleport the requester to that location
    @Override
    @Nonnull
    public InteractionResult useOn(@Nonnull UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();

        // Check for self-link and remove it first
        checkAndRemoveSelfLink(stack, player);

        ModComponents.RequestData req = stack.get(ModComponents.INCOMING_REQUEST.get());
        if (req == null) return InteractionResult.PASS;

        // Bring the requesting player here
        if (req.type().equals(ModComponents.ERequestType.BRING_REQUESTER_HERE)) {

            // Teleport on top of the block if there is space, otherwise act like a Spawn Egg
            BlockPos clickedPos = ctx.getClickedPos();
            BlockPos abovePos = clickedPos.above();
            BlockPos newPos = level.isEmptyBlock(abovePos) ? abovePos : (level.getBlockState(clickedPos).getCollisionShape(level, clickedPos).isEmpty()
                    ? clickedPos : clickedPos.relative(ctx.getClickedFace()));
            if (!Level.isInSpawnableBounds(newPos)) return InteractionResult.FAIL;

            return acceptTeleportHereRequest(req, player, stack, level, newPos)
                    ? InteractionResult.sidedSuccess(player.level().isClientSide)
                    : InteractionResult.FAIL;

        }

        // Go to the requesting player
        return acceptTeleportToRequest(req, player, stack, level)
                ? InteractionResult.sidedSuccess(player.level().isClientSide)
                : InteractionResult.FAIL;
    }

    private void handleOfflineFail(ItemStack linkItemStack, Player player) {
        removeComponent(linkItemStack, player, ModComponents.INCOMING_REQUEST.get());
        sendStatusMessage(player, Component.translatable("etherealconvergence.message.requester_offline"), ChatFormatting.RED);
    }

    private boolean acceptTeleportToRequest(ModComponents.RequestData requestData, Player acceptingPlayer, ItemStack linkItemStack, Level level) {

        if (!(level instanceof ServerLevel serverLevel)) {
            handleOfflineFail(linkItemStack, acceptingPlayer);
            return false;
        }

        ServerPlayer requester = serverLevel.getServer().getPlayerList().getPlayer(UUID.fromString(requestData.requester()));
        if (requester == null) {
            handleOfflineFail(linkItemStack, acceptingPlayer);
            return false;
        }

        if (requester.level() != serverLevel) {
            handleOfflineFail(linkItemStack, acceptingPlayer);
            return false;
        }

        // Cast accepting player to ServerPlayer for teleportation
        if (!(acceptingPlayer instanceof ServerPlayer serverAcceptingPlayer)) {
            removeComponent(linkItemStack, acceptingPlayer, ModComponents.INCOMING_REQUEST.get());
            return false;
        }

        sendStatusMessage(requester, Component.translatable("etherealconvergence.message.request_accepted"), ChatFormatting.GREEN);
        sendStatusMessage(acceptingPlayer, Component.translatable("etherealconvergence.message.tp_success_to", requester.getGameProfile().getName()), ChatFormatting.GREEN);

        // Teleport the accepting player to the requester's location
        BlockPos requesterPos = requester.blockPosition();

        // Empty set provided to avoid interpolation when teleporting
        serverAcceptingPlayer.teleportTo(serverLevel, requesterPos.getX(), requesterPos.getY(), requesterPos.getZ(), Set.of(), serverAcceptingPlayer.getYRot(), serverAcceptingPlayer.getXRot());
        serverAcceptingPlayer.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, requester.getEyePosition());
        serverLevel.playSound(serverAcceptingPlayer, requesterPos, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        removeComponent(linkItemStack, acceptingPlayer, ModComponents.INCOMING_REQUEST.get());

        ItemStack requesterLinkStack = findLinkedItem(requester, acceptingPlayer.getUUID());
        ItemStack acceptingLinkStack = findLinkInHand(acceptingPlayer);
        if (!acceptingLinkStack.isEmpty()) {
            acceptingLinkStack.getItem().damageItem(acceptingLinkStack, 1, serverAcceptingPlayer, p -> {
                // Sever the link for the requester
                removeComponent(requesterLinkStack, requester, ModComponents.LINK.get());
                sendStatusMessage(requester, Component.translatable("etherealconvergence.message.link_broken", acceptingPlayer.getGameProfile().getName()), ChatFormatting.RED);
            });
        }
        serverAcceptingPlayer.getCooldowns().addCooldown(this, EtherealConvergenceConfig.getTeleportCooldownTicks());

        return true;
    }

    private InteractionResultHolder<ItemStack> handleShiftClick(ItemStack stack, Player player) {
        if (!player.level().isClientSide) {
            ModComponents.RequestData req = stack.get(ModComponents.INCOMING_REQUEST.get());

            // Cancel any active requests first
            if (req != null) {
                ServerPlayer target = Objects.requireNonNull(player.getServer()).getPlayerList().getPlayer(UUID.fromString(req.requester()));
                if (target != null) {
                    sendStatusMessage(target, Component.translatable("etherealconvergence.message.request_denied"), ChatFormatting.YELLOW);
                }

                sendStatusMessage(player, Component.translatable("etherealconvergence.message.request_denied"), ChatFormatting.YELLOW);

                removeComponent(stack, player, ModComponents.INCOMING_REQUEST.get());

                return InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide);
            }
            // If no active request, clear the current link
            if (stack.has(ModComponents.LINK.get())) {
                ModComponents.LinkData data = stack.get(ModComponents.LINK.get());

                removeComponent(stack, player, ModComponents.LINK.get());

                String linkedPlayerName = (data != null && !data.name().isEmpty()) ? data.name() : "???";
                sendStatusMessage(player, Component.translatable("etherealconvergence.message.unlinked", linkedPlayerName), ChatFormatting.YELLOW);

                return InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide);
    }

    private InteractionResultHolder<ItemStack> sendRequest(ItemStack linkItemStack, Player player) {
        ModComponents.LinkData link = linkItemStack.get(ModComponents.LINK.get());

        if (link == null) {
            sendStatusMessage(player, Component.translatable("etherealconvergence.message.not_linked"), ChatFormatting.RED);
            return InteractionResultHolder.fail(linkItemStack);
        }

        // Check if server is null to prevent NPE
        if (player.getServer() == null) {
            sendStatusMessage(player, Component.translatable("etherealconvergence.message.not_linked"), ChatFormatting.RED);
            return InteractionResultHolder.fail(linkItemStack);
        }

        ServerPlayer target = player.getServer().getPlayerList().getPlayer(UUID.fromString(link.linkedUUID()));
        if (target == null) {
            sendStatusMessage(player, Component.translatable("etherealconvergence.message.target_offline", link.name()), ChatFormatting.RED);
            return InteractionResultHolder.fail(linkItemStack);
        }

        ItemStack targetLink = findLinkedItem(target, player.getUUID());

        if (targetLink.isEmpty()) {
            sendStatusMessage(player, Component.translatable("etherealconvergence.message.no_valid_link"), ChatFormatting.RED);
            removeComponent(linkItemStack, player, ModComponents.LINK.get());
            return InteractionResultHolder.fail(linkItemStack);
        }


        ModComponents.RequestData requestData = targetLink.get(ModComponents.INCOMING_REQUEST.get());
        if (checkRequestValid(requestData)) {
            sendStatusMessage(player, Component.translatable("etherealconvergence.message.request_pending"), ChatFormatting.YELLOW);
            return InteractionResultHolder.fail(linkItemStack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(linkItemStack);
        }

        // Adds a request component to the other player (target)'s link item.
        // If we are not holding shift, we want to teleport to them, otherwise we want them to come to us.
        ModComponents.ERequestType requestType = player.isShiftKeyDown() ?
                ModComponents.ERequestType.TP_TO_REQUESTER
                : ModComponents.ERequestType.BRING_REQUESTER_HERE;

        setComponent(targetLink, target, ModComponents.INCOMING_REQUEST.get(), new ModComponents.RequestData(player.getUUID().toString(), getCurrentTick(), requestType));

        sendStatusMessage(serverPlayer, Component.translatable("etherealconvergence.message.request_sent", link.name()), ChatFormatting.GREEN);

        if (requestType.equals(ModComponents.ERequestType.BRING_REQUESTER_HERE)) {
            sendStatusMessage(target, Component.translatable("etherealconvergence.message.request_incoming", player.getGameProfile().getName()), ChatFormatting.AQUA);
        } else {
            sendStatusMessage(target, Component.translatable("etherealconvergence.message.request_incoming_tp", player.getGameProfile().getName()), ChatFormatting.AQUA);
        }


        serverPlayer.getCooldowns().addCooldown(linkItemStack.getItem(), EtherealConvergenceConfig.getRequestTimeoutTicks());

        return InteractionResultHolder.success(linkItemStack);
    }


    private boolean checkRequestValid(ModComponents.RequestData requestData) {
        if (requestData == null) return false;
        return getCurrentTick() - requestData.time() <= EtherealConvergenceConfig.getRequestTimeoutTicks();
    }

    private boolean acceptTeleportHereRequest(ModComponents.RequestData requestData, Player acceptingPlayer, ItemStack linkItemStack, Level level, BlockPos pos) {

        if (!(level instanceof ServerLevel serverLevel)) {
            sendStatusMessage(acceptingPlayer, Component.translatable("etherealconvergence.message.requester_offline"), ChatFormatting.RED);
            removeComponent(linkItemStack, acceptingPlayer, ModComponents.INCOMING_REQUEST.get());
            return false;
        }

        ServerPlayer requester = serverLevel.getServer().getPlayerList().getPlayer(UUID.fromString(requestData.requester()));
        if (requester == null) {
            removeComponent(linkItemStack, acceptingPlayer, ModComponents.INCOMING_REQUEST.get());
            sendStatusMessage(acceptingPlayer, Component.translatable("etherealconvergence.message.requester_offline"), ChatFormatting.RED);
            return false;
        }

        sendStatusMessage(requester, Component.translatable("etherealconvergence.message.tp_success_to", acceptingPlayer.getGameProfile().getName()), ChatFormatting.GREEN);
        sendStatusMessage(acceptingPlayer, Component.translatable("etherealconvergence.message.request_accepted"), ChatFormatting.GREEN);

        // Empty set provided to avoid interpolation when teleporting
        requester.teleportTo(serverLevel, pos.getX(), pos.getY(), pos.getZ(), Set.of(), requester.getYRot(), requester.getXRot());
        requester.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, acceptingPlayer.getEyePosition());
        level.playSound(requester, pos, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        removeComponent(linkItemStack, acceptingPlayer, ModComponents.INCOMING_REQUEST.get());

        ItemStack requesterLinkStack = findLinkedItem(requester, acceptingPlayer.getUUID());
        ItemStack acceptingLinkStack = findLinkInHand(acceptingPlayer);
        if (!requesterLinkStack.isEmpty()) {
            requesterLinkStack.getItem().damageItem(requesterLinkStack, 1, requester, p -> {
                // Sever the link for the acceptingPlayer
                removeComponent(acceptingLinkStack, acceptingPlayer, ModComponents.LINK.get());
                sendStatusMessage(acceptingPlayer, Component.translatable("etherealconvergence.message.link_broken", requester.getGameProfile().getName()), ChatFormatting.RED);

            });
        }
        requester.getCooldowns().addCooldown(this, EtherealConvergenceConfig.getTeleportCooldownTicks());

        return true;
    }


    @Override
    public boolean isValidRepairItem(@Nonnull ItemStack stack, @Nonnull ItemStack repairCandidate) {
        return repairCandidate.is(Items.ENDER_PEARL);
    }

    private void sendStatusMessage(Player player, Component message, ChatFormatting color) {
        player.displayClientMessage(message.copy().withStyle(color), true);
    }
}
