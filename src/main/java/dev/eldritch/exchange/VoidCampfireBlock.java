package dev.eldritch.exchange;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The burner's campfire: black wood with a green flame. Placed by converting a soul campfire on redstone. */
public final class VoidCampfireBlock extends Block {

    /** Half a block tall, so players can step over it. */
    private static final VoxelShape HALF = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);

    public VoidCampfireBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return HALF;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return HALF;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.9;
        double z = pos.getZ() + 0.5;
        for (int i = 0; i < 3; i++) {
            level.addParticle(ModParticles.VOID_FLAME.get(),
                    x + (random.nextDouble() - 0.5) * 0.6, y + random.nextDouble() * 0.6, z + (random.nextDouble() - 0.5) * 0.6,
                    0.0, 0.04, 0.0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.5, z, 0.0, 0.05, 0.0);
        }
    }
}
