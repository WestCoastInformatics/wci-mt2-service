/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
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
     * @throws Exception the exception indicating validation failure
     */
    public void validateAdd() throws Exception;

    /**
     * Validate update.
     *
     * @param other the other
     * @throws Exception the exception indicating validation failure
     */
    public void validateUpdate(T other) throws Exception;

    /**
     * Validate delete.
     *
     * @throws Exception the exception indicating validation failure
     */
    public void validateDelete() throws Exception;

}
