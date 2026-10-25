package dev.eldritch.exchange;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Eldritch.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_SOUL =
            PARTICLES.register("void_soul", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_FLAME =
            PARTICLES.register("void_flame", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
