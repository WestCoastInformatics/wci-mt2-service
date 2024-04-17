
package org.ihtsdo.refsetservice.model;

/**
 * Used to represent objects that internally store their data as json.
 */
public interface HasJsonData extends HasModified {

  /**
   * Returns the data.
   *
   * @throws Exception the exception
   */
  public void marshall() throws Exception;

  /**
   * Sets the data.
   *
   * @throws Exception the exception
   */
  public void unmarshall() throws Exception;
}
