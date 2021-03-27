package localhost.adifferentperson.frogesp;

import dev.rootnet.addons.api.addon.Addon;
import dev.rootnet.addons.api.annotations.RootnetAddon;
import org.apache.logging.log4j.Level;

import java.io.IOException;

/**
 * Frog ESP addon class
 */
@SuppressWarnings("unused")
@RootnetAddon(name = "FrogESP", author = "ADifferentPerson", version = "#VERSION#")
public final class FrogEspAddon extends Addon {

    static final String FROG_URL = "https://i.imgur.com/UI94ded.png";

    /**
     * Will be called by the addon loader
     */
    public FrogEspAddon() {
    }

    @Override
    public void init() {
        super.init();
        log(Level.INFO, "Initializing FrogESP Addon...");
        try {
            getRootnet().registerModule(this, new FrogEspModule());
        } catch (IOException ex) {
            log(Level.ERROR, "Failed to initialize FrogESP");
            ex.printStackTrace();
        }
    }

}