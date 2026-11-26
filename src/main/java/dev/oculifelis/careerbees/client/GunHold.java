package dev.oculifelis.careerbees.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.item.BeeGunItem;
import dev.oculifelis.careerbees.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How the Bee-Bee Gun is held. Third person: the gun arm straight out ({@link GunPose}). First person: the gun sits where
 * vanilla holds an item, shown by its model's first-person display, and the player's arm is a fist closed on the grip with
 * the forearm along the barrel, turned a little out about the grip, as the Ultima Ratio guns are held. A shot kicks gun and
 * arm up and back, then they settle, and switching chamber gives a small click tilt.
 */
@EventBusSubscriber(modid = CareerBees.MODID, value = Dist.CLIENT)
public final class GunHold {
    /** The model's first-person display (translation px, rotation degrees, scale), as in bee_gun.json. */
    private static final float[] FP_DISPLAY = {-0.743F, 4.663F, -6.625F, -90.0F, 84.0F, 90.0F, 0.8203F};
    /** The middle of the grip's top face in the model (px), where it meets the frame. */
    private static final Vector3f GRIP_TOP = new Vector3f(4.85F, 5.0F, 8.0F);
    /** The arm's turn out (yaw) and down (pitch) about the grip, degrees. */
    private static final float GUN_YAW = 15.0F, GUN_PITCH = 0.0F;
    /**
     * The vanilla hand as a fist, in pixels: FIST_H from its top face to its bottom face; the grip's top lies GRIP_DEPTH
     * inside the top face, GRIP_Y back from the fist's end.
     */
    private static final float FIST_H = 4.0F, GRIP_Y = 0.8F, GRIP_DEPTH = -0.15F;
    /** Shot kick at its peak: up and back (blocks), muzzle up (degrees). Chamber click roll (degrees). */
    private static final float KICK_UP = 0.04F, KICK_BACK = 0.16F, KICK_PITCH = 9.0F, CLICK_ROLL = 8.0F;

    /** Development: a fixed time since the shot (ticks) for screenshots of the kick; -1 = off. */
    private static final float DEV_KICK = Float.parseFloat(System.getProperty("careerbees.dev.kick", "-1"));

    private static int clickTicks = 100;

    /** Called when the chamber is switched. */
    public static void clicked() {
        clickTicks = 0;
    }

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return GunPose.pose();
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float pt, float equip, float swing) {
                if (player.isScoping()) return false;
                place(pose, player, arm, stack, pt, equip);
                return true;
            }
        }, ModItems.BEE_GUN.get());
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (clickTicks < 100) clickTicks++;
    }

    /** Vanilla's held-item spot, lowered while the gun is drawn, then the shot kick and the chamber click. */
    private static void place(PoseStack pose, AbstractClientPlayer player, HumanoidArm arm, ItemStack stack, float pt, float equip) {
        float inv = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pose.translate(inv * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        float k = kick(player, stack, pt);
        pose.translate(0.0F, k * KICK_UP, k * KICK_BACK);
        pose.mulPose(Axis.XP.rotationDegrees(k * KICK_PITCH));
        float c = click(pt);
        if (c > 0.0F) {
            Vector3f g = gripPoint(arm == HumanoidArm.LEFT);
            pose.translate(g.x, g.y, g.z);
            pose.mulPose(Axis.ZP.rotationDegrees(inv * -c * CLICK_ROLL));
            pose.translate(-g.x, -g.y, -g.z);
        }
    }

    /** The player's fist on the grip. Vanilla still draws the gun itself after this. */
    @SubscribeEvent
    public static void render(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.BEE_GUN.get())) return;
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isScoping() || player.isInvisible()) return;
        HumanoidArm main = player.getMainArm();
        boolean left = main == HumanoidArm.LEFT;
        float own = left ? -1.0F : 1.0F;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        place(pose, player, main, stack, event.getPartialTick(), event.getEquipProgress());
        // the grip's frame: origin at the middle of the grip's top face, y along the barrel, z toward the gun's top
        Vector3f g = gripPoint(left);
        pose.translate(g.x, g.y, g.z);
        pose.mulPose(new Quaternionf().rotationXYZ((float) Math.toRadians(FP_DISPLAY[3]), (float) Math.toRadians(own * FP_DISPLAY[4]),
            (float) Math.toRadians(own * FP_DISPLAY[5])));
        pose.mulPose(new Matrix4f(0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F));
        // the fist's top face under the frame, the forearm along the barrel, turned out and down about the grip
        pose.mulPose(Axis.ZP.rotationDegrees(own * GUN_YAW));
        pose.mulPose(Axis.XP.rotationDegrees(GUN_PITCH));
        pose.translate(0.0F, GRIP_Y / 16.0F, -(FIST_H / 2.0F - GRIP_DEPTH) / 16.0F);
        pose.translate(own * 6.0F / 16.0F, -12.0F / 16.0F, 0.0F);
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
        var skin = player.getSkin().body().texturePath();
        SubmitNodeCollector out = event.getSubmitNodeCollector();
        int light = event.getPackedLight();
        if (main == HumanoidArm.RIGHT) {
            renderer.renderRightHand(pose, out, light, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE), player);
        } else {
            renderer.renderLeftHand(pose, out, light, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE), player);
        }
        pose.popPose();
    }

    /** Where the grip's top lands from the hand pose (blocks): the model's first-person display applied to it. */
    private static Vector3f gripPoint(boolean left) {
        float s = left ? -1.0F : 1.0F;
        float[] d = FP_DISPLAY;
        Matrix4f m = new Matrix4f()
            .translate(s * d[0] / 16.0F, d[1] / 16.0F, d[2] / 16.0F)
            .rotateXYZ((float) Math.toRadians(d[3]), (float) Math.toRadians(s * d[4]), (float) Math.toRadians(s * d[5]))
            .scale(d[6])
            .translate(-0.5F, -0.5F, -0.5F);
        return m.transformPosition(new Vector3f(GRIP_TOP).div(16.0F));
    }

    /** 0 at rest, up to about 0.85 just after a shot, settling back over the cooldown. */
    private static float kick(AbstractClientPlayer player, ItemStack stack, float pt) {
        float since;
        if (DEV_KICK >= 0.0F) {
            since = DEV_KICK;
        } else {
            float left = player.getCooldowns().getCooldownPercent(stack, pt);
            if (left <= 0.0F) return 0.0F;
            since = (1.0F - left) * BeeGunItem.COOLDOWN;
        }
        return (float) Math.exp(-since * 0.65F) * Math.min(1.0F, since * 3.0F + 0.2F);
    }

    /** The chamber click: a quick tilt that settles in about a third of a second. */
    private static float click(float pt) {
        float s = clickTicks + pt;
        if (s >= 8.0F) return 0.0F;
        return (float) Math.exp(-s * 0.7F) * Math.min(1.0F, s * 3.0F + 0.3F);
    }

    private GunHold() {}
}
