package top.ribs.scguns.item;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import top.ribs.scguns.Config;
import top.ribs.scguns.client.GunItemStackRenderer;
import top.ribs.scguns.client.KeyBinds;
import top.ribs.scguns.common.*;
import top.ribs.scguns.init.ModEnchantments;
import top.ribs.scguns.init.ModItems;
import top.ribs.scguns.init.ModTags;
import top.ribs.scguns.item.attachment.IAttachment;
import top.ribs.scguns.util.GunEnchantmentHelper;
import top.ribs.scguns.util.GunModifierHelper;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

public class GunItem extends Item implements IColored, IMeta {
    // CustomData is immutable; weak identity keys reuse a live stack component without copying or hashing its NBT.
    private final Cache<CustomData, Gun> modifiedGunCache = CacheBuilder.newBuilder().weakKeys().build();
    private Gun gun = new Gun();

    public GunItem(Item.Properties properties) {
        super(properties);
    }

    public void setGun(NetworkGunManager.Supplier supplier) {
        this.gun = supplier.getGun();
        this.modifiedGunCache.invalidateAll();
    }

    public Gun getGun() {
        return this.gun;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.OFF_HAND && player.getMainHandItem().getItem() instanceof GunItem) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Nullable
    private static CompoundTag getStackTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty() ? data.copyTag() : null;
    }

    private static CompoundTag getOrCreateStackTag(ItemStack stack) {
        CompoundTag tag = getStackTag(stack);
        return tag != null ? tag : new CompoundTag();
    }

    private static void setStackTag(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Gun modifiedGun = this.getModifiedGun(stack);
        Level worldIn = context.level();

        ResourceLocation advantage = modifiedGun.getProjectile().getAdvantage();
        float baseDamage = modifiedGun.getProjectile().getDamage();
        baseDamage = GunModifierHelper.getModifiedProjectileDamage(stack, baseDamage);
        baseDamage = GunEnchantmentHelper.getAcceleratorDamage(stack, baseDamage);
        baseDamage = GunEnchantmentHelper.getHeavyShotDamage(stack, baseDamage);
        baseDamage = GunEnchantmentHelper.getPuncturingDamageReductionForTooltip(stack, baseDamage);

        if (worldIn != null && Config.GunScalingConfig.getInstance().isScalingEnabled()) {
            long worldDay = worldIn.getDayTime() / 24000L;
            double scaledDamage = Config.GunScalingConfig.getInstance().getBaseDamage() +
                    (Config.GunScalingConfig.getInstance().getDamageIncreaseRate() * worldDay);
            baseDamage *= (float) Math.min(scaledDamage, Config.GunScalingConfig.getInstance().getMaxDamage());
        }
        baseDamage *= Config.COMMON.gameplay.globalDamageMultiplier.get().floatValue();
        String additionalDamageText = "";
        CompoundTag tagCompound = getStackTag(stack);
        if (tagCompound != null) {
            if (tagCompound.contains("AdditionalDamage", Tag.TAG_ANY_NUMERIC)) {
                float additionalDamage = tagCompound.getFloat("AdditionalDamage");
                additionalDamage += GunModifierHelper.getAdditionalDamage(stack, false);


                if (additionalDamage > 0) {
                    additionalDamageText = ChatFormatting.GREEN + " +" + ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(additionalDamage);
                } else if (additionalDamage < 0) {
                    additionalDamageText = ChatFormatting.RED + " " + ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(additionalDamage);
                }
            }
        }

        tooltip.add(Component.translatable("info.scguns.damage")
                .append(": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(baseDamage) + additionalDamageText).withStyle(ChatFormatting.WHITE)));
        Gun.WeaponType weaponType = modifiedGun.getGeneral().getWeaponType();
        if (weaponType != null) {
            tooltip.add(Component.translatable("info.scguns.weapon_type").withStyle(ChatFormatting.GRAY)
                    .append(": ")
                    .append(Component.translatable("desc.scguns." + weaponType.id()).withStyle(ChatFormatting.WHITE)));
        }
        if (!advantage.equals(ModTags.Entities.NONE.location())) {
            tooltip.add(Component.translatable("info.scguns.advantage").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("advantage." + advantage).withStyle(ChatFormatting.GOLD)));
        }
        String fireMode = modifiedGun.getGeneral().getFireMode().id().toString();
        tooltip.add(Component.translatable("info.scguns.fire_mode").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("fire_mode." + fireMode).withStyle(ChatFormatting.WHITE)));

        Item ammo = modifiedGun.getProjectile().getItem();
        Item reloadItem = modifiedGun.getReloads().getReloadItem();
        if (modifiedGun.getReloads().getReloadType() == ReloadType.SINGLE_ITEM) {
            ammo = reloadItem;
        }
        if (modifiedGun.getReloads().getReloadType() != ReloadType.SINGLE_ITEM && modifiedGun.getAcceptedProjectiles().size() > 1) {
            MutableComponent ammoTypes = Component.empty();
            boolean first = true;
            for (Gun.Projectile projectile : modifiedGun.getAcceptedProjectiles()) {
                Item projectileItem = projectile.getItem();
                if (projectileItem == null) {
                    continue;
                }
                if (!first) {
                    ammoTypes.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
                ammoTypes.append(Component.translatable(projectileItem.getDescriptionId()).withStyle(ChatFormatting.WHITE));
                first = false;
            }
            tooltip.add(Component.translatable("info.scguns.ammo_type", ammoTypes).withStyle(ChatFormatting.GRAY));
        } else if (ammo != null) {
            tooltip.add(Component.translatable("info.scguns.ammo_type", Component.translatable(ammo.getDescriptionId()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        }
        if (tagCompound != null) {
            if (tagCompound.getBoolean("IgnoreAmmo")) {
                tooltip.add(Component.translatable("info.scguns.ignore_ammo").withStyle(ChatFormatting.AQUA));
            } else {
                int ammoCount = tagCompound.getInt("AmmoCount");
                tooltip.add(Component.translatable("info.scguns.ammo")
                        .append(": ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(ammoCount + "/" + GunModifierHelper.getModifiedAmmoCapacity(stack, modifiedGun)).withStyle(ChatFormatting.WHITE)));
            }
        }
        float totalMeleeDamage = getTotalMeleeDamage(stack);
        if (totalMeleeDamage > 0) {
            String meleeDamageText = (totalMeleeDamage % 1.0 == 0) ? String.format("%d", (int) totalMeleeDamage) : String.format("%.1f", totalMeleeDamage);
            tooltip.add(Component.translatable("info.scguns.melee_damage")
                    .append(": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(meleeDamageText).withStyle(ChatFormatting.WHITE)));
        }
        ResourceLocation effectLocation = modifiedGun.getProjectile().getImpactEffect();
        if (effectLocation != null) {
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(effectLocation).orElse(null);
            if (effect != null) {
                tooltip.add(Component.translatable("info.scguns.impact_effect").withStyle(ChatFormatting.GRAY)
                        .append(": ")
                        .append(Component.translatable(effect.getDescriptionId()).withStyle(ChatFormatting.BLUE)));
            }
        }
        tooltip.add(Component.translatable("info.scguns.attachment_help", KeyBinds.KEY_ATTACHMENTS.getTranslatedKeyMessage().getString().toUpperCase(Locale.ENGLISH)).withStyle(ChatFormatting.YELLOW));
    }



    public float getBayonetAdditionalDamage(ItemStack gunStack) {
        float additionalDamage = 0.0F;
        for (IAttachment.Type type : IAttachment.Type.values()) {
            ItemStack attachmentStack = Gun.getAttachment(type, gunStack);
            if (attachmentStack != null && attachmentStack.getItem() instanceof BayonetItem) {
                additionalDamage += ((BayonetItem) attachmentStack.getItem()).getAdditionalDamage();
            }
        }
        return additionalDamage;
    }

    public float getTotalMeleeDamage(ItemStack stack) {
        Gun gun = this.getModifiedGun(stack);
        float baseMeleeDamage = gun.getGeneral().getMeleeDamage();
        float bayonetDamage = getBayonetAdditionalDamage(stack);
        return baseMeleeDamage + bayonetDamage;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        Gun gun = ((GunItem) stack.getItem()).getModifiedGun(stack);
        return gun.getGeneral().getRate() * 4;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamaged();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        this.getModifiedGun(stack);
        return Math.round(13.0F - (float) stack.getDamageValue() * 13.0F / (float) this.getMaxDamage(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if (stack.getDamageValue() >= (stack.getMaxDamage() - 1)) {
            return 0x808080;
        }

        if (stack.getDamageValue() >= (stack.getMaxDamage() / 1.5)) {
            return Objects.requireNonNull(ChatFormatting.RED.getColor());
        }
        float stackMaxDamage = this.getMaxDamage(stack);
        float f = Math.max(0.0F, (stackMaxDamage - (float) stack.getDamageValue()) / stackMaxDamage);
        return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
    }

    public Gun getModifiedGun(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return this.gun;
        }
        Gun cached = this.modifiedGunCache.getIfPresent(data);
        if (cached != null) {
            return cached;
        }
        CompoundTag tagCompound = data.copyTag();
        Gun modifiedGun = this.gun;
        if (tagCompound.contains("Gun", Tag.TAG_COMPOUND)) {
            if (tagCompound.getBoolean("Custom")) {
                modifiedGun = Gun.create(tagCompound.getCompound("Gun"));
            } else {
                modifiedGun = this.gun.copy();
                modifiedGun.deserializeNBT(tagCompound.getCompound("Gun"));
            }
        }
        this.modifiedGunCache.put(data, modifiedGun);
        return modifiedGun;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        if (stack.is(ModTags.Items.MINING_GUN)) {
            if (enchantment.is(Enchantments.FORTUNE) || enchantment.is(Enchantments.SILK_TOUCH)) {
                return true;
            }
        }
        return stack.is(Items.ENCHANTED_BOOK) || enchantment.value().isSupportedItem(stack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return this.getMaxStackSize(stack) == 1;
    }

    @Override
    public int getEnchantmentValue() {
        return 13;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return new GunItemStackRenderer();
            }
        });
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isValidRepairItem(ItemStack pToRepair, ItemStack pRepair) {
        return pRepair.is(ModItems.REPAIR_KIT.get());
    }

    public ItemStack getAttachment(ItemStack heldItem, IAttachment.Type type) {
        return Gun.getAttachment(type, heldItem);
    }

    public boolean hasBayonet(ItemStack gunStack) {
        if (isBuiltInBayonetGun(gunStack)) {
            return true;
        }
        for (IAttachment.Type type : IAttachment.Type.values()) {
            ItemStack attachmentStack = Gun.getAttachment(type, gunStack);
            if (attachmentStack != null && attachmentStack.getItem() instanceof BayonetItem) {
                return true;
            }
        }
        return false;
    }
    public boolean isBuiltInBayonetGun(ItemStack gunStack) {
        return gunStack.is(ModTags.Items.BUILT_IN_BAYONET);
    }


    public boolean hasExtendedBarrel(ItemStack gunStack) {
        for (IAttachment.Type type : IAttachment.Type.values()) {
            ItemStack attachmentStack = Gun.getAttachment(type, gunStack);
            if (attachmentStack != null && attachmentStack.getItem() instanceof ExtendedBarrelItem) {
                return true;
            }
        }
        return false;
    }
    public boolean isOneHandedCarbineCandidate(ItemStack gunStack) {
        return gunStack.is(ModTags.Items.ONE_HANDED_CARBINE);
    }
    public void onAttachmentChanged(ItemStack stack) {
        CompoundTag tag = getOrCreateStackTag(stack);
        tag.putBoolean("AttachmentChanged", true);
        setStackTag(stack, tag);
    }

    public int getBayonetBanzaiLevel(ItemStack gunStack) {
        for (IAttachment.Type type : IAttachment.Type.values()) {
            ItemStack attachmentStack = Gun.getAttachment(type, gunStack);
            if (attachmentStack != null && attachmentStack.getItem() instanceof BayonetItem) {
                return EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.BANZAI, attachmentStack);
            }
        }
        return 0;
    }

    public Gun getGunProperties() {
        return this.gun;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        if (stack.is(ModTags.Items.MINING_GUN)) {
            return true;
        }
        return super.isBookEnchantable(stack, book);
    }

}
