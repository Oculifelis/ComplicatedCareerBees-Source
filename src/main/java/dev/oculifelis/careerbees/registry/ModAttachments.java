package dev.oculifelis.careerbees.registry;

import com.mojang.serialization.Codec;
import dev.oculifelis.careerbees.CareerBees;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CareerBees.MODID);

    /** Effect ticks counted since the housing's effect last fired. */
    public static final Supplier<AttachmentType<Integer>> EFFECT_TICKS = REGISTER.register("effect_ticks",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("ticks")).build());

    private ModAttachments() {}
}
