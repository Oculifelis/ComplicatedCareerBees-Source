package dev.oculifelis.careerbees.block;

import com.mojang.serialization.MapCodec;
import dev.oculifelis.careerbees.Config;
import dev.oculifelis.careerbees.effect.PedestalEffect;
import dev.oculifelis.careerbees.item.PheromonesItem;
import dev.oculifelis.careerbees.registry.ModBlocks;
import dev.oculifelis.careerbees.util.Bees;
import com.accbdd.complicated_bees.bees.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Flower Pedestal. The advanced one is bound to a species by a Vial of Pheromones. */
public class PedestalBlock extends BaseEntityBlock {
    public static final MapCodec<PedestalBlock> BASIC_CODEC = simpleCodec(p -> new PedestalBlock(p, false));
    public static final MapCodec<PedestalBlock> ADVANCED_CODEC = simpleCodec(p -> new PedestalBlock(p, true));
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 10, 14);

    private final boolean advanced;

    public PedestalBlock(Properties props, boolean advanced) {
        super(props);
        this.advanced = advanced;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return advanced ? ADVANCED_CODEC : BASIC_CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PedestalBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PedestalBlockEntity be)) return InteractionResult.PASS;
        if (stack.getItem() instanceof PheromonesItem) {
            if (advanced) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            Identifier species = PheromonesItem.species(stack);
            if (!(Bees.defaultEffect(species) instanceof PedestalEffect)) {
                if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("message.careerbees.pedestal.no_use"));
                return InteractionResult.SUCCESS;
            }
            ItemStack held = be.getItem();
            be.setItem(ItemStack.EMPTY);
            level.setBlock(pos, ModBlocks.PEDESTAL_ADVANCED.get().defaultBlockState(), 3);
            if (level.getBlockEntity(pos) instanceof PedestalBlockEntity bound) {
                bound.bind(species);
                bound.setItem(held);
            }
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }
        if (!be.canInsert(stack)) return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack old = be.getItem();
        be.setItem(stack.copyWithCount(1));
        stack.consume(1, player);
        if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PedestalBlockEntity be) || be.getItem().isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack old = be.getItem();
        be.setItem(ItemStack.EMPTY);
        player.getInventory().placeItemBackInInventory(old);
        return InteractionResult.SUCCESS;
    }

    /** Ambient spell motes in the bound species' two colours, every 8 ticks. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!advanced || !Config.PEDESTAL_PARTICLES.get() || level.getGameTime() % 8 != 0) return;
        if (!(level.getBlockEntity(pos) instanceof PedestalBlockEntity be)) return;
        Species s = Bees.species(be.species());
        if (s == null) return;
        int colour = random.nextBoolean() ? s.getColor() : s.getNestColor();
        level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | colour),
                pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.1 + random.nextDouble() * 0.3, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0, 0);
    }
}
