package dev.eldritch.exchange.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import dev.eldritch.exchange.Eldritch;
import dev.eldritch.exchange.TentacleEntity;

public class TentacleModel extends HierarchicalModel<TentacleEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Eldritch.MODID, "tentacle"), "main");

    private static final int SEGMENTS = 14;
    /** Total body length in model pixels (1/16 block). Used to hide the body under the ground. */
    private static final float SINK = 96.0F;
    /** Mob models render flipped and lifted 1.5 blocks, so model Y=24 is the ground the entity stands on. */
    private static final float GROUND_Y = 24.0F;

    // Segment data from art/tentacle/tentacle_v2.blend: x, y, height, width, depth, unused.
    private static final double[][] SEGS = {
            {0.023, -0.053, 0.358, 0.447, 0.364, 0.547},
            {0.104, -0.124, 0.832, 0.457, 0.308, 0.542},
            {0.222, -0.156, 1.299, 0.346, 0.264, 0.541},
            {0.367, -0.155, 1.759, 0.392, 0.284, 0.539},
            {0.527, -0.129, 2.211, 0.349, 0.277, 0.534},
            {0.689, -0.084, 2.653, 0.280, 0.222, 0.524},
            {0.843, -0.029, 3.085, 0.318, 0.201, 0.508},
            {0.976, 0.029, 3.504, 0.247, 0.214, 0.486},
            {1.078, 0.084, 3.910, 0.220, 0.188, 0.460},
            {1.136, 0.129, 4.302, 0.229, 0.145, 0.433},
            {1.139, 0.155, 4.678, 0.161, 0.137, 0.413},
            {1.076, 0.156, 5.036, 0.155, 0.134, 0.408},
            {0.934, 0.124, 5.377, 0.135, 0.104, 0.428},
            {0.703, 0.053, 5.697, 0.087, 0.076, 0.480},
    };

    private final ModelPart rootPart;
    private final ModelPart[] segments = new ModelPart[SEGMENTS];

    public TentacleModel(ModelPart rootPart) {
        super();
        this.rootPart = rootPart;
        ModelPart parent = rootPart;
        for (int i = 0; i < SEGMENTS; i++) {
            parent = parent.getChild("seg" + i);
            segments[i] = parent;
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parent = mesh.getRoot();

        // Each segment pivots at its base and extends upward (negative Y) by its length.
        // The next segment is a child of this one, so bending carries up the whole body.
        for (int i = 0; i < SEGMENTS; i++) {
            double[] s = SEGS[i];
            double[] next = SEGS[Math.min(i + 1, SEGMENTS - 1)];
            double[] prev = SEGS[Math.max(i - 1, 0)];
            float length = (float) ((i < SEGMENTS - 1 ? next[2] - s[2] : s[2] - prev[2]) * 16.0);
            float w = (float) s[3] * 16.0F;
            float d = (float) s[4] * 16.0F;
            float pivotY = i == 0 ? 0.0F : -(float) ((s[2] - SEGS[i - 1][2]) * 16.0);
            // Each segment's UV block is about 24x12 texels, so spread them across the 64x64 sheet.
            parent = parent.addOrReplaceChild("seg" + i,
                    CubeListBuilder.create().texOffs((i % 2) * 32, ((i / 2) % 5) * 12)
                            .addBox(-w / 2.0F, -length, -d / 2.0F, w, length, d),
                    PartPose.offset(0.0F, pivotY, 0.0F));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return rootPart;
    }

    @Override
    public void setupAnim(TentacleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - entity.tickCount;
        float emerge = entity.emergeProgress(partial);
        float retract = entity.retractProgress(partial);
        float death = entity.deathTime > 0 ? Math.min(1.0F, (entity.deathTime + partial) / 20.0F) : 0.0F;

        // Pose lean for the current action, applied to every segment weighted toward the tip.
        float lean = 0.0F;
        float curl = 0.0F;
        float at = entity.animTicks(partial);
        switch (entity.animState()) {
            case TentacleEntity.ANIM_ATTACK -> lean = attackLean(at);
            case TentacleEntity.ANIM_HIT -> {
                float fade = Math.max(0.0F, 1.0F - at / TentacleEntity.HIT_TICKS);
                lean = -0.9F * fade;
                curl = (float) Math.sin(at * 1.5F) * 0.4F * fade;
            }
            case TentacleEntity.ANIM_GRAB -> {
                lean = 1.1F * Math.min(1.0F, at / 20.0F);
                curl = (float) Math.sin(ageInTicks * 0.3F) * 0.15F;
            }
            default -> {
            }
        }

        // Rise out of the ground, then sink back into it. Lower segments stay hidden underground.
        rootPart.y = GROUND_Y + ((1.0F - emerge) + retract + death * 0.6F) * SINK;

        for (int i = 0; i < SEGMENTS; i++) {
            float f = (i + 1) / (float) SEGMENTS; // 0 at the base, 1 at the tip
            float sway = 0.06F + 0.26F * f;
            if (entity.isGrasping()) {
                sway *= 1.6F;
            }
            if (death > 0.0F) {
                sway *= 1.0F - death;
            }

            ModelPart seg = segments[i];
            seg.zRot = (float) Math.sin(ageInTicks * 0.11F - i * 0.55F) * sway + curl * f;
            seg.xRot = (float) Math.cos(ageInTicks * 0.077F - i * 0.4F) * sway * 0.7F
                    + (1.0F - emerge) * 0.9F * f
                    + retract * 1.2F * f
                    + lean * f
                    + death * 1.4F * f;
        }
    }

    /** Wind back, whip forward on the impact tick, then recover to rest. */
    private static float attackLean(float at) {
        if (at < 0.0F) {
            return 0.0F;
        }
        if (at < 8.0F) {
            return -0.5F * (at / 8.0F);
        }
        if (at < TentacleEntity.ATTACK_IMPACT) {
            return -0.5F + 2.0F * ((at - 8.0F) / (TentacleEntity.ATTACK_IMPACT - 8.0F));
        }
        return Math.max(0.0F, 1.5F * (1.0F - (at - TentacleEntity.ATTACK_IMPACT) / (TentacleEntity.ATTACK_TICKS - TentacleEntity.ATTACK_IMPACT)));
    }
}
