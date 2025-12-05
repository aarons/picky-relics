package pickyrelics.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logging utility that prefixes all messages with the mod name.
 */
public class Log {
    private static final String PREFIX = "[Picky Relics] ";
    private static final Logger logger = LogManager.getLogger(Log.class);

    /**
     * Determines whether info messages are logged.
     * Defaults to false for release builds, can be enabled by setting system property "pickyrelics.debug" to "true".
     */
    private static final boolean DEBUG_MODE = Boolean.parseBoolean(System.getProperty("pickyrelics.debug", "false"));

    /**
     * Logs an info message if debug mode is enabled.
     * In release builds, info messages are suppressed by default to avoid console spam.
     * To enable info logging, start the game with JVM argument: -Dpickyrelics.debug=true
     */
    public static void info(String message) {
        if (DEBUG_MODE) {
            logger.info(PREFIX + message);
        }
    }

    public static void debug(String message) {
        logger.debug(PREFIX + message);
    }

    public static void warn(String message) {
        logger.warn(PREFIX + message);
    }

    public static void error(String message) {
        logger.error(PREFIX + message);
    }

    public static void error(String message, Throwable t) {
        logger.error(PREFIX + message, t);
    }
}
