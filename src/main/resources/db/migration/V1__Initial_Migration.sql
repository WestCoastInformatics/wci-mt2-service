drop table ${pre_if_exists} refsets_definition_clauses ${post_if_exists};
drop table ${pre_if_exists} definition_clauses ${post_if_exists};
drop table ${pre_if_exists} refset_tags ${post_if_exists};
drop table ${pre_if_exists} refsets ${post_if_exists};
drop table ${pre_if_exists} edition_defaultlanguagerefsets ${post_if_exists};
drop table ${pre_if_exists} editions ${post_if_exists};
drop table ${pre_if_exists} projects ${post_if_exists};
drop table ${pre_if_exists} organizations ${post_if_exists};
   
CREATE TABLE `organizations` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `projects` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `organization_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3gwrleyyq6prcnqekmkobbimd` (`organization_id`),
  CONSTRAINT `FK3gwrleyyq6prcnqekmkobbimd` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
);
    
CREATE TABLE `editions` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `branch` varchar(255) DEFAULT NULL,
  `defaultLanguageCode` varchar(256) DEFAULT NULL,
  `iconUri` varchar(512) DEFAULT NULL,
  `name` varchar(4000) NOT NULL,
  `namespace` varchar(256) DEFAULT NULL,
  `shortName` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `edition_defaultlanguagerefsets` (
  `Edition_id` varchar(64) NOT NULL,
  `defaultLanguageRefsets` varchar(255) DEFAULT NULL,
  KEY `FKsty54m8wa2yvysx49lsgdapq0` (`Edition_id`),
  CONSTRAINT `FKsty54m8wa2yvysx49lsgdapq0` FOREIGN KEY (`Edition_id`) REFERENCES `editions` (`id`)
);

CREATE TABLE `refsets` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `externalUrl` varchar(4000) DEFAULT NULL,
  `localSet` bit(1) NOT NULL,
  `latestVersion` bit(1) DEFAULT false,
  `moduleId` varchar(256) NOT NULL,
  `name` varchar(4000) NOT NULL,
  `narrative` longtext,
  `privateRefset` bit(1) NOT NULL,
  `refsetId` varchar(256) NOT NULL,
  `type` varchar(256) NOT NULL,
  `versionDate` datetime(6) DEFAULT NULL,
  `versionNotes` longtext,
  `versionStatus` varchar(256) NOT NULL,
  `edition_id` varchar(64) DEFAULT NULL,
  `project_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK4emt69axwy8ehf0vkr3t2acea` (`edition_id`),
  KEY `FKapij9mkufxno7uncjc6oo20en` (`project_id`),
  CONSTRAINT `FK4emt69axwy8ehf0vkr3t2acea` FOREIGN KEY (`edition_id`) REFERENCES `editions` (`id`),
  CONSTRAINT `FKapij9mkufxno7uncjc6oo20en` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`)
);

CREATE TABLE `refset_tags` (
  `Refset_id` varchar(64) NOT NULL,
  `tags` varchar(255) DEFAULT NULL,
  KEY `FKhamy5caidejdqf663hp9gftu6` (`Refset_id`),
  CONSTRAINT `FKhamy5caidejdqf663hp9gftu6` FOREIGN KEY (`Refset_id`) REFERENCES `refsets` (`id`)
);

CREATE TABLE `definition_clauses` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `negated` bit(1) NOT NULL,
  `value` varchar(4000) NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `refsets_definition_clauses` (
  `Refset_id` varchar(64) NOT NULL,
  `definitionClauses_id` varchar(64) NOT NULL,
  UNIQUE KEY `UK_93xq9bgm9nffpwt4gfjx5f548` (`definitionClauses_id`),
  KEY `FKdowc61fwiejkojh1wj7wk0mn0` (`Refset_id`),
  CONSTRAINT `FKbxe21a6g8xufs1yh5537pya8p` FOREIGN KEY (`definitionClauses_id`) REFERENCES `definition_clauses` (`id`),
  CONSTRAINT `FKdowc61fwiejkojh1wj7wk0mn0` FOREIGN KEY (`Refset_id`) REFERENCES `refsets` (`id`)
);