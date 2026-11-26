package dev.oculifelis.careerbees.network;

import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.item.BeeGunItem;
import dev.oculifelis.careerbees.item.Chambers;
import dev.oculifelis.careerbees.registry.ModComponents;
import dev.oculifelis.careerbees.registry.ModItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sneak-scrolling with the Bee-Bee Gun picks the chamber it fires from. */
public record SelectChamber(int chamber) implements CustomPacketPayload {
    public static final Type<SelectChamber> TYPE = new Type<>(CareerBees.id("select_chamber"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectChamber> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectChamber::chamber, SelectChamber::new);

    public static void handle(SelectChamber packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            int c = packet.chamber();
            if (c < 0 || c >= Chambers.SIZE) return;
            Player player = context.player();
            ItemStack gun = player.getMainHandItem();
            if (!gun.is(ModItems.BEE_GUN.get())) return;
            gun.set(ModComponents.CHAMBER.get(), c);
            player.sendOverlayMessage(Component.translatable("message.careerbees.bee_gun.chamber", c + 1,
                    BeeGunItem.name(BeeGunItem.chambers(gun).get(c))));
        });
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1.0.0").playToServer(TYPE, STREAM_CODEC, SelectChamber::handle);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
