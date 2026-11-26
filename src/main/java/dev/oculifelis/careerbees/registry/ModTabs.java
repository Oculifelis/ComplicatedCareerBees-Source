package dev.oculifelis.careerbees.registry;

import dev.oculifelis.careerbees.CareerBees;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CareerBees.MODID);

    public static final Supplier<CreativeModeTab> MAIN = REGISTER.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.careerbees"))
            .icon(() -> new ItemStack(ModItems.HONEYCOMB_INGOT.get()))
            .displayItems((params, out) -> ModItems.TAB_ORDER.forEach(i -> {
                if (i == ModItems.BARK) ModItems.BARK.get().fillTab(out);
                else if (i == ModItems.PHEROMONES) ModItems.PHEROMONES.get().fillTab(out);
                else out.accept(i.get());
            }))
            .build());

    private ModTabs() {}
}
