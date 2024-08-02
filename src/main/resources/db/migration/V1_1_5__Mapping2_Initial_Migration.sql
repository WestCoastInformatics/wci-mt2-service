DROP TABLE ${pre_if_exists} additional_map_entry_info ${post_if_exists};
DROP TABLE ${pre_if_exists} map_advices ${post_if_exists};
DROP TABLE ${pre_if_exists} map_age_ranges ${post_if_exists};
DROP TABLE ${pre_if_exists} map_entries ${post_if_exists};
DROP TABLE ${pre_if_exists} map_entries_additional_map_entry_info ${post_if_exists};
DROP TABLE ${pre_if_exists} map_notes ${post_if_exists};
DROP TABLE ${pre_if_exists} map_principles ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects_additional_map_entry_infos ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects_error_messages ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects_map_advices ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects_map_age_ranges ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects_map_leads ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects_map_principles ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects_map_relations ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects_map_specialists ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects_report_definitions ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects_scope_concepts ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects_scope_excluded_concepts ${post_if_exists};
DROP TABLE ${pre_if_exists} map_relations ${post_if_exists};
DROP TABLE ${pre_if_exists} map_report_definitions ${post_if_exists};
DROP TABLE ${pre_if_exists} map_projects ${post_if_exists};
DROP TABLE ${pre_if_exists} map_sets ${post_if_exists};
DROP TABLE ${pre_if_exists} map_users ${post_if_exists};
DROP TABLE ${pre_if_exists} mapentry_advices ${post_if_exists};
DROP TABLE ${pre_if_exists} mappings ${post_if_exists};

CREATE TABLE `map_users` (
  `id` varchar(64) NOT NULL,
  `applicationRole` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `team` varchar(255) DEFAULT NULL,
  `userName` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_b0l6mrkqu9wrqxard54u90ln5` (`userName`)
);

CREATE TABLE `additional_map_entry_info` (
  `id` varchar(64) NOT NULL,
  `field` varchar(4000) NOT NULL,
  `name` varchar(4000) NOT NULL,
  `value` varchar(4000) NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `map_advices` (
  `id` varchar(64) NOT NULL,
  `detail` varchar(255) NOT NULL,
  `isAllowableForNullTarget` bit(1) NOT NULL,
  `isComputed` bit(1) NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_dj6g0w6nfmh71hi2kpbroh3ai` (`detail`),
  UNIQUE KEY `UK_n3im41eenihk4u15bprol3sfj` (`name`)
);

CREATE TABLE `map_age_ranges` (
  `id` varchar(64) NOT NULL,
  `lowerInclusive` bit(1) NOT NULL,
  `lowerUnits` varchar(255) NOT NULL,
  `lowerValue` int NOT NULL,
  `name` varchar(255) NOT NULL,
  `upperInclusive` bit(1) NOT NULL,
  `upperUnits` varchar(255) NOT NULL,
  `upperValue` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKlnttfbpe2x2uwbfqv8l885mk7` (`name`)
);

CREATE TABLE `map_entries` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `block` int NOT NULL,
  `map_group` int NOT NULL,
  `priority` int NOT NULL,
  `relation` varchar(4000) NOT NULL,
  `rule` varchar(4000) NOT NULL,
  `toCode` varchar(4000) NOT NULL,
  `toName` varchar(4000) NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `map_entries_additional_map_entry_info` (
  `MapEntry_id` varchar(64) NOT NULL,
  `additionalMapEntryInfos_id` varchar(64) NOT NULL,
  PRIMARY KEY (`MapEntry_id`,`additionalMapEntryInfos_id`),
  UNIQUE KEY `UK_6k3gw8ynhg6jr1ggfggf0s4tj` (`additionalMapEntryInfos_id`),
  CONSTRAINT `FKqeradtj8frtko3x60ke9q7w52` FOREIGN KEY (`additionalMapEntryInfos_id`) REFERENCES `additional_map_entry_info` (`id`),
  CONSTRAINT `FKqxyvyylrpfmduxjebf7eli8ry` FOREIGN KEY (`MapEntry_id`) REFERENCES `map_entries` (`id`)
);


CREATE TABLE `map_principles` (
  `id` varchar(64) NOT NULL,
  `detail` varchar(4000) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `principleId` varchar(255) DEFAULT NULL,
  `sectionRef` varchar(4000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKb2xnkyhwouj23jxfryekhulfr` (`name`,`principleId`)
);

CREATE TABLE `map_relations` (
  `id` varchar(64) NOT NULL,
  `abbreviation` varchar(255) DEFAULT NULL,
  `isAllowableForNullTarget` bit(1) NOT NULL,
  `isComputed` bit(1) NOT NULL,
  `name` varchar(255) NOT NULL,
  `terminologyId` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKaucnn0m7p5mxvd3ix3egxysoe` (`name`)
);

CREATE TABLE `map_sets` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `branchPath` varchar(255) NOT NULL,
  `fromBranchPath` varchar(255) NOT NULL,
  `fromTerminology` varchar(255) NOT NULL,
  `fromVersion` varchar(255) NOT NULL,
  `moduleId` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `refSetCode` varchar(255) DEFAULT NULL,
  `refSetName` varchar(255) DEFAULT NULL,
  `toBranchPath` varchar(255) NOT NULL,
  `toTerminology` varchar(255) NOT NULL,
  `toVersion` varchar(255) NOT NULL,
  `version` varchar(255) NOT NULL,
  `versionStatus` varchar(256) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKneur0v3alqt6vuup23cqlhiwt` (`name`)
);

CREATE TABLE `map_notes` (
  `id` varchar(64) NOT NULL,
  `note` varchar(4000) NOT NULL,
  `timestamp` datetime(6) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqj63jhjm8upkx36uhv7t74fms` (`user_id`),
  CONSTRAINT `FKqj63jhjm8upkx36uhv7t74fms` FOREIGN KEY (`user_id`) REFERENCES `map_users` (`id`)
);

CREATE TABLE `map_projects` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `destinationTerminology` varchar(255) NOT NULL,
  `destinationTerminologyVersion` varchar(255) NOT NULL,
  `editingCycleBeginDate` datetime(6) DEFAULT NULL,
  `groupStructure` bit(1) NOT NULL,
  `latestPublicationDate` datetime(6) DEFAULT NULL,
  `mapNotesPublic` bit(1) NOT NULL,
  `mapPrincipleSourceDocument` varchar(255) DEFAULT NULL,
  `mapPrincipleSourceDocumentName` varchar(255) DEFAULT NULL,
  `mapRefsetPattern` varchar(255) DEFAULT NULL,
  `mapRelationStyle` varchar(255) DEFAULT NULL,
  `moduleId` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `primaryContactEmail` varchar(255) DEFAULT NULL,
  `privateProject` bit(1) NOT NULL,
  `propagatedFlag` bit(1) NOT NULL,
  `propagationDescendantThreshold` int DEFAULT NULL,
  `published` bit(1) NOT NULL,
  `refSetId` varchar(255) DEFAULT NULL,
  `refSetName` varchar(255) DEFAULT NULL,
  `reverseMapPattern` bit(1) DEFAULT NULL,
  `ruleBased` bit(1) NOT NULL,
  `scopeDescendantsFlag` bit(1) NOT NULL,
  `scopeExcludedDescendantsFlag` bit(1) NOT NULL,
  `sourceTerminology` varchar(255) NOT NULL,
  `sourceTerminologyVersion` varchar(255) NOT NULL,
  `teamBased` bit(1) NOT NULL,
  `useTags` bit(1) NOT NULL,
  `workflowType` varchar(255) DEFAULT NULL,
  `edition_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKc1n6vnli5c9gcm53qv8qsfj25` (`name`)
);
ALTER TABLE `map_projects` ADD INDEX `FK70c8p1lcf83c2sxj840vuws8x` (`edition_id`);
ALTER TABLE `map_projects` ADD CONSTRAINT `FK70c8p1lcf83c2sxj840vuws8x` FOREIGN KEY (`edition_id`) REFERENCES `editions` (`id`);

CREATE TABLE `map_report_definitions` (
  `id` varchar(64) NOT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `diffReportDefinitionName` varchar(255) DEFAULT NULL,
  `frequency` varchar(255) NOT NULL,
  `isDiffReport` bit(1) NOT NULL,
  `isQACheck` bit(1) NOT NULL,
  `name` varchar(255) NOT NULL,
  `query` varchar(10000) DEFAULT NULL,
  `queryType` varchar(255) DEFAULT NULL,
  `resultType` varchar(255) DEFAULT NULL,
  `roleRequired` varchar(255) DEFAULT NULL,
  `timePeriod` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKoobvxui0rcq35a5hey8i2f32n` (`name`)
);

CREATE TABLE `map_projects_additional_map_entry_infos` (
  `map_projects_id` varchar(64) NOT NULL,
  `additionalMapEntryInfos_id` varchar(64) NOT NULL,
  PRIMARY KEY (`map_projects_id`,`additionalMapEntryInfos_id`),
  KEY `FKk6i62c88anhjr87k3tcgd9vgy` (`additionalMapEntryInfos_id`),
  CONSTRAINT `FKfdswe3vude0tdgog8lhy99fvk` FOREIGN KEY (`map_projects_id`) REFERENCES `map_projects` (`id`),
  CONSTRAINT `FKk6i62c88anhjr87k3tcgd9vgy` FOREIGN KEY (`additionalMapEntryInfos_id`) REFERENCES `additional_map_entry_info` (`id`)
);

CREATE TABLE `map_projects_error_messages` (
  `id` varchar(64) NOT NULL,
  `errorMessages` varchar(255) DEFAULT NULL,
  KEY `FKhd1m20iodgyedaiqaor63cw4j` (`id`),
  CONSTRAINT `FKhd1m20iodgyedaiqaor63cw4j` FOREIGN KEY (`id`) REFERENCES `map_projects` (`id`)
);

CREATE TABLE `map_projects_map_advices` (
  `map_projects_id` varchar(64) NOT NULL,
  `mapAdvices_id` varchar(64) NOT NULL,
  PRIMARY KEY (`map_projects_id`,`mapAdvices_id`),
  KEY `FKnj2iid596571uobbdsl7atlt7` (`mapAdvices_id`),
  CONSTRAINT `FK25moab8s0q4hrl8itb8w44o3k` FOREIGN KEY (`map_projects_id`) REFERENCES `map_projects` (`id`),
  CONSTRAINT `FKnj2iid596571uobbdsl7atlt7` FOREIGN KEY (`mapAdvices_id`) REFERENCES `map_advices` (`id`)
);

CREATE TABLE `map_projects_map_age_ranges` (
  `map_projects_id` varchar(64) NOT NULL,
  `presetAgeRanges_id` varchar(64) NOT NULL,
  PRIMARY KEY (`map_projects_id`,`presetAgeRanges_id`),
  KEY `FKfpwlenbm0g40ys91344n6c9tv` (`presetAgeRanges_id`),
  CONSTRAINT `FKfpwlenbm0g40ys91344n6c9tv` FOREIGN KEY (`presetAgeRanges_id`) REFERENCES `map_age_ranges` (`id`),
  CONSTRAINT `FKipe7witcjp5ojdq2kx6uxcc8q` FOREIGN KEY (`map_projects_id`) REFERENCES `map_projects` (`id`)
);

CREATE TABLE `map_projects_map_leads` (
  `map_projects_id` varchar(64) NOT NULL,
  `map_users_id` varchar(64) NOT NULL,
  PRIMARY KEY (`map_projects_id`,`map_users_id`),
  KEY `FK1gcmi96jhxnk50i0k3t98soci` (`map_users_id`),
  CONSTRAINT `FK1gcmi96jhxnk50i0k3t98soci` FOREIGN KEY (`map_users_id`) REFERENCES `map_users` (`id`),
  CONSTRAINT `FKaql4u6p2exwmvxdh3oq9jtl5r` FOREIGN KEY (`map_projects_id`) REFERENCES `map_projects` (`id`)
);

CREATE TABLE `map_projects_map_principles` (
  `map_projects_id` varchar(64) NOT NULL,
  `mapPrinciples_id` varchar(64) NOT NULL,
  PRIMARY KEY (`map_projects_id`,`mapPrinciples_id`),
  KEY `FK8y7rnuc0flvwqxp67vby8pntq` (`mapPrinciples_id`),
  CONSTRAINT `FK8y7rnuc0flvwqxp67vby8pntq` FOREIGN KEY (`mapPrinciples_id`) REFERENCES `map_principles` (`id`),
  CONSTRAINT `FKqhftkpp8ebc8r6ubjjl8hu405` FOREIGN KEY (`map_projects_id`) REFERENCES `map_projects` (`id`)
);

CREATE TABLE `map_projects_map_relations` (
  `map_projects_id` varchar(64) NOT NULL,
  `mapRelations_id` varchar(64) NOT NULL,
  PRIMARY KEY (`map_projects_id`,`mapRelations_id`),
  KEY `FKm6qicc4ql84i0vg3os4ej73bm` (`mapRelations_id`),
  CONSTRAINT `FKeccx2af8ci65o72bghbpdtarm` FOREIGN KEY (`map_projects_id`) REFERENCES `map_projects` (`id`),
  CONSTRAINT `FKm6qicc4ql84i0vg3os4ej73bm` FOREIGN KEY (`mapRelations_id`) REFERENCES `map_relations` (`id`)
);

CREATE TABLE `map_projects_map_specialists` (
  `map_projects_id` varchar(64) NOT NULL,
  `map_users_id` varchar(64) NOT NULL,
  PRIMARY KEY (`map_projects_id`,`map_users_id`),
  KEY `FKbgdxii8lauudgdxgpo3cy7ace` (`map_users_id`),
  CONSTRAINT `FKbgdxii8lauudgdxgpo3cy7ace` FOREIGN KEY (`map_users_id`) REFERENCES `map_users` (`id`),
  CONSTRAINT `FKsu88ijb07fto1rt9ngsyqjf9h` FOREIGN KEY (`map_projects_id`) REFERENCES `map_projects` (`id`)
);

CREATE TABLE `map_projects_report_definitions` (
  `map_projects_id` varchar(64) NOT NULL,
  `mapMapReportDefinitions_id` varchar(64) NOT NULL,
  PRIMARY KEY (`map_projects_id`,`mapMapReportDefinitions_id`),
  KEY `FKjoqbf82xdy847r5llxt5d3rwx` (`mapMapReportDefinitions_id`),
  CONSTRAINT `FK7c2gkskvsql9miehjde847rod` FOREIGN KEY (`map_projects_id`) REFERENCES `map_projects` (`id`),
  CONSTRAINT `FKjoqbf82xdy847r5llxt5d3rwx` FOREIGN KEY (`mapMapReportDefinitions_id`) REFERENCES `map_report_definitions` (`id`)
);

CREATE TABLE `map_projects_scope_concepts` (
  `id` varchar(64) NOT NULL,
  `scopeConcepts` varchar(255) DEFAULT NULL,
  KEY `FKh4smuktsjoo05y4mlygvc9bf9` (`id`),
  CONSTRAINT `FKh4smuktsjoo05y4mlygvc9bf9` FOREIGN KEY (`id`) REFERENCES `map_projects` (`id`)
);

CREATE TABLE `map_projects_scope_excluded_concepts` (
  `id` varchar(64) NOT NULL,
  `scopeExcludedConcepts` varchar(255) DEFAULT NULL,
  KEY `FKefkqy4p1bfbytfalno99yw70h` (`id`),
  CONSTRAINT `FKefkqy4p1bfbytfalno99yw70h` FOREIGN KEY (`id`) REFERENCES `map_projects` (`id`)
);

CREATE TABLE `mapentry_advices` (
  `MapEntry_id` varchar(64) NOT NULL,
  `advices` varchar(255) DEFAULT NULL,
  KEY `FKmu59hc176sv6evmi658h8k16a` (`MapEntry_id`),
  CONSTRAINT `FKmu59hc176sv6evmi658h8k16a` FOREIGN KEY (`MapEntry_id`) REFERENCES `map_entries` (`id`)
);

CREATE TABLE `mappings` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `code` varchar(4000) DEFAULT NULL,
  `mapSetId` varchar(4000) DEFAULT NULL,
  `name` varchar(4000) DEFAULT NULL,
  `mapEntries_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoqackhvvnir5m8jblyv6ibyqm` (`mapEntries_id`),
  CONSTRAINT `FKoqackhvvnir5m8jblyv6ibyqm` FOREIGN KEY (`mapEntries_id`) REFERENCES `map_entries` (`id`)
);
