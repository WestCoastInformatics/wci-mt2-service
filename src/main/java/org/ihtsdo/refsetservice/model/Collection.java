
package org.ihtsdo.refsetservice.model;

import java.util.List;

/**
 * Generically represents a collection result.
 *
 * @param <T> the
 */
public interface Collection<T> {

  /**
   * Returns the items.
   *
   * @return the items
   */
  public List<T> getItems();

  /**
   * Sets the items.
   *
   * @param items the items
   */
  public void setItems(List<T> items);

  /**
   * Returns the total.
   *
   * @return the total
   */
  public int getTotal();

  /**
   * Sets the total.
   *
   * @param total the total
   */
  public void setTotal(int total);

  /**
   * Returns the limit.
   *
   * @return the limit
   */
  public int getLimit();

  /**
   * Sets the limit.
   *
   * @param limit the limit
   */
  public void setLimit(int limit);

  /**
   * Returns the offset.
   *
   * @return the offset
   */
  public int getOffset();

  /**
   * Sets the offset.
   *
   * @param offset the offset
   */
  public void setOffset(int offset);

}
