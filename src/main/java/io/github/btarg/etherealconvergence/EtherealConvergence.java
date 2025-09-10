package io.github.btarg.etherealconvergence;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.btarg.etherealconvergence.config.EtherealConvergenceConfig;
import io.github.btarg.etherealconvergence.item.AkashicLinkItem;
import io.github.btarg.etherealconvergence.item.ModComponents;
import io.github.btarg.etherealconvergence.network.ModNetwork;
import io.github.btarg.etherealconvergence.util.LinkHelpers;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.lwjgl.glfw.GLFW;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(EtherealConvergence.MODID)
public class EtherealConvergence {
    public static final String MODID = "etherealconvergence";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<AkashicLinkItem> AKASHIC_LINK = ITEMS.register("akashic_link",
            () -> new AkashicLinkItem(new Item.Properties().stacksTo(1)
                    .durability(3)));

    public static final Lazy<KeyMapping> UNLINK_KEY_MAPPING = Lazy.of(() -> {
        KeyMapping keyMapping = new KeyMapping(
                "key.etherealconvergence.unlink",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                "key.categories.misc"
        );
        keyMapping.setKeyConflictContext(KeyConflictContext.IN_GAME);
        return keyMapping;
    });

    public EtherealConvergence(IEventBus modEventBus, ModContainer modContainer) {
        ModComponents.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.register(this);
        modEventBus.register(ModNetwork.class);

        NeoForge.EVENT_BUS.addListener(EtherealConvergence::onClientTick);

        modContainer.registerConfig(
                net.neoforged.fml.config.ModConfig.Type.COMMON,
                EtherealConvergenceConfig.CONFIG_SPEC
        );
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (EtherealConvergence.UNLINK_KEY_MAPPING.get().consumeClick()) {

            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) continue;
            ItemStack stack = LinkHelpers.findLinkInHand(player);
            if (stack.isEmpty()) continue;
            if (player.getCooldowns().isOnCooldown(EtherealConvergence.AKASHIC_LINK.get())) continue;

            PacketDistributor.sendToServer(new ModNetwork.PlayerPressButtonOnStackPayload(player.getInventory().findSlotMatchingItem(stack)));

        }
    }

    @SubscribeEvent
    public void registerBindings(RegisterKeyMappingsEvent event) {
        event.register(UNLINK_KEY_MAPPING.get());
    }

    @SubscribeEvent
    private void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(AKASHIC_LINK);
        }
    }

}