package org.ihtsdo.refsetservice.model;

import java.util.Arrays;
import java.util.List;

/**
 * Workflow phase for a single source-concept mapping.
 *
 * <p>Phase only — who owns the mapping is stored in {@code assigned_user}, not in the status name.
 * Publication is handled at mapset level, not per mapping.
 *
 * <p>Workflow status names may not be initial substrings of any other workflow status.
 *
 * <p>Constants must appear in ascending workflow order (e.g. {@code NEW} before
 * {@code EDITING_IN_PROGRESS}). {@code READY_FOR_PUBLICATION} and {@code REVISION} are terminal phases.
 */
public enum MapWorkflowStatus {

  /** New, unedited specialist record */
  NEW,

  /** Editing in progress by a specialist */
  EDITING_IN_PROGRESS,

  /** Editing completed by a specialist */
  EDITING_DONE,

  /** Conflict has been detected. */
  CONFLICT_DETECTED,

  /** Conflict resolution by a lead is in progress */
  CONFLICT_IN_PROGRESS,

  /**
   * Conflict resolution by a lead is resolved, but not released.
   */
  CONFLICT_RESOLVED,

  /**
   * Conflict finished. Ready for review by another lead.
   */
  CONFLICT_FINISHED,

  /** Pre-publication state for review by lead */
  REVIEW_NEEDED,

  /** Review in progress */
  REVIEW_IN_PROGRESS,

  /**
   * Review resolved, but not released.
   */
  REVIEW_RESOLVED,

  /**
   * Review finished. Ready for review by another lead.
   */
  REVIEW_FINISHED,

  /** Pre-publication state for qa */
  QA_NEEDED,

  /** QA in progress */
  QA_IN_PROGRESS,

  /** QA resolved, but not released */
  QA_RESOLVED,

  /** The consensus needed. */
  CONSENSUS_NEEDED,

  /** The consensus begun, with no editing */
  CONSENSUS_NEW,

  /** The consensus in progress. */
  CONSENSUS_IN_PROGRESS,

  /** Ready for publication this cycle. */
  READY_FOR_PUBLICATION,

  /** Sent back from publication QA; re-enters edit or review */
  REVISION;

  /**
   * From string.
   *
   * @param text the text
   * @return the map workflow status
   */
  public static MapWorkflowStatus fromString(final String text) {

    for (final MapWorkflowStatus status : MapWorkflowStatus.values()) {
      if (status.toString().equalsIgnoreCase(text)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown MapWorkflowStatus: " + text);
  }

  /**
   * Returns the values.
   *
   * @return the values
   */
  public static List<MapWorkflowStatus> getValues() {

    return Arrays.asList(MapWorkflowStatus.values());
  }
}
