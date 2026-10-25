package dev.eldritch.exchange;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Dark green sludge that lays down in layers like snow, slows what walks on it, and creeps outward slowly. */
public final class MassBlock extends SnowLayerBlock {

    private static final int SPREAD_ODDS = 40;
    /** Mass only grows and converts items while a player is this close, so idle mass costs nothing. */
    private static final double SPREAD_RANGE = 64.0;

    public MassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(SPREAD_ODDS) != 0 || !level.hasNearbyAlivePlayer(pos.getX(), pos.getY(), pos.getZ(), SPREAD_RANGE)) {
            return;
        }
        BlockPos target = pos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
        BlockState there = level.getBlockState(target);
        if (there.is(this)) {
            int layers = there.getValue(LAYERS);
            if (layers < 8) {
                level.setBlock(target, there.setValue(LAYERS, layers + 1), 3);
            }
        } else if (there.canBeReplaced() && level.getBlockState(target.below()).isFaceSturdy(level, target.below(), Direction.UP)) {
            level.setBlock(target, defaultBlockState(), 3);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
        } else if (entity instanceof ItemEntity item && !item.getItem().is(ModItems.CODEX.get())
                && !item.getItem().is(ModItems.TENTACLE_FLESH.get())
                && level.hasNearbyAlivePlayer(item.getX(), item.getY(), item.getZ(), SPREAD_RANGE)) {
            ItemStack stack = item.getItem();
            item.setItem(new ItemStack(ModItems.TENTACLE_FLESH.get(), stack.getCount()));
        }
    }
}
