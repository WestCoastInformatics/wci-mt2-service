package org.ihtsdo.refsetservice.model;

/**
 * Generically represents something that can self-validate crud operations.
 *
 * @param <T> the
 */
public interface ValidateCrud<T> {

    /**
     * Validate add.
     *
     * @param context the context
     * @throws Exception the exception indicating validation failure
     */
    public void validateAdd(AuthContext context) throws Exception;

    /**
     * Validate update.
     *
     * @param context the context
     * @param other the other
     * @throws Exception the exception indicating validation failure
     */
    public void validateUpdate(AuthContext context, T other) throws Exception;

    /**
     * Validate delete.
     *
     * @param context the context
     * @throws Exception the exception indicating validation failure
     */
    public void validateDelete(AuthContext context) throws Exception;

}
