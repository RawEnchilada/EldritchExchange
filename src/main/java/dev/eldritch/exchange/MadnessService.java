package dev.eldritch.exchange;

import dev.eldritch.exchange.core.Demand;
import dev.eldritch.exchange.core.LedgerData;
import dev.eldritch.exchange.core.Madness;
import dev.eldritch.exchange.core.Starvation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Madness is a slow, deliberate haunting rather than a metronome. Every player
 * has their own schedule; a trick fires at random intervals and never repeats one
 * of the last few kinds. The pool grows with the madness tier.
 */
public final class MadnessService {

    public static final String HALLUCINATION_TAG = "eldritch_hallucination";

    private static final int DECAY_PULSE = Madness.DECAY_INTERVAL_TICKS;
    private static final int HALLUCINATION_LIFE = 180;
    private static final int BURNER_DREAD_INTERVAL = 80;
    private static final double BURNER_DREAD_RADIUS = 6.0;
    private static final int RECENT_TRICKS = 4;

    private record Hallucination(Entity entity, long expiresAt) {
    }

    private static final class Mind {
        long nextTrick;
        final Deque<Trick> recent = new ArrayDeque<>();

        Mind(long now) {
            this.nextTrick = now + 100;
        }

        void remember(Trick trick) {
            recent.addFirst(trick);
            while (recent.size() > RECENT_TRICKS) {
                recent.removeLast();
            }
        }
    }

    private static final RandomSource random = RandomSource.create();
    private static final Map<UUID, Mind> minds = new HashMap<>();
    private static final List<Hallucination> hallucinations = new ArrayList<>();
    private static final Map<UUID, Long> lastHarass = new HashMap<>();
    private static final Map<UUID, TentacleEntity> grasps = new HashMap<>();

    private record PendingTithe(int slot, ItemStack stack, long takeAt) {
    }

    private static final Map<UUID, PendingTithe> pendingTithes = new HashMap<>();

    private record ThiefState(net.minecraft.world.entity.Entity bat, net.minecraft.world.entity.Display.ItemDisplay display,
                             ItemStack stack, long stolenAt, long cooldownUntil) {
    }

    private static final Map<UUID, ThiefState> activeThieves = new HashMap<>();
    private static final Map<UUID, Long> thiefCooldowns = new HashMap<>();

    private MadnessService() {
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        cleanupHallucinations(now);

        Ledger ledger = Ledger.get(server);
        boolean dirty = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            LedgerData.Account account = ledger.data.account(id);
            Madness.Tier tier = Madness.tier(account.madness());

            // Ensure fed timer is initialized for bound burners.
            LedgerData.Burner burner = ledger.data.burner(id);
            if (burner != null && account.lastFedTick() <= 0) {
                account.setLastFedTick(now);
            }

            if (tier == Madness.Tier.CALM) {
                minds.remove(id);
            } else {
                Mind mind = minds.computeIfAbsent(id, key -> new Mind(now));
                if (now >= mind.nextTrick) {
                    Trick trick = pickTrick(tier, mind);
                    if (trick != null) {
                        trick.run(player, tier);
                        mind.remember(trick);
                    }
                    mind.nextTrick = now + delay(tier);
                }
                if (tier == Madness.Tier.DEEP && now % BURNER_DREAD_INTERVAL == 0 && nearBurner(player)) {
                    burnerDread(player);
                }
            }

            // Decay with 1-minute pause after gains.
            if (now % DECAY_PULSE == 0 && Madness.canDecay(account.lastGainTick(), now)) {
                ledger.data.setMadness(id, Madness.decay(account.madness()));
                dirty = true;
            }

            // Starvation.
            if (burner != null && BurnerService.stands(player.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : server.overworld(), burner)) {
                if (Starvation.shouldWarn(account.lastFedTick(), account.lastWarningTick(), now)) {
                    int idx = Starvation.warningIndex(account.lastFedTick(), now);
                    account.setStarvationTier(idx);
                    account.setLastWarningTick(now);
                    int delta = Starvation.madnessForWarning(idx);
                    ledger.data.addMadness(id, delta);
                    dirty = true;
                    String msg = idx >= 3 ? "Thal'ggor is starving." : (Thalggor.name(server, id) + " hungers.");
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg)
                            .withStyle(net.minecraft.ChatFormatting.DARK_RED));
                }
            }

            // Demand issuing.
            if (Madness.tier(ledger.data.account(id).madness()).ordinal() >= Madness.Tier.CRYPTIC.ordinal()
                    && Demand.eligible(ledger.data.account(id).madness(), ledger.data.learned().size(),
                            !"NONE".equals(ledger.data.account(id).demandState()))) {
                // roll once per 200 ticks
                if (now % 200 == 0 && random.nextInt(600) == 0) {
                    String demanded = Demand.pickDemanded(ledger.data.learned(), ledger.data.account(id).sealed(), new java.util.Random(now));
                    if (demanded != null) {
                        Madness.Tier t = Madness.tier(ledger.data.account(id).madness());
                        int cnt = Demand.countFor(t, new java.util.Random(now));
                        long cooldown = Demand.cooldownTicks(t);
                        List<String> sealed = Demand.buildSealed(demanded, ledger.data.learned(), new java.util.Random(now));
                        ledger.data.account(id).issueDemand(demanded, cnt, now, now + cooldown, sealed);
                        dirty = true;
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                Thalggor.name(server, id) + " demands " + cnt + "x " + demanded + ". Sealed items await.")
                                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
                    }
                }
            }

            // Tentacle harassment (75+) and grasp (90+).
            if (account.madness() >= 75) {
                long last = lastHarass.getOrDefault(id, 0L);
                if (now - last >= 200) {
                    lastHarass.put(id, now);
                    int count = 1 + random.nextInt(2);
                    for (int i = 0; i < count; i++) {
                        spawnTentacle(player, false);
                    }
                }
            }
            // Grasp handling.
            TentacleEntity grasp = grasps.get(id);
            if (grasp != null) {
                if (!grasp.isAlive() || grasp.isRemoved() || account.madness() < 90) {
                    grasps.remove(id);
                } else {
                    // root movement
                    player.setDeltaMovement(0, player.getDeltaMovement().y * 0.1, 0);
                    player.hurtMarked = true;
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, false));
                    // suffocation after grace
                    long heldFor = now - grasp.tickCount + 0; // approximate
                    if (grasp.tickCount > 60 && grasp.tickCount % 40 == 0) {
                        player.hurt(player.damageSources().mobAttack(grasp), 1.0F);
                    }
                    if (grasp.tickCount > 200) {
                        grasp.release();
                        grasps.remove(id);
                    }
                }
            } else if (account.madness() >= 90 && random.nextInt(300) == 0) {
                TentacleEntity t = spawnTentacle(player, true);
                if (t != null) {
                    t.setGraspTarget(player.getId());
                    grasps.put(id, t);
                }
            }

            // Light lash from nearby non-grasping tentacles.
            if (account.madness() >= 75 && player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                for (TentacleEntity t : sl.getEntitiesOfClass(TentacleEntity.class, player.getBoundingBox().inflate(6.0))) {
                    if (t.isGrasping() || !t.isAlive()) continue;
                    if (t.tickCount % 60 == 0 && player.distanceTo(t) < 3.0) {
                        player.hurt(player.damageSources().mobAttack(t), 1.0F);
                    }
                }
            }

            // Pending tithe execution.
            PendingTithe pending = pendingTithes.get(id);
            if (pending != null && now >= pending.takeAt()) {
                pendingTithes.remove(id);
                net.minecraft.world.entity.player.Inventory inv = player.getInventory();
                if (pending.slot() >= 0 && pending.slot() < inv.items.size()) {
                    ItemStack current = inv.items.get(pending.slot());
                    if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, pending.stack())) {
                        inv.items.set(pending.slot(), ItemStack.EMPTY);
                        player.inventoryMenu.broadcastChanges();
                        String itemId = ValueTable.itemId(pending.stack().getItem());
                        ledger.data.account(id).issueTithe(itemId, pending.stack().getCount(), now, now + 6000);
                        dirty = true;
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                Thalggor.name(server, id) + " takes its due. Burn an offering of equal worth to reclaim it.")
                                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
                    }
                }
            }

            // Tithe reclamation: if player has debt and burns, handled in BurnerService; here just expiry.
            LedgerData.Account acc = ledger.data.account(id);
            if (acc.titheItemId() != null && !acc.titheExpired() && now >= acc.titheReclaimDeadline()) {
                acc.expireTithe();
                long val = ValueTable.value(acc.titheItemId()) * acc.titheCount();
                ledger.data.addEssence(id, Math.max(1, val / 4));
                dirty = true;
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "The tithe is consumed by the abyss.")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                acc.clearTithe();
            }

            // Demand expiry.
            LedgerData.Account dacc = ledger.data.account(id);
            if ("ACTIVE".equals(dacc.demandState()) && dacc.demandDeadlineTick() > 0 && now >= dacc.demandDeadlineTick()) {
                dacc.expireDemand();
                dacc.sealed().clear();
                ledger.data.addMadness(id, 2);
                dirty = true;
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        Thalggor.name(server, id) + " is displeased. The seals break, but madness deepens.")
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED));
            }

            // Active book thief handling.
            ThiefState thief = activeThieves.get(id);
            if (thief != null) {
                net.minecraft.world.entity.Entity bat = thief.bat();
                if (bat == null || bat.isRemoved() || !bat.isAlive() || now - thief.stolenAt() > 12000) {
                    // drop codex
                    net.minecraft.world.level.Level lvl = player.level();
                    if (bat != null && bat.isAlive()) {
                        lvl = bat.level();
                    }
                    if (lvl instanceof net.minecraft.server.level.ServerLevel sl) {
                        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(sl,
                                (bat != null ? bat.getX() : player.getX()),
                                (bat != null ? bat.getY() : player.getY()),
                                (bat != null ? bat.getZ() : player.getZ()), thief.stack().copy());
                        sl.addFreshEntity(drop);
                    }
                    if (thief.display() != null) thief.display().discard();
                    if (bat != null) bat.discard();
                    activeThieves.remove(id);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("The Codex returns.")
                            .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
                } else {
                    // move display to bat
                    if (thief.display() != null && !thief.display().isRemoved()) {
                        thief.display().setPos(bat.getX(), bat.getY() + 0.5, bat.getZ());
                    }
                }
            }
        }
        if (dirty) {
            ledger.setDirty();
        }
    }

    private static long delay(Madness.Tier tier) {
        return switch (tier) {
            case WHISPER -> 400 + random.nextInt(600);
            case CRYPTIC -> 320 + random.nextInt(480);
            case DEEP -> 200 + random.nextInt(400);
            default -> 600;
        };
    }

    private static Trick pickTrick(Madness.Tier tier, Mind mind) {
        int total = 0;
        for (Trick trick : Trick.values()) {
            if (tier.ordinal() >= trick.minTier.ordinal() && !mind.recent.contains(trick)) {
                total += trick.weight;
            }
        }
        if (total == 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Trick trick : Trick.values()) {
            if (tier.ordinal() < trick.minTier.ordinal() || mind.recent.contains(trick)) {
                continue;
            }
            roll -= trick.weight;
            if (roll < 0) {
                return trick;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ tricks

    private enum Trick {
        WHISPER(Madness.Tier.WHISPER, 6, MadnessService::whisper),
        NAMED_WHISPER(Madness.Tier.WHISPER, 4, MadnessService::namedWhisper),
        PLAYER_CHAT(Madness.Tier.WHISPER, 7, MadnessService::playerChat),
        MOB_SOUND(Madness.Tier.WHISPER, 7, MadnessService::mobSound),
        FOOTSTEPS(Madness.Tier.WHISPER, 5, MadnessService::footsteps),
        NEARBY_NOISE(Madness.Tier.WHISPER, 4, MadnessService::nearbyNoise),
        ANIMAL_PANIC(Madness.Tier.WHISPER, 3, MadnessService::scareAnimals),
        HALLUCINATION(Madness.Tier.CRYPTIC, 4, MadnessService::hallucination),
        BLIND_FLICKER(Madness.Tier.CRYPTIC, 4, MadnessService::blindFlicker),
        TURN_AROUND(Madness.Tier.CRYPTIC, 3, MadnessService::turnAround),
        BURNER_CALL(Madness.Tier.CRYPTIC, 3, MadnessService::burnerCall),
        PUSH(Madness.Tier.CRYPTIC, 3, MadnessService::push),
        DARKNESS(Madness.Tier.DEEP, 3, MadnessService::darkness),
        ANGER_MOBS(Madness.Tier.DEEP, 3, MadnessService::angerMobs),
        DROP_ITEM(Madness.Tier.DEEP, 2, MadnessService::dropHeldItem),
        FAKE_JOIN(Madness.Tier.DEEP, 2, MadnessService::fakeJoin),
        FAKE_LEAVE(Madness.Tier.DEEP, 1, MadnessService::fakeLeave),
        GAZE(Madness.Tier.CRYPTIC, 2, MadnessService::gaze),
        TITHE(Madness.Tier.CRYPTIC, 2, MadnessService::tithe),
        HUNGERING_DARK(Madness.Tier.DEEP, 2, MadnessService::hungeringDark),
        BOOK_THIEF(Madness.Tier.DEEP, 1, MadnessService::bookThief);

        final Madness.Tier minTier;
        final int weight;
        final TrickAction action;

        Trick(Madness.Tier minTier, int weight, TrickAction action) {
            this.minTier = minTier;
            this.weight = weight;
            this.action = action;
        }

        void run(ServerPlayer player, Madness.Tier tier) {
            action.run(player, tier);
        }
    }

    @FunctionalInterface
    private interface TrickAction {
        void run(ServerPlayer player, Madness.Tier tier);
    }

    private static void whisper(ServerPlayer player, Madness.Tier tier) {
        String[] lines = {
                "Something behind the stone is counting.",
                "The dark has your shape memorized.",
                "Do not look at the ceiling.",
                "It learned your footsteps.",
                "That was not your reflection.",
                "The codex is still open.",
                "You left something at the burner.",
                "It is closer than the sound.",
                "Stop reading this."
        };
        if (tier == Madness.Tier.DEEP) {
            lines = new String[]{
                    "You are the offering.",
                    "It is standing in the doorway.",
                    "The burner is still warm.",
                    "Do not turn around.",
                    "We have been waiting for you."
            };
        }
        say(player, lines[random.nextInt(lines.length)], ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
    }

    private static void namedWhisper(ServerPlayer player, Madness.Tier tier) {
        String name = randomName(player);
        String[] lines = {
                name + " is standing behind you.",
                name + " has been very quiet.",
                name + " asked where you went.",
                name + " is not breathing.",
                "Put it back, " + player.getGameProfile().getName() + "."
        };
        say(player, lines[random.nextInt(lines.length)], ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
    }

    private static void playerChat(ServerPlayer player, Madness.Tier tier) {
        long now = player.server.getTickCount();
        List<ChatMemory.Line> all = ChatMemory.snapshot();
        List<ChatMemory.Line> old = new ArrayList<>();
        for (ChatMemory.Line line : all) {
            if (now - line.at() > 100) {
                old.add(line);
            }
        }
        if (!old.isEmpty() || !all.isEmpty()) {
            List<ChatMemory.Line> source = old.isEmpty() ? all : old;
            ChatMemory.Line line = source.get(random.nextInt(source.size()));
            say(player, "<" + line.name() + "> " + line.text(), ChatFormatting.WHITE);
            return;
        }
        String[] lines = {
                "anyone else hear that?",
                "i'm at the burner",
                "don't go down there",
                "who keeps breaking the torches",
                "can you hear me",
                "stop following me",
                "i found another one",
                "it's not a real creeper"
        };
        say(player, "<" + randomName(player) + "> " + lines[random.nextInt(lines.length)], ChatFormatting.WHITE);
    }

    private static void mobSound(ServerPlayer player, Madness.Tier tier) {
        SoundEvent[] sounds = {
                SoundEvents.ZOMBIE_AMBIENT,
                SoundEvents.SKELETON_AMBIENT,
                SoundEvents.SPIDER_AMBIENT,
                SoundEvents.ENDERMAN_SCREAM,
                SoundEvents.ENDERMAN_STARE,
                SoundEvents.CREEPER_PRIMED,
                SoundEvents.GHAST_SCREAM,
                SoundEvents.WITHER_AMBIENT,
                SoundEvents.WITHER_SKELETON_AMBIENT,
                SoundEvents.WARDEN_HEARTBEAT
        };
        playBehind(player, sounds[random.nextInt(sounds.length)], 4 + random.nextDouble() * 6,
                1.0F, 0.7F + random.nextFloat() * 0.5F);
    }

    private static void footsteps(ServerPlayer player, Madness.Tier tier) {
        for (int i = 0; i < 3; i++) {
            SoundEvent step = random.nextBoolean() ? SoundEvents.GRASS_STEP : SoundEvents.STONE_STEP;
            playBehind(player, step, 2.0 + i * 1.5, 0.7F, 0.8F + random.nextFloat() * 0.3F);
        }
    }

    private static void nearbyNoise(ServerPlayer player, Madness.Tier tier) {
        SoundEvent[] sounds = {
                SoundEvents.CHEST_OPEN,
                SoundEvents.ENDER_CHEST_OPEN,
                SoundEvents.WOODEN_DOOR_OPEN,
                SoundEvents.WOODEN_TRAPDOOR_OPEN
        };
        playBehind(player, sounds[random.nextInt(sounds.length)], 2 + random.nextDouble() * 4,
                0.8F, 0.9F + random.nextFloat() * 0.2F);
    }

    private static void blindFlicker(ServerPlayer player, Madness.Tier tier) {
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 70, 0, false, false));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.AMBIENT, 1.0F, 0.8F);
    }

    private static void darkness(ServerPlayer player, Madness.Tier tier) {
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false));
    }

    private static void turnAround(ServerPlayer player, Madness.Tier tier) {
        float yaw = player.getYRot() + 180.0F + (random.nextFloat() - 0.5F) * 40.0F;
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, player.getXRot());
        player.setYHeadRot(yaw);
    }

    private static void push(ServerPlayer player, Madness.Tier tier) {
        double angle = random.nextDouble() * Math.PI * 2;
        player.push(Math.cos(angle) * 0.6, 0.35, Math.sin(angle) * 0.6);
        player.hurtMarked = true;
    }

    private static void dropHeldItem(ServerPlayer player, Madness.Tier tier) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            hand = InteractionHand.OFF_HAND;
            held = player.getOffhandItem();
        }
        if (held.isEmpty() || player.isInLava() || player.isOnFire()) {
            namedWhisper(player, tier);
            return;
        }
        ItemStack dropped = held.copy();
        player.setItemInHand(hand, ItemStack.EMPTY);
        player.drop(dropped, false, true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.6F, 0.5F);
    }

    private static void scareAnimals(ServerPlayer player, Madness.Tier tier) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(10.0));
        int scared = 0;
        for (Animal animal : animals) {
            Vec3 away = animal.position().subtract(player.position());
            if (away.lengthSqr() < 0.01) {
                away = new Vec3(0, 0, 1);
            }
            away = away.normalize();
            animal.setLastHurtByMob(player);
            animal.setDeltaMovement(away.x * 0.55, 0.3, away.z * 0.55);
            animal.hurtMarked = true;
            if (++scared >= 6) {
                break;
            }
        }
        if (scared == 0) {
            mobSound(player, tier);
        }
    }

    private static void angerMobs(ServerPlayer player, Madness.Tier tier) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(14.0));
        int angered = 0;
        for (Mob mob : mobs) {
            if (!mob.isAlive() || mob.getTags().contains(HALLUCINATION_TAG)) {
                continue;
            }
            if (mob instanceof NeutralMob neutral) {
                neutral.setPersistentAngerTarget(player.getUUID());
                neutral.startPersistentAngerTimer();
                mob.setTarget(player);
                angered++;
            } else if (mob instanceof Monster) {
                mob.setLastHurtByMob(player);
                mob.setTarget(player);
                angered++;
            }
            if (angered >= 3) {
                break;
            }
        }
        if (angered == 0) {
            mobSound(player, tier);
        }
    }

    private static void burnerCall(ServerPlayer player, Madness.Tier tier) {
        LedgerData.Burner burner = Ledger.get(player.server).data.burner(player.getUUID());
        if (burner == null || !(player.level() instanceof ServerLevel level) || !BurnerService.stands(level, burner)) {
            whisper(player, tier);
            return;
        }
        Vec3 at = new Vec3(burner.x() + 0.5, burner.y() + 1.0, burner.z() + 0.5);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(),
                SoundSource.AMBIENT, 1.4F, 0.7F);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 12, 0.4, 0.5, 0.4, 0.01);
        if (player.position().distanceTo(at) < 24.0) {
            double dx = at.x - player.getX();
            double dz = at.z - player.getZ();
            float yaw = (float) (Math.atan2(-dx, dz) * 180.0 / Math.PI);
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, player.getXRot());
            player.setYHeadRot(yaw);
            say(player, "The burner remembers your name.", ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
        }
    }

    private static void fakeJoin(ServerPlayer player, Madness.Tier tier) {
        player.sendSystemMessage(Component.translatable("multiplayer.player.joined", randomName(player))
                .withStyle(ChatFormatting.YELLOW));
    }

    private static void fakeLeave(ServerPlayer player, Madness.Tier tier) {
        player.sendSystemMessage(Component.translatable("multiplayer.player.left", randomName(player))
                .withStyle(ChatFormatting.YELLOW));
    }

    private static void burnerDread(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(),
                SoundSource.AMBIENT, 1.2F, 0.6F);
    }

    private static boolean nearBurner(ServerPlayer player) {
        LedgerData.Burner burner = Ledger.get(player.server).data.burner(player.getUUID());
        if (burner == null || !(player.level() instanceof ServerLevel level) || !BurnerService.stands(level, burner)) {
            return false;
        }
        double dx = player.getX() - (burner.x() + 0.5);
        double dy = player.getY() - burner.y();
        double dz = player.getZ() - (burner.z() + 0.5);
        return dx * dx + dy * dy + dz * dz <= BURNER_DREAD_RADIUS * BURNER_DREAD_RADIUS;
    }

    // ------------------------------------------------------------ hallucinations

    public static void spawnHallucination(ServerPlayer player) {
        hallucination(player, Madness.Tier.CRYPTIC);
    }

    private static void hallucination(ServerPlayer player, Madness.Tier tier) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        EntityType<?>[] types = {EntityType.CREEPER, EntityType.ENDERMAN, EntityType.SPIDER, EntityType.WITCH};
        EntityType<?> type = types[random.nextInt(types.length)];
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 5 + random.nextDouble() * 4;
        mob.moveTo(player.getX() + Math.cos(angle) * distance, player.getY(),
                player.getZ() + Math.sin(angle) * distance, random.nextFloat() * 360.0F, 0.0F);
        mob.setNoAi(true);
        mob.setSilent(true);
        mob.setInvulnerable(true);
        mob.addTag(HALLUCINATION_TAG);
        mob.lookAt(player, 180.0F, 180.0F);
        level.addFreshEntity(mob);
        hallucinations.add(new Hallucination(mob, level.getServer().getTickCount() + HALLUCINATION_LIFE));
        level.playSound(null, mob.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT, 0.7F, 1.3F);
    }

    private static void cleanupHallucinations(long now) {
        hallucinations.removeIf(hallucination -> {
            if (now >= hallucination.expiresAt() || !hallucination.entity().isAlive()) {
                hallucination.entity().discard();
                return true;
            }
            return false;
        });
    }

    public static void onHurt(LivingIncomingDamageEvent event) {
        Entity entity = event.getEntity();
        if (!entity.getTags().contains(HALLUCINATION_TAG)) {
            return;
        }
        event.setCanceled(true);
        Level level = entity.level();
        level.playSound((Player) null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0F, 1.2F);
        if (level instanceof ServerLevel serverLevel) {
            Vec3 at = entity.position();
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1, at.z, 2, 0.3, 0.5, 0.3, 0.0);
        }
        entity.discard();
    }

    private static void gaze(ServerPlayer player, Madness.Tier tier) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 50, 0, false, false));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 60, 0, false, false));
        say(player, "Something is looking back.", net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC);
    }

    private static void tithe(ServerPlayer player, Madness.Tier tier) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        int selected = inv.selected;
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (stack.isEmpty()) continue;
            if (i == selected) continue;
            if (stack.getItem() == ModItems.CODEX.get()) continue;
            LedgerData.Account acc = Ledger.get(player.server).data.account(player.getUUID());
            if (acc.isSealed(ValueTable.itemId(stack.getItem()))) continue;
            // skip tools/armor/food
            if (stack.is(net.minecraft.tags.ItemTags.SWORDS) || stack.is(net.minecraft.tags.ItemTags.AXES)
                    || stack.is(net.minecraft.tags.ItemTags.PICKAXES) || stack.is(net.minecraft.tags.ItemTags.SHOVELS)
                    || stack.is(net.minecraft.tags.ItemTags.HOES)) continue;
            if (stack.getItem().getFoodProperties(stack, null) != null) continue;
            if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem) continue;
            eligible.add(i);
        }
        if (eligible.isEmpty()) {
            whisper(player, tier);
            return;
        }
        int slot = eligible.get(random.nextInt(eligible.size()));
        ItemStack stack = inv.items.get(slot).copy();
        String name = stack.getHoverName().getString();
        say(player, "Something reaches for your " + name + "...", net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC);
        pendingTithes.put(player.getUUID(), new PendingTithe(slot, stack, player.server.getTickCount() + 60));
    }

    private static void hungeringDark(ServerPlayer player, Madness.Tier tier) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 120, 0, false, false));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 120, 0, false, false));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 120, 0, false, false));
        say(player, "The dark is hungry.", net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC);
    }

    private static void bookThief(ServerPlayer player, Madness.Tier tier) {
        UUID id = player.getUUID();
        long now = player.server.getTickCount();
        if (thiefCooldowns.getOrDefault(id, 0L) > now) {
            whisper(player, tier);
            return;
        }
        // find codex
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.items.size(); i++) {
            if (inv.items.get(i).getItem() == ModItems.CODEX.get()) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            whisper(player, tier);
            return;
        }
        ItemStack codex = inv.items.get(slot).copy();
        inv.items.set(slot, ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();

        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            // return codex
            if (!player.addItem(codex)) player.drop(codex, false);
            return;
        }
        net.minecraft.world.entity.EntityType<?> batType = net.minecraft.world.entity.EntityType.BAT;
        net.minecraft.world.entity.Mob bat = (net.minecraft.world.entity.Mob) batType.create(level);
        if (bat == null) {
            if (!player.addItem(codex)) player.drop(codex, false);
            return;
        }
        bat.setSilent(true);
        bat.setInvisible(true);
        bat.addTag("eldritch_thief");
        double angle = random.nextDouble() * Math.PI * 2;
        bat.moveTo(player.getX() + Math.cos(angle) * 2, player.getY() + 1, player.getZ() + Math.sin(angle) * 2, 0, 0);
        level.addFreshEntity(bat);

        net.minecraft.world.entity.Display.ItemDisplay display = net.minecraft.world.entity.EntityType.ITEM_DISPLAY.create(level);
        if (display != null) {
            display.getSlot(0).set(codex);
            display.setPos(bat.getX(), bat.getY() + 0.5, bat.getZ());
            level.addFreshEntity(display);
        }

        thiefCooldowns.put(id, now + 36000);
        activeThieves.put(id, new ThiefState(bat, display, codex, now, thiefCooldowns.get(id)));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Something steals your Codex...")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED));
    }

    private static TentacleEntity spawnTentacle(ServerPlayer player, boolean grasp) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        TentacleEntity t = ModEntities.TENTACLE.get().create(level);
        if (t == null) {
            return null;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = grasp ? 1.2 : 3.0 + random.nextDouble() * 3.0;
        t.moveTo(player.getX() + Math.cos(angle) * distance, player.getY(), player.getZ() + Math.sin(angle) * distance,
                (float) (random.nextDouble() * 360.0), 0.0F);
        level.addFreshEntity(t);
        return t;
    }

    public static void reset() {
        for (Hallucination hallucination : hallucinations) {
            hallucination.entity().discard();
        }
        hallucinations.clear();
        minds.clear();
        grasps.clear();
        lastHarass.clear();
        pendingTithes.clear();
        activeThieves.clear();
        thiefCooldowns.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static void say(ServerPlayer player, String text, ChatFormatting... formats) {
        player.sendSystemMessage(Component.literal(text).withStyle(formats));
    }

    private static String randomName(ServerPlayer player) {
        List<ServerPlayer> players = player.server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return player.getGameProfile().getName();
        }
        return players.get(random.nextInt(players.size())).getGameProfile().getName();
    }

    private static void playBehind(ServerPlayer player, SoundEvent sound, double distance, float volume, float pitch) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0, 0, 1);
        }
        flat = flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        Vec3 at = player.position().subtract(flat.scale(distance))
                .add(side.scale((random.nextDouble() - 0.5) * distance));
        player.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.AMBIENT, volume, pitch);
    }
}
