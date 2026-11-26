package dev.oculifelis.careerbees.item;

import dev.oculifelis.careerbees.registry.ModComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Tree Bark: remembers the log it was peeled from. */
public class BarkItem extends Item {
    public BarkItem(Properties props) {
        super(props);
    }

    public static ItemStack of(Item bark, Block log, int count) {
        ItemStack stack = new ItemStack(bark, count);
        stack.set(ModComponents.LOG.get(), BuiltInRegistries.BLOCK.getKey(log));
        return stack;
    }

    /** The log this bark came from, or null when it has none or the log is no longer registered. */
    public static Block logOrNull(ItemStack stack) {
        Identifier id = stack.get(ModComponents.LOG.get());
        return id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    public static Block logOf(ItemStack stack) {
        Identifier id = stack.get(ModComponents.LOG.get());
        if (id == null) return Blocks.OAK_LOG;
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.OAK_LOG);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.careerbees.bark.of", logOf(stack).getName());
    }

    public void fillTab(CreativeModeTab.Output out) {
        BuiltInRegistries.BLOCK.getTagOrEmpty(BlockTags.LOGS).forEach(h -> out.accept(of(this, h.value(), 1)));
    }
}
