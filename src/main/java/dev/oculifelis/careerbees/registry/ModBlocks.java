package dev.oculifelis.careerbees.registry;

import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.block.PedestalBlock;
import dev.oculifelis.careerbees.block.PedestalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(CareerBees.MODID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CareerBees.MODID);

    public static final DeferredBlock<PedestalBlock> PEDESTAL = REGISTER.registerBlock("flower_pedestal",
            p -> new PedestalBlock(p, false), p -> p.noOcclusion().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<PedestalBlock> PEDESTAL_ADVANCED = REGISTER.registerBlock("flower_pedestal_advanced",
            p -> new PedestalBlock(p, true), p -> p.noOcclusion().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));

    public static final Supplier<BlockEntityType<PedestalBlockEntity>> PEDESTAL_ENTITY = ENTITIES.register("flower_pedestal",
            () -> new BlockEntityType<>(PedestalBlockEntity::new, PEDESTAL.get(), PEDESTAL_ADVANCED.get()));

    public static final DeferredItem<BlockItem> PEDESTAL_ITEM = ModItems.addBlock(PEDESTAL);
    public static final DeferredItem<BlockItem> PEDESTAL_ADVANCED_ITEM = ModItems.REGISTER.registerSimpleBlockItem(PEDESTAL_ADVANCED);

    private ModBlocks() {}
}
