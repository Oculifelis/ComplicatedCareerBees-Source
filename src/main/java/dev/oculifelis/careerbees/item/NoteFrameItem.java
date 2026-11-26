package dev.oculifelis.careerbees.item;

import com.accbdd.complicated_bees.bees.BeeHousingModifier;
import com.accbdd.complicated_bees.item.FrameItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Consumer;

/** A frame with fixed tooltip lines instead of the modifier list. */
public class NoteFrameItem extends FrameItem {
    private final String[] lines;

    public NoteFrameItem(Properties props, BeeHousingModifier modifier, ModConfigSpec.ConfigValue<Boolean> enabled, String... lines) {
        super(props, modifier, enabled);
        this.lines = lines;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        for (String key : lines) out.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
