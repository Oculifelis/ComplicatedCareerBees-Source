package dev.oculifelis.careerbees.clientshot;

import com.accbdd.complicated_bees.block.entity.ApiaryBlockEntity;
import com.accbdd.complicated_bees.block.entity.CentrifugeBlockEntity;
import com.accbdd.complicated_bees.item.FrameItem;
import com.accbdd.complicated_bees.item.UpgradeItem;
import com.accbdd.complicated_bees.registry.ItemsRegistration;
import com.accbdd.complicated_bees.registry.SpeciesRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only client check of Complicated Bees itself, on only with -Dcbshot=true: blocks in the world, the Apiary screen
 * with a princess, drone and frame working, the Centrifuge screen with an upgrade, an inventory of bee items, and the
 * frame/upgrade data map values the client sees. Screenshots cbshot_*.png, log lines "CBSHOT".
 */
@EventBusSubscriber(modid = "careerbees_clientshot", value = Dist.CLIENT)
public final class CbShot {
    private static final Logger LOG = LoggerFactory.getLogger("CbShot");
    static final boolean ON = Boolean.getBoolean("cbshot");
    private static final BlockPos APIARY = new BlockPos(3, -60, 0);
    private static final BlockPos CENTRIFUGE = new BlockPos(3, -60, 2);
    private static int t;
    private static int fails;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) { t = 0; return; }
        t++;
        MinecraftServer server = mc.getSingleplayerServer();
        try {
            switch (t) {
                case 100 -> server.execute(() -> world(server));
                case 115 -> server.execute(() -> guard(() -> setup(server)));
                case 130 -> shot(mc, "world_blocks");
                case 135 -> checkMaps();
                case 140 -> { ClientShot.showGui(mc); server.execute(() -> open(server, APIARY)); }
                case 190 -> shot(mc, "apiary_gui");
                case 420 -> { server.execute(() -> apiaryState(server)); shot(mc, "apiary_gui_later"); }
                case 430 -> mc.player.closeContainer();
                case 450 -> server.execute(() -> open(server, CENTRIFUGE));
                case 500 -> shot(mc, "centrifuge_gui");
                case 510 -> mc.player.closeContainer();
                case 520 -> server.execute(() -> cmd(server, "time set 1000"));
                case 530 -> mc.setScreen(new InventoryScreen(mc.player));
                case 570 -> shot(mc, "inventory_bees");
                case 580 -> { mc.setScreen(null); LOG.info("CBSHOT done fails={}", fails); }
                default -> { }
            }
        } catch (Throwable e) {
            fails++;
            LOG.error("CBSHOT error at {}", t, e);
        }
    }

    private static void cmd(MinecraftServer server, String c) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), c);
    }

    private static List<Identifier> species(MinecraftServer server) {
        var reg = server.registryAccess().lookupOrThrow(SpeciesRegistration.SPECIES_REGISTRY_KEY);
        List<Identifier> out = new ArrayList<>();
        for (var key : reg.keySet()) if (key.getNamespace().equals("complicated_bees")) out.add(key);
        out.sort(null);
        return out;
    }

    private static ItemStack bee(Identifier id, net.minecraft.world.item.Item kind) {
        return SpeciesRegistration.getFromIdentifier(id).toStack(kind);
    }

    private static void guard(Runnable r) {
        try { r.run(); } catch (Throwable e) { fails++; LOG.error("CBSHOT error in server task", e); }
    }

    private static void world(MinecraftServer server) {
        cmd(server, "gamemode survival @a");
        cmd(server, "time set 1000");
        cmd(server, "weather clear");
        cmd(server, "gamerule doDaylightCycle false");
        cmd(server, "fill 1 -60 -4 7 -60 4 minecraft:air");
        cmd(server, "fill 5 -60 -4 7 -60 4 minecraft:dandelion");
        cmd(server, "setblock " + APIARY.getX() + " -60 " + APIARY.getZ() + " complicated_bees:apiary");
        cmd(server, "setblock " + CENTRIFUGE.getX() + " -60 " + CENTRIFUGE.getZ() + " complicated_bees:centrifuge");
        cmd(server, "setblock 3 -60 -2 complicated_bees:bee_nest");
        cmd(server, "tp @a 0.5 -60 0.5 -90 15");
    }

    private static void setup(MinecraftServer server) {
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);

        Identifier forest = Identifier.fromNamespaceAndPath("complicated_bees", "forest");
        if (p.level().getBlockEntity(APIARY) instanceof ApiaryBlockEntity a) {
            a.getBeeItems().setStackInSlot(0, bee(forest, ItemsRegistration.PRINCESS.get()));
            a.getBeeItems().setStackInSlot(1, bee(forest, ItemsRegistration.DRONE.get()));
            a.getFrameItems().setStackInSlot(0, new ItemStack(ItemsRegistration.FRAME.get()));
            var mods = a.getHousingModifiers();
            LOG.info("CBSHOT apiary filled frames={} productivity={}", mods.size(), mods.isEmpty() ? -1f : mods.get(0).getProductivityMod());
        } else {
            fails++;
            LOG.error("CBSHOT no apiary block entity");
        }
        if (p.level().getBlockEntity(CENTRIFUGE) instanceof CentrifugeBlockEntity c) {
            ItemStack left = ((net.neoforged.neoforge.items.IItemHandler) c.getUpgradeItemHandler())
                    .insertItem(0, new ItemStack(ItemsRegistration.WAXED_UPGRADE.get()), false);
            LOG.info("CBSHOT centrifuge upgrade inserted={}", left.isEmpty());
        } else {
            fails++;
            LOG.error("CBSHOT no centrifuge block entity");
        }

        p.getInventory().clearContent();
        List<Identifier> sp = species(server);
        int slot = 0;
        for (int i = 0; i < sp.size() && slot < 36; i++) {
            var kind = switch (i % 3) {
                case 0 -> ItemsRegistration.PRINCESS.get();
                case 1 -> ItemsRegistration.DRONE.get();
                default -> ItemsRegistration.QUEEN.get();
            };
            p.getInventory().setItem(slot++, bee(sp.get(i), kind));
        }
        LOG.info("CBSHOT setup species={} inventory={}", sp.size(), slot);
    }

    private static void open(MinecraftServer server, BlockPos pos) {
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        var state = p.level().getBlockState(pos);
        var r = state.useWithoutItem(p.level(), p, new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false));
        LOG.info("CBSHOT open {} result={} menu={}", state.getBlock(), r, p.containerMenu.getClass().getSimpleName());
    }

    private static void apiaryState(MinecraftServer server) {
        if (server.overworld().getBlockEntity(APIARY) instanceof ApiaryBlockEntity a) {
            ItemStack bee = a.getBeeItems().getStackInSlot(0);
            int out = 0;
            for (int i = 0; i < a.getOutputItems().getSlots(); i++) out += a.getOutputItems().getStackInSlot(i).getCount();
            LOG.info("CBSHOT apiary later bee={} progress={} errors={} output={}", bee.getItem(), a.getCycleProgress(), a.getErrors(), out);
        }
    }

    /** Frame and upgrade stats come from the data maps (synced to the client); compare with the shipped JSON values. */
    private static void checkMaps() {
        check("frame productivity", ((FrameItem) ItemsRegistration.FRAME.get()).getModifier().getProductivityMod(), 1.25f);
        check("waxed_frame productivity", ((FrameItem) ItemsRegistration.WAXED_FRAME.get()).getModifier().getProductivityMod(), 1.5f);
        check("hot_frame lifespan", ((FrameItem) ItemsRegistration.HOT_FRAME.get()).getModifier().getLifespanMod(), 0.6f);
        check("waxed_upgrade efficiency", ((UpgradeItem) ItemsRegistration.WAXED_UPGRADE.get()).getModifier().getEfficiencyMod(), 1.25f);
        check("basic_upgrade speed", ((UpgradeItem) ItemsRegistration.BASIC_UPGRADE.get()).getModifier().getSpeedMod(), 1.1f);
        var hot = ((FrameItem) ItemsRegistration.HOT_FRAME.get()).getModifier();
        LOG.info("CBSHOT hot_frame temperature={} tooltip={}", hot.getTemperatureMod(),
                hot.getTooltipComponent().stream().map(c -> c.getString()).toList());
    }

    private static void check(String what, float got, float want) {
        boolean ok = Math.abs(got - want) < 1e-4;
        if (!ok) fails++;
        LOG.info("CBSHOT map {} got={} want={} {}", what, got, want, ok ? "PASS" : "FAIL");
    }

    private static void shot(Minecraft mc, String name) throws Exception {
        Screenshot.grab(mc.gameDirectory, "cbshot_" + name + ".png", ClientShot.renderTarget(mc), 1, msg -> { });
        LOG.info("CBSHOT shot {} screen={}", name, ClientShot.screen(mc) == null ? "none" : ClientShot.screen(mc).getClass().getSimpleName());
    }
}
