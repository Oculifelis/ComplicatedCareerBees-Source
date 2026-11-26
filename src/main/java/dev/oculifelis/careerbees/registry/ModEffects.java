package dev.oculifelis.careerbees.registry;

import com.accbdd.complicated_bees.bees.effect.IBeeEffect;
import com.accbdd.complicated_bees.registry.BeeEffectRegistration;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.Config;
import dev.oculifelis.careerbees.effect.IdleEffect;
import dev.oculifelis.careerbees.effect.LifeEffects;
import dev.oculifelis.careerbees.effect.PedestalEffects;
import dev.oculifelis.careerbees.effect.SocialEffects;
import dev.oculifelis.careerbees.effect.TradeEffects;
import dev.oculifelis.careerbees.effect.PowerEffects;
import dev.oculifelis.careerbees.effect.WorldEffects;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class ModEffects {
    public static final DeferredRegister<IBeeEffect> REGISTER = DeferredRegister.create(BeeEffectRegistration.BEE_REGISTRY_KEY, CareerBees.MODID);

    private static final Map<String, Supplier<IBeeEffect>> WORKING = Map.ofEntries(
            Map.entry("healing", LifeEffects.Healing::new),
            Map.entry("curative", LifeEffects.Curative::new),
            Map.entry("electrify", LifeEffects.Electrify::new),
            Map.entry("husbandry", LifeEffects.Husbandry::new),
            Map.entry("butcher", LifeEffects.Butcher::new),
            Map.entry("taxation", LifeEffects.Taxation::new),
            Map.entry("creeper", LifeEffects.CreeperSpawn::new),
            Map.entry("assassin", LifeEffects.Assassin::new),
            Map.entry("thief", LifeEffects.Thief::new),
            Map.entry("burning", WorldEffects.Burning::new),
            Map.entry("mason", WorldEffects.Mason::new),
            Map.entry("lumber", WorldEffects.Lumber::new),
            Map.entry("teleposition", WorldEffects.Teleposition::new),
            Map.entry("explosion", WorldEffects.Blast::new),
            Map.entry("mining", () -> new WorldEffects.Mining("mining", WorldEffects.Mining.Tool.PLAIN)),
            Map.entry("mining_fortune", () -> new WorldEffects.Mining("mining_fortune", WorldEffects.Mining.Tool.FORTUNE)),
            Map.entry("mining_silk", () -> new WorldEffects.Mining("mining_silk", WorldEffects.Mining.Tool.SILK)),
            Map.entry("pickup", WorldEffects.Pickup::new),
            Map.entry("energy", PowerEffects.Energy::new),
            Map.entry("clockwinding", PowerEffects.Clockwinding::new),
            Map.entry("temporal", PowerEffects.Temporal::new),
            Map.entry("police", SocialEffects.Police::new),
            Map.entry("painting", SocialEffects.Artist::new),
            Map.entry("jazz", SocialEffects.Jazz::new),
            Map.entry("business", TradeEffects.Business::new),
            Map.entry("junk_sale", TradeEffects.JunkSale::new),
            Map.entry("crusher", PedestalEffects.Crusher::new),
            Map.entry("smelt", PedestalEffects.Smelt::new),
            Map.entry("honey_ingot", PedestalEffects.HoneyIngot::new),
            Map.entry("repair", PedestalEffects.Repair::new),
            Map.entry("sharpen", PedestalEffects.Sharpen::new),
            Map.entry("armorer", PedestalEffects.Armorer::new),
            Map.entry("glazing", PedestalEffects.Glazing::new),
            Map.entry("retraining", PedestalEffects.Retraining::new),
            Map.entry("priest", PedestalEffects.Priest::new));

    /** Ids registered as idle until their pedestal, report or client side exists. */
    public static final Set<String> IDLE = new HashSet<>();

    static {
        for (String id : Config.EFFECTS) {
            Supplier<IBeeEffect> s = WORKING.get(id);
            if (s == null) {
                IDLE.add(id);
                REGISTER.register(id, () -> new IdleEffect(id));
            } else {
                REGISTER.register(id, s);
            }
        }
    }

    private ModEffects() {}
}
