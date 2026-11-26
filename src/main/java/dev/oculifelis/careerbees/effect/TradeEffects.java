package dev.oculifelis.careerbees.effect;

import dev.oculifelis.careerbees.compat.Mc;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** The businessman and junk seller bees, which trade with the adjacent inventories using vanilla villager trades. */
public final class TradeEffects {
    private static MinecraftServer cachedFor;
    private static List<MerchantOffer> cached = List.of();

    /** Every vanilla trade offer, rolled once per server with a stand-in villager at the housing. */
    public static synchronized List<MerchantOffer> offers(ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (cachedFor == level.getServer()) return cached;
        List<MerchantOffer> out = new ArrayList<>();
        Villager villager = new Villager(Mc.VILLAGER, level);
        villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.registryAccess().lookupOrThrow(Registries.TRADE_SET).listElements().forEach(setHolder -> {
            TradeSet set = setHolder.value();
            for (Holder<VillagerTrade> trade : set.getTrades()) {
                try {
                    LootContext ctx = new LootContext.Builder(new LootParams.Builder(level)
                            .withParameter(LootContextParams.ORIGIN, villager.position())
                            .withParameter(LootContextParams.THIS_ENTITY, villager)
                            .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                            .create(LootContextParamSets.VILLAGER_TRADE))
                            .create(set.randomSequence());
                    MerchantOffer offer = trade.value().getOffer(ctx);
                    if (offer != null && !offer.getResult().isEmpty()) out.add(offer);
                } catch (RuntimeException ignored) {
                    // a trade whose conditions need a real villager is skipped
                }
            }
        });
        villager.discard();
        cachedFor = level.getServer();
        cached = List.copyOf(out);
        return cached;
    }

    /** Whether the inventories hold enough of every cost; pulls them when take is true. */
    static boolean pay(List<IItemHandler> inv, List<ItemCost> costs, boolean take) {
        for (ItemCost c : costs) {
            int need = c.count();
            for (IItemHandler h : inv) {
                for (int i = 0; i < h.getSlots() && need > 0; i++) {
                    ItemStack s = h.getStackInSlot(i);
                    if (s.isEmpty() || !c.test(s)) continue;
                    need -= h.extractItem(i, need, !take).getCount();
                }
            }
            if (need > 0) return false;
        }
        return true;
    }

    static List<ItemCost> costs(MerchantOffer o) {
        List<ItemCost> out = new ArrayList<>();
        out.add(o.getItemCostA());
        o.getItemCostB().ifPresent(out::add);
        return out;
    }

    static boolean filtered(CareerEffect.Ctx ctx, MerchantOffer o) {
        for (ItemCost c : costs(o)) if (!ItemFilter.passes(ctx, new ItemStack(c.item(), c.count()))) return false;
        return true;
    }

    private static boolean emerald(ItemStack s) {
        return s.is(CareerDataMaps.TRADE_CURRENCY);
    }

    public static final class Business extends CareerEffect {
        public Business() { super("business", 100, 1); }

        @Override
        protected void fire(Ctx ctx) {
            List<IItemHandler> inv = adjacentInventories(ctx);
            if (inv.isEmpty()) return;
            List<MerchantOffer> pool = new ArrayList<>();
            for (MerchantOffer o : offers(ctx.level(), ctx.pos())) {
                if (emerald(o.getResult()) && !emerald(o.getCostA()) && filtered(ctx, o)) pool.add(o);
            }
            Collections.shuffle(pool, new java.util.Random(ctx.random().nextLong()));
            for (MerchantOffer o : pool) {
                List<ItemCost> costs = costs(o);
                if (!pay(inv, costs, false)) continue;
                pay(inv, costs, true);
                int payout = o.getResult().getCount();
                if (payout >= 2) payout = payout / 2 + ctx.random().nextInt(payout - 1 - payout / 2 + 1);
                output(ctx, new ItemStack(o.getResult().getItem(), payout));
                return;
            }
        }
    }

    public static final class JunkSale extends CareerEffect {
        public JunkSale() { super("junk_sale", 100, 1); }

        @Override
        protected void fire(Ctx ctx) {
            List<IItemHandler> inv = adjacentInventories(ctx);
            if (inv.isEmpty()) return;
            List<MerchantOffer> pool = new ArrayList<>();
            for (MerchantOffer o : offers(ctx.level(), ctx.pos())) {
                if (emerald(o.getCostA()) && o.getItemCostB().isEmpty() && !emerald(o.getResult())) pool.add(o);
            }
            if (pool.isEmpty()) return;
            int tries = 2 + ctx.random().nextInt(10);
            for (int t = 0; t < tries; t++) {
                MerchantOffer o = pool.get(ctx.random().nextInt(pool.size()));
                ItemStack item = plain(ctx, o.getResult());
                if (!ItemFilter.passes(ctx, item)) continue;
                List<ItemCost> costs = costs(o);
                if (!pay(inv, costs, false)) continue;
                pay(inv, costs, true);
                output(ctx, item);
            }
        }

        /** A fresh copy of the item that keeps only its enchantments; damageable items arrive badly worn. */
        static ItemStack plain(CareerEffect.Ctx ctx, ItemStack result) {
            Item item = result.getItem();
            ItemStack out = new ItemStack(item, result.getCount());
            Optional.ofNullable(result.get(DataComponents.ENCHANTMENTS)).ifPresent(e -> out.set(DataComponents.ENCHANTMENTS, e));
            Optional.ofNullable(result.get(DataComponents.STORED_ENCHANTMENTS)).ifPresent(e -> out.set(DataComponents.STORED_ENCHANTMENTS, e));
            if (out.isDamageableItem()) {
                int max = out.getMaxDamage();
                out.setDamageValue(max - Math.max(1, ctx.random().nextInt(Math.max(1, max / 2))));
            }
            return out;
        }
    }

    private TradeEffects() {}
}
