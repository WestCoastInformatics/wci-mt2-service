package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Result of a map note import attempt.
 */
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapNoteImportResult {

    /** True when notes were created. */
    private boolean success;

    /** Validation preview (always present). */
    private MapNoteImportPreview preview;

    /** Created notes when {@link #success} is true. */
    private List<MapNote> notes = new ArrayList<>();

    /**
     * Instantiates an empty {@link MapNoteImportResult}.
     */
    public MapNoteImportResult() {

        // n/a
    }

    /**
     * Successful import result.
     *
     * @param notes the created notes
     * @param preview the preview
     * @return the result
     */
    public static MapNoteImportResult success(final List<MapNote> notes, final MapNoteImportPreview preview) {

        final MapNoteImportResult result = new MapNoteImportResult();
        result.success = true;
        result.preview = preview;
        result.notes = notes == null ? new ArrayList<>() : notes;
        return result;
    }

    /**
     * Failed import result (nothing persisted).
     *
     * @param preview the preview
     * @return the result
     */
    public static MapNoteImportResult failure(final MapNoteImportPreview preview) {

        final MapNoteImportResult result = new MapNoteImportResult();
        result.success = false;
        result.preview = preview;
        return result;
    }

    /**
     * Returns whether import succeeded.
     *
     * @return true if success
     */
    public boolean isSuccess() {

        return success;
    }

    /**
     * Sets whether import succeeded.
     *
     * @param success the success flag
     */
    public void setSuccess(final boolean success) {

        this.success = success;
    }

    /**
     * Returns the preview.
     *
     * @return the preview
     */
    public MapNoteImportPreview getPreview() {

        return preview;
    }

    /**
     * Sets the preview.
     *
     * @param preview the preview
     */
    public void setPreview(final MapNoteImportPreview preview) {

        this.preview = preview;
    }

    /**
     * Returns created notes.
     *
     * @return the notes
     */
    public List<MapNote> getNotes() {

        return notes;
    }

    /**
     * Sets created notes.
     *
     * @param notes the notes
     */
    public void setNotes(final List<MapNote> notes) {

        this.notes = notes == null ? new ArrayList<>() : notes;
    }

}
