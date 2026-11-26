package dev.oculifelis.careerbees.clientshot;

import com.accbdd.complicated_bees.registry.SpeciesRegistration;
import dev.oculifelis.careerbees.effect.Guns;
import dev.oculifelis.careerbees.entity.BeeSwarm;
import dev.oculifelis.careerbees.item.BeeGunItem;
import dev.oculifelis.careerbees.menu.GunMenu;
import dev.oculifelis.careerbees.registry.ModComponents;
import dev.oculifelis.careerbees.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev-only client check beside the Port Test Kit: loads a Bee-Bee Gun, screenshots its screen, the chamber selector,
 * a swarm in flight and a swarm latched on a mob (ccbshot_*.png). Logs "CCBSHOT" lines.
 */
@Mod(value = "careerbees_clientshot", dist = Dist.CLIENT)
@EventBusSubscriber(modid = "careerbees_clientshot", value = Dist.CLIENT)
public final class ClientShot {
    private static final Logger LOG = LoggerFactory.getLogger("CcbShot");
    private static int t;
    private static BeeSwarm swarm;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (CbShot.ON) return;
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) { t = 0; return; }
        t++;
        var server = mc.getSingleplayerServer();
        try {
            switch (t) {
                case 120 -> server.execute(() -> setup(server));
                case 160 -> { showGui(mc); server.execute(() -> openGun(server)); }
                case 200 -> shot(mc, "gun_screen");
                case 210 -> { if (mc.player != null) mc.player.closeContainer(); }
                case 230 -> showSelector();
                case 240 -> shot(mc, "selector");
                case 270 -> server.execute(() -> fire(server));
                case 282 -> shot(mc, "swarm_flight");
                case 330 -> shot(mc, "swarm_latched");
                case 340 -> { LOG.info("CCBSHOT swarm alive={} ", swarm != null && !swarm.isRemoved()); LOG.info("CCBSHOT done"); }
                default -> { }
            }
        } catch (Throwable e) {
            LOG.error("CCBSHOT error at {}", t, e);
        }
    }

    private static ItemStack drone(net.minecraft.server.MinecraftServer server, String effectId) {
        var reg = server.registryAccess().lookupOrThrow(SpeciesRegistration.SPECIES_REGISTRY_KEY);
        for (var key : reg.keySet()) {
            if (!key.getNamespace().equals("careerbees")) continue;
            var sp = SpeciesRegistration.getFromIdentifier(key);
            if (sp == null) continue;
            var d = sp.toStack(com.accbdd.complicated_bees.registry.ItemsRegistration.DRONE.get());
            var e = Guns.effect(d);
            if (e != null && e.id().equals(effectId)) return d;
        }
        return ItemStack.EMPTY;
    }

    private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void setup(net.minecraft.server.MinecraftServer server) {
        ServerPlayer p = player(server);
        p.getInventory().clearContent();
        ItemStack gun = new ItemStack(ModItems.BEE_GUN.get());
        var chambers = BeeGunItem.chambers(gun);
        String[] ids = {"police", "healing", "husbandry", "smelt", "lumber", "temporal"};
        for (int i = 0; i < ids.length; i++) {
            ItemStack d = drone(server, ids[i]);
            if (!d.isEmpty()) chambers = chambers.with(i, d.copyWithCount(8));
        }
        gun.set(ModComponents.CHAMBERS.get(), chambers);
        gun.set(ModComponents.HONEY.get(), 600);
        p.getInventory().setItem(0, gun);
        p.getInventory().setItem(1, new ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE, 5));
        p.getInventory().setSelectedSlot(0);
        p.teleportTo(p.level(), 0.5, p.getY(), 0.5, java.util.Set.of(), -90f, 10f, true);
        LOG.info("CCBSHOT setup chambers={} police={}", chambers.stacks().size(), !drone(server, "police").isEmpty());
    }

    private static void openGun(net.minecraft.server.MinecraftServer server) {
        ServerPlayer p = player(server);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new GunMenu(id, inv, 0), Component.translatable("container.careerbees.bee_gun")),
                buf -> buf.writeVarInt(0));
    }

    private static void showSelector() throws Exception {
        var f = dev.oculifelis.careerbees.client.GunClient.class.getDeclaredField("shown");
        f.setAccessible(true);
        f.setInt(null, 120);
    }

    private static void fire(net.minecraft.server.MinecraftServer server) {
        ServerPlayer p = player(server);
        var level = p.level();
        Mob z = (Mob) dev.oculifelis.careerbees.compat.Mc.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
        z.setPos(p.getX() + 5, p.getY(), p.getZ());
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.setYRot(90f);
        level.addFreshEntity(z);
        ItemStack bee = drone(server, "police");
        var effect = Guns.effect(bee);
        swarm = BeeSwarm.fire(level, p, BeeGunItem.queen(bee), Guns.gun(effect), effect);
        swarm.shootFromRotation(p, 10f, -90f, 0f, 0.25f, 0f);
        level.addFreshEntity(swarm);
        LOG.info("CCBSHOT fired effect={} at zombie", effect.id());
    }

    private static void shot(Minecraft mc, String name) throws Exception {
        Screenshot.grab(mc.gameDirectory, "ccbshot_" + name + ".png", renderTarget(mc), 1, msg -> { });
        Object screen = screen(mc);
        LOG.info("CCBSHOT shot {} screen={}", name, screen == null ? "none" : screen.getClass().getSimpleName());
    }

    // 26.1.2 and 26.2 name these differently; reflection keeps one source for both targets.
    static com.mojang.blaze3d.pipeline.RenderTarget renderTarget(Minecraft mc) throws Exception {
        try {
            return (com.mojang.blaze3d.pipeline.RenderTarget) Minecraft.class.getMethod("getMainRenderTarget").invoke(mc);
        } catch (NoSuchMethodException e) {
            return (com.mojang.blaze3d.pipeline.RenderTarget) mc.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(mc.gameRenderer);
        }
    }

    static Object screen(Minecraft mc) throws Exception {
        try {
            return Minecraft.class.getField("screen").get(mc);
        } catch (NoSuchFieldException e) {
            Object gui = Minecraft.class.getField("gui").get(mc);
            return gui.getClass().getMethod("screen").invoke(gui);
        }
    }

    static void showGui(Minecraft mc) {
        try {
            mc.options.getClass().getField("hideGui").setBoolean(mc.options, false);
        } catch (NoSuchFieldException e) {
            // 26.2 has no hideGui option; the HUD state lives in Gui.hud there.
            try {
                Object gui = Minecraft.class.getField("gui").get(mc);
                Object hud = gui.getClass().getField("hud").get(gui);
                if ((Boolean) hud.getClass().getMethod("isHidden").invoke(hud)) {
                    hud.getClass().getMethod("toggle").invoke(hud);
                }
            } catch (ReflectiveOperationException e2) {
                LOG.warn("CCBSHOT could not show the HUD", e2);
            }
        } catch (ReflectiveOperationException e) {
            LOG.warn("CCBSHOT could not show the HUD", e);
        }
    }
}
