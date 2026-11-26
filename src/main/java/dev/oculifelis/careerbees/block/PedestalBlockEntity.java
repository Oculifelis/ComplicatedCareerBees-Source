package dev.oculifelis.careerbees.block;

import com.accbdd.complicated_bees.bees.effect.IBeeEffect;
import dev.oculifelis.careerbees.effect.PedestalEffect;
import dev.oculifelis.careerbees.registry.ModBlocks;
import dev.oculifelis.careerbees.util.Bees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/** Holds one item and, on the advanced pedestal, the species it is bound to. */
public class PedestalBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;
    @Nullable
    private Identifier species;
    private final Handler handler = new Handler();

    public PedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PEDESTAL_ENTITY.get(), pos, state);
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack stack) {
        item = stack;
        changed();
    }

    @Nullable
    public Identifier species() {
        return species;
    }

    public void bind(Identifier id) {
        species = id;
        changed();
    }

    public IItemHandlerModifiable handler() {
        return handler;
    }

    /** Unbound pedestals serve every species; bound ones only their own. */
    public boolean usableBy(@Nullable Identifier speciesId) {
        return species == null || species.equals(speciesId);
    }

    @Nullable
    public PedestalEffect boundEffect() {
        IBeeEffect e = Bees.defaultEffect(species);
        return e instanceof PedestalEffect p ? p : null;
    }

    /** Whether this pedestal will take the item, by hand or by pipe. */
    public boolean canInsert(ItemStack stack) {
        if (stack.isEmpty() || level == null) return false;
        if (species != null) {
            PedestalEffect e = boundEffect();
            return e != null && e.accepts(level, stack);
        }
        for (PedestalEffect e : PedestalEffect.ALL) if (e.accepts(level, stack)) return true;
        return false;
    }

    /** Automation may pull the item once no bee can still work on it. */
    public boolean canExtract() {
        if (item.isEmpty() || level == null) return false;
        if (species == null) return true;
        PedestalEffect e = boundEffect();
        return e == null || !e.workable(level, item);
    }

    /** Drops the held item when the block goes, whatever removed it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !item.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, item);
        item = ItemStack.EMPTY;
        super.preRemoveSideEffects(pos, state);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!item.isEmpty()) out.store("item", ItemStack.CODEC, item);
        out.storeNullable("species", Identifier.CODEC, species);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        item = in.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        species = in.read("species", Identifier.CODEC).orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private class Handler implements IItemHandlerModifiable {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return item;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            setItem(stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!item.isEmpty() || !canInsert(stack)) return stack;
            if (!simulate) setItem(stack.copyWithCount(1));
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || !canExtract()) return ItemStack.EMPTY;
            int n = Math.min(amount, item.getCount());
            ItemStack out = item.copyWithCount(n);
            if (!simulate) {
                ItemStack rest = item.copy();
                rest.shrink(n);
                setItem(rest.isEmpty() ? ItemStack.EMPTY : rest);
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canInsert(stack);
        }
    }
}
