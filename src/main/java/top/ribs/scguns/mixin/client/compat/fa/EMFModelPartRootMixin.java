package top.ribs.scguns.mixin.client.compat.fa;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.client.compat.OptionalClientCompat;

import java.util.Map;

/** Legacy EMF context adapter; current EMF versions use the public animation hook instead. */
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartRoot", remap = false)
@Pseudo
public abstract class EMFModelPartRootMixin {
    @Shadow(remap = false)
    @Final
    private Map<String, ModelPart> allVanillaParts;

    @Inject(method = "animate", at = @At("TAIL"), require = 0, remap = false)
    private void scguns$reapplyGunPoseAfterEntityModelFeatures(CallbackInfo ci) {
        if (!OptionalClientCompat.isEntityModelFeaturesFirstPersonHand()) {
            try {
                OptionalClientCompat.reapplyEntityModelFeaturesGunPose(
                        OptionalClientCompat.getEntityModelFeaturesContextEntity(), allVanillaParts);
            } catch (Throwable e) {
                // Cache the failure instead of throwing again on every animation.
                OptionalClientCompat.disableEntityModelFeaturesCompat(e);
            }
        }
    }
}
