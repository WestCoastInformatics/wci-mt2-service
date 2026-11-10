package org.ihtsdo.refsetservice.model.enums;

import java.util.Arrays;
import java.util.List;

/**
 * The Enum RefsetType.
 */
public enum RefsetType {

    /** The intensional. */
    INTENSIONAL("Intensional"),

    /** The extensional. */
    EXTENSIONAL("Extensional"),

    /** The external. */
    EXTERNAL("External");

    /** The label. */
    private final String label;

    /**
     * Instantiates a {@link RefsetType} from the specified parameters.
     *
     * @param label the label
     */
    private RefsetType(final String label) {

        this.label = label;
    }

    /**
     * Returns the label.
     *
     * @return the label
     */
    public String getLabel() {

        return label;
    }

    /**
     * From string.
     *
     * @param text the text
     * @return the refset type
     */
    // convert string to enum
    public static RefsetType fromString(final String text) {

        for (final RefsetType wfAction : RefsetType.values()) {
            if (wfAction.toString().equalsIgnoreCase(text)) {
                return wfAction;
            }
        }
        return null;
    }

    /**
     * Returns the values.
     *
     * @return the values
     */
    public static List<RefsetType> getValues() {

        return Arrays.asList(RefsetType.values());
    }
}
