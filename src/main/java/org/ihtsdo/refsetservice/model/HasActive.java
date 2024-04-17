
package org.ihtsdo.refsetservice.model;

/**
 * Generically represents something that is active or inactive.
 */
public interface HasActive {

  /**
   * Indicates whether or not active is the case.
   *
   * @return <code>true</code> if so, <code>false</code> otherwise
   */
  public boolean isActive();

  /**
   * Sets the active.
   *
   * @param active the active
   */
  public void setActive(final boolean active);
}
