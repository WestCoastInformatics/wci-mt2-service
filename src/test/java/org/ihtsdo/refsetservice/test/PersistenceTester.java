
package org.ihtsdo.refsetservice.test;

import java.lang.reflect.Field;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.model.HasId;
import org.ihtsdo.refsetservice.model.HasJsonData;
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automates JUnit testing of Hibernate persistence.
 * 
 * <p>
 * It may be used in exclusive or inclusive mode. In exclusive mode, which is the default, all JavaBeans properties (getter/setter method pairs with matching
 * names) are tested unless they are excluded beforehand. For example:
 * 
 * <pre>
 * MyClass objectToTest = new MyClass();
 * PersistenceTester gst = new PersistenceTester(objectToTest);
 * gst.exclude(&quot;complexProperty&quot;);
 * gst.exclude(&quot;anotherProperty&quot;);
 * gst.test();
 * </pre>
 * 
 * The following property types are supported:
 * 
 * <ul>
 * <li>All Java primitive types.
 * <li>Interfaces.
 * <li>All non-final classes if <a href="http://cglib.sourceforge.net">cglib</a> is on your classpath -- this uses cglib even when a no-argument constructor is
 * available because a constructor might have side effects that you wouldn.t want to trigger in a unit test.
 * <li>Java 5 enums.
 * </ul>
 * 
 * <p>
 * Properties whose types are classes declared <code>final</code> are not supported; neither are non-primitive, non-interface properties if you don't have
 * cglib.
 * 
 * <p>
 * Copyright (c) 2005, Steven Grimm.<br>
 * This software may be used for any purpose, commercial or noncommercial, so long as this copyright notice is retained. If you make improvements to the code,
 * you're encouraged (but not required) to send them to me so I can make them available to others. For updates, please check
 * <a href="http://www.plaintivemewling.com/?p=34">here</a>.
 * 
 * @author Steven Grimm, koreth@midwinter.com
 * @version 1.0 (2005/11/08).
 */
public class PersistenceTester extends ProxyTester {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(PersistenceTester.class);

    /** HasId under test. */
    @SuppressWarnings("unused")
    private HasId obj;

    /** If true, output trace information. */
    @SuppressWarnings("unused")
    private boolean verbose = false;

    /** The indexed flag. */
    private boolean indexedFlag = true;

    /** The generated id flag. */
    private boolean generatedIdFlag = true;

    /**
     * Constructs a new getter/setter tester to test objects of a particular class.
     * 
     * @param obj Object to test.
     */
    public PersistenceTester(final HasId obj) {

        super(obj);
        this.obj = obj;
    }

    /**
     * Instantiates a {@link PersistenceTester} from the specified parameters.
     *
     * @param obj the obj
     * @param indexedFlag the indexed flag
     * @param generatedIdFlag the generated id flag
     */
    public PersistenceTester(final HasId obj, final boolean indexedFlag, final boolean generatedIdFlag) {

        super(obj);
        this.obj = obj;
        this.indexedFlag = indexedFlag;
        this.generatedIdFlag = generatedIdFlag;
    }

    /**
     * Sets the verbosity flag.
     * @param verbose the verbose flag
     * @return this
     */
    public PersistenceTester setVerbose(final boolean verbose) {

        this.verbose = verbose;
        return this;
    }

    /**
     * Walks through the methods in the class looking for getters and setters that are on our include list (if any) and are not on our exclude list.
     *
     * @throws Exception the exception
     */
    public void test() throws Exception {

        HasId object = (HasId) createObject(1);
        // We could check first if there's a generator on the object Id
        if (generatedIdFlag) {
            object.setId(null);
        }

        // Verify @Entity annotation
        if (!object.getClass().isAnnotationPresent(Entity.class)) {
            LOG.error("  MISSING @Entity");
            throw new Exception("  MISSING @Entity");
        }

        // Verify @Table annotation (with un-camel-cased name
        if (!object.getClass().isAnnotationPresent(Table.class)) {
            LOG.error("  MISSING @Table");
            throw new Exception("  MISSING @Table");
        }
        // final String tableClassName =
        // ConfigUtility.unCamelCase(object.getClass().getSimpleName().replace("Jpa",
        // ""))
        // .replaceAll(" ", "_").toLowerCase();
        // final String tableName =
        // object.getClass().getAnnotation(Table.class).name();
        // if (!tableName.equals(tableClassName)) {
        // // exceptions for sql keywords
        // if (!tableName.equals("orders")) {
        // LOG.error(" @Table annotation name does not match class = " +
        // tableClassName);
        // throw new Exception(" @Table annotation name does not match class = "
        // +
        // tableClassName);
        // }
        // }

        // Verify @Indexed annotation
        if (indexedFlag && !object.getClass().isAnnotationPresent(Indexed.class)) {
            LOG.error("  MISSING @Indexed");
            throw new Exception("  MISSING @Indexed");
        }

        // Verify @XmlRootElement
        // if (!object.getClass().isAnnotationPresent(XmlRootElement.class)) {
        // LOG.error(" MISSING @XmlRootElement");
        // throw new Exception(" MISSING @XmlRootElement");
        // }

        // Check indexed fields
        if (indexedFlag) {
            final Set<String> fieldNames = IndexUtility.getIndexedFieldNames(object.getClass(), "all");
            LOG.info("  field names = " + fieldNames);
            if (fieldNames.size() <= 4) {
                throw new Exception("Indexed fields should include more than id, active, modified, modifiedBy");
            }
        }

        // Check @Transient fields for <HasJsonData
        if (object instanceof HasJsonData) {
            boolean problem = false;
            final StringBuilder sb = new StringBuilder();
            for (final Field field : object.getClass().getDeclaredFields()) {
                // handle meta-fields
                if (field.getName().startsWith("$")) {
                    continue;
                }
                if (!field.isAnnotationPresent(Transient.class)) {
                    problem = true;
                    sb.append(field.getName()).append(", ");
                    LOG.error("    field without @Transient = " + field.getName());
                }
            }
            if (problem) {
                throw new Exception("HasJsonData class with non-transient local fields = " + sb.toString());
            }
        }

        // just testing that this doesn't throw errors
        try (final TerminologyService service = new TerminologyService() {

            /**
             * Validate init.
             *
             * @throws Exception the exception
             */
            @Override
            public void validateInit() throws Exception {

                // n/a
            }
        }) {
            service.setModifiedBy("persistenceTester");

            // Add an object
            LOG.info("  test add object = " + object);
            if (object instanceof HasModified) {
                service.add((HasModified) object);
            } else {
                service.addObject(object);
            }

            // Verify that the id is not null
            final String origId = object.getId();
            if (origId == null) {
                throw new Exception("Original id is unexpectedly null");
            }

            // get object
            LOG.info("  test get object");
            object = service.get(object.getId(), object.getClass());
            LOG.info("    id = " + object.getId());
            if (!origId.equals(object.getId())) {
                throw new Exception("Original id unexpectedly does not match object id = " + origId + ", " + object.getId());
            }

            if (indexedFlag) {
                LOG.info("  test find objects");
                ResultList<? extends HasId> list = null;
                // test find
                list = service.find(origId, null, object.getClass(), null);
                LOG.info("    find = " + list);
                if (list.size() != 1) {
                    throw new Exception("Search results size is unexpectedly not 1 = " + list.size());
                }
                if (service.findIds(origId, null, object.getClass(), null).size() != 1) {
                    throw new Exception("Search results size for findIds is unexpectedly not 1 = " + list.size());
                }

                object = list.getItems().get(0);
                LOG.info("  id = " + object.getId());
                if (!origId.equals(object.getId())) {
                    throw new Exception("Original id unexpectedly does not match object id = " + origId + ", " + object.getId());
                }

                // test find on "modifiedBy"
                if (object instanceof HasModified) {
                    list = service.find("persistenceTester", null, object.getClass(), null);
                    LOG.info("    find = " + list);
                    if (list.size() != 1) {
                        throw new Exception("Search results size is unexpectedly not 1 = " + list.size());
                    }

                    LOG.info("  test update objects");
                    service.setModifiedBy("persistenceTester2");
                    service.update((HasModified) object);
                    list = service.find("persistenceTester", null, object.getClass(), null);
                    LOG.info("    find = " + list);
                    if (list.size() != 0) {
                        throw new Exception("Search results size is unexpectedly not empty = " + list.size());
                    }
                    list = service.find("persistenceTester2", null, object.getClass(), null);
                    LOG.info("    find = " + list);
                    if (list.size() != 1) {
                        throw new Exception("Search results size is unexpectedly not 1 = " + list.size());
                    }
                }
            }

            // delete the object
            LOG.info("  test delete object");
            service.removeObject(object);
            object = service.get(object.getId(), object.getClass());
            if (object != null) {
                throw new Exception("Search results size is unexpectedly not empty = " + object.getId());
            }

        }
    }

}
