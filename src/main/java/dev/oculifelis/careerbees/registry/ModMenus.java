package dev.oculifelis.careerbees.registry;

import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.menu.GunMenu;
import dev.oculifelis.careerbees.menu.SettingsMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(Registries.MENU, CareerBees.MODID);

    public static final Supplier<MenuType<SettingsMenu>> SETTINGS = REGISTER.register("settings",
            () -> IMenuTypeExtension.create((windowId, inv, data) -> SettingsMenu.fromNetwork(windowId, inv)));

    public static final Supplier<MenuType<GunMenu>> GUN = REGISTER.register("bee_gun",
            () -> IMenuTypeExtension.create(GunMenu::fromNetwork));

    private ModMenus() {}
}
