package dev.oculifelis.careerbees.client;

import com.accbdd.complicated_bees.port.PortGui;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.effect.FilterSettings;
import dev.oculifelis.careerbees.menu.SettingsMenu;
import dev.oculifelis.careerbees.network.SetFilterTag;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Settings Frame screen: ghost filter slot, four toggle buttons, a tag text box and the player inventory. */
public class SettingsScreen extends AbstractContainerScreen<SettingsMenu> {
    private static final Identifier TEX = CareerBees.id("textures/gui/settings.png");
    private static final int[] BUTTON_X = {39, 65, 91, 117};
    private static final int BUTTON_Y = 15, GHOST_X = 10, GHOST_Y = 18;
    private static final int TILE = 196, TILE_ON = 216, TILE_HOVER = 236;

    private EditBox tagBox;

    public SettingsScreen(SettingsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 194, 219);
        this.titleLabelX = 9;
        this.titleLabelY = 5;
        this.inventoryLabelX = 16;
    }

    @Override
    protected void init() {
        super.init();
        tagBox = new EditBox(font, leftPos + 12, topPos + 49, 168, 10, Component.translatable("gui.careerbees.settings.tag_hint"));
        tagBox.setCanLoseFocus(false);
        tagBox.setTextColor(-1);
        tagBox.setTextColorUneditable(0xFF808080);
        tagBox.setBordered(false);
        tagBox.setMaxLength(FilterSettings.MAX_TAG);
        tagBox.setValue(menu.settings().tag());
        tagBox.setHint(Component.translatable("gui.careerbees.settings.tag_hint"));
        tagBox.setResponder(this::onTagChanged);
        addRenderableWidget(tagBox);
        setInitialFocus(tagBox);
        updateTagBox();
    }

    private void onTagChanged(String tag) {
        if (tag.equals(menu.settings().tag())) return;
        menu.edit(s -> s.withTag(tag));
        ClientPacketDistributor.sendToServer(new SetFilterTag(tag));
        updateTagBox();
    }

    private void updateTagBox() {
        FilterSettings s = menu.settings();
        tagBox.setEditable(s.tagMode());
        boolean bad = !s.tag().isBlank() && s.tagKey() == null;
        tagBox.setTextColor(bad ? 0xFFFF5555 : -1);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateTagBox();
    }

    @Override
    public void resize(int width, int height) {
        String old = tagBox.getValue();
        init(width, height);
        tagBox.setValue(old);
    }

    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h;
    }

    private int buttonAt(double mx, double my) {
        for (int i = 0; i < BUTTON_X.length; i++) if (over(mx, my, BUTTON_X[i] + 1, BUTTON_Y + 1, 20, 20)) return i;
        return -1;
    }

    private void press(int id) {
        menu.clickMenuButton(minecraft.player, id);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        updateTagBox();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int b = buttonAt(event.x(), event.y());
            if (b >= 0) {
                press(b);
                return true;
            }
        }
        if (over(event.x(), event.y(), GHOST_X, GHOST_Y, 16, 16)) {
            press(SettingsMenu.GHOST);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            minecraft.player.closeContainer();
            return true;
        }
        return !tagBox.keyPressed(event) && !tagBox.canConsumeInput() ? super.keyPressed(event) : true;
    }

    private boolean on(int i, FilterSettings s) {
        return switch (i) {
            case 1 -> s.ignoreComponents();
            case 2 -> s.ignoreDamage();
            case 3 -> s.voidExcess();
            default -> false;
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        PortGui.blit(g, TEX, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        FilterSettings s = menu.settings();
        int hover = buttonAt(mouseX, mouseY);
        int[][] icons = {s.tagMode() ? new int[]{216, 22} : new int[]{196, 22}, {236, 22}, {196, 40}, {216, 40}};
        for (int i = 0; i < BUTTON_X.length; i++) {
            int x = leftPos + BUTTON_X[i] + 1, y = topPos + BUTTON_Y + 1;
            int u = on(i, s) ? TILE_ON : hover == i ? TILE_HOVER : TILE;
            PortGui.blit(g, TEX, x, y, u, 0, 20, 20);
            PortGui.blit(g, TEX, x + 2, y + 2, icons[i][0], icons[i][1], 16, 16);
        }
        if (!s.tagMode() && !s.item().isEmpty()) g.item(s.item(), leftPos + GHOST_X, topPos + GHOST_Y);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        extractTooltip(g, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) return;
        FilterSettings s = menu.settings();
        int b = buttonAt(mouseX, mouseY);
        if (b >= 0) {
            Component text = switch (b) {
                case 0 -> Component.translatable(s.tagMode() ? "gui.careerbees.settings.mode_tag" : "gui.careerbees.settings.mode_item");
                case 1 -> toggle("gui.careerbees.settings.ignore_components", s.ignoreComponents());
                case 2 -> toggle("gui.careerbees.settings.ignore_damage", s.ignoreDamage());
                default -> toggle("gui.careerbees.settings.void_excess", s.voidExcess());
            };
            PortGui.tooltip(g, font, text, mouseX, mouseY);
        } else if (over(mouseX, mouseY, GHOST_X, GHOST_Y, 16, 16)) {
            List<Component> lines = new ArrayList<>();
            ItemStack item = s.item();
            if (!item.isEmpty()) lines.add(item.getHoverName());
            lines.add(Component.translatable("gui.careerbees.settings.ghost"));
            PortGui.tooltip(g, font, lines, mouseX, mouseY);
        } else if (s.tagMode() && over(mouseX, mouseY, 9, 46, 175, 13) && !s.tag().isBlank() && s.tagKey() == null) {
            PortGui.tooltip(g, font, Component.translatable("gui.careerbees.settings.bad_tag"), mouseX, mouseY);
        }
    }

    private static Component toggle(String key, boolean value) {
        return Component.translatable(key).append(": ").append(Component.translatable(value ? "gui.careerbees.settings.on" : "gui.careerbees.settings.off"));
    }
}
