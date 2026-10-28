package egg.client.media;

import com.sun.jna.Native;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;

public final class OptMedia {

    private static boolean loaded = false;
    public static MediaControlLibrary INSTANCE;

    private OptMedia() {
    }

    public static synchronized boolean init() {
        if (loaded) {
            return INSTANCE != null;
        }
        try (InputStream in = OptMedia.class.getResourceAsStream("/assets/egg/natives/OptMedia.dll")) {
            if (in == null) {
                return false;
            }
            Path tempDll = Files.createTempFile("OptMedia", ".dll");
            tempDll.toFile().deleteOnExit();
            Files.copy(in, tempDll, StandardCopyOption.REPLACE_EXISTING);

            HashMap<String, Object> options = new HashMap<>();
            options.put("allow-objects", Boolean.TRUE);
            INSTANCE = (MediaControlLibrary) Native.load(tempDll.toAbsolutePath().toString(), MediaControlLibrary.class, options);
            loaded = true;
            return true;
        } catch (Throwable t) {
            loaded = true;
            return false;
        }
    }
}
