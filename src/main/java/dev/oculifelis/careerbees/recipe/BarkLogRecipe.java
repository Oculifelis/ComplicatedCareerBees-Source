package dev.oculifelis.careerbees.recipe;

import com.mojang.serialization.MapCodec;
import dev.oculifelis.careerbees.item.BarkItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Four bark of one log type in a 2x2 square give that log back. Mixed types, or bark without a type, don't craft. */
public class BarkLogRecipe extends CustomRecipe {
    public static final BarkLogRecipe INSTANCE = new BarkLogRecipe();
    public static final MapCodec<BarkLogRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, BarkLogRecipe> STREAM_CODEC = StreamCodec.<ByteBuf, BarkLogRecipe>unit(INSTANCE).cast();
    public static final RecipeSerializer<BarkLogRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    /** The log all four bark share, or null when the grid is not a valid bark square. */
    public static Block log(CraftingInput input) {
        if (input.width() != 2 || input.height() != 2 || input.ingredientCount() != 4) return null;
        Block log = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (!(s.getItem() instanceof BarkItem)) return null;
            Block b = BarkItem.logOrNull(s);
            if (b == null || (log != null && b != log)) return null;
            log = b;
        }
        return log;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return log(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Block log = log(input);
        return log == null ? ItemStack.EMPTY : new ItemStack(log.asItem());
    }

    @Override
    public RecipeSerializer<BarkLogRecipe> getSerializer() {
        return SERIALIZER;
    }
}
