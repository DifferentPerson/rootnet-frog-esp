package localhost.adifferentperson.frogesp;

import dev.rootnet.addons.api.addon.Addon;
import dev.rootnet.addons.api.annotations.RootnetAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Level;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

@SuppressWarnings("unused")
@RootnetAddon(name = "FrogESP", author = "ADifferentPerson", version = "1.1.0")
public final class FrogEspAddon extends Addon {
    private static final String FROG_URL = "https://i.imgur.com/UI94ded.png";
    private static final Minecraft MC = Minecraft.getMinecraft();
    static ResourceLocation frog;
    static double frogRatio;
    static FrogEspAddon INSTANCE;

    @Override
    public final void init() {
        INSTANCE = this;
        log(Level.INFO, "Initializing FrogESP Addon...");
        try {
            final BufferedImage image = ImageIO.read(new URL(FROG_URL));
            frogRatio = ((double) image.getWidth()) / ((double) image.getHeight());
            final DynamicTexture dynamicTexture = new DynamicTexture(image);
            dynamicTexture.loadTexture(MC.getResourceManager());
            frog = MC.getTextureManager().getDynamicTextureLocation("FROG", dynamicTexture);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        getRootnet().registerModule(this, new FrogEspModule());
    }
}