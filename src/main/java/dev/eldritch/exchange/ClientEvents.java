package dev.eldritch.exchange;

import dev.eldritch.exchange.client.TentacleModel;
import dev.eldritch.exchange.client.TentacleRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.SoulParticle;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = Eldritch.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CODEX.get(), CodexScreen::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TentacleModel.LAYER, TentacleModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TENTACLE.get(), TentacleRenderer::new);
    }

    @SubscribeEvent
    public static void setupClient(FMLClientSetupEvent event) {
        // The campfire fire is see-through, so it needs the cutout layer to show transparent pixels.
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.VOID_CAMPFIRE.get(), RenderType.cutout()));
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.VOID_SOUL.get(), SoulParticle.Provider::new);
        event.registerSpriteSet(ModParticles.VOID_FLAME.get(), FlameParticle.Provider::new);
    }
}