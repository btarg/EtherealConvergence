package io.github.btarg.etherealconvergence.item;

import io.github.btarg.etherealconvergence.config.EtherealConvergenceConfig;
import io.github.btarg.etherealconvergence.util.ItemHelpers;
import io.github.btarg.etherealconvergence.util.LinkHelpers;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AkashicLinkItem extends Item {

    public AkashicLinkItem(Properties props) {
        super(props);
    }

    private static long getCurrentTimeInTicks() {
        // For now, convert current time to approximate ticks (50ms per tick)
        return System.currentTimeMillis() / 50;
    }

    private static void checkAndRemoveSelfLink(ItemStack stack, Player player) {
        ModComponents.LinkData link = stack.get(ModComponents.LINK_DATA.get());
        if (link != null && link.linkedUUID().equals(player.getUUID().toString())) {
            LinkHelpers.unlink(stack, player);
        }
    }

    private static @NotNull String getTeleportString(ModComponents.RequestData requestData, ModComponents.LinkData link) {
        if (requestData == null || link == null) return "";

        String requesterName = link.name();
        String targetName = "You"; // The current player holding the item

        return requestData.type() == ModComponents.ERequestType.BRING_REQUESTER_HERE
                ? requesterName + " → " + targetName
                : targetName + " → " + requesterName;
    }

    private boolean hasValidRequestTicksClient(ItemStack stack) {
        ModComponents.RequestData req = stack.get(ModComponents.INCOMING_REQUEST.get());
        if (req == null) return false;
        return getCurrentTimeInTicks() - req.time() <= EtherealConvergenceConfig.getRequestTimeoutTicks();
    }

    @Override
    public boolean isEnchantable(@Nonnull ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return hasValidRequestTicksClient(stack);
    }

    @Override
    @Nonnull
    public Component getName(@Nonnull ItemStack stack) {
        ModComponents.LinkData link = stack.get(ModComponents.LINK_DATA.get());

        ChatFormatting style;
        if (link == null) {
            style = ChatFormatting.WHITE;
        } else {
            style = hasValidRequestTicksClient(stack) ? ChatFormatting.DARK_PURPLE : ChatFormatting.AQUA;
        }

        return Component.translatable("item.etherealconvergence.akashic_link").withStyle(style);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context, @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag tooltipFlag) {

        ModComponents.RequestData requestData = stack.get(ModComponents.INCOMING_REQUEST.get());
        ModComponents.LinkData linkData = stack.get(ModComponents.LINK_DATA.get());
        ModComponents.ConfirmationData conformation = stack.get(ModComponents.CONFIRMATION.get());

        if (hasValidRequestTicksClient(stack)) {
            String teleportString = getTeleportString(requestData, linkData);
            tooltipComponents.add(Component.translatable("item.etherealconvergence.incoming", teleportString).withStyle(ChatFormatting.DARK_PURPLE));
        }
        else if (conformation != null) {
            String linkingPlayerName = conformation.targetUUID().isEmpty() ? "???" : conformation.targetUUID();
            tooltipComponents.add(Component.translatable("etherealconvergence.message.link_pending", "You ⇄ " + linkingPlayerName).withStyle(ChatFormatting.BLUE));
        }

        boolean noLink = linkData == null || linkData.linkedUUID().isEmpty();
        MutableComponent linkName = noLink ?
                Component.translatable("item.etherealconvergence.unlinked")
                : Component.translatable("etherealconvergence.message.linked", linkData.name());

        tooltipComponents.add(linkName.withStyle(ChatFormatting.BLUE));

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    // Right-click player to start a link request
    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player, @Nonnull LivingEntity entity, @Nonnull InteractionHand hand) {
        if (!(entity instanceof Player target))
            return InteractionResult.PASS;

        ItemStack targetLink = LinkHelpers.findLinkInHand(target);
        if (targetLink.isEmpty()) {
            player.displayClientMessage(Component.translatable("etherealconvergence.message.no_valid_link").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        // Check if target wants to link with us
        ModComponents.ConfirmationData targetConfirmation = targetLink.get(ModComponents.CONFIRMATION.get());
        boolean targetWantsToLink = targetConfirmation != null && 
            targetConfirmation.targetUUID().equals(player.getUUID().toString()) && 
            (System.currentTimeMillis() - targetConfirmation.time() <= EtherealConvergenceConfig.getConfirmationTimeout());

        if (targetWantsToLink) {
            return createMutualLink(stack, player, target, targetLink);
        }

        // Send link request
        ItemHelpers.setComponent(stack, player, ModComponents.CONFIRMATION.get(), 
            new ModComponents.ConfirmationData(target.getUUID().toString(), System.currentTimeMillis()));
        
        player.displayClientMessage(Component.translatable("etherealconvergence.message.link_request_sent", target.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW), true);
        target.displayClientMessage(Component.translatable("etherealconvergence.message.link_request_received", player.getGameProfile().getName()).withStyle(ChatFormatting.AQUA), true);

        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }


    private InteractionResult createMutualLink(ItemStack playerItemStack, Player player, Player target, ItemStack targetItemStack) {
        
        // Create the mutual link
        ItemHelpers.setComponent(playerItemStack, player, ModComponents.LINK_DATA.get(), 
            new ModComponents.LinkData(target.getUUID().toString(), target.getGameProfile().getName()));
        ItemHelpers.setComponent(targetItemStack, target, ModComponents.LINK_DATA.get(), 
            new ModComponents.LinkData(player.getUUID().toString(), player.getGameProfile().getName()));

        // Clear confirmations
        ItemHelpers.removeComponent(playerItemStack, player, ModComponents.CONFIRMATION.get());
        ItemHelpers.removeComponent(targetItemStack, target, ModComponents.CONFIRMATION.get());

        // Notify both players
        player.displayClientMessage(Component.translatable("etherealconvergence.message.linked", target.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
        target.displayClientMessage(Component.translatable("etherealconvergence.message.linked", player.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);

        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    // Right-click air to send TP request
    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Check for self-link and remove it first
        checkAndRemoveSelfLink(stack, player);

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        ModComponents.RequestData req = stack.get(ModComponents.INCOMING_REQUEST.get());

        // If we don't have a request then send one
        if (!checkRequestValid(req)) return sendRequest(stack, player, player.blockPosition());


        // Deny the incoming request if holding shift
        if (player.isShiftKeyDown()) {
            player.displayClientMessage(Component.translatable("etherealconvergence.message.request_denied").withStyle(ChatFormatting.RED), true);
            ItemHelpers.removeComponent(stack, player, ModComponents.INCOMING_REQUEST.get());
            return InteractionResultHolder.success(stack);
        }

        // Bring the requesting player here
        if (req != null && req.type().equals(ModComponents.ERequestType.BRING_REQUESTER_HERE)) {
            return acceptBringRequesterHere(req, serverPlayer, stack, serverPlayer.serverLevel(), serverPlayer.blockPosition())
                    ? InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide)
                    : InteractionResultHolder.fail(stack);
        }

        // Go to the requesting player
        return acceptGoToRequestingPlayer(req, player, stack, level)
                ? InteractionResultHolder.sidedSuccess(stack, player.level().isClientSide)
                : InteractionResultHolder.fail(stack);

    }

    // Right-click block to accept request and teleport the requesterUUID to that location
    @Override
    @Nonnull
    public InteractionResult useOn(@Nonnull UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();

        // Check for self-link and remove it first
        checkAndRemoveSelfLink(stack, player);

        // Teleport on top of the block if there is space, otherwise act like a Spawn Egg
        BlockPos clickedPos = ctx.getClickedPos();
        BlockPos abovePos = clickedPos.above();
        BlockPos spawnPos = level.isEmptyBlock(abovePos) ? abovePos : (level.getBlockState(clickedPos).getCollisionShape(level, clickedPos).isEmpty()
                ? clickedPos : clickedPos.relative(ctx.getClickedFace()));
        if (!Level.isInSpawnableBounds(spawnPos)) return InteractionResult.FAIL;

        ModComponents.RequestData req = stack.get(ModComponents.INCOMING_REQUEST.get());

        if (req == null) return sendRequest(stack, player, spawnPos).getResult();


        // Bring the requesting player here
        if (req.type().equals(ModComponents.ERequestType.BRING_REQUESTER_HERE)) {
            return acceptBringRequesterHere(req, player, stack, level, spawnPos)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.FAIL;
        }

        // Go to the requesting player
        return acceptGoToRequestingPlayer(req, player, stack, level)
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL;
    }

    private void handleOfflineFail(ItemStack linkItemStack, Player player) {
        ItemHelpers.removeComponent(linkItemStack, player, ModComponents.INCOMING_REQUEST.get());
        player.displayClientMessage(Component.translatable("etherealconvergence.message.offline").withStyle(ChatFormatting.RED), true);
    }

    private boolean acceptGoToRequestingPlayer(ModComponents.RequestData requestData, Player acceptingPlayer, ItemStack linkItemStack, Level level) {

        if (!(level instanceof ServerLevel serverLevel)) {
            handleOfflineFail(linkItemStack, acceptingPlayer);
            return false;
        }

        ServerPlayer requester = serverLevel.getServer().getPlayerList().getPlayer(UUID.fromString(requestData.requesterUUID()));
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
            ItemHelpers.removeComponent(linkItemStack, acceptingPlayer, ModComponents.INCOMING_REQUEST.get());
            return false;
        }

        requester.displayClientMessage(Component.translatable("etherealconvergence.message.request_accepted").withStyle(ChatFormatting.GREEN), true);
        acceptingPlayer.displayClientMessage(Component.translatable("etherealconvergence.message.tp_success_to", requester.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);

        BlockPos teleportPos = requestData.blockPos();

        // Empty set provided to avoid interpolation when teleporting
        serverAcceptingPlayer.teleportTo(serverLevel, teleportPos.getX(), teleportPos.getY(), teleportPos.getZ(), Set.of(), serverAcceptingPlayer.getYRot(), serverAcceptingPlayer.getXRot());

        serverAcceptingPlayer.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, requester.getEyePosition());
        serverLevel.playSound(serverAcceptingPlayer, teleportPos, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        ItemHelpers.removeComponent(linkItemStack, acceptingPlayer, ModComponents.INCOMING_REQUEST.get());

        ItemStack requesterLinkStack = LinkHelpers.findLinkedItem(requester, acceptingPlayer.getUUID());
        ItemStack acceptingLinkStack = LinkHelpers.findLinkInHand(acceptingPlayer);
        if (!acceptingLinkStack.isEmpty()) {
            acceptingLinkStack.getItem().damageItem(acceptingLinkStack, 1, serverAcceptingPlayer, p -> {
                // Sever the link for the requesterUUID
                LinkHelpers.unlink(requesterLinkStack, requester);
                requester.displayClientMessage(
                        Component.translatable("etherealconvergence.message.link_broken",
                                acceptingPlayer.getGameProfile().getName()).withStyle(ChatFormatting.RED), true);
            });
        }
        serverAcceptingPlayer.getCooldowns().addCooldown(this, EtherealConvergenceConfig.getTeleportCooldownTicks());

        return true;
    }


    private InteractionResultHolder<ItemStack> sendRequest(ItemStack linkItemStack, Player player, BlockPos blockPos) {
        ModComponents.LinkData linkData = linkItemStack.get(ModComponents.LINK_DATA.get());

        if (linkData == null) {
            player.displayClientMessage(Component.translatable("etherealconvergence.message.not_linked").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(linkItemStack);
        }

        if (player.getServer() == null) {
            return InteractionResultHolder.fail(linkItemStack);
        }

        ServerPlayer target = player.getServer().getPlayerList().getPlayer(UUID.fromString(linkData.linkedUUID()));
        if (target == null) {
            player.displayClientMessage(Component.translatable("etherealconvergence.message.offline").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(linkItemStack);
        }

        ItemStack targetLink = LinkHelpers.findLinkedItem(target, player.getUUID());

        if (targetLink.isEmpty()) {
            player.displayClientMessage(Component.translatable("etherealconvergence.message.no_valid_link").withStyle(ChatFormatting.RED), true);
            LinkHelpers.unlink(linkItemStack, player);
            return InteractionResultHolder.fail(linkItemStack);
        }


        ModComponents.RequestData requestData = targetLink.get(ModComponents.INCOMING_REQUEST.get());
        if (checkRequestValid(requestData)) {
            player.displayClientMessage(Component.translatable("etherealconvergence.message.request_pending").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(linkItemStack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(linkItemStack);
        }

        // Adds a request component to the other player (target)'s link item.
        // If we are not holding shift, we want to teleport to them, otherwise we want them to come to us.
        ModComponents.ERequestType requestType = player.isShiftKeyDown()
                ? ModComponents.ERequestType.TP_TO_REQUESTER
                : ModComponents.ERequestType.BRING_REQUESTER_HERE;

        ItemHelpers.setComponent(targetLink, target, ModComponents.INCOMING_REQUEST.get(),
                new ModComponents.RequestData(player.getUUID().toString(), getCurrentTimeInTicks(), requestType, blockPos));

        String requestSentMessage = requestType == ModComponents.ERequestType.BRING_REQUESTER_HERE
                ? "etherealconvergence.message.request_sent_tpa"
                : "etherealconvergence.message.request_sent_tpa_here";
        serverPlayer.displayClientMessage(Component.translatable(requestSentMessage, linkData.name()).withStyle(ChatFormatting.GREEN), true);

        String incomingRequestMessage = requestType == ModComponents.ERequestType.BRING_REQUESTER_HERE
                ? "etherealconvergence.message.request_incoming"
                : "etherealconvergence.message.request_incoming_tp";
        target.displayClientMessage(Component.translatable(incomingRequestMessage, player.getGameProfile().getName()).withStyle(ChatFormatting.AQUA), true);

        serverPlayer.getCooldowns().addCooldown(linkItemStack.getItem(), EtherealConvergenceConfig.getRequestTimeoutTicks());
        return InteractionResultHolder.success(linkItemStack);
    }


    private boolean checkRequestValid(ModComponents.RequestData requestData) {
        if (requestData == null) return false;
        return getCurrentTimeInTicks() - requestData.time() <= EtherealConvergenceConfig.getRequestTimeoutTicks();
    }

    private boolean acceptBringRequesterHere(ModComponents.RequestData requestData, Player acceptingPlayer, ItemStack linkItemStack, Level level, @Nullable BlockPos blockPos) {

        if (!(level instanceof ServerLevel serverLevel)) {
            acceptingPlayer.displayClientMessage(Component.translatable("etherealconvergence.message.offline").withStyle(ChatFormatting.RED), true);
            ItemHelpers.removeComponent(linkItemStack, acceptingPlayer, ModComponents.INCOMING_REQUEST.get());
            return false;
        }

        ServerPlayer requester = serverLevel.getServer().getPlayerList().getPlayer(UUID.fromString(requestData.requesterUUID()));
        if (requester == null) {
            ItemHelpers.removeComponent(linkItemStack, acceptingPlayer, ModComponents.INCOMING_REQUEST.get());
            acceptingPlayer.displayClientMessage(Component.translatable("etherealconvergence.message.offline").withStyle(ChatFormatting.RED), true);
            return false;
        }

        requester.displayClientMessage(Component.translatable("etherealconvergence.message.tp_success_to", acceptingPlayer.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
        acceptingPlayer.displayClientMessage(Component.translatable("etherealconvergence.message.request_accepted").withStyle(ChatFormatting.GREEN), true);

        BlockPos finalPos = (blockPos == null) ? requestData.blockPos() : blockPos;

        // Empty set provided to avoid interpolation when teleporting
        requester.teleportTo(serverLevel, finalPos.getX(), finalPos.getY(), finalPos.getZ(), Set.of(), requester.getYRot(), requester.getXRot());

        requester.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, acceptingPlayer.getEyePosition());
        level.playSound(requester, finalPos, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        ItemHelpers.removeComponent(linkItemStack, acceptingPlayer, ModComponents.INCOMING_REQUEST.get());

        ItemStack requesterLinkStack = LinkHelpers.findLinkedItem(requester, acceptingPlayer.getUUID());
        ItemStack acceptingLinkStack = LinkHelpers.findLinkInHand(acceptingPlayer);
        if (!requesterLinkStack.isEmpty()) {
            requesterLinkStack.getItem().damageItem(requesterLinkStack, 1, requester, p -> {
                // Sever the link for the acceptingPlayer
                LinkHelpers.unlink(acceptingLinkStack, acceptingPlayer);
                acceptingPlayer.displayClientMessage(Component.translatable("etherealconvergence.message.link_broken", requester.getGameProfile().getName()).withStyle(ChatFormatting.RED), true);
            });
        }
        requester.getCooldowns().addCooldown(this, EtherealConvergenceConfig.getTeleportCooldownTicks());

        return true;
    }


    @Override
    public boolean isValidRepairItem(@Nonnull ItemStack stack, @Nonnull ItemStack repairCandidate) {
        return repairCandidate.is(Items.ENDER_PEARL);
    }

}
