package dev.oculifelis.careerbees;

import com.accbdd.complicated_bees.port.PortCapabilities;
import dev.oculifelis.careerbees.network.SetFilterTag;
import dev.oculifelis.careerbees.registry.*;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CareerBees.MODID)
public class CareerBees {
    public static final String MODID = "careerbees";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public CareerBees(ModContainer container, IEventBus bus) {
        container.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC, "careerbees-common.toml");
        container.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC, "careerbees-server.toml");
        ModComponents.REGISTER.register(bus);
        ModAttachments.REGISTER.register(bus);
        ModBlocks.REGISTER.register(bus);
        ModBlocks.ENTITIES.register(bus);
        ModItems.REGISTER.register(bus);
        ModTabs.REGISTER.register(bus);
        ModEffects.REGISTER.register(bus);
        ModConditions.REGISTER.register(bus);
        ModRecipes.SERIALIZERS.register(bus);
        ModMenus.REGISTER.register(bus);
        dev.oculifelis.careerbees.registry.ModEntities.REGISTER.register(bus);
        bus.addListener(SetFilterTag::register);
        bus.addListener(dev.oculifelis.careerbees.network.SelectChamber::register);
        bus.addListener(CareerBees::capabilities);
        dev.oculifelis.careerbees.effect.CareerDataMaps.init(bus);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        PortCapabilities.registerBlockEntity(event, PortCapabilities.ItemHandler.BLOCK, ModBlocks.PEDESTAL_ENTITY.get(), (be, side) -> be.handler());
    }
}
