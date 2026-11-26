package dev.oculifelis.careerbees.client;

import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.registry.ModBlocks;
import dev.oculifelis.careerbees.registry.ModMenus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = CareerBees.MODID, value = Dist.CLIENT)
public final class CareerClient {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.PEDESTAL_ENTITY.get(), PedestalRenderer::new);
        event.registerEntityRenderer(dev.oculifelis.careerbees.registry.ModEntities.SWARM.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    @SubscribeEvent
    public static void tints(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(CareerBees.id("species"), SpeciesTint.CODEC);
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SETTINGS.get(), SettingsScreen::new);
        event.register(ModMenus.GUN.get(), GunScreen::new);
    }

    private CareerClient() {}
}
