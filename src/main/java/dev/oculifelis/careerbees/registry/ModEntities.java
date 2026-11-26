package dev.oculifelis.careerbees.registry;

import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.entity.BeeSwarm;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(Registries.ENTITY_TYPE, CareerBees.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BeeSwarm>> SWARM = REGISTER.register("bee_swarm",
            id -> EntityType.Builder.<BeeSwarm>of(BeeSwarm::new, MobCategory.MISC).sized(0.5f, 0.5f)
                    .clientTrackingRange(4).updateInterval(1).noSave().noSummon()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private ModEntities() {}
}
