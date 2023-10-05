package org.ihtsdo.refsetservice.util;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigUtility {

    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(ConfigUtility.class);

    /** The config. */
    private static Properties config = null;

    /**
     * Instantiates an empty {@link ConfigUtility}.
     */
    private ConfigUtility() {

        // n/a
    }

    /**
     * Returns the config properties.
     * @return the config properties
     *
     * @throws Exception the exception
     */
    public static Properties getConfigProperties() throws Exception {

        if (isNull(config)) {

            final String label = getConfigLabel();

            // Now get the properties from the corresponding setting
            // This is a complicated mechanism to support multiple simultaneous
            // installations within the same container (e.g. tomcat).
            // Default setups do not require this.
            final String configFileName = System.getProperty("run.config" + label);
            String path = null;
            if (configFileName != null) {
                // logger.debug(" run.config" + label + " = " + configFileName);
                config = new Properties();
                try (final FileReader in = new FileReader(new File(configFileName))) {
                    config.load(in);
                    path = new File(configFileName).getParent();
                }
            } else if (new File(getLocalConfigFile()).exists()) {
                LOG.debug("Cannot find ENV 'run.config" + label + "', using " + getLocalConfigFile());
                config = new Properties();
                try (final FileReader in = new FileReader(new File(getLocalConfigFile()));) {
                    config.load(in);
                    path = new File(getLocalConfigFile()).getParent();
                }
            } else {
                try (final InputStream is = ConfigUtility.class.getResourceAsStream("/config.properties")) {
                    LOG.debug("Cannot find ENV 'run.config" + label + "', looking for config.properties in the classpath");
                    if (is != null) {
                        config = new Properties();
                        config.load(is);
                    }
                }
            }

            // Interpolate "env" variables
            if (config != null) {
                final Map<String, String> envMap = System.getenv();
                for (final Object property : config.keySet()) {
                    String value = config.getProperty(property.toString());
                    while (value.matches(".*\\$\\{env:.*}.*")) {
                        final String envProp = value.replaceFirst(".*\\$\\{env:(.*)}.*", "$1");
                        // Find system property - use blank value if cannot be found
                        final String env = envMap.get(envProp) == null ? "" : envMap.get(envProp);
                        value = value.replaceFirst("(.*)(\\$\\{env:.*})(.*)", "$1" + env + "$3");
                        LOG.debug(
                            "    Interpolated " + envProp + " as " + ((envProp.toLowerCase().contains("password") || envProp.toLowerCase().contains("_key"))
                                ? "*******" : (ConfigUtility.isEmpty(env) ? "<blank>" : env)));
                    }
                    config.setProperty(property.toString(), value);
                }
            } else {
                // If nothing else, return an empty properties file
                config = new Properties();
            }
            // This can reveal passwords and injected properties
            // logger.debug(" properties = " + config);

            // Handle import
            handleConfigImport(config, path);

        }

        return config;
    }

    /**
     * Indicates whether or not a string is empty.
     *
     * @param str the str
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isEmpty(final String str) {

        return str == null || str.isEmpty();
    }

    /**
     * Indicates whether or not empty is the case.
     *
     * @param collection the collection
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isEmpty(final Collection<?> collection) {

        return collection == null || collection.isEmpty();
    }

    /**
     * This method is intended to bypass some incorrect static code analysis from the FindBugs Eclipse plugin.
     *
     * @param o the o
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isNull(final Object o) {

        return o == null;
    }

    /**
     * Handle config import.
     *
     * @param config the config
     * @param path the path
     * @throws Exception the exception
     */
    private static void handleConfigImport(final Properties config, final String path) throws Exception {

        // Check if config has "config.import"
        if (config.containsKey("config.import")) {

            int size = config.size();

            // Assume config-import.properties if not specified
            final String name =
                config.getProperty("config.import").isEmpty() ? "config-import.properties" : "config-" + config.getProperty("config.import") + ".properties";
            LOG.debug("    import config = " + name);
            final Properties config2 = new Properties();
            // Try loading as a file
            if (new File(ConfigUtility.isEmpty(path) ? "./" : path, name).exists()) {
                try (final FileReader in = new FileReader(new File(ConfigUtility.isEmpty(path) ? "./" : path, name))) {
                    config2.load(in);
                    config.putAll(config2);
                    LOG.debug("    found import config file = " + config2.size());
                }
            }

            // Try loading as a resource
            else {
                try (final InputStream is = ConfigUtility.class.getResourceAsStream("/" + name)) {
                    if (is != null) {
                        config2.load(is);
                        config.putAll(config2);
                        LOG.debug("    found import config resource = " + config2.size());
                    }
                }
            }

            // IF import is specified and not found or no data loaded, fail
            if (size == config.size() && !config.getProperty("config.import").isEmpty()) {
                throw new Exception("Unable to find import config = " + name);
            }
        }
    }

    /**
     * Get the config label.
     *
     * @return the label
     * @throws Exception the exception
     */
    public static String getConfigLabel() throws Exception {

        // Need to determine the label (default "demo")
        String label = "";
        final Properties labelProp = new Properties();

        // If no resource is available, go with the default
        // ONLY setups that explicitly intend to override the setting
        // cause it to be something other than the default.
        try (final InputStream input = ConfigUtility.class.getResourceAsStream("/label.prop");) {
            if (input != null) {
                labelProp.load(input);
                // If a run.config.label override can be found, use it
                final String candidateLabel = labelProp.getProperty("run.config.label");
                // If the default, uninterpolated value is used, stick again with the
                // default
                if (candidateLabel != null && !candidateLabel.equals("${run.config.label}")) {
                    label = candidateLabel;
                }
                // } else {
                // logger.debug(" label.prop resource cannot be found, using default");
            }
            // logger.debug(" run.config.label = " + label);

            return (ConfigUtility.isEmpty(label)) ? "" : "." + label;
        }
    }

    /**
     * The get local config file.
     *
     * @return the local config file
     * @throws Exception the exception
     */
    public static String getLocalConfigFile() throws Exception {

        return getLocalConfigFolder() + "config.properties";
    }

    /**
     * Gets the local config folder.
     *
     * @return the local config folder
     * @throws Exception the exception
     */
    public static String getLocalConfigFolder() throws Exception {

        // Instead of "user.home" let's use "src/resources"
        // return System.getProperty("user.home") + "/.term-server/" +
        // getConfigLabel() + "/";
        return "src/resources/";
    }

    /**
     * Returns the header token.
     *
     * @return the header token
     * @throws Exception the exception
     */
    public static String getHeaderToken() throws Exception {

        config = new Properties();
        return config.getProperty("jwt.header") != null ? config.getProperty("jwt.header") : "Authorization";
    }
}
