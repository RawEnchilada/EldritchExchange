package dev.eldritch.exchange;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TentacleFleshItem extends Item {

    public TentacleFleshItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide) {
            MobEffectInstance effect = switch (level.getRandom().nextInt(4)) {
                case 0 -> new MobEffectInstance(MobEffects.CONFUSION, 200, 0);
                case 1 -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 0);
                case 2 -> new MobEffectInstance(MobEffects.DARKNESS, 200, 0);
                default -> new MobEffectInstance(MobEffects.REGENERATION, 100, 0);
            };
            entity.addEffect(effect);
        }
        return result;
    }
}
