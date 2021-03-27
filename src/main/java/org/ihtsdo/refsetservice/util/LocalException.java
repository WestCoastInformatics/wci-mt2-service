
package org.ihtsdo.refsetservice.util;

/**
 * Represents a known exception with a user-friendly error message that is
 * handled differently by error handlers.
 */
public class LocalException extends Exception {

    /**
     * Instantiates a {@link LocalException} from the specified parameters.
     *
     * @param message the message
     * @param t the t
     */
    public LocalException(final String message, final Exception t) {
        super(message, t);
    }

    /**
     * Instantiates a {@link LocalException} from the specified parameters.
     *
     * @param message the message
     */
    public LocalException(final String message) {
        super(message);

    }
}
