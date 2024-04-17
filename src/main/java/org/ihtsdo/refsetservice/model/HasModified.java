
package org.ihtsdo.refsetservice.model;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Generically represents something that can be tracked as it changes.
 */
public interface HasModified extends HasId, HasActive, HasLazyInit {

  /**
   * Returns the modified.
   *
   * @return the modified
   */
  @Schema(description = "last modified date")
  public Date getModified();

  /**
   * Sets the modified.
   *
   * @param modified the modified
   */
  public void setModified(final Date modified);

  /**
   * Returns the created.
   *
   * @return the created
   */
  @Schema(description = "created date")
  public Date getCreated();

  /**
   * Sets the created.
   *
   * @param created the created
   */
  public void setCreated(final Date created);

  /**
   * Returns the modified by.
   *
   * @return the modified by
   */
  @Schema(description = "last modified by")
  public String getModifiedBy();

  /**
   * Sets the modified by.
   *
   * @param modifiedBy the modified by
   */
  public void setModifiedBy(final String modifiedBy);

  /**
   * Clear tracking fields.
   */
  public void clearTrackingFields();

}
