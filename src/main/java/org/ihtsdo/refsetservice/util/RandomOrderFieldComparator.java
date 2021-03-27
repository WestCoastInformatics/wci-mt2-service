
package org.ihtsdo.refsetservice.util;

import java.util.Date;
import java.util.Random;

import org.apache.lucene.search.FieldComparator;

/**
 * Comparator for random sort order.
 */
public class RandomOrderFieldComparator extends FieldComparator.LongComparator {

    /** The random number generator. */
    private final Random random = new Random(new Date().getTime());

    /**
     * Instantiates a {@link RandomOrderFieldComparator} from the specified
     * parameters.
     *
     * @param numHits the num hits
     * @param field the field
     * @param missingValue the missing value
     */
    public RandomOrderFieldComparator(final int numHits, final String field,
            final Long missingValue) {
        super(numHits, field, missingValue);
    }

    /* see superclass */
    @Override
    public Long value(final int slot) {
        return random.nextLong();
    }

    /* see superclass */
    @Override
    public int compare(final int slot1, final int slot2) {
        return random.nextInt();
    }

    /* see superclass */
    @Override
    public int compareBottom(final int doc) {
        return random.nextInt();
    }
}
