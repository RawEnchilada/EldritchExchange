package dev.eldritch.exchange;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Eldritch.MODID);

    public static final DeferredBlock<MassBlock> MASS =
            BLOCKS.registerBlock("mass", MassBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW));

    public static final DeferredBlock<VoidCampfireBlock> VOID_CAMPFIRE =
            BLOCKS.registerBlock("void_campfire", VoidCampfireBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_CAMPFIRE).lightLevel(state -> 10));

    private ModBlocks() {
    }
}
