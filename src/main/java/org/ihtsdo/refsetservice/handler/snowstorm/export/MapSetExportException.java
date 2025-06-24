/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm.export;

/**
 * Exception thrown when there is an error during map set export.
 */
public class MapSetExportException extends Exception {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new map set export exception.
     *
     * @param message the message
     */
    public MapSetExportException(String message) {

        super(message);
    }

    /**
     * Instantiates a new map set export exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public MapSetExportException(String message, Throwable cause) {

        super(message, cause);
    }
}