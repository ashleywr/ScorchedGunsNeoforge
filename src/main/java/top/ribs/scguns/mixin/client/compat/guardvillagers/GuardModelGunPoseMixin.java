package top.ribs.scguns.mixin.client.compat.guardvillagers;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.client.render.pose.AimPose;
import top.ribs.scguns.client.render.pose.LimbPose;
import top.ribs.scguns.client.render.pose.WeaponPose;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.item.GunItem;


@Pseudo
@Mixin(targets = "tallestegg.guardvillagers.client.models.GuardModel", remap = false)
public class GuardModelGunPoseMixin {
    private static final ResourceLocation GUARD_ID = ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard");

    @Inject(method = "setupAnim(Ltallestegg/guardvillagers/common/entities/Guard;FFFFF)V", at = @At("TAIL"), remap = false)
    private void scguns$applyGunPose(@Coerce Object entityObject, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entityObject instanceof LivingEntity entity)) {
            return;
        }
        if (!GUARD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
            return;
        }

        ItemStack heldItem = entity.getMainHandItem();
        if (!(heldItem.getItem() instanceof GunItem gunItem)) {
            return;
        }

        Gun gun = gunItem.getModifiedGun(heldItem);
        Object heldAnimation = gun.getGeneral().getGripType(heldItem).heldAnimation();
        HumanoidModel<LivingEntity> model = (HumanoidModel<LivingEntity>) (Object) this;

        if (heldAnimation instanceof WeaponPose weaponPose) {
            AimPose forwardPose = weaponPose.getCachedForwardPose();
            if (forwardPose != null) {
                float aimProgress = entity instanceof Mob mob && mob.getTarget() != null ? 1.0F : 0.0F;
                scguns$applyForwardPose(forwardPose, model.rightArm, model.leftArm, aimProgress);
                return;
            }
        }

        scguns$applyFallbackPose(model.rightArm, model.leftArm);
    }

    private static void scguns$applyForwardPose(AimPose forwardPose, ModelPart rightArm, ModelPart leftArm, float aimProgress) {
        scguns$applyLimbPose(forwardPose.getIdle().getRightArm(), forwardPose.getAiming().getRightArm(), rightArm, aimProgress, 1.0F);
        scguns$applyLimbPose(forwardPose.getIdle().getLeftArm(), forwardPose.getAiming().getLeftArm(), leftArm, aimProgress, 1.0F);
    }

    private static void scguns$applyLimbPose(LimbPose idlePose, LimbPose aimingPose, ModelPart modelPart, float aimProgress, float handedness) {
        if (idlePose == null) {
            return;
        }
        if (aimingPose == null) {
            aimingPose = idlePose;
        }

        modelPart.xRot = scguns$lerpRotation(idlePose.getRotationAngleX(), aimingPose.getRotationAngleX(), modelPart.xRot, aimProgress, 1.0F);
        modelPart.yRot = scguns$lerpRotation(idlePose.getRotationAngleY(), aimingPose.getRotationAngleY(), modelPart.yRot, aimProgress, handedness);
        modelPart.zRot = scguns$lerpRotation(idlePose.getRotationAngleZ(), aimingPose.getRotationAngleZ(), modelPart.zRot, aimProgress, handedness);

        modelPart.x = scguns$lerp(idlePose.getRotationPointX(), aimingPose.getRotationPointX(), modelPart.x, aimProgress, handedness);
        modelPart.y = scguns$lerp(idlePose.getRotationPointY(), aimingPose.getRotationPointY(), modelPart.y, aimProgress, 1.0F);
        modelPart.z = scguns$lerp(idlePose.getRotationPointZ(), aimingPose.getRotationPointZ(), modelPart.z, aimProgress, 1.0F);
    }

    private static float scguns$lerp(Float idleValue, Float aimingValue, float fallback, float progress, float multiplier) {
        if (idleValue == null && aimingValue == null) {
            return fallback;
        }
        float start = idleValue != null ? idleValue : fallback;
        float end = aimingValue != null ? aimingValue : start;
        return (start + (end - start) * progress) * multiplier;
    }

    private static float scguns$lerpRotation(Float idleDegrees, Float aimingDegrees, float fallbackRadians, float progress, float multiplier) {
        if (idleDegrees == null && aimingDegrees == null) {
            return fallbackRadians;
        }
        float start = idleDegrees != null ? idleDegrees : (float) Math.toDegrees(fallbackRadians);
        float end = aimingDegrees != null ? aimingDegrees : start;
        return (float) Math.toRadians((start + (end - start) * progress) * multiplier);
    }

    private static void scguns$applyFallbackPose(ModelPart rightArm, ModelPart leftArm) {
        rightArm.xRot = (float) Math.toRadians(-80F);
        rightArm.yRot = (float) Math.toRadians(-10F);
        rightArm.zRot = 0F;

        leftArm.xRot = (float) Math.toRadians(-35F);
        leftArm.yRot = (float) Math.toRadians(15F);
        leftArm.zRot = 0F;
    }
}
