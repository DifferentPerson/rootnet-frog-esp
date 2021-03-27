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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.util.vector.Matrix4f;

import javax.imageio.ImageIO;
import javax.vecmath.Vector4d;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Main Frog ESP module class
 */
@RootnetModule(name = "FrogESP")
public final class FrogEspModule extends AddonModule {

    private static final Minecraft MC = Minecraft.getMinecraft();

    private final FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);
    private final FloatBuffer modelView = GLAllocation.createDirectFloatBuffer(16);
    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();

    private final ResourceLocation frog;
    private final double frogRatio;

    /**
     * Creates the module, downloading the frog image
     * @throws IOException If the file image could not be downloaded
     */
    FrogEspModule() throws IOException {
        final BufferedImage image = ImageIO.read(new URL(FrogEspAddon.FROG_URL));
        frogRatio = ((double) image.getWidth()) / ((double) image.getHeight());
        final DynamicTexture dynamicTexture = new DynamicTexture(image);
        dynamicTexture.loadTexture(MC.getResourceManager());
        frog = MC.getTextureManager().getDynamicTextureLocation("FROG", dynamicTexture);
    }

    /**
     * Disables drawing other players; do draw self, unless in freecam
     */
    @SuppressWarnings("unused")
    @EventMethod
    public void onRenderLivingBase(final RenderLivingBaseEvent event) {
        final EntityLivingBase entity = event.getEntityLivingBase();
        if (entity instanceof AbstractClientPlayer && !entity.equals(MC.getRenderViewEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Save the modelView and projection matrices into {@link #modelMatrix} and {@link #projectionMatrix}, allowing
     * us to use them to find where we should draw the image
     */
    @Override
    public void renderWorld() {
        glGetFloat(GL_PROJECTION_MATRIX, projection);
        glGetFloat(GL_MODELVIEW_MATRIX, modelView);
        modelMatrix.load(modelView.asReadOnlyBuffer());
        projectionMatrix.load(projection.asReadOnlyBuffer());
    }

    /**
     * Renders the frogs over players
     */
    @Override
    public void renderOverlay() {
        final Entity renderViewEntity = MC.getRenderViewEntity();
        if (renderViewEntity == null) {
            return;
        }

        final ScreenSize screenSize;
        {
            final ScaledResolution scaledResolution = new ScaledResolution(MC);
            final double screenWidth = scaledResolution.getScaledWidth_double();
            final double screenHeight = scaledResolution.getScaledHeight_double();
            screenSize = new ScreenSize(screenWidth, screenHeight);
        }

        final float partialTicks = MC.getRenderPartialTicks();

        final Vec3d cameraPos;
        {
            final Vec3d eyePos = ActiveRenderInfo.projectViewFromEntity(renderViewEntity, partialTicks);

            cameraPos = new Vec3d(
                    eyePos.x * 2.0 -
                            (renderViewEntity.prevPosX
                                    + (renderViewEntity.posX - renderViewEntity.prevPosX) * partialTicks),
                    eyePos.y * 2.0 -
                            (renderViewEntity.prevPosY
                                    + (renderViewEntity.posY - renderViewEntity.prevPosY) * partialTicks),
                    eyePos.z * 2.0 -
                            (renderViewEntity.prevPosZ
                                    + (renderViewEntity.posZ - renderViewEntity.prevPosZ) * partialTicks)
            );
        }

        for (final Entity entity : MC.world.loadedEntityList) {
            if (entity instanceof AbstractClientPlayer && !entity.equals(renderViewEntity)) {
                renderFrog((AbstractClientPlayer) entity, cameraPos, screenSize, partialTicks);
            }
        }
    }

    private void renderFrog(final AbstractClientPlayer player, final Vec3d cameraPos, final ScreenSize screenSize,
                            final float partialTicks) {
        final Vec3d bottomVec = getRenderPos(player, partialTicks);
        final Vec3d topVec = bottomVec.add(
                0.0,
                player.getRenderBoundingBox().maxY - player.posY,
                0.0
        );

        final ScreenPos top = project(cameraPos, screenSize, topVec);
        final ScreenPos bottom = project(cameraPos, screenSize, bottomVec);


        if (!top.visible && !bottom.visible) {
            return;
        }

        final double doubleHeight = bottom.y - top.y;
        final int height = (int) doubleHeight;
        final double doubleWidth = doubleHeight * frogRatio;
        final int width = (int) (doubleHeight * frogRatio);

        int x = (int) (top.x - doubleWidth / 2.0);
        int y = (int) top.y;

        // draw frog
        MC.getTextureManager().bindTexture(frog);

        GlStateManager.color(255, 255, 255);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, width, height, width, height, width, height);

    }

    private ScreenPos project(final Vec3d cameraPos, final ScreenSize screenSize, final Vec3d position) {
        final Vector4d pos = new Vector4d(
                cameraPos.x - position.x,
                cameraPos.y - position.y,
                cameraPos.z - position.z,
                1.0f
        );

        transformVecByMatrix(pos, modelMatrix);
        transformVecByMatrix(pos, projectionMatrix);

        double posX;
        double posY;

        if (pos.w >= 0.0) {
            posX = pos.x * -100000;
            posY = pos.y * -100000;
        } else {
            posX = pos.x / pos.w;
            posY = pos.y / pos.w;
        }

        posX = (1 + posX) * (screenSize.width / 2.0);
        posY = (1 - posY) * (screenSize.height / 2.0);
        final boolean visible = posX >= 0.0 && posY >= 0.0 && posX <= screenSize.width && posY <= screenSize.height;

        return new ScreenPos(posX, posY, visible);
    }

    private static void transformVecByMatrix(final Vector4d vec, final Matrix4f matrix) {
        final double x = vec.x;
        final double y = vec.y;
        final double z = vec.z;
        vec.x = (x * matrix.m00) + (y * matrix.m10) + (z * matrix.m20) + matrix.m30;
        vec.y = (x * matrix.m01) + (y * matrix.m11) + (z * matrix.m21) + matrix.m31;
        vec.z = (x * matrix.m02) + (y * matrix.m12) + (z * matrix.m22) + matrix.m32;
        vec.w = (x * matrix.m03) + (y * matrix.m13) + (z * matrix.m23) + matrix.m33;
    }

    private static Vec3d getRenderPos(final Entity entity, final double partialTicks) {
        return new Vec3d(
                entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks,
                entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks,
                entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks
        );
    }

    /**
     * A pair of {@link #width} and {@link #height}
     */
    private static final class ScreenSize {

        private final double width;
        private final double height;

        private ScreenSize(double width, double height) {
            this.width = width;
            this.height = height;
        }

    }

    /**
     * Data structure consisting of an {@link #x}, {@link #y} on the screen, and whether or not the
     * position is {@link #visible}
     */
    private static final class ScreenPos {

        private final double x;
        private final double y;
        private final boolean visible;

        private ScreenPos(final double x, final double y, final boolean visible) {
            this.x = x;
            this.y = y;
            this.visible = visible;
        }

    }

}
