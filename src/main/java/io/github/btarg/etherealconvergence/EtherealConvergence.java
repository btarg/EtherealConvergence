package io.github.btarg.etherealconvergence;

import io.github.btarg.etherealconvergence.item.AkashicLinkItem;
import io.github.btarg.etherealconvergence.item.ModComponents;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(EtherealConvergence.MODID)
public class EtherealConvergence {
    public static final String MODID = "etherealconvergence";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<AkashicLinkItem> AKASHIC_LINK = ITEMS.register("akashic_link",
            () -> new AkashicLinkItem(new Item.Properties().stacksTo(1).durability(3)));

    public EtherealConvergence(IEventBus modEventBus, ModContainer modContainer) {
        ModComponents.register(modEventBus);
        ITEMS.register(modEventBus);

        modContainer.registerConfig(
                net.neoforged.fml.config.ModConfig.Type.COMMON,
                io.github.btarg.etherealconvergence.config.EtherealConvergenceConfig.CONFIG_SPEC
        );
    }


}