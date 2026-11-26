package dev.oculifelis.careerbees.item;

import com.accbdd.complicated_bees.bees.BeeHousingModifier;
import com.accbdd.complicated_bees.bees.IBeeHousing;
import com.accbdd.complicated_bees.item.FrameItem;
import dev.oculifelis.careerbees.Config;
import dev.oculifelis.careerbees.effect.CareerEffect;
import dev.oculifelis.careerbees.registry.ModItems;
import dev.oculifelis.careerbees.util.Bees;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Frames that act once per bee cycle, through Complicated Bees' frame cycle hook. */
public abstract class ActiveFrameItem extends FrameItem {
    protected ActiveFrameItem(Properties props, BeeHousingModifier modifier, ModConfigSpec.ConfigValue<Boolean> enabled) {
        super(props, modifier, enabled);
    }

    /** Each cycle, a chance to make a Vial of Pheromones for the queen's primary or secondary species. */
    public static final class Pheromone extends ActiveFrameItem {
        public Pheromone(Properties props, BeeHousingModifier modifier, ModConfigSpec.ConfigValue<Boolean> enabled) {
            super(props, modifier, enabled);
        }

        @Override
        public void onBeeCycle(IBeeHousing housing, ItemStack frame, ItemStack queen) {
            if (!(housing instanceof BlockEntity be) || !(be.getLevel() instanceof ServerLevel level)) return;
            if (level.getRandom().nextDouble() >= Config.PHEROMONE_CHANCE.get()) return;
            Identifier species = Bees.speciesId(queen, level.getRandom().nextBoolean());
            if (species == null) species = Bees.speciesId(queen, true);
            if (species == null) return;
            housing.addToOutput(PheromonesItem.of(ModItems.PHEROMONES.get(), species));
        }
    }

    /** Each cycle, Poison II for 30 s on every living thing within 2 blocks, apiarists in full armour excepted. */
    public static final class Poison extends ActiveFrameItem {
        public static final double RANGE = 2;

        public Poison(Properties props, BeeHousingModifier modifier, ModConfigSpec.ConfigValue<Boolean> enabled) {
            super(props, modifier, enabled);
        }

        @Override
        public void onBeeCycle(IBeeHousing housing, ItemStack frame, ItemStack queen) {
            if (!(housing instanceof BlockEntity be) || !(be.getLevel() instanceof ServerLevel level)) return;
            Vec3 c = Vec3.atCenterOf(be.getBlockPos());
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(RANGE))) {
                if (e instanceof Player p && (CareerEffect.protectedApiarist(p) || p.isSpectator())) continue;
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 1));
                level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF87A363),
                        e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.3, 0.4, 0.3, 0);
            }
        }
    }
}
