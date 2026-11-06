package dev.szo9k4.limbdamage.dummy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.szo9k4.limbdamage.dummy.DummyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Draws the dummy as a player wearing the spawner's own skin and arm model (wide or slim), with everything it
 * has equipped: armor (with trims and enchant glint), items in both hands, a head item (pumpkin, skull...) and
 * an elytra. The two models are two small inner renderers; this outer one just picks between them per entity.
 */
public class DummyRenderer extends EntityRenderer<DummyEntity> {

    private final Skinned wide;
    private final Skinned slim;

    public DummyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wide = new Skinned(context, false);
        this.slim = new Skinned(context, true);
    }

    @Override
    public void render(DummyEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        (isSlim(entity) ? slim : wide).render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DummyEntity entity) {
        return skin(entity);
    }

    /** Owner's UUID; with no owner (e.g. /summon) the local player's skin is used. */
    private static UUID skinOwner(DummyEntity entity) {
        UUID id = entity.getOwnerUuid().orElse(null);
        if (id == null && Minecraft.getInstance().player != null) {
            id = Minecraft.getInstance().player.getUUID();
        }
        return id != null ? id : entity.getUUID();
    }

    private static PlayerInfo info(UUID id) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null ? connection.getPlayerInfo(id) : null;
    }

    private static ResourceLocation skin(DummyEntity entity) {
        UUID id = skinOwner(entity);
        PlayerInfo info = info(id);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id);
    }

    private static boolean isSlim(DummyEntity entity) {
        UUID id = skinOwner(entity);
        PlayerInfo info = info(id);
        String model = info != null ? info.getModelName() : DefaultPlayerSkin.getSkinModelName(id);
        return "slim".equals(model);
    }

    private static final class Skinned extends LivingEntityRenderer<DummyEntity, PlayerModel<DummyEntity>> {
        Skinned(EntityRendererProvider.Context context, boolean slim) {
            super(context, new PlayerModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5F);
            this.addLayer(new HumanoidArmorLayer<DummyEntity, PlayerModel<DummyEntity>, HumanoidModel<DummyEntity>>(this,
                    new HumanoidModel<DummyEntity>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidModel<DummyEntity>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                    context.getModelManager()));
            this.addLayer(new ItemInHandLayer<DummyEntity, PlayerModel<DummyEntity>>(this, context.getItemInHandRenderer()));
            this.addLayer(new CustomHeadLayer<DummyEntity, PlayerModel<DummyEntity>>(this, context.getModelSet(), context.getItemInHandRenderer()));
            this.addLayer(new ElytraLayer<DummyEntity, PlayerModel<DummyEntity>>(this, context.getModelSet()));
        }

        @Override
        public void render(DummyEntity entity, float yaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource buffer, int packedLight) {
            PlayerModel<DummyEntity> m = this.getModel();
            m.rightArmPose = entity.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
            m.leftArmPose = entity.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
            super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
        }

        @Override
        public ResourceLocation getTextureLocation(DummyEntity entity) {
            return skin(entity);
        }
    }
}
