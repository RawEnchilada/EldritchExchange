package dev.eldritch.exchange;

import dev.eldritch.exchange.core.Economy;
import dev.eldritch.exchange.core.LedgerData;
import dev.eldritch.exchange.core.Madness;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class BurnerService {

    /** How often burn feedback is allowed to reach chat, in ticks (5 seconds). */
    private static final int REPORT_INTERVAL = 100;
    /** Madness removed per Eye of Azathoth burned. */
    private static final int EYE_MADNESS = 2;
    /** Minimum ticks between Eyes for one player (one minute). Eyes past the limit wait on the burner. */
    private static final int EYE_COOLDOWN = 1200;
    private static final int MAX_LISTED_NAMES = 3;

    private static final class DisplayState {
        final Display.TextDisplay display;
        long worth;
        long lastSeen;

        DisplayState(Display.TextDisplay display, long worth, long lastSeen) {
            this.display = display;
            this.worth = worth;
            this.lastSeen = lastSeen;
        }
    }

    /** Burn results waiting to be reported, so automation cannot flood chat. */
    private static final class BurnStats {
        long essence;
        long offerings;
        final Set<String> learned = new LinkedHashSet<>();
        long lastReport;

        BurnStats(long now) {
            this.lastReport = now - REPORT_INTERVAL;
        }
    }

    private static final Map<BlockPos, DisplayState> displays = new HashMap<>();
    private static final Map<UUID, BurnStats> burnStats = new HashMap<>();
    private static final Map<UUID, Long> eyeCooldowns = new HashMap<>();

    private BurnerService() {
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        ServerLevel level = server.overworld();
        if (now % 5 == 0) {
            burnScan(server, level, true);
            lookDisplays(server, level, now);
        }
        if (now % 4 == 0) {
            flameRing(server, level, now);
        }
        if (now % 20 == 0) {
            ambience(server, level);
        }
    }

    public static boolean stands(Level level, LedgerData.Burner burner) {
        BlockPos pos = new BlockPos(burner.x(), burner.y(), burner.z());
        return (level.getBlockState(pos).is(Blocks.SOUL_CAMPFIRE) || level.getBlockState(pos).is(ModBlocks.VOID_CAMPFIRE.get()))
                && level.getBlockState(pos.below()).is(Blocks.REDSTONE_BLOCK);
    }

    /** Scans every bound burner once. Exposed so the game tests can drive a burn without waiting for a tick. */
    public static void burnOnce(MinecraftServer server) {
        burnScan(server, server.overworld(), false);
    }

    /** With requirePlayer set, burners nobody is near are skipped, so idle burners cost nothing. */
    private static void burnScan(MinecraftServer server, ServerLevel level, boolean requirePlayer) {
        Ledger ledger = Ledger.get(server);
        boolean dirty = false;
        for (Map.Entry<UUID, LedgerData.Burner> entry : ledger.data.burners().entrySet()) {
            LedgerData.Burner burner = entry.getValue();
            if (!stands(level, burner)
                    || requirePlayer && !level.hasNearbyAlivePlayer(burner.x() + 0.5, burner.y(), burner.z() + 0.5, 32)) {
                continue;
            }
            AABB box = new AABB(burner.x(), burner.y() - 0.5, burner.z(), burner.x() + 1, burner.y() + 2, burner.z() + 1);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
                ItemStack stack = item.getItem();
                if (!item.isAlive()) {
                    continue;
                }
                if (stack.is(ModItems.EYE_OF_AZATHOTH.get())) {
                    // The Eye is never learned or valued. Every Eye lowers madness, but the message is shown once a minute.
                    long eyeNow = server.getTickCount();
                    ledger.data.addMadness(entry.getKey(), -EYE_MADNESS * stack.getCount());
                    item.discard();
                    dirty = true;
                    Long last = eyeCooldowns.get(entry.getKey());
                    ServerPlayer sp = server.getPlayerList().getPlayer(entry.getKey());
                    if (sp != null && (last == null || eyeNow - last >= EYE_COOLDOWN)) {
                        eyeCooldowns.put(entry.getKey(), eyeNow);
                        sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "The Eye sinks into the flame. Your thoughts grow quiet.")
                                .withStyle(net.minecraft.ChatFormatting.AQUA));
                    }
                    continue;
                }
                if (stack.is(ModItems.TENTACLE_FLESH.get())) {
                    // Flesh is food for the burner: it resets starvation but yields no essence.
                    LedgerData.Account fed = ledger.data.account(entry.getKey());
                    fed.setLastFedTick(server.getTickCount());
                    fed.setStarvationTier(0);
                    item.discard();
                    dirty = true;
                    continue;
                }
                String itemId = ValueTable.itemId(stack.getItem());
                long value = ValueTable.value(itemId);
                if (value <= 0) {
                    continue;
                }
                boolean isNew = Economy.burn(ledger.data, entry.getKey(), itemId, value, stack.getCount());
                long essence = value * stack.getCount();
                item.discard();
                dirty = true;

                LedgerData.Account account = ledger.data.account(entry.getKey());
                account.setLastFedTick(server.getTickCount());
                account.setLastGainTick(server.getTickCount());
                account.setStarvationTier(0);
                account.setLastWarningTick(0);

                // Tithe reclamation.
                if (account.titheItemId() != null && !account.titheExpired()) {
                    long owed = ValueTable.value(account.titheItemId()) * account.titheCount();
                    if (owed > 0 && value * stack.getCount() >= owed) {
                        Item reclaimItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(account.titheItemId()));
                        ItemStack reclaim = new ItemStack(reclaimItem, account.titheCount());
                        ServerPlayer sp = server.getPlayerList().getPlayer(entry.getKey());
                        if (sp != null) {
                            if (!sp.addItem(reclaim)) {
                                sp.drop(reclaim, false);
                            }
                            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "The tithe is returned."));
                        }
                        account.clearTithe();
                        dirty = true;
                    }
                }

                // Demand progress.
                if (itemId.equals(account.demandItemId()) && "ACTIVE".equals(account.demandState())) {
                    int remaining = account.demandCount() - stack.getCount();
                    if (remaining <= 0) {
                        account.fulfilDemand();
                        account.setFulfilledDemands(account.fulfilledDemands() + 1);
                        account.sealed().clear();
                        account.addFavour(1);
                        ledger.data.addMadness(entry.getKey(), -15);
                        ServerPlayer sp = server.getPlayerList().getPlayer(entry.getKey());
                        if (sp != null) {
                            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    Thalggor.name(server, entry.getKey()) + " is satisfied. The seals loosen."));
                        }
                    } else {
                        // store remaining count back via re-issue? Account has no setter for count.
                        // Use a small mutable hack: clear and re-issue with remaining.
                        String demanded = account.demandItemId();
                        dev.eldritch.exchange.core.Demand.Kind kind = account.demandKind();
                        long issued = account.demandIssuedTick();
                        long deadline = account.demandDeadlineTick();
                        List<String> sealed = new ArrayList<>(account.sealed());
                        account.clearDemand();
                        account.issueDemand(kind, demanded, remaining, issued, deadline, sealed);
                    }
                }
                burnEffects(level, burner);

                BurnStats stats = burnStats.computeIfAbsent(entry.getKey(),
                        key -> new BurnStats(server.getTickCount()));
                stats.essence += essence;
                stats.offerings += stack.getCount();
                if (isNew) {
                    stats.learned.add(itemId);
                }
            }
        }
        if (dirty) {
            ledger.setDirty();
        }
        reportBurnStats(server);
    }

    /** Sends at most one burn summary per player every {@link #REPORT_INTERVAL} ticks. */
    private static void reportBurnStats(MinecraftServer server) {
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, BurnStats>> it = burnStats.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BurnStats> entry = it.next();
            BurnStats stats = entry.getValue();
            if (stats.offerings == 0 || now - stats.lastReport < REPORT_INTERVAL) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            player.sendSystemMessage(burnSummary(stats));
            stats.essence = 0;
            stats.offerings = 0;
            stats.learned.clear();
            stats.lastReport = now;
        }
    }

    private static Component burnSummary(BurnStats stats) {
        String offerings = stats.offerings == 1 ? "1 offering" : stats.offerings + " offerings";
        MutableComponent message = Component.literal(
                "The burner devours " + offerings + ", granting " + stats.essence + " essence.");
        if (!stats.learned.isEmpty()) {
            message.append(Component.literal(" New in the codex: " + learnedNames(stats.learned) + ".")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        return message;
    }

    private static String learnedNames(Set<String> learned) {
        List<String> names = new ArrayList<>();
        int hidden = 0;
        for (String id : learned) {
            if (names.size() >= MAX_LISTED_NAMES) {
                hidden++;
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            names.add(item == Items.AIR ? id : new ItemStack(item).getHoverName().getString());
        }
        String list = String.join(", ", names);
        return hidden > 0 ? list + " and " + hidden + " more" : list;
    }

    private static void burnEffects(ServerLevel level, LedgerData.Burner burner) {
        level.sendParticles(ModParticles.VOID_FLAME.get(), burner.x() + 0.5, burner.y() + 1.2, burner.z() + 0.5,
                40, 0.3, 0.4, 0.3, 0.05);
        level.sendParticles(ParticleTypes.PORTAL, burner.x() + 0.5, burner.y() + 1.2, burner.z() + 0.5,
                20, 0.2, 0.2, 0.2, 0.5);
        level.playSound((Player) null, burner.x() + 0.5, burner.y() + 0.5, burner.z() + 0.5,
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    private static void flameRing(MinecraftServer server, ServerLevel level, long now) {
        double angle = (now % 628) / 100.0;
        for (LedgerData.Burner burner : Ledger.get(server).data.burners().values()) {
            if (!stands(level, burner) || !level.hasNearbyAlivePlayer(burner.x() + 0.5, burner.y(), burner.z() + 0.5, 48)) {
                continue;
            }
            for (int k = 0; k < 3; k++) {
                double a = angle + k * (Math.PI * 2 / 3);
                level.sendParticles(ModParticles.VOID_FLAME.get(),
                        burner.x() + 0.5 + Math.cos(a) * 0.6, burner.y() + 1.1, burner.z() + 0.5 + Math.sin(a) * 0.6,
                        1, 0, 0.03, 0, 0.0);
            }
        }
    }

    private static void ambience(MinecraftServer server, ServerLevel level) {
        for (LedgerData.Burner burner : Ledger.get(server).data.burners().values()) {
            if (stands(level, burner) && level.hasNearbyAlivePlayer(burner.x() + 0.5, burner.y(), burner.z() + 0.5, 48)) {
                level.sendParticles(ModParticles.VOID_SOUL.get(), burner.x() + 0.5, burner.y() + 1, burner.z() + 0.5,
                        3, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    private static void lookDisplays(MinecraftServer server, ServerLevel level, long now) {
        Ledger ledger = Ledger.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                continue;
            }
            long worth = ValueTable.value(ValueTable.itemId(held.getItem()));
            if (worth <= 0) {
                continue;
            }
            HitResult hit = player.pick(6.0, 0.0F, false);
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            BlockPos pos = blockHit.getBlockPos();
            LedgerData.Burner match = null;
            for (LedgerData.Burner burner : ledger.data.burners().values()) {
                if (burner.x() == pos.getX() && burner.y() == pos.getY() && burner.z() == pos.getZ()) {
                    match = burner;
                }
            }
            if (match == null || !stands(level, match)) {
                continue;
            }
            showWorth(level, pos, worth, now);
        }
        Iterator<Map.Entry<BlockPos, DisplayState>> it = displays.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, DisplayState> entry = it.next();
            if (now - entry.getValue().lastSeen > 10) {
                entry.getValue().display.discard();
                it.remove();
            }
        }
    }

    private static void showWorth(ServerLevel level, BlockPos pos, long worth, long now) {
        DisplayState state = displays.get(pos);
        if (state != null && (state.display.isRemoved() || state.worth != worth)) {
            state.display.discard();
            displays.remove(pos);
            state = null;
        }
        if (state == null) {
            Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
            if (display == null) {
                return;
            }
            display.setPos(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5);
            // The text has to be set before the entity is added: the spawn packet is
            // what carries synced entity data to the client.
            setText(display, Component.literal("Worth: " + worth).withStyle(ChatFormatting.DARK_PURPLE),
                    level.registryAccess());
            level.addFreshEntity(display);
            state = new DisplayState(display, worth, now);
            displays.put(pos, state);
        }
        state.lastSeen = now;
    }

    static void setText(Display.TextDisplay display, Component text, HolderLookup.Provider registries) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        // 1.21.1 TextDisplay reads "text" as a JSON string, not as an NBT component.
        tag.putString("text", Component.Serializer.toJson(text, registries));
        tag.putString("billboard", "center");
        display.load(tag);
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!event.getPlacedBlock().is(Blocks.SOUL_CAMPFIRE)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (level.dimension() != Level.OVERWORLD) {
            player.sendSystemMessage(Component.literal("Burners only take root in the overworld."));
            return;
        }
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos.below()).is(Blocks.REDSTONE_BLOCK)) {
            player.sendSystemMessage(Component.literal("A burner needs a redstone block beneath it."));
            return;
        }
        Ledger ledger = Ledger.get(player.server);
        LedgerData.Burner old = ledger.data.burner(player.getUUID());
        if (old != null && stands(level, old)) {
            player.sendSystemMessage(Component.literal("Your burner still stands. Break it before binding another."));
            return;
        }
        ledger.data.bindBurner(player.getUUID(), new LedgerData.Burner(pos.getX(), pos.getY(), pos.getZ()));
        ledger.data.account(player.getUUID()).setLastFedTick(player.server.getTickCount());
        ledger.setDirty();
        level.setBlock(pos, ModBlocks.VOID_CAMPFIRE.get().defaultBlockState(), 3);
        player.sendSystemMessage(Component.literal("The burner answers to you. Drop offerings upon it."));
        player.addItem(new ItemStack(ModItems.CODEX.get()));
    }
}
