package io.github.btarg.etherealconvergence.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.btarg.etherealconvergence.EtherealConvergence;
import io.github.btarg.etherealconvergence.config.EtherealConvergenceConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModComponents {
    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, EtherealConvergence.MODID);

    // --- Link Data ---
    public record LinkData(String linkedUUID, String name) {}

    public static final Codec<LinkData> LINK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("linkedUUID").forGetter(LinkData::linkedUUID),
            Codec.STRING.fieldOf("name").forGetter(LinkData::name)
    ).apply(instance, LinkData::new));

    public static final StreamCodec<ByteBuf, LinkData> LINK_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, LinkData::linkedUUID,
            ByteBufCodecs.STRING_UTF8, LinkData::name,
            LinkData::new
    );

    public static final Supplier<DataComponentType<LinkData>> LINK =
            REGISTER.registerComponentType("link", builder -> builder
                    .persistent(LINK_CODEC)
                    .networkSynchronized(LINK_STREAM_CODEC));

    // --- Request Data ---
    public record RequestData(String requester, long time) {}

    public static final Codec<RequestData> REQUEST_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("requester").forGetter(RequestData::requester),
            Codec.LONG.fieldOf("time").forGetter(RequestData::time)
    ).apply(inst, RequestData::new));

    public static final StreamCodec<ByteBuf, RequestData> REQUEST_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestData::requester,
            ByteBufCodecs.VAR_LONG, RequestData::time,
            RequestData::new
    );

    public static final Supplier<DataComponentType<RequestData>> REQUEST =
            REGISTER.registerComponentType("request", builder -> builder
                    .persistent(REQUEST_CODEC)
                    .networkSynchronized(REQUEST_STREAM_CODEC));

    // --- Confirmation Data ---
    public record ConfirmationData(String targetUUID, long time) {}

    public static final Codec<ConfirmationData> CONFIRMATION_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("targetUUID").forGetter(ConfirmationData::targetUUID),
            Codec.LONG.fieldOf("time").forGetter(ConfirmationData::time)
    ).apply(inst, ConfirmationData::new));

    public static final StreamCodec<ByteBuf, ConfirmationData> CONFIRMATION_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConfirmationData::targetUUID,
            ByteBufCodecs.VAR_LONG, ConfirmationData::time,
            ConfirmationData::new
    );

    public static final Supplier<DataComponentType<ConfirmationData>> CONFIRMATION =
            REGISTER.registerComponentType("confirmation", builder -> builder
                    .persistent(CONFIRMATION_CODEC)
                    .networkSynchronized(CONFIRMATION_STREAM_CODEC));

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        REGISTER.register(bus);
    }

    public static boolean hasValidRequest(net.minecraft.world.item.ItemStack stack, long now) {
        RequestData req = stack.get(REQUEST.get());
        if (req == null) return false;
        return now - req.time() <= EtherealConvergenceConfig.getRequestTimeoutTicks();
    }

    public static boolean hasValidRequestTicks(net.minecraft.world.item.ItemStack stack, long currentTick) {
        RequestData req = stack.get(REQUEST.get());
        if (req == null) return false;
        return currentTick - req.time() <= EtherealConvergenceConfig.getRequestTimeoutTicks();
    }
}
