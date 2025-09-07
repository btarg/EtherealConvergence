package io.github.btarg.etherealconvergence.item;

import io.github.btarg.etherealconvergence.EtherealConvergence;
import io.github.btarg.etherealconvergence.config.EtherealConvergenceConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public class AkashicLinkItem extends Item {

    public AkashicLinkItem(Properties props) {
        super(props);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
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

    private static boolean checkAndRemoveSelfLink(ItemStack stack, ServerPlayer player) {
        ModComponents.LinkData link = stack.get(ModComponents.LINK.get());
        if (link != null && link.linkedUUID().equals(player.getUUID().toString())) {
            stack.remove(ModComponents.LINK.get());
            return true;
        }
        return false;
    }

    private static long getCurrentTick() {
        // For now, convert current time to approximate ticks (50ms per tick)
        return System.currentTimeMillis() / 50;
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return ModComponents.hasValidRequestTicks(stack, getCurrentTick());
    }

    @Override
    @Nonnull
    public Component getName(@Nonnull ItemStack stack) {
        boolean hasIncomingRequest = ModComponents.hasValidRequestTicks(stack, getCurrentTick());
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

        if (ModComponents.hasValidRequestTicks(stack, getCurrentTick())) {
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
        if (!(entity instanceof ServerPlayer target) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }

        // Check for self-link and remove it first
        checkAndRemoveSelfLink(stack, serverPlayer);

        // Handle shift-click for request cancellation/unlinking
        if (serverPlayer.isShiftKeyDown()) {

            stack.remove(ModComponents.LINK.get());
            sendStatusMessage(serverPlayer, Component.translatable("etherealconvergence.message.link_cleared"), ChatFormatting.YELLOW);
            return InteractionResult.sidedSuccess(serverPlayer.level().isClientSide);

        }

        ModComponents.LinkData currentLink = stack.get(ModComponents.LINK.get());
        if (currentLink == null) {
            return createLink(stack, serverPlayer, target);
        }

        if (currentLink.linkedUUID().equals(target.getUUID().toString())) {
            return handleUnlink(stack, serverPlayer, target);
        } else {
            return handleRelink(stack, serverPlayer, target);
        }
    }

    // Right-click air to send TP request
    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        // Check for self-link and remove it first
        if (checkAndRemoveSelfLink(stack, serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide);
        }

        if (serverPlayer.isShiftKeyDown()) {
            return handleShiftClick(stack, serverPlayer);
        }

        if (ModComponents.hasValidRequestTicks(stack, getCurrentTick())) {
            return acceptRequest(serverPlayer, stack, hand, (ServerLevel) level, player.blockPosition())
                    ? InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide)
                    : InteractionResultHolder.fail(stack);
        }

        return sendRequest(stack, serverPlayer);
    }

    // Right-click block to accept request and teleport the requester to that location
    @Override
    @Nonnull
    public InteractionResult useOn(@Nonnull UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel serverLevel) || !(ctx.getPlayer() instanceof ServerPlayer target)) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = ctx.getItemInHand();

        // Check for self-link and remove it first
        if (checkAndRemoveSelfLink(stack, target)) {
            return InteractionResult.SUCCESS;
        }

        // Spawn egg style teleport location
        BlockPos clickedPos = ctx.getClickedPos();
        BlockPos newPos = serverLevel.getBlockState(clickedPos).getCollisionShape(serverLevel, clickedPos).isEmpty()
                ? clickedPos : clickedPos.relative(ctx.getClickedFace());

        return acceptRequest(target, ctx.getItemInHand(), ctx.getHand(), serverLevel, newPos)
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL;
    }

    private InteractionResultHolder<ItemStack> handleShiftClick(ItemStack stack, ServerPlayer player) {
        // Cancel any active requests first
        if (ModComponents.hasValidRequestTicks(stack, getCurrentTick())) {
            ModComponents.RequestData req = stack.get(ModComponents.REQUEST.get());
            ServerPlayer target = Objects.requireNonNull(player.getServer()).getPlayerList().getPlayer(UUID.fromString(req.requester()));
            if (target != null) {
                sendStatusMessage(target, Component.translatable("etherealconvergence.message.request_denied"), ChatFormatting.YELLOW);
            }

            sendStatusMessage(player, Component.translatable("etherealconvergence.message.request_denied"), ChatFormatting.YELLOW);

            stack.remove(ModComponents.REQUEST.get());

            return InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide);
        }
        // If no active request, clear the current link
        if (stack.has(ModComponents.LINK.get())) {
            ModComponents.LinkData data = stack.get(ModComponents.LINK.get());
            stack.remove(ModComponents.LINK.get());
            String linkedPlayerName = (data != null && !data.name().isEmpty()) ? data.name() : "???";

            sendStatusMessage(player, Component.translatable("etherealconvergence.message.unlinked", linkedPlayerName), ChatFormatting.YELLOW);
            return InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide);
        }
        return InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide);
    }

    private InteractionResult handleUnlink(ItemStack stack, ServerPlayer player, ServerPlayer target) {
        stack.remove(ModComponents.LINK.get());
        sendStatusMessage(player, Component.translatable("etherealconvergence.message.unlinked", target.getGameProfile().getName()), ChatFormatting.YELLOW);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    private InteractionResult handleRelink(ItemStack stack, ServerPlayer player, ServerPlayer target) {
        ModComponents.ConfirmationData confirmation = stack.get(ModComponents.CONFIRMATION.get());

        if (confirmation != null && confirmation.targetUUID().equals(target.getUUID().toString()) && System.currentTimeMillis() - confirmation.time() <= EtherealConvergenceConfig.getConfirmationTimeout()) {
            stack.remove(ModComponents.CONFIRMATION.get());
            return createLink(stack, player, target);
        } else {
            stack.set(ModComponents.CONFIRMATION.get(), new ModComponents.ConfirmationData(target.getUUID().toString(), System.currentTimeMillis()));

            sendStatusMessage(player, Component.translatable("etherealconvergence.message.confirm_relink", target.getGameProfile().getName()), ChatFormatting.YELLOW);
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
    }

    private InteractionResult createLink(ItemStack linkItemStack, ServerPlayer player, ServerPlayer target) {
        ItemStack targetLink = findLinkInHand(target);
        if (targetLink.isEmpty()) {
            sendStatusMessage(player, Component.translatable("etherealconvergence.message.no_valid_link"), ChatFormatting.RED);
            return InteractionResult.FAIL;
        }

        linkItemStack.set(ModComponents.LINK.get(), new ModComponents.LinkData(target.getUUID().toString(), target.getGameProfile().getName()));
        sendStatusMessage(player, Component.translatable("etherealconvergence.message.linked", target.getGameProfile().getName()), ChatFormatting.GREEN);

        targetLink.set(ModComponents.LINK.get(), new ModComponents.LinkData(player.getUUID().toString(), player.getGameProfile().getName()));
        sendStatusMessage(target, Component.translatable("etherealconvergence.message.linked", player.getGameProfile().getName()), ChatFormatting.GREEN);

        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    private InteractionResultHolder<ItemStack> sendRequest(ItemStack linkItemStack, ServerPlayer player) {
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

            // Remove link component
            linkItemStack.remove(ModComponents.LINK.get());

            return InteractionResultHolder.fail(linkItemStack);
        }

        if (ModComponents.hasValidRequestTicks(targetLink, getCurrentTick())) {
            sendStatusMessage(player, Component.translatable("etherealconvergence.message.request_pending"), ChatFormatting.YELLOW);
            return InteractionResultHolder.fail(linkItemStack);
        }

        targetLink.set(ModComponents.REQUEST.get(), new ModComponents.RequestData(player.getUUID().toString(), getCurrentTick()));
        sendStatusMessage(player, Component.translatable("etherealconvergence.message.request_sent", link.name()), ChatFormatting.GREEN);
        sendStatusMessage(target, Component.translatable("etherealconvergence.message.request_incoming", player.getGameProfile().getName()), ChatFormatting.AQUA);

        // Use config value for request timeout cooldown
        player.getCooldowns().addCooldown(linkItemStack.getItem(), EtherealConvergenceConfig.getRequestTimeoutTicks());

        return InteractionResultHolder.success(linkItemStack);
    }

    private boolean acceptRequest(ServerPlayer acceptingPlayer, ItemStack linkItemStack, InteractionHand hand, ServerLevel level, BlockPos pos) {
        ModComponents.RequestData req = linkItemStack.get(ModComponents.REQUEST.get());
        if (req == null) return false;

        if (getCurrentTick() - req.time() > EtherealConvergenceConfig.getRequestTimeoutTicks()) {
            linkItemStack.remove(ModComponents.REQUEST.get());
            sendStatusMessage(acceptingPlayer, Component.translatable("etherealconvergence.message.request_expired"), ChatFormatting.RED);
            return false;
        }

        ServerPlayer requester = level.getServer().getPlayerList().getPlayer(UUID.fromString(req.requester()));
        if (requester == null) {
            linkItemStack.remove(ModComponents.REQUEST.get());
            sendStatusMessage(acceptingPlayer, Component.translatable("etherealconvergence.message.requester_offline"), ChatFormatting.RED);
            return false;
        }

        sendStatusMessage(requester, Component.translatable("etherealconvergence.message.tp_success_to", acceptingPlayer.getGameProfile().getName()), ChatFormatting.GREEN);
        sendStatusMessage(acceptingPlayer, Component.translatable("etherealconvergence.message.request_accepted"), ChatFormatting.GREEN);

        requester.teleportTo(level, pos.getX(), pos.getY(), pos.getZ(), acceptingPlayer.getYRot(), acceptingPlayer.getXRot());

        // Play ender pearl sound
        level.playSound(requester, pos, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        // Update components before modifying the stack
        linkItemStack.remove(ModComponents.REQUEST.get());

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        linkItemStack.hurtAndBreak(1, acceptingPlayer, slot);
        requester.getCooldowns().addCooldown(this, EtherealConvergenceConfig.getTeleportCooldownTicks());


        return true;
    }

    private void sendStatusMessage(ServerPlayer player, Component message, ChatFormatting color) {
        player.displayClientMessage(message.copy().withStyle(color), true);
    }

}
