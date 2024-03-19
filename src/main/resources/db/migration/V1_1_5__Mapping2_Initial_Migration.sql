DROP TABLE ${pre_if_exists} map_users ${post_if_exists};
DROP TABLE ${pre_if_exists} additional_map_entry_info ${post_if_exists};
DROP TABLE ${pre_if_exists} map_advices ${post_if_exists};
DROP TABLE ${pre_if_exists} map_principles ${post_if_exists};
DROP TABLE ${pre_if_exists} map_relations ${post_if_exists};
DROP TABLE ${pre_if_exists} map_sets ${post_if_exists};
DROP TABLE ${pre_if_exists} map_entries ${post_if_exists};
DROP TABLE ${pre_if_exists} map_entries_additional_map_entry_info ${post_if_exists};
DROP TABLE ${pre_if_exists} map_notes ${post_if_exists};
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

CREATE TABLE `map_notes` (
  `id` varchar(64) NOT NULL,
  `note` varchar(4000) NOT NULL,
  `timestamp` datetime(6) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqj63jhjm8upkx36uhv7t74fms` (`user_id`),
  CONSTRAINT `FKqj63jhjm8upkx36uhv7t74fms` FOREIGN KEY (`user_id`) REFERENCES `map_users` (`id`)
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