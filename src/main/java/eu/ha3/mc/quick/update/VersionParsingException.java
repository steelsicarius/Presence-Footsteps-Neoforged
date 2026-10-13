package eu.ha3.mc.quick.update;

/**
 * Thrown when a version string cannot be parsed.
 */
public class VersionParsingException extends Exception {
    public VersionParsingException(String message) {
        super(message);
    }

    public VersionParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
