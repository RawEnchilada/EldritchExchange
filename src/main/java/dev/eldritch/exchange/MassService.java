package dev.eldritch.exchange;

import dev.eldritch.exchange.core.Demand;
import dev.eldritch.exchange.core.LedgerData;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Low-favour mass: seeds sludge around the burner and sends tentacles up out of it near players.
 */
public final class MassService {

    private static final int INTERVAL = 100;
    private static final int RANGE = 24;
    static final String MASS_TENTACLE_TAG = "eldritch_mass_tentacle";

    private static final Random random = new Random();

    private MassService() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        Ledger ledger = Ledger.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            LedgerData.Account account = ledger.data.account(player.getUUID());
            if (account.favour() <= Demand.LOW_FAVOUR) {
                seedFromBurner(server.overworld(), ledger.data.burner(player.getUUID()));
            }
            spawnTentacle(player);
        }
    }

    private static void seedFromBurner(ServerLevel level, LedgerData.Burner burner) {
        if (burner == null || !BurnerService.stands(level, burner)) {
            return;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            int dx = random.nextInt(5) - 2;
            int dz = random.nextInt(5) - 2;
            BlockPos pos = new BlockPos(burner.x() + dx, burner.y(), burner.z() + dz);
            for (int i = 0; i < 3 && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP); i++) {
                pos = pos.below();
            }
            placeMass(level, pos);
        }
    }

    private static void placeMass(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            level.setBlock(pos, ModBlocks.MASS.get().defaultBlockState(), 3);
        }
    }

    private static void spawnTentacle(ServerPlayer player) {
        int cap = EldritchConfig.MASS_TENTACLE_CAP.get();
        if (cap <= 0 || random.nextInt(3) != 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        int count = level.getEntitiesOfClass(TentacleEntity.class, player.getBoundingBox().inflate(RANGE),
                t -> t.getTags().contains(MASS_TENTACLE_TAG)).size();
        if (count >= cap) {
            return;
        }
        BlockPos source = null;
        for (int i = 0; i < 12 && source == null; i++) {
            BlockPos p = player.blockPosition().offset(
                    random.nextInt(2 * RANGE + 1) - RANGE, random.nextInt(9) - 4, random.nextInt(2 * RANGE + 1) - RANGE);
            if (level.getBlockState(p).is(ModBlocks.MASS.get())) {
                source = p;
            }
        }
        if (source == null) {
            return;
        }
        TentacleEntity tentacle = ModEntities.TENTACLE.get().create(level);
        if (tentacle == null) {
            return;
        }
        double dx = player.getX() - (source.getX() + 0.5);
        double dz = player.getZ() - (source.getZ() + 0.5);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        tentacle.moveTo(source.getX() + 0.5, source.getY(), source.getZ() + 0.5, yaw, 0.0F);
        tentacle.setYHeadRot(yaw);
        tentacle.setYBodyRot(yaw);
        tentacle.addTag(MASS_TENTACLE_TAG);
        level.addFreshEntity(tentacle);
    }
}
