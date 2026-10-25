package top.ribs.scguns.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;
import top.ribs.scguns.Reference;
import top.ribs.scguns.ScorchedGuns;
import top.ribs.scguns.client.handler.AimingHandler;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.item.GunItem;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class OptionalClientCompat {
    private static final String EMF_ANIMATION_API = "traben.entity_model_features.EMFAnimationApi";
    private static boolean emfVariableRegistered;

    private static ModelPart regularRightArm;
    private static ModelPart regularRightSleeve;
    private static ModelPart regularLeftArm;
    private static ModelPart regularLeftSleeve;
    private static ModelPart slimRightArm;
    private static ModelPart slimRightSleeve;
    private static ModelPart slimLeftArm;
    private static ModelPart slimLeftSleeve;

    private OptionalClientCompat() {
    }

    public static boolean isEntityModelFeaturesLoaded() {
        return EntityModelFeaturesCompat.isAvailable();
    }

    public static void registerEntityModelFeaturesVariables() {
        if (emfVariableRegistered || !isEntityModelFeaturesLoaded()) {
            return;
        }
        try {
            EntityModelFeaturesCompat.registerAnimationHook();
            Class<?> api = Class.forName(EMF_ANIMATION_API, false, OptionalClientCompat.class.getClassLoader());
            Method register = api.getMethod(
                    "registerSingletonAnimationVariable",
                    String.class,
                    String.class,
                    String.class,
                    BooleanSupplier.class
            );
            register.invoke(
                    null,
                    Reference.MOD_ID,
                    "is_scguns_gun_held",
                    "True when the rendered player is holding a Scorched Guns gun item.",
                    (BooleanSupplier) OptionalClientCompat::isScorchedGunHeldByEntityModelFeaturesContext
            );
            emfVariableRegistered = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            EntityModelFeaturesCompat.disable(e);
        }
    }

    public static boolean isScorchedGunHeldByLocalPlayer() {
        return isScorchedGunHeldByPlayer(Minecraft.getInstance().player);
    }

    public static boolean isScorchedGunHeldByPlayer(Player player) {
        return player != null && (isScorchedGunStack(player.getMainHandItem()) || isScorchedGunStack(player.getOffhandItem()));
    }

    public static boolean isScorchedGunStack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof GunItem;
    }

    public static boolean renderCleanFirstPersonArm(LocalPlayer player, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int light) {
        if (!isEntityModelFeaturesLoaded() || !isScorchedGunHeldByPlayer(player)) {
            return false;
        }
        boolean slim = isSlim(player);
        ensureArmParts(slim);
        ModelPart armPart = getArmPart(slim, arm);
        ModelPart sleevePart = getSleevePart(slim, arm);
        if (armPart == null || sleevePart == null) {
            return false;
        }

        armPart.resetPose();
        sleevePart.resetPose();
        sleevePart.copyFrom(armPart);

        ResourceLocation playerSkin = player.getSkin().texture();
        RenderSystem.setShaderTexture(0, playerSkin);
        armPart.render(poseStack, buffer.getBuffer(RenderType.entitySolid(playerSkin)), light, OverlayTexture.NO_OVERLAY);

        PlayerModelPart sleeve = arm == HumanoidArm.RIGHT ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE;
        if (player.isModelPartShown(sleeve)) {
            sleevePart.render(poseStack, buffer.getBuffer(RenderType.entityTranslucent(playerSkin)), light, OverlayTexture.NO_OVERLAY);
        }
        return true;
    }

    public static boolean renderCleanAnimatedArm(LocalPlayer player, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, ResourceLocation playerSkin, GeoBone bone, int light, int overlay) {
        if (!isEntityModelFeaturesLoaded() || !isScorchedGunHeldByPlayer(player)) {
            return false;
        }
        boolean slim = isSlim(player);
        ensureArmParts(slim);
        ModelPart armPart = getArmPart(slim, arm);
        ModelPart sleevePart = getSleevePart(slim, arm);
        if (armPart == null || sleevePart == null) {
            return false;
        }

        poseStack.scale(0.66F, arm == HumanoidArm.RIGHT ? 0.78F : 0.79F, 0.66F);
        poseStack.translate(arm == HumanoidArm.RIGHT ? 0.25D : -0.25D, -0.1D, 0.1625D);

        prepareArmPart(armPart, bone);
        armPart.render(poseStack, buffer.getBuffer(RenderType.entitySolid(playerSkin)), light, overlay);
        prepareArmPart(sleevePart, bone);
        sleevePart.render(poseStack, buffer.getBuffer(RenderType.entityTranslucent(playerSkin)), light, overlay);
        return true;
    }

    public static void cancelFallbackFirstPersonHandWhenGunHeld(Player player, CallbackInfo ci) {
        if (isEntityModelFeaturesLoaded() && isScorchedGunHeldByPlayer(player)) {
            ci.cancel();
        }
    }

    private static boolean isScorchedGunHeldByEntityModelFeaturesContext() {
        Object entity = getEntityModelFeaturesContextEntity();
        return entity instanceof Player player && isScorchedGunHeldByPlayer(player);
    }

    private static boolean isSlim(LocalPlayer player) {
        return "slim".equals(player.getSkin().model().id());
    }

    private static void ensureArmParts(boolean slim) {
        if (slim) {
            if (slimRightArm == null) {
                bakeArmParts(true);
            }
        } else if (regularRightArm == null) {
            bakeArmParts(false);
        }
    }

    private static void bakeArmParts(boolean slim) {
        LayerDefinition layerDefinition = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64);
        ModelPart root = layerDefinition.bakeRoot();
        if (slim) {
            slimRightArm = root.getChild("right_arm");
            slimRightSleeve = root.getChild("right_sleeve");
            slimLeftArm = root.getChild("left_arm");
            slimLeftSleeve = root.getChild("left_sleeve");
        } else {
            regularRightArm = root.getChild("right_arm");
            regularRightSleeve = root.getChild("right_sleeve");
            regularLeftArm = root.getChild("left_arm");
            regularLeftSleeve = root.getChild("left_sleeve");
        }
    }

    private static ModelPart getArmPart(boolean slim, HumanoidArm arm) {
        if (slim) {
            return arm == HumanoidArm.RIGHT ? slimRightArm : slimLeftArm;
        }
        return arm == HumanoidArm.RIGHT ? regularRightArm : regularLeftArm;
    }

    private static ModelPart getSleevePart(boolean slim, HumanoidArm arm) {
        if (slim) {
            return arm == HumanoidArm.RIGHT ? slimRightSleeve : slimLeftSleeve;
        }
        return arm == HumanoidArm.RIGHT ? regularRightSleeve : regularLeftSleeve;
    }

    private static void prepareArmPart(ModelPart armPart, GeoBone bone) {
        armPart.resetPose();
        armPart.visible = true;
        armPart.skipDraw = false;
        armPart.xScale = 1.0F;
        armPart.yScale = 1.0F;
        armPart.zScale = 1.0F;
        armPart.setPos(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
        armPart.setRotation(0.0F, 0.0F, 0.0F);
    }

    public static void disableEntityModelFeaturesCompat(Throwable error) {
        EntityModelFeaturesCompat.disable(error);
    }

    public static Object getEntityModelFeaturesContextEntity() {
        return EntityModelFeaturesCompat.getCurrentEntity();
    }

    public static boolean isEntityModelFeaturesFirstPersonHand() {
        return EntityModelFeaturesCompat.isLegacyFirstPersonHand();
    }

    public static void reapplyEntityModelFeaturesGunPose(Object entity, Map<String, ? extends ModelPart> parts) {
        if (!(entity instanceof Player player)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gunItem)) {
            return;
        }
        ModelPart rightArm = parts.get("right_arm");
        ModelPart leftArm = parts.get("left_arm");
        ModelPart head = parts.get("head");
        if (rightArm == null || leftArm == null || head == null) {
            return;
        }
        rightArm.resetPose();
        leftArm.resetPose();
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float aimProgress = AimingHandler.get().getAimProgress(player, partialTick);
        Gun gun = gunItem.getModifiedGun(stack);
        gun.getGeneral().getGripType(stack).heldAnimation()
                .applyPlayerModelRotation(player, rightArm, leftArm, head, InteractionHand.MAIN_HAND, aimProgress);
        copyPose(rightArm, parts.get("right_sleeve"));
        copyPose(leftArm, parts.get("left_sleeve"));
        copyPose(head, parts.get("hat"));
        copyPose(head, parts.get("headwear"));
    }

    private static void copyPose(ModelPart source, ModelPart target) {
        if (target != null) {
            target.copyFrom(source);
        }
    }
}
