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

    private final ModelPart rootPart;

    public TentacleModel(ModelPart rootPart) {
        super();
        this.rootPart = rootPart;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();

        // Segment data from art/tentacle/tentacle_v2.blend (14 segments).
        double[][] segs = {
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

        double bx = segs[0][0], by = segs[0][1], bz = segs[0][2];
        for (int i = 0; i < segs.length; i++) {
            double[] s = segs[i];
            float rx = (float) (s[0] - bx);
            float ry = (float) (s[2] - bz); // height
            float rz = (float) (s[1] - by);
            float w = (float) s[3];
            float d = (float) s[4];
            float h = (float) s[5];
            part.addOrReplaceChild("seg" + (i + 1),
                    CubeListBuilder.create().texOffs(0, i * 4)
                            .addBox(-w / 2.0F, 0.0F, -d / 2.0F, w, h, d),
                    PartPose.offset(rx, ry, rz));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        // Root part is the first segment's parent; use a virtual root by returning first segment.
        // HierarchicalModel requires a root; create an empty root is not available, so return seg1.
        return rootPart;
    }

    @Override
    public void setupAnim(TentacleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // gentle sway per segment
        for (int i = 1; i <= 14; i++) {
            ModelPart seg = rootPart.getChild("seg" + i);
            if (seg != null) {
                seg.yRot = (float) Math.sin((ageInTicks + i * 4) * 0.08) * 0.06F * i;
            }
        }
    }
}