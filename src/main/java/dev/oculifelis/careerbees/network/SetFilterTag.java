package dev.oculifelis.careerbees.network;

import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.effect.FilterSettings;
import dev.oculifelis.careerbees.menu.SettingsMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The tag text typed into the Settings Frame screen. Buttons use the vanilla container button packet instead. */
public record SetFilterTag(String tag) implements CustomPacketPayload {
    public static final Type<SetFilterTag> TYPE = new Type<>(CareerBees.id("set_filter_tag"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterTag> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(FilterSettings.MAX_TAG), SetFilterTag::tag, SetFilterTag::new);

    public static void handle(SetFilterTag packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof SettingsMenu menu) menu.edit(s -> s.withTag(packet.tag()));
        });
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1.0.0").playToServer(TYPE, STREAM_CODEC, SetFilterTag::handle);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
