package dev.eldritch.exchange;

import dev.eldritch.exchange.core.Risk;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Vex;

public final class RiskEffects {

    private RiskEffects() {
    }

    /** Applies the bad outcome of a withdrawal. Returns true when the item should still be given. */
    public static boolean apply(ServerPlayer player, Risk.Event event) {
        ServerLevel level = (ServerLevel) player.level();
        switch (event) {
            case LIGHTNING -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(player.getX(), player.getY(), player.getZ());
                    level.addFreshEntity(bolt);
                }
                player.sendSystemMessage(Component.literal("The sky tears open above you."));
                return true;
            }
            case WHISPERS -> {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300, 0));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400, 0));
                player.sendSystemMessage(Component.literal("Something beneath the world whispers your name."));
                return true;
            }
            case SWARM -> {
                for (int i = -1; i <= 1; i += 2) {
                    Vex vex = EntityType.VEX.create(level);
                    if (vex != null) {
                        vex.moveTo(player.getX() + i, player.getY(), player.getZ());
                        vex.setLimitedLife(20 * 30);
                        level.addFreshEntity(vex);
                    }
                }
                player.sendSystemMessage(Component.literal("Things with too many eyes unfold from the dark."));
                return true;
            }
            case CRUMBLE -> {
                player.sendSystemMessage(Component.literal("The offering crumbles to ash in your hands."));
                return false;
            }
            default -> {
                return true;
            }
        }
    }
}
