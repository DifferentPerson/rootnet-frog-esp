package localhost.adifferentperson.frogesp;

import dev.rootnet.addons.api.addon.AddonModule;
import dev.rootnet.addons.api.annotations.EventMethod;
import dev.rootnet.addons.api.annotations.RootnetModule;
import dev.rootnet.addons.api.events.RenderLivingBaseEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

@RootnetModule(name = "FrogEsp")
public final class FrogEspModule extends AddonModule {

    private final Minecraft MC = Minecraft.getMinecraft();
    private final FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);
    private final FloatBuffer modelView = GLAllocation.createDirectFloatBuffer(16);
    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();

    @SuppressWarnings("unused")
    @EventMethod
    public void onRenderLivingBase(final RenderLivingBaseEvent e) {
        final EntityLivingBase entityLivingBase = e.getEntityLivingBase();
        if (!(entityLivingBase instanceof AbstractClientPlayer)) {
            return;
        }
        final AbstractClientPlayer player = (AbstractClientPlayer) entityLivingBase;
        if (player.equals(MC.player)) {
            return;
        }
        e.setCancelled(true);
    }

    @Override
    public final void renderWorld() {
        glGetFloat(GL_PROJECTION_MATRIX, projection);
        glGetFloat(GL_MODELVIEW_MATRIX, modelView);
    }

    private Entity renderViewEntity;
    private double screenWidth;
    private double screenHeight;
    private double halfWidth;
    private double halfHeight;

    @Override
    public void renderOverlay() {
        renderViewEntity = MC.getRenderViewEntity();
        if (renderViewEntity == null) {
            return;
        }
        final ScaledResolution scaledResolution = new ScaledResolution(MC);
        screenWidth = scaledResolution.getScaledWidth_double();
        screenHeight = scaledResolution.getScaledHeight_double();
        halfWidth = screenWidth / 2.0d;
        halfHeight = screenHeight / 2.0d;
        for (final Entity entity : MC.world.loadedEntityList) {
            if (!(entity instanceof AbstractClientPlayer)) {
                continue;
            }
            final AbstractClientPlayer player = (AbstractClientPlayer) entity;
            if (player.equals(renderViewEntity)) {
                continue;
            }
            renderFrog(player);
        }
    }

    private void renderFrog(final AbstractClientPlayer player) {
        final Vec3d bottomVec = getInterpolatedPos(player, MC.getRenderPartialTicks());
        final Vec3d topVec = bottomVec.add(
                0.0,
                player.getRenderBoundingBox().maxY - player.posY,
                0.0
        );

        final ScreenPos top = toScreen(topVec.x, topVec.y, topVec.z);
        final ScreenPos bottom = toScreen(bottomVec.x, bottomVec.y, bottomVec.z);

        if (top.visible || bottom.visible) {

            final double doubleHeight = bottom.y - top.y;
            final int height = (int) doubleHeight;
            final double doubleWidth = doubleHeight * FrogEspAddon.frogRatio;
            final int width = (int) (doubleHeight * FrogEspAddon.frogRatio);

            int x = (int) (top.x - doubleWidth / 2.0);
            int y = (int) top.y;

            // draw frog
            MC.renderEngine.bindTexture(FrogEspAddon.frog);

            GlStateManager.color(255, 255, 255);
            Gui.drawScaledCustomSizeModalRect(
                    x,
                    y,
                    0,
                    0,
                    width,
                    height,
                    width,
                    height,
                    width,
                    height
            );

        }

    }

    private void vecTransformCoordinate(final Vector4f vec, final Matrix4f matrix) {
        final float x = vec.x;
        final float y = vec.y;
        final float z = vec.z;
        vec.x = (x * matrix.m00) + (y * matrix.m10) + (z * matrix.m20) + matrix.m30;
        vec.y = (x * matrix.m01) + (y * matrix.m11) + (z * matrix.m21) + matrix.m31;
        vec.z = (x * matrix.m02) + (y * matrix.m12) + (z * matrix.m22) + matrix.m32;
        vec.w = (x * matrix.m03) + (y * matrix.m13) + (z * matrix.m23) + matrix.m33;
    }

    private static Vec3d getInterpolatedPos(final Entity entity, final double partialTicks) {
        return new Vec3d(
                entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks,
                entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks,
                entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks
        );
    }

    private ScreenPos toScreen(final double x, final double y, final double z) {
        modelMatrix.load(modelView.asReadOnlyBuffer());
        projectionMatrix.load(projection.asReadOnlyBuffer());

        final float partialTicks = MC.getRenderPartialTicks();

        final Vec3d eyePos = ActiveRenderInfo.projectViewFromEntity(renderViewEntity, partialTicks);

        // this is such a stupid hack to get cam position
        // rootnet mixins when
        final double camX = eyePos.x - (renderViewEntity.prevPosX +
                (renderViewEntity.posX - renderViewEntity.prevPosX) * partialTicks);
        final double camY = eyePos.y - (renderViewEntity.prevPosY +
                (renderViewEntity.posY - renderViewEntity.prevPosY) * partialTicks);
        final double camZ = eyePos.z - (renderViewEntity.prevPosZ +
                (renderViewEntity.posZ - renderViewEntity.prevPosZ) * partialTicks);

        final Vector4f pos = new Vector4f(
                (float) ((camX + eyePos.x) - (float) x),
                (float) ((camY + eyePos.y) - (float) y),
                (float) ((camZ + eyePos.z) - (float) z),
                1.0f
        );

        vecTransformCoordinate(pos, modelMatrix);
        vecTransformCoordinate(pos, projectionMatrix);

        if (pos.w > 0.0f) {
            pos.x *= -100000;
            pos.y *= -100000;
        } else {
            final float invert = 1.0f / pos.w;
            pos.x *= invert;
            pos.y *= invert;
        }

        final double posX = halfWidth + (0.5 * pos.x * screenWidth + 0.5);
        final double posY = halfHeight - (0.5 * pos.y * screenHeight + 0.5);

        return new ScreenPos(
                posX,
                posY,
                posX >= 0
                        && posY >= 0
                        && posX <= screenWidth
                        && posY <= screenHeight
        );
    }

    private static class ScreenPos {

        private final double x;
        private final double y;

        private final boolean visible;

        public ScreenPos(final double x, final double y, final boolean visible) {
            this.x = x;
            this.y = y;
            this.visible = visible;
        }

    }

}
