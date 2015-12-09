package nl.opengeogroep.safetymaps.onboard;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Matthijs Laan
 */
public class Version {
    private static final Properties properties = new Properties();

    static {
        InputStream gitPropsIn = null, versionPropsIn = null;
        try {
            gitPropsIn = Version.class.getResourceAsStream("/git.properties");
            versionPropsIn = Version.class.getResourceAsStream("/version.properties");
            properties.load(gitPropsIn);
            properties.load(versionPropsIn);
        } catch(IOException e) {
        } finally {
            IOUtils.closeQuietly(gitPropsIn);
            IOUtils.closeQuietly(versionPropsIn);
        }
    }

    public static Properties getProperties() {
        return properties;
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static String getProjectVersion() {
        return getProperty("project.version");
    }
}