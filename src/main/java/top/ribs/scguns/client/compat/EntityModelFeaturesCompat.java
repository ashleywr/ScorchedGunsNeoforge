package top.ribs.scguns.client.compat;

import top.ribs.scguns.ScorchedGuns;
import traben.entity_model_features.EMFAnimationApi;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Optional EMF classes are isolated behind adapters selected once during client setup. */
final class EntityModelFeaturesCompat {
    private static Access access = resolve();

    private EntityModelFeaturesCompat() {
    }

    static boolean isAvailable() {
        return access != null;
    }

    static void registerAnimationHook() throws ReflectiveOperationException {
        if (access != null) {
            try {
                access.registerHook();
            } catch (Exception e) {
                throw new ReflectiveOperationException(e);
            }
        }
    }

    static Object getCurrentEntity() {
        if (access != null) {
            try {
                return access.currentEntity();
            } catch (Throwable e) {
                disable(e);
            }
        }
        return null;
    }

    static boolean isLegacyFirstPersonHand() {
        if (access instanceof Legacy legacy) {
            try {
                return (boolean) legacy.firstPersonHand.invokeExact();
            } catch (Throwable e) {
                disable(e);
            }
        }
        return false;
    }

    static void disable(Throwable error) {
        if (access != null) {
            access = null;
            ScorchedGuns.LOGGER.warn("Entity Model Features gun-pose compatibility was skipped: unsupported or failed API.", error);
        }
    }

    private static Access resolve() {
        ClassLoader loader = EntityModelFeaturesCompat.class.getClassLoader();
        Class<?> api;
        try {
            api = Class.forName("traben.entity_model_features.EMFAnimationApi", false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
        try {
            // API v10 supplies a stable animation context, including the first-person hand flag.
            if ((int) api.getMethod("getApiVersion").invoke(null) >= 10) {
                return new Modern();
            }
            Class<?> context = Class.forName("traben.entity_model_features.models.animation.EMFAnimationEntityContext", false, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle entity = lookup.unreflect(context.getMethod("getEMFEntity"))
                    .asType(MethodType.methodType(Object.class));
            MethodHandle hand = lookup.unreflectGetter(context.getField("isFirstPersonHand"));
            return new Legacy(entity, hand);
        } catch (ReflectiveOperationException | LinkageError e) {
            ScorchedGuns.LOGGER.warn("Entity Model Features gun-pose compatibility was skipped: unsupported API.", e);
            return null;
        }
    }

    private interface Access {
        Object currentEntity() throws Throwable;

        default void registerHook() throws Exception {
        }
    }

    private static final class Modern implements Access {
        @Override
        public void registerHook() throws Exception {
            EMFAnimationApi.registerAnimationHook(new GunPoseHook());
        }

        @Override
        public Object currentEntity() {
            return EMFAnimationApi.getCurrentEntity();
        }
    }

    private record Legacy(MethodHandle entity, MethodHandle firstPersonHand) implements Access {
        @Override
        public Object currentEntity() throws Throwable {
            return (Object) entity.invokeExact();
        }
    }

    private static final class GunPoseHook extends EMFAnimationApi.EMFAnimationHook {
        @Override
        public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
            if (access == null || wasCancelledByHook) {
                return;
            }
            try {
                if (context.error() != null) {
                    return;
                }
                var state = context.activeState();
                if (state != null && !state.isFirstPersonHand()) {
                    OptionalClientCompat.reapplyEntityModelFeaturesGunPose(
                            state.emfEntity(), context.animatingModelRoot().getAllVanillaPartsByNameEMF());
                }
            } catch (Throwable e) {
                // Do not let a compatibility failure make EMF discard this model's animations.
                disable(e);
            }
        }
    }
}
