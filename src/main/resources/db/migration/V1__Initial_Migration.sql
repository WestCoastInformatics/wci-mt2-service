drop table ${pre_if_exists} refset_history_definition_clauses_history ${post_if_exists};
drop table ${pre_if_exists} refsetedithistory_tags ${post_if_exists};
drop table ${pre_if_exists} refset_history ${post_if_exists};
drop table ${pre_if_exists} definition_clauses_history ${post_if_exists};
drop table ${pre_if_exists} user_roles ${post_if_exists};
drop table ${pre_if_exists} users ${post_if_exists};
drop table ${pre_if_exists} workflow_history ${post_if_exists};
drop table ${pre_if_exists} refsets_definition_clauses ${post_if_exists};
drop table ${pre_if_exists} definition_clauses ${post_if_exists};
drop table ${pre_if_exists} refset_tags ${post_if_exists};
drop table ${pre_if_exists} refsets ${post_if_exists};
drop table ${pre_if_exists} projects ${post_if_exists};
drop table ${pre_if_exists} organizations ${post_if_exists};
drop table ${pre_if_exists} edition_defaultlanguagerefsets ${post_if_exists};
drop table ${pre_if_exists} editions ${post_if_exists};

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
  `topLevelModule` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `edition_defaultlanguagerefsets` (
  `Edition_id` varchar(64) NOT NULL,
  `defaultLanguageRefsets` varchar(255) DEFAULT NULL,
  KEY `FKsty54m8wa2yvysx49lsgdapq0` (`Edition_id`),
  CONSTRAINT `FKsty54m8wa2yvysx49lsgdapq0` FOREIGN KEY (`Edition_id`) REFERENCES `editions` (`id`)
);

CREATE TABLE `organizations` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `edition_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK4emt69axwy8ehf0vkr3t2acea` (`edition_id`),
  CONSTRAINT `FK4emt69axwy8ehf0vkr3t2acea` FOREIGN KEY (`edition_id`) REFERENCES `editions` (`id`)
);

CREATE TABLE `projects` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `privateProject` bit(1) NOT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `organization_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3gwrleyyq6prcnqekmkobbimd` (`organization_id`),
  CONSTRAINT `FK3gwrleyyq6prcnqekmkobbimd` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
);

CREATE TABLE `refsets` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `externalUrl` varchar(4000) DEFAULT NULL,
  `localSet` bit(1) NOT NULL,
  `latestPublishedVersion` bit(1) DEFAULT false,
  `hasVersionInDevelopment` bit(1) DEFAULT false,
  `moduleId` varchar(256) NOT NULL,
  `editBranchId` varchar(256),
  `name` varchar(4000) NOT NULL,
  `narrative` longtext,
  `privateRefset` bit(1) NOT NULL,
  `refsetId` varchar(256) NOT NULL,
  `assignedUser` varchar(256),
  `type` varchar(256) NOT NULL,
  `versionDate` datetime(6) DEFAULT NULL,
  `versionNotes` longtext,
  `versionStatus` varchar(256) NOT NULL,
  `workflowStatus` varchar(256),
  `project_id` varchar(64) DEFAULT NULL,
  `memberCount` int DEFAULT '-1',
  PRIMARY KEY (`id`),
  KEY `FKapij9mkufxno7uncjc6oo20en` (`project_id`),
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

CREATE TABLE `workflow_history` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `notes` longtext,
  `userName` varchar(256) NOT NULL,
  `workflowStatus` varchar(256) DEFAULT NULL,
  `workflowAction` varchar(256) DEFAULT NULL,
  `refset_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7c0nnfqf1yumohfk60ysf68y7` (`refset_id`),
  CONSTRAINT `FK7c0nnfqf1yumohfk60ysf68y7` FOREIGN KEY (`refset_id`) REFERENCES `refsets` (`id`)
);

CREATE TABLE `users` (
	`id` varchar(64) NOT NULL,
	`active` bit(1) NOT NULL,
	`created` datetime(6) NOT NULL,
	`modified` datetime(6) NOT NULL,
	`modifiedBy` varchar(256) NOT NULL,
	`username` varchar(250) NOT NULL,
	`name` varchar(250) NOT NULL,
	`email` varchar(255) NOT NULL,
	PRIMARY KEY (`id`)
);

CREATE TABLE `user_roles` (
  `user_id` varchar(64) NOT NULL,
  `roles` varchar(255) DEFAULT NULL,
  KEY `FK7ppgoj8kxsmh27hyahk1m96v7` (`user_id`),
  CONSTRAINT `FK7ppgoj8kxsmh27hyahk1m96v7` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
);

CREATE TABLE `definition_clauses_history` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `negated` bit(1) NOT NULL,
  `value` varchar(4000) NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `refset_history` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `editBranchId` varchar(256) DEFAULT NULL,
  `externalUrl` varchar(4000) DEFAULT NULL,
  `localSet` bit(1) NOT NULL,
  `moduleId` varchar(256) NOT NULL,
  `name` varchar(4000) NOT NULL,
  `narrative` longtext,
  `privateRefset` bit(1) NOT NULL,
  `refsetId` varchar(256) NOT NULL,
  `type` varchar(256) NOT NULL,
  `versionDate` datetime(6) DEFAULT NULL,
  `versionNotes` longtext,
  `versionStatus` varchar(256) NOT NULL,
  `workflowStatus` varchar(256) DEFAULT NULL,
  `memberCount` int DEFAULT '-1',
  PRIMARY KEY (`id`)
);

CREATE TABLE `refsetedithistory_tags` (
  `RefsetEditHistory_id` varchar(64) NOT NULL,
  `tags` varchar(255) DEFAULT NULL,
  KEY `FKp3ibc0vuk4awosxcicl40d9mf` (`RefsetEditHistory_id`),
  CONSTRAINT `FKp3ibc0vuk4awosxcicl40d9mf` FOREIGN KEY (`RefsetEditHistory_id`) REFERENCES `refset_history` (`id`)
);

CREATE TABLE `refset_history_definition_clauses_history` (
  `RefsetEditHistory_id` varchar(64) NOT NULL,
  `definitionClauses_id` varchar(64) NOT NULL,
  UNIQUE KEY `UK_i1trc62t28cd2ktnwm5mw83n3` (`definitionClauses_id`),
  KEY `FKwyybq7oy7spmr11a78ix5b4p` (`RefsetEditHistory_id`),
  CONSTRAINT `FKo21bax91dosoykcp70vg8709n` FOREIGN KEY (`definitionClauses_id`) REFERENCES `definition_clauses_history` (`id`),
  CONSTRAINT `FKwyybq7oy7spmr11a78ix5b4p` FOREIGN KEY (`RefsetEditHistory_id`) REFERENCES `refset_history` (`id`)
);

CREATE TABLE `upgrade_inactive_concecpts` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `code` varchar(256) NOT NULL,
  `descriptions` longtext NOT NULL,
  `inactivationReason` varchar(256),
  `refsetId` varchar(256) NOT NULL,
  `replaced` bit(1) NOT NULL,
  `stillMember` bit(1) NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `upgrade_replacement_concecpts` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `added` bit(1) NOT NULL,
  `code` varchar(256) NOT NULL,
  `descriptions` longtext NOT NULL,
  `existingMember` bit(1) NOT NULL,
  `reason` varchar(256) NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `upgrade_inactive_concecpts_upgrade_replacement_concecpts` (
  `UpgradeInactiveConcecpt_id` varchar(64) NOT NULL,
  `replacementConcecpts_id` varchar(64) NOT NULL,
  UNIQUE KEY `UK_gvw8w97h67cp3wamn80hv4279` (`replacementConcecpts_id`),
  KEY `FKr832qlbgsqdr1o9do0qseqm4m` (`UpgradeInactiveConcecpt_id`),
  CONSTRAINT `FKju78x6kjym3y2y90mfmon9lge` FOREIGN KEY (`replacementConcecpts_id`) REFERENCES `upgrade_replacement_concecpts` (`id`),
  CONSTRAINT `FKr832qlbgsqdr1o9do0qseqm4m` FOREIGN KEY (`UpgradeInactiveConcecpt_id`) REFERENCES `upgrade_inactive_concecpts` (`id`)
);

