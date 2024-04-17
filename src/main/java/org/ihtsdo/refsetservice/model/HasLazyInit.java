
package org.ihtsdo.refsetservice.model;

/**
 * Generically represents something that can be lazy initialized.
 */
public interface HasLazyInit {

  /**
   * Lazy init.
   */
  public void lazyInit();

}
