package dev.oculifelis.careerbees.item;

import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.item.BeeItem;
import com.accbdd.complicated_bees.item.DroneItem;
import com.accbdd.complicated_bees.item.QueenItem;
import dev.oculifelis.careerbees.Config;
import dev.oculifelis.careerbees.effect.CareerEffect;
import dev.oculifelis.careerbees.effect.Guns;
import dev.oculifelis.careerbees.entity.BeeSwarm;
import dev.oculifelis.careerbees.menu.GunMenu;
import dev.oculifelis.careerbees.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Six chambers of bees and a honey tank. Each shot launches a swarm of the selected chamber's bees (see
 * {@link BeeSwarm}); drones are used up, princesses and queens are not.
 */
public class BeeGunItem extends Item {
    public static final int OFFHAND = 40, COOLDOWN = 10;

    public BeeGunItem(Properties props) {
        super(props.stacksTo(1));
    }

    public static int capacity() {
        return Config.server(Config.GUN_CAPACITY);
    }

    public static int honey(ItemStack gun) {
        return gun.getOrDefault(ModComponents.HONEY.get(), 0);
    }

    public static int chamber(ItemStack gun) {
        return Mth.clamp(gun.getOrDefault(ModComponents.CHAMBER.get(), 0), 0, Chambers.SIZE - 1);
    }

    public static Chambers chambers(ItemStack gun) {
        return gun.getOrDefault(ModComponents.CHAMBERS.get(), Chambers.EMPTY);
    }

    /** Firing and switching chamber change the gun's data; the gun stays up instead of being drawn again. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    /** True for a bee whose career can be fired. */
    public static boolean loadable(ItemStack bee) {
        return bee.getItem() instanceof BeeItem && Guns.gun(Guns.effect(bee)) != null;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : OFFHAND;
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new GunMenu(id, inv, slot),
                        Component.translatable("container.careerbees.bee_gun")), buf -> buf.writeVarInt(slot));
            }
            return InteractionResult.SUCCESS;
        }
        int chamber = chamber(gun);
        ItemStack bee = chambers(gun).get(chamber);
        CareerEffect effect = bee.isEmpty() ? null : Guns.effect(bee);
        Guns.Gun g = Guns.gun(effect);
        int cost = Config.server(Config.GUN_PER_SHOT);
        boolean free = player.getAbilities().instabuild;
        if (player.getCooldowns().isOnCooldown(gun) || g == null || (!free && honey(gun) < cost)) {
            if (!level.isClientSide()) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.4f);
                player.getCooldowns().addCooldown(gun, COOLDOWN);
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            ItemStack queen = queen(bee);
            if (queen.isEmpty()) return InteractionResult.FAIL;
            BeeSwarm swarm = BeeSwarm.fire(server, player, queen, g, effect);
            int[] r = Guns.radii(queen);
            float power = power(r);
            swarm.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, power, 1f);
            server.addFreshEntity(swarm);
            if (bee.getItem() instanceof DroneItem && !free) gun.set(ModComponents.CHAMBERS.get(), chambers(gun).with(chamber, bee.copyWithCount(bee.getCount() - 1)));
            if (!free) gun.set(ModComponents.HONEY.get(), honey(gun) - cost);
            player.getCooldowns().addCooldown(gun, COOLDOWN);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEEHIVE_EXIT, SoundSource.PLAYERS, 1f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }

    /** The queen whose genes the swarm carries: a queen as is, anything else crossed with itself. */
    public static ItemStack queen(ItemStack bee) {
        if (bee.getItem() instanceof QueenItem) return bee.copyWithCount(1);
        ItemStack one = bee.copyWithCount(1);
        return GeneticHelper.createQueenFromPrincessAndDrone(one, one.copy());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return honey(stack) < capacity();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * Math.min(honey(stack), capacity()) / Math.max(1, capacity()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = Math.min(honey(stack), capacity()) / (float) Math.max(1, capacity());
        return Mth.hsvToRgb(f / 3f, 1f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.careerbees.bee_gun.honey", honey(stack), capacity()).withStyle(ChatFormatting.GOLD));
        int c = chamber(stack);
        out.accept(Component.translatable("tooltip.careerbees.bee_gun.chamber", c + 1, name(chambers(stack).get(c))).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("tooltip.careerbees.bee_gun.use").withStyle(ChatFormatting.DARK_GRAY));
        out.accept(Component.translatable("tooltip.careerbees.bee_gun.scroll").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static Component name(@Nullable ItemStack bee) {
        if (bee == null || bee.isEmpty()) return Component.translatable("tooltip.careerbees.bee_gun.empty");
        return bee.getCount() > 1 ? Component.literal(bee.getCount() + " x ").append(bee.getHoverName()) : bee.getHoverName();
    }

    /** Launch speed: (territory x + y + z) x 0.3 / 24, at least 0.25 so tiny territories still fly. */
    public static float power(int[] radii) {
        return (float) Math.max(0.25, (radii[0] * 2 + radii[1]) * 0.3 / 24.0);
    }
}
