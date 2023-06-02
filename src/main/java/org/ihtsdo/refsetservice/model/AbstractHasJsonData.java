
package org.ihtsdo.refsetservice.model;

import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

import org.hibernate.annotations.Type;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Abstractly represents something that persists itself with json data payload. This class makes use of hibernate lifecycle callbacks as explained here:
 * https://www.logicbig.com/tutorials/java-ee-tutorial/jpa/entity-call-back.html .
 */
@MappedSuperclass
@org.hibernate.annotations.TypeDef(name = "JsonBType", typeClass = JsonBType.class)
public abstract class AbstractHasJsonData extends AbstractHasModified implements HasJsonData {

    /** The data. */
    @Type(type = "JsonBType")
    private String data;

    /**
     * Instantiates an empty {@link AbstractHasJsonData}.
     */
    protected AbstractHasJsonData() {

        // n/a
    }

    /**
     * Instantiates a {@link AbstractHasJsonData} from the specified parameters.
     *
     * @param other the other
     */
    protected AbstractHasJsonData(final HasId other) {

        populateFrom(other);
    }

    /* see superclass */
    @PrePersist
    @PreUpdate
    @Override
    public void marshall() throws Exception {

        data = ModelUtility.toJson(this);
    }

    /**
     * Returns the data.
     *
     * @return the data
     */
    @JsonIgnore
    public String getData() {

        return data;
    }

    // Left out so that copy constructor doesn't pay attention to this
    // public void setData(final String data) {
    // this.data = data;
    // }

    // USE this method for all sub-classes
    // /* see superclass */
    // @Override
    // @PostLoad
    // public void unmarshall() throws Exception {
    // final Resource resource = ConfigUtility.getGraphForJson(getData(),
    // this.getClass());
    // if (resource != null) {
    // populateFrom(resource);
    // }
    // }

}
