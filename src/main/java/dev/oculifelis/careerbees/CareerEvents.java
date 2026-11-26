package dev.oculifelis.careerbees;

import dev.oculifelis.careerbees.condition.RecentExplosionCondition;
import dev.oculifelis.careerbees.effect.LifeEffects;
import dev.oculifelis.careerbees.effect.PedestalEffects;
import dev.oculifelis.careerbees.effect.PowerEffects;
import dev.oculifelis.careerbees.effect.SocialEffects;
import dev.oculifelis.careerbees.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

@EventBusSubscriber(modid = CareerBees.MODID)
public final class CareerEvents {
    private static final int GLAZE_TICKS = 2400;

    /** Creepers from the N.C.A. bee drop nothing; police kills drop into the housing. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (e.entityTags().contains(LifeEffects.BOMB_TAG)) {
            event.setCanceled(true);
            return;
        }
        if (e instanceof Mob mob && SocialEffects.Police.takeDrops(mob, event.getDrops())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) PowerEffects.Boosts.tick(level);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        RecentExplosionCondition.record(event.getLevel(), BlockPos.containing(event.getExplosion().center()));
    }

    /** Sharpened weapons hit harder; armoured pieces give more armour. */
    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        float sharpen = stack.getOrDefault(ModComponents.SHARPEN.get(), 0f);
        if (sharpen > 0) {
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(CareerBees.id("sharpen"), sharpen, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        float bonus = stack.getOrDefault(ModComponents.ARMOR_BONUS.get(), 0f);
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (bonus > 0 && eq != null) {
            double base = PedestalEffects.Armorer.baseArmor(stack);
            if (base > 0) {
                event.addModifier(Attributes.ARMOR, new AttributeModifier(CareerBees.id("armor_bonus"), base * bonus, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.bySlot(eq.slot()));
            }
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tip = event.getToolTip();
        float sharpen = stack.getOrDefault(ModComponents.SHARPEN.get(), 0f);
        float bonus = stack.getOrDefault(ModComponents.ARMOR_BONUS.get(), 0f);
        if (sharpen > 0 || bonus > 0) {
            tip.add(Component.translatable("tooltip.careerbees.bonus").withStyle(ChatFormatting.GOLD));
            if (sharpen > 0) tip.add(Component.translatable("tooltip.careerbees.bonus.sharpen", String.format("%.2f", sharpen)).withStyle(ChatFormatting.BLUE));
            if (bonus > 0) tip.add(Component.translatable("tooltip.careerbees.bonus.armor", Math.round(bonus * 100)).withStyle(ChatFormatting.BLUE));
        }
        int glaze = stack.getOrDefault(ModComponents.GLAZE.get(), 0);
        if (glaze > 0) {
            tip.add(Component.translatable("tooltip.careerbees.glaze", glaze / 2.0).withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Eating a glazed food gives absorption worth its glaze. */
    @SubscribeEvent
    public static void onEaten(LivingEntityUseItemEvent.Finish event) {
        int glaze = event.getItem().getOrDefault(ModComponents.GLAZE.get(), 0);
        LivingEntity e = event.getEntity();
        if (glaze <= 0 || e.level().isClientSide()) return;
        e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, GLAZE_TICKS, (int) Math.ceil(glaze / 4.0) - 1));
        e.setAbsorptionAmount(Math.max(e.getAbsorptionAmount(), glaze));
    }

    /** A glazed food can be eaten when full, as long as its glaze would still add absorption. */
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        int glaze = stack.getOrDefault(ModComponents.GLAZE.get(), 0);
        Player p = event.getEntity();
        if (glaze <= 0 || !stack.has(DataComponents.FOOD) || p.getFoodData().needsFood()) return;
        if (p.getAbsorptionAmount() >= glaze) return;
        p.startUsingItem(event.getHand());
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    private CareerEvents() {}
}
