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

    @SuppressWarnings("unused")
    @EventMethod
    public void onRenderLivingBase(final RenderLivingBaseEvent e) {
        final EntityLivingBase entityLivingBase = e.getEntityLivingBase();
        if(!(entityLivingBase instanceof AbstractClientPlayer)) { return; }
        final AbstractClientPlayer player = (AbstractClientPlayer)entityLivingBase;
        if(player.equals(MC.player)) { return; }
        e.setCancelled(true);
    }

    @Override
    public void renderOverlay() {
        for(final Entity entity : MC.world.loadedEntityList) {

            if(!(entity instanceof AbstractClientPlayer)) { continue; }

            final AbstractClientPlayer player = (AbstractClientPlayer)entity;

            if(player.equals(MC.player)) { continue; }

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

        final Plane top = toScreen(topVec.x, topVec.y, topVec.z);
        final Plane bottom = toScreen(bottomVec.x, bottomVec.y, bottomVec.z);

        if(top.visible || bottom.visible) {

            final double doubleHeight = bottom.y - top.y;
            final int height = (int)doubleHeight;
            final double doubleWidth = doubleHeight * FrogEspAddon.frogRatio;
            final int width = (int)(doubleHeight * FrogEspAddon.frogRatio);

            int x = (int)(top.x - doubleWidth / 2.0);
            int y = (int)top.y;

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

    private static Vec3d getInterpolatedPos(final Entity entity, final double partialTicks) {
        return new Vec3d(
                entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks,
                entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks,
                entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks
        );
    }

    private final FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);
    private final FloatBuffer modelView = GLAllocation.createDirectFloatBuffer(16);

    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();

    private void vecTransformCoordinate(final Vector4f vec, final Matrix4f matrix) {
        final float x = vec.x;
        final float y = vec.y;
        final float z = vec.z;
        vec.x = (x * matrix.m00) + (y * matrix.m10) + (z * matrix.m20) + matrix.m30;
        vec.y = (x * matrix.m01) + (y * matrix.m11) + (z * matrix.m21) + matrix.m31;
        vec.z = (x * matrix.m02) + (y * matrix.m12) + (z * matrix.m22) + matrix.m32;
        vec.w = (x * matrix.m03) + (y * matrix.m13) + (z * matrix.m23) + matrix.m33;
    }

    public void renderWorld() {
        glGetFloat(GL_PROJECTION_MATRIX, projection);
        glGetFloat(GL_MODELVIEW_MATRIX, modelView);
    }

    private Plane toScreen(final double x, final double y, final double z) {

        modelMatrix.load(modelView.asReadOnlyBuffer());
        projectionMatrix.load(projection.asReadOnlyBuffer());

        final Entity view = MC.getRenderViewEntity();

        if(view == null) { return new Plane(0.0, 0.0, false); }

        final Vec3d camPos = ActiveRenderInfo.getCameraPosition();
        final Vec3d eyePos = ActiveRenderInfo.projectViewFromEntity(view, MC.getRenderPartialTicks());

        final Vector4f pos = new Vector4f(
                (float)((camPos.x + eyePos.x) - (float)x),
                (float)((camPos.y + eyePos.y) - (float)y),
                (float)((camPos.z + eyePos.z) - (float)z),
                1.0f
        );

        vecTransformCoordinate(pos, modelMatrix);
        vecTransformCoordinate(pos, projectionMatrix);

        if(pos.w > 0.0f) {
            pos.x *= -100000;
            pos.y *= -100000;
        } else {
            final float invert = 1.0f / pos.w;
            pos.x *= invert;
            pos.y *= invert;
        }

        final ScaledResolution scaledResolution = new ScaledResolution(MC);
        final int screenWidth = scaledResolution.getScaledWidth();
        final int screenHeight = scaledResolution.getScaledHeight();

        float halfWidth = ((float)screenWidth) / 2.0f;
        float halfHeight = ((float)screenHeight) / 2.0f;

        final double posX = halfWidth + (0.5 * pos.x * (double)screenWidth + 0.5);
        final double posY = halfHeight - (0.5 * pos.y * (double)screenHeight + 0.5);

        return new Plane(
                posX,
                posY,
                !(
                        posX < 0
                        || posY < 0
                        || posX > screenWidth
                        || posY > screenHeight
                )
        );
    }

    private static class Plane {

        private final double x;
        private final double y;

        private final boolean visible;

        public Plane(final double x, final double y, final boolean visible) {
            this.x = x;
            this.y = y;
            this.visible = visible;
        }

    }

}
