package io.github.btarg.etherealconvergence.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.btarg.etherealconvergence.EtherealConvergence;
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
    public static final Codec<RequestData> REQUEST_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("requester").forGetter(RequestData::requester),
            Codec.LONG.fieldOf("time").forGetter(RequestData::time),
            Codec.INT.fieldOf("type").forGetter(data -> data.type.index)
    ).apply(instance, (requester, time, typeIndex) -> new RequestData(requester, time, ERequestType.fromIndex(typeIndex))));
    public static final StreamCodec<ByteBuf, RequestData> REQUEST_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestData::requester,
            ByteBufCodecs.VAR_LONG, RequestData::time,
            ByteBufCodecs.VAR_INT.map(ERequestType::fromIndex, type -> type.index), RequestData::type,
            RequestData::new
    );
    // This component is added to the target player's Link item when sending a teleport request.
    public static final Supplier<DataComponentType<RequestData>> INCOMING_REQUEST =
            REGISTER.registerComponentType("request", builder -> builder
                    .persistent(REQUEST_CODEC)
                    .networkSynchronized(REQUEST_STREAM_CODEC));
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

    // --- Request Data ---
    public enum ERequestType {
        BRING_REQUESTER_HERE(0),
        TP_TO_REQUESTER(1);

        public final int index;

        ERequestType(int index) {
            this.index = index;
        }

        public static ERequestType fromIndex(int index) {
            return values()[index];
        }
    }

    // --- Link Data ---
    public record LinkData(String linkedUUID, String name) {
    }

    public record RequestData(String requester, long time, ERequestType type) {
    }

    // --- Confirmation Data ---
    public record ConfirmationData(String targetUUID, long time) {
    }

}
