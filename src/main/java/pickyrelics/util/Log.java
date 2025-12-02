package pickyrelics.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logging utility that prefixes all messages with the mod name.
 */
public class Log {
    private static final String PREFIX = "[Picky Relics] ";
    private static final Logger logger = LogManager.getLogger("PickyRelics");

    public static void info(String message) {
        logger.info(PREFIX + message);
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
