package org.ihtsdo.refsetservice.model;

import org.ihtsdo.refsetservice.model.Error;

/**
 * Wrapper REST exception so we can properly format all exception responses to
 * REST calls.
 */
public class RestException extends RuntimeException {

    /** The error. */
    private Error error;

    /**
     * Instantiates an empty {@link RestException}.
     */
    public RestException() {
        // n/a
    }

    /**
     * Instantiates a {@link RestException} from the specified parameters.
     *
     * @param local the local
     * @param status the status
     * @param error the error
     * @param message the message
     */
    public RestException(final boolean local, final int status, final String error,
            final String message) {
        this.error = new Error(local, status, error, message);
    }

    /**
     * Instantiates a {@link RestException} from the specified parameters.
     *
     * @param error the error
     */
    public RestException(final Error error) {
        this.error = error;
    }

    /**
     * Returns the error.
     *
     * @return the error
     */
    public Error getError() {
        return error;
    }

    /**
     * Sets the error.
     *
     * @param error the error
     */
    public void setError(final Error error) {
        this.error = error;
    }

    /* see superclass */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((error == null) ? 0 : error.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RestException other = (RestException) obj;
        if (error == null) {
            if (other.error != null) {
                return false;
            }
        } else if (!error.equals(other.error)) {
            return false;
        }
        return true;
    }

}
