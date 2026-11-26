package dev.oculifelis.careerbees.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/** The Bee-Bee Gun's third-person hold: the gun arm straight out along the look, the other arm left at the side. */
public final class GunPose {
    public static final EnumProxy<HumanoidModel.ArmPose> PISTOL = new EnumProxy<>(HumanoidModel.ArmPose.class, false, false,
            (IArmPoseTransformer) GunPose::pistol);

    private static void pistol(HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm arm) {
        ModelPart a = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        a.xRot = model.head.xRot - Mth.HALF_PI;
        a.yRot = model.head.yRot + (arm == HumanoidArm.RIGHT ? -0.08F : 0.08F);
        a.zRot = 0.0F;
    }

    public static HumanoidModel.ArmPose pose() {
        return PISTOL.getValue();
    }

    private GunPose() {}
}
