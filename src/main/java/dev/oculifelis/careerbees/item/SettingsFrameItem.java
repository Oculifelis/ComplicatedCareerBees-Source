package dev.oculifelis.careerbees.item;

import com.accbdd.complicated_bees.bees.BeeHousingModifier;
import com.accbdd.complicated_bees.item.FrameItem;
import dev.oculifelis.careerbees.effect.FilterSettings;
import dev.oculifelis.careerbees.menu.SettingsMenu;
import dev.oculifelis.careerbees.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Consumer;

/** A frame that holds an item filter for the career effects. Use it in hand to edit the filter. */
public class SettingsFrameItem extends FrameItem {
    public SettingsFrameItem(Properties props, ModConfigSpec.ConfigValue<Boolean> enabled) {
        super(props, new BeeHousingModifier.Builder().build(), enabled);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            int slot = player.getInventory().getSelectedSlot();
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SettingsMenu(id, inv, slot),
                    Component.translatable("item.careerbees.settings_frame")));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        FilterSettings s = stack.get(ModComponents.FILTER.get());
        if (s == null || (s.tagMode() ? s.tag().isBlank() : s.item().isEmpty())) {
            out.accept(Component.translatable("tooltip.careerbees.settings_frame.none").withStyle(ChatFormatting.GRAY));
        } else if (s.tagMode()) {
            out.accept(Component.translatable("tooltip.careerbees.settings_frame.tag", s.tag().strip().replaceFirst("^#", "")).withStyle(ChatFormatting.GRAY));
        } else {
            out.accept(Component.translatable("tooltip.careerbees.settings_frame.item", s.item().getHoverName()).withStyle(ChatFormatting.GRAY));
            if (s.ignoreComponents()) out.accept(Component.translatable("tooltip.careerbees.settings_frame.ignore_components").withStyle(ChatFormatting.DARK_GRAY));
            else if (s.ignoreDamage()) out.accept(Component.translatable("tooltip.careerbees.settings_frame.ignore_damage").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (s != null && s.voidExcess()) out.accept(Component.translatable("tooltip.careerbees.settings_frame.void_excess").withStyle(ChatFormatting.DARK_GRAY));
        out.accept(Component.translatable("tooltip.careerbees.settings_frame.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
