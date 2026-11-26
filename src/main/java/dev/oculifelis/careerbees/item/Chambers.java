package dev.oculifelis.careerbees.item;

import com.mojang.serialization.Codec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The six bee chambers of a Bee-Bee Gun. Immutable; every change returns a copy. */
public record Chambers(List<ItemStack> stacks) {
    public static final int SIZE = 6;
    public static final Chambers EMPTY = new Chambers(List.of());

    public static final Codec<Chambers> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(Chambers::new, Chambers::stacks);
    public static final StreamCodec<RegistryFriendlyByteBuf, Chambers> STREAM_CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(Chambers::new, Chambers::stacks);

    public Chambers {
        List<ItemStack> fixed = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) fixed.add(i < stacks.size() ? stacks.get(i).copy() : ItemStack.EMPTY);
        stacks = List.copyOf(fixed.stream().map(s -> s.isEmpty() ? ItemStack.EMPTY : s).toList());
    }

    public ItemStack get(int slot) {
        return stacks.get(slot).copy();
    }

    public Chambers with(int slot, ItemStack stack) {
        NonNullList<ItemStack> copy = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) copy.set(i, i == slot ? stack.copy() : stacks.get(i));
        return new Chambers(copy);
    }

    public boolean isEmpty() {
        return stacks.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Chambers c)) return false;
        for (int i = 0; i < SIZE; i++) if (!ItemStack.matches(stacks.get(i), c.stacks.get(i))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (ItemStack s : stacks) h = 31 * h + (s.isEmpty() ? 0 : ItemStack.hashItemAndComponents(s) * 31 + s.getCount());
        return h;
    }
}
