package dev.eldritch.exchange.gametest;

import dev.eldritch.exchange.BurnerService;
import dev.eldritch.exchange.Eldritch;
import dev.eldritch.exchange.Ledger;
import dev.eldritch.exchange.ValueTable;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Eldritch.MODID)
@PrefixGameTestTemplate(false)
public final class BurnerGameTests {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private BurnerGameTests() {
    }

    @GameTest(template = "empty")
    public static void offeringOnBurnerAwardsEssence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ValueTable.refresh(level.getServer());

        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.SOUL_CAMPFIRE.defaultBlockState());

        Ledger ledger = Ledger.get(level.getServer());
        ledger.data.bindBurner(OWNER, new dev.eldritch.exchange.core.LedgerData.Burner(
                pos.getX(), pos.getY(), pos.getZ()));

        ItemEntity offering = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                new ItemStack(Items.DIRT, 5));
        level.addFreshEntity(offering);

        long before = ledger.data.account(OWNER).essence();
        BurnerService.burnOnce(level.getServer());

        long essence = ledger.data.account(OWNER).essence() - before;
        if (essence != 5) {
            helper.fail("expected 5 essence from 5 dirt, got " + essence);
        }
        if (!ledger.data.learned().contains("minecraft:dirt")) {
            helper.fail("dirt was not learned");
        }
        if (offering.isAlive()) {
            helper.fail("offering was not consumed");
        }
        ledger.data.unbindBurner(OWNER);
        ledger.setDirty();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unboundCampfireDoesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ValueTable.refresh(level.getServer());

        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.SOUL_CAMPFIRE.defaultBlockState());

        ItemEntity offering = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                new ItemStack(Items.DIRT, 3));
        level.addFreshEntity(offering);

        BurnerService.burnOnce(level.getServer());

        if (!offering.isAlive()) {
            helper.fail("an unbound campfire consumed an item");
        }
        helper.succeed();
    }
}
