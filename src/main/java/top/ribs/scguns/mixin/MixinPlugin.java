package top.ribs.scguns.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.RETURN;

public class MixinPlugin implements IMixinConfigPlugin {
    private boolean isFrameworkInstalled;
    private boolean isEntityModelFeaturesInstalled;
    private boolean isLegacyEntityModelFeaturesContextPresent;
    private boolean isPunchyInstalled;
    private boolean isSableInstalled;
    private boolean isGuardVillagersInstalled;

    @Override
    public void onLoad(String mixinPackage) {
        isFrameworkInstalled = isClassPresent("com.mrcrayfish.framework.FrameworkNeoForge")
                || isClassPresent("com.mrcrayfish.framework.FrameworkForge")
                || isClassPresent("com.mrcrayfish.framework.api.FrameworkAPI");
        isLegacyEntityModelFeaturesContextPresent = getClass().getClassLoader().getResource(
                "traben/entity_model_features/models/animation/EMFAnimationEntityContext.class") != null
                && getClass().getClassLoader().getResource(
                "traben/entity_model_features/EMFAnimationApi$EMFAnimationHook$AnimationContext.class") == null;
        isEntityModelFeaturesInstalled = isModLoaded("entity_model_features")
                || isClassPresent("traben.entity_model_features.models.parts.EMFModelPartRoot")
                && isLegacyEntityModelFeaturesContextPresent;
        isPunchyInstalled = isModLoaded("punchy")
                || isClassPresent("punchy.client.render.PunchyArmRenderer")
                || isClassPresent("punchy.client.state.MovementStateMachine")
                || isClassPresent("punchy.client.render.HandRenderBobContext");
        isSableInstalled = isModLoaded("sable")
                || isClassPresent("dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor");
        isGuardVillagersInstalled = isModLoaded("guardvillagers")
                || isClassPresent("tallestegg.guardvillagers.common.entities.Guard");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!isFrameworkInstalled) {
            return false; // this makes sure that forge's helpful mods not found screen shows up
        }
        if (mixinClassName.equals("top.ribs.scguns.mixin.client.compat.fa.EMFModelPartRootMixin")) {
            return isEntityModelFeaturesInstalled && isLegacyEntityModelFeaturesContextPresent;
        }
        if (mixinClassName.startsWith("top.ribs.scguns.mixin.client.compat.fa.")) {
            return isEntityModelFeaturesInstalled;
        }
        if (mixinClassName.startsWith("top.ribs.scguns.mixin.client.compat.punchy.")) {
            return isPunchyInstalled;
        }
        if (mixinClassName.startsWith("top.ribs.scguns.mixin.common.compat.sable.")) {
            return isSableInstalled;
        }
        if (mixinClassName.startsWith("top.ribs.scguns.mixin.common.compat.guardvillagers.")) {
            return isGuardVillagersInstalled;
        }
        if (mixinClassName.startsWith("top.ribs.scguns.mixin.client.compat.guardvillagers.")) {
            return isGuardVillagersInstalled;
        }
        return true;
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, this.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isModLoaded(String modId) {
        try {
            Class<?> loadingModList = Class.forName("net.neoforged.fml.loading.LoadingModList", false, this.getClass().getClassLoader());
            Object modList = loadingModList.getMethod("get").invoke(null);
            return loadingModList.getMethod("getModFileById", String.class).invoke(modList, modId) != null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if ("top.ribs.scguns.mixin.common.compat.sable.TurretBlockEntitySableActorMixin".equals(mixinClassName)) {
            applySableTurretActorBridge(targetClass);
        }
    }

    private void applySableTurretActorBridge(ClassNode targetClass) {
        String actorInterface = "dev/ryanhcode/sable/api/block/BlockEntitySubLevelActor";
        if (!targetClass.interfaces.contains(actorInterface)) {
            targetClass.interfaces.add(actorInterface);
        }
        for (MethodNode method : targetClass.methods) {
            if ("sable$physicsTick".equals(method.name)
                    && "(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V".equals(method.desc)) {
                return;
            }
        }

        MethodNode method = new MethodNode(
                ACC_PUBLIC,
                "sable$physicsTick",
                "(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V",
                null,
                null
        );
        InsnList instructions = method.instructions;
        instructions.add(new VarInsnNode(ALOAD, 0));
        instructions.add(new VarInsnNode(ALOAD, 1));
        instructions.add(new MethodInsnNode(
                INVOKESTATIC,
                "top/ribs/scguns/blockentity/TurretBlockEntity",
                "sablePhysicsTickBridge",
                "(Ltop/ribs/scguns/blockentity/TurretBlockEntity;Ljava/lang/Object;)V",
                false
        ));
        instructions.add(new InsnNode(RETURN));
        method.maxStack = 2;
        method.maxLocals = 5;
        targetClass.methods.add(method);
    }
}
