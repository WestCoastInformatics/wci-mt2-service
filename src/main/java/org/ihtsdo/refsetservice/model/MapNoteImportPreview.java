package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Preview / validation result for a map note import file. No notes are created when {@link #valid} is false.
 */
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapNoteImportPreview {

    /** True when the file can be imported as-is. */
    private boolean valid;

    /** Number of data rows parsed (excludes header/blank lines). */
    private int totalRows;

    /** User names in the file that do not match an existing {@link MapUser}. */
    private List<String> unknownUsers = new ArrayList<>();

    /** Row-level validation errors (format, missing fields, bad dates, etc.). */
    private List<String> errors = new ArrayList<>();

    /**
     * Returns whether the import is valid.
     *
     * @return true if valid
     */
    public boolean isValid() {

        return valid;
    }

    /**
     * Sets whether the import is valid.
     *
     * @param valid the valid flag
     */
    public void setValid(final boolean valid) {

        this.valid = valid;
    }

    /**
     * Returns the total data row count.
     *
     * @return the total rows
     */
    public int getTotalRows() {

        return totalRows;
    }

    /**
     * Sets the total data row count.
     *
     * @param totalRows the total rows
     */
    public void setTotalRows(final int totalRows) {

        this.totalRows = totalRows;
    }

    /**
     * Returns unknown user names.
     *
     * @return the unknown users
     */
    public List<String> getUnknownUsers() {

        return unknownUsers;
    }

    /**
     * Sets unknown user names.
     *
     * @param unknownUsers the unknown users
     */
    public void setUnknownUsers(final List<String> unknownUsers) {

        this.unknownUsers = unknownUsers == null ? new ArrayList<>() : unknownUsers;
    }

    /**
     * Returns validation errors.
     *
     * @return the errors
     */
    public List<String> getErrors() {

        return errors;
    }

    /**
     * Sets validation errors.
     *
     * @param errors the errors
     */
    public void setErrors(final List<String> errors) {

        this.errors = errors == null ? new ArrayList<>() : errors;
    }

}
