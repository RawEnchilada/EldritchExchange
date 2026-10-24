package dev.eldritch.exchange.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import dev.eldritch.exchange.Eldritch;
import dev.eldritch.exchange.TentacleEntity;

public class TentacleRenderer extends MobRenderer<TentacleEntity, TentacleModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Eldritch.MODID, "textures/entity/tentacle.png");

    public TentacleRenderer(EntityRendererProvider.Context context) {
        super(context, new TentacleModel(context.bakeLayer(TentacleModel.LAYER)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(TentacleEntity entity) {
        return TEXTURE;
    }
}