package dev.oculifelis.careerbees.client;

import com.accbdd.complicated_bees.port.PortGui;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.item.BeeGunItem;
import dev.oculifelis.careerbees.menu.GunMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Bee-Bee Gun loading screen: six chambers around the barrel, the honey gauge and the bottle slot. */
public class GunScreen extends AbstractContainerScreen<GunMenu> {
    private static final Identifier TEX = CareerBees.id("textures/gui/bee_gun.png");

    public GunScreen(GunMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 178);
        this.inventoryLabelY = 85;
    }

    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        PortGui.blit(g, TEX, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int cap = Math.max(1, BeeGunItem.capacity());
        int h = Math.round(GunMenu.GAUGE_H * Math.min(BeeGunItem.honey(menu.gun()), cap) / (float) cap);
        if (h > 0) PortGui.blit(g, TEX, leftPos + GunMenu.GAUGE_X, topPos + GunMenu.GAUGE_Y + GunMenu.GAUGE_H - h,
                184, GunMenu.GAUGE_H - h, GunMenu.GAUGE_W, h);
        int[] p = GunMenu.CHAMBER_POS[BeeGunItem.chamber(menu.gun())];
        PortGui.blit(g, TEX, leftPos + p[0] - 1, topPos + p[1] - 1, 204, 0, 18, 18);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        extractTooltip(g, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) return;
        if (over(mouseX, mouseY, GunMenu.GAUGE_X, GunMenu.GAUGE_Y, GunMenu.GAUGE_W, GunMenu.GAUGE_H)) {
            PortGui.tooltip(g, font, List.of(
                    Component.translatable("gui.careerbees.bee_gun.honey", BeeGunItem.honey(menu.gun()), BeeGunItem.capacity()),
                    Component.translatable("gui.careerbees.bee_gun.bottle")), mouseX, mouseY);
        }
    }
}
