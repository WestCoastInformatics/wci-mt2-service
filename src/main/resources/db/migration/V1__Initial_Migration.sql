drop table ${pre_if_exists} concepts ${post_if_exists};
drop table ${pre_if_exists} users ${post_if_exists};
   
CREATE TABLE concepts
(
    id character varying(64) NOT NULL,
    active boolean NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    modifiedby character varying(256) NOT NULL,
    code character varying(255),
    name character varying(255),
    terminology character varying(255),
    version character varying(255),
    CONSTRAINT concepts_pkey PRIMARY KEY (id)
);
    
CREATE TABLE editions
(
    id character varying(64) NOT NULL,
    active boolean NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    modifiedby character varying(256) NOT NULL,
    branch character varying(255),
    code character varying(255) NOT NULL,
    country character varying(255) NOT NULL,
    description character varying(4000),
    iconuri character varying(255),
    name character varying(255) NOT NULL,
    CONSTRAINT editions_pkey PRIMARY KEY (id)
);

CREATE TABLE refsets
(
    id character varying(64) NOT NULL,
    active boolean NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    modifiedby character varying(256) NOT NULL,
    externalurl character varying(4000),
    localset boolean NOT NULL,
    moduleid character varying(256) NOT NULL,
    name character varying(4000) NOT NULL,
    narrative character varying(10000),
    privaterefset boolean NOT NULL,
    projectid character varying(256) NOT NULL,
    refsetid character varying(256) NOT NULL,
    type character varying(256) NOT NULL,
    versiondate timestamp without time zone,
    versionnotes character varying(10000),
    versionstatus character varying(256) NOT NULL,
    edition_id character varying(64),
    CONSTRAINT refsets_pkey PRIMARY KEY (id),
    CONSTRAINT fk4emt69axwy8ehf0vkr3t2acea FOREIGN KEY (edition_id)
        REFERENCES editions (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);

CREATE TABLE refset_tags
(
    refset_id character varying(64) NOT NULL,
    tags character varying(255),
    CONSTRAINT fkhamy5caidejdqf663hp9gftu6 FOREIGN KEY (refset_id)
        REFERENCES refsets (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);

CREATE TABLE tags
(
    id character varying(64) NOT NULL,
    active boolean NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    modifiedby character varying(256) NOT NULL,
    key character varying(256) NOT NULL,
    value character varying(256) NOT NULL,
    CONSTRAINT tags_pkey PRIMARY KEY (id)
);

CREATE TABLE definition_clauses
(
    id character varying(64) NOT NULL,
    active boolean NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    modifiedby character varying(256) NOT NULL,
    negated boolean NOT NULL,
    value character varying(4000) NOT NULL,
    CONSTRAINT definition_clauses_pkey PRIMARY KEY (id)
);

CREATE TABLE refsets_definition_clauses
(
    refset_id character varying(64) NOT NULL,
    definitionclauses_id character varying(64) NOT NULL,
    CONSTRAINT uk_93xq9bgm9nffpwt4gfjx5f548 UNIQUE (definitionclauses_id),
    CONSTRAINT fkbxe21a6g8xufs1yh5537pya8p FOREIGN KEY (definitionclauses_id)
        REFERENCES definition_clauses (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT fkdowc61fwiejkojh1wj7wk0mn0 FOREIGN KEY (refset_id)
        REFERENCES refsets (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);