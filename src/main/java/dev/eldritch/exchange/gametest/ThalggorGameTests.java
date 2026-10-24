package dev.eldritch.exchange.gametest;

import dev.eldritch.exchange.BurnerService;
import dev.eldritch.exchange.Eldritch;
import dev.eldritch.exchange.Ledger;
import dev.eldritch.exchange.ValueTable;
import dev.eldritch.exchange.core.Demand;
import dev.eldritch.exchange.core.LedgerData;
import dev.eldritch.exchange.core.Madness;
import java.util.ArrayList;
import java.util.List;
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
public final class ThalggorGameTests {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private ThalggorGameTests() {
    }

    @GameTest(template = "empty")
    public static void sealedSetIncludesDemandedItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ValueTable.refresh(level.getServer());
        Ledger ledger = Ledger.get(level.getServer());

        // Learn a handful of items manually.
        for (String id : List.of("minecraft:dirt", "minecraft:stone", "minecraft:iron_ingot",
                "minecraft:gold_ingot", "minecraft:diamond", "minecraft:coal")) {
            ledger.data.learn(id);
        }
        ledger.data.setMadness(OWNER, 60);

        List<String> sealed = Demand.buildSealed("minecraft:iron_ingot", ledger.data.learned(), new java.util.Random(1));
        if (!sealed.contains("minecraft:iron_ingot")) {
            helper.fail("sealed set must contain the demanded item");
        }
        if (sealed.size() < 3 || sealed.size() > 6) {
            helper.fail("sealed set size out of range: " + sealed.size());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void burningDemandedItemFulfilsDemand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ValueTable.refresh(level.getServer());

        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.SOUL_CAMPFIRE.defaultBlockState());

        Ledger ledger = Ledger.get(level.getServer());
        ledger.data.bindBurner(OWNER, new LedgerData.Burner(pos.getX(), pos.getY(), pos.getZ()));
        ledger.data.account(OWNER).setLastFedTick(level.getServer().getTickCount());

        for (String id : List.of("minecraft:dirt", "minecraft:stone", "minecraft:iron_ingot",
                "minecraft:gold_ingot", "minecraft:diamond", "minecraft:coal")) {
            ledger.data.learn(id);
        }
        ledger.data.setMadness(OWNER, 60);

        List<String> sealed = Demand.buildSealed("minecraft:iron_ingot", ledger.data.learned(), new java.util.Random(1));
        ledger.data.account(OWNER).issueDemand("minecraft:iron_ingot", 2, level.getServer().getTickCount(),
                level.getServer().getTickCount() + 1000, sealed);

        int madnessBefore = ledger.data.account(OWNER).madness();
        int fulfilledBefore = ledger.data.account(OWNER).fulfilledDemands();

        ItemEntity offering = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                new ItemStack(Items.IRON_INGOT, 2));
        level.addFreshEntity(offering);

        BurnerService.burnOnce(level.getServer());

        if (ledger.data.account(OWNER).fulfilledDemands() != fulfilledBefore + 1) {
            helper.fail("demand was not fulfilled");
        }
        if (!ledger.data.account(OWNER).sealed().isEmpty()) {
            helper.fail("seals were not cleared");
        }
        // Burning the demanded item also grants burn madness (+1 for 128 essence),
        // so the net change is -14 rather than -15.
        if (ledger.data.account(OWNER).madness() != Math.max(0, madnessBefore - 14)) {
            helper.fail("madness was not reduced as expected (got " + ledger.data.account(OWNER).madness() + ")");
        }
        ledger.data.unbindBurner(OWNER);
        ledger.setDirty();
        helper.succeed();
    }
}