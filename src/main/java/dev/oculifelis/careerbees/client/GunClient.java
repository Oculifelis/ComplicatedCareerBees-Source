package dev.oculifelis.careerbees.client;

import com.accbdd.complicated_bees.port.PortGui;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.item.BeeGunItem;
import dev.oculifelis.careerbees.item.Chambers;
import dev.oculifelis.careerbees.network.SelectChamber;
import dev.oculifelis.careerbees.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Sneak and scroll picks the gun's chamber and shows the six-chamber selector for a few seconds. */
@EventBusSubscriber(modid = CareerBees.MODID, value = Dist.CLIENT)
public final class GunClient {
    private static final Identifier TEX = CareerBees.id("textures/gui/gun_selector.png");
    private static final int[][] HEX = {{19, 2}, {34, 10}, {34, 28}, {19, 36}, {4, 28}, {4, 10}};
    private static int shown;

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isShiftKeyDown()) return;
        ItemStack gun = player.getMainHandItem();
        if (!gun.is(ModItems.BEE_GUN.get()) || event.getScrollDeltaY() == 0) return;
        int next = Math.floorMod(BeeGunItem.chamber(gun) - (int) Math.signum(event.getScrollDeltaY()), Chambers.SIZE);
        ClientPacketDistributor.sendToServer(new SelectChamber(next));
        GunHold.clicked();
        shown = 120;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (shown > 0) shown--;
    }

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(CareerBees.id("bee_gun_selector"), (g, delta) -> draw(g));
    }

    private static void draw(GuiGraphicsExtractor g) {
        if (shown <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack gun = mc.player.getMainHandItem();
        if (!gun.is(ModItems.BEE_GUN.get())) return;
        int x = mc.getWindow().getGuiScaledWidth() / 2 + 24, y = mc.getWindow().getGuiScaledHeight() / 2 - 26;
        PortGui.blit(g, TEX, x, y, 0f, 0f, 52, 52, 64, 64);
        int[] h = HEX[Mth.clamp(BeeGunItem.chamber(gun), 0, Chambers.SIZE - 1)];
        PortGui.blit(g, TEX, x + h[0], y + h[1], 51f, 51f, 13, 13, 64, 64);
    }

    private GunClient() {}
}
