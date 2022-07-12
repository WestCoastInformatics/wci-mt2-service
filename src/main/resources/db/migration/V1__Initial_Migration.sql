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
drop table ${pre_if_exists} organization_members ${post_if_exists};
drop table ${pre_if_exists} edition_defaultlanguagerefsets ${post_if_exists};
drop table ${pre_if_exists} editions ${post_if_exists};
drop table ${pre_if_exists} teams ${post_if_exists};
drop table ${pre_if_exists} team_members ${post_if_exists};
drop table ${pre_if_exists} team_roles ${post_if_exists};
drop table ${pre_if_exists} project_teams ${post_if_exists};
drop table ${pre_if_exists} discussion_posts ${post_if_exists};
drop table ${pre_if_exists} discussion_threads ${post_if_exists};
drop table ${pre_if_exists} discussion_threads_discussion_posts ${post_if_exists};
drop table ${pre_if_exists} audit_entries ${post_if_exists};
drop table ${pre_if_exists} artifacts ${post_if_exists};


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
  `defaultLanguageRefsets` varchar(255) DEFAULT NULL
);

ALTER TABLE `edition_defaultlanguagerefsets` ADD INDEX `FKsty54m8wa2yvysx49lsgdapq0` (`Edition_id`);
ALTER TABLE `edition_defaultlanguagerefsets` ADD CONSTRAINT `FKsty54m8wa2yvysx49lsgdapq0` FOREIGN KEY (`Edition_id`) REFERENCES `editions` (`id`);

CREATE TABLE `organizations` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `primaryContactEmail` varchar(255) DEFAULT NULL,
  `edition_id` varchar(64) DEFAULT NULL,
  `iconUri` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
);
ALTER TABLE `organizations` ADD INDEX `FK9og41jo3e6xe033my21t6wscf` (`edition_id`);
ALTER TABLE `organizations` ADD CONSTRAINT `FK9og41jo3e6xe033my21t6wscf` FOREIGN KEY (`edition_id`) REFERENCES `editions` (`id`);

CREATE TABLE `projects` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `primaryContactEmail` varchar(255) DEFAULT NULL,
  `crowdProjectId` varchar(255) DEFAULT NULL,
  `privateProject` bit(1) NOT NULL,
  `organization_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`)
);
ALTER TABLE `projects` ADD INDEX `FK3gwrleyyq6prcnqekmkobbimd` (`organization_id`);
ALTER TABLE `projects` ADD CONSTRAINT `FK3gwrleyyq6prcnqekmkobbimd` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`);

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
  PRIMARY KEY (`id`)
);
ALTER TABLE `refsets` ADD INDEX `FKapij9mkufxno7uncjc6oo20en` (`project_id`);
ALTER TABLE `refsets` ADD CONSTRAINT `FKapij9mkufxno7uncjc6oo20en` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`);

CREATE TABLE `refset_tags` (
  `Refset_id` varchar(64) NOT NULL,
  `tags` varchar(255) DEFAULT NULL
);
ALTER TABLE `refset_tags` ADD INDEX `FKhamy5caidejdqf663hp9gftu6` (`Refset_id`);
ALTER TABLE `refset_tags` ADD CONSTRAINT `FKhamy5caidejdqf663hp9gftu6` FOREIGN KEY (`Refset_id`) REFERENCES `refsets` (`id`);

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
  `definitionClauses_id` varchar(64) NOT NULL
);
ALTER TABLE `refsets_definition_clauses` ADD UNIQUE KEY `UK_93xq9bgm9nffpwt4gfjx5f548` (`definitionClauses_id`);
ALTER TABLE `refsets_definition_clauses` ADD INDEX `FKdowc61fwiejkojh1wj7wk0mn0` (`Refset_id`);
ALTER TABLE `refsets_definition_clauses` ADD CONSTRAINT `FKbxe21a6g8xufs1yh5537pya8p` FOREIGN KEY (`definitionClauses_id`) REFERENCES `definition_clauses` (`id`);
ALTER TABLE `refsets_definition_clauses` ADD CONSTRAINT `FKdowc61fwiejkojh1wj7wk0mn0` FOREIGN KEY (`Refset_id`) REFERENCES `refsets` (`id`);

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
  PRIMARY KEY (`id`)
);
-- ALTER  TABLE `workflow_history` ADD INDEX `FK7c0nnfqf1yumohfk60ysf68y7` (`refset_id`);
ALTER  TABLE `workflow_history` ADD CONSTRAINT `FK7c0nnfqf1yumohfk60ysf68y7` FOREIGN KEY (`refset_id`) REFERENCES `refsets` (`id`);

CREATE TABLE `users` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `company` varchar(250) DEFAULT NULL,
  `email` varchar(250) NOT NULL,
  `name` varchar(250) NOT NULL,
  `title` varchar(250) DEFAULT NULL,
  `userName` varchar(250) NOT NULL,
  `iconUri` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
);
ALTER TABLE `users` ADD UNIQUE KEY `UK_mmns67o5v4bfippoqitu4v3t6` (`userName`);

--
ALTER TABLE `users` ADD index `index_userName` (`username`);
ALTER TABLE `users` ADD index `index_email` (`email`);
--

CREATE TABLE `user_roles` (
  `user_id` varchar(64) NOT NULL,
  `roles` varchar(255) DEFAULT NULL
);
ALTER TABLE `user_roles` ADD INDEX `FK7ppgoj8kxsmh27hyahk1m96v7` (`user_id`);
ALTER TABLE `user_roles` ADD CONSTRAINT `FK7ppgoj8kxsmh27hyahk1m96v7` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

CREATE TABLE `organization_members` (
  `user_id` varchar(64) NOT NULL,
  `organization_id` varchar(64) NOT NULL,
  PRIMARY KEY (`user_id`,`organization_id`)
);
ALTER TABLE `organization_members` ADD INDEX `FK9us8isobqed8ba2wqq6cqoupl` (`organization_id`);
ALTER TABLE `organization_members` ADD CONSTRAINT `FK9us8isobqed8ba2wqq6cqoupl` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`);
ALTER TABLE `organization_members` ADD CONSTRAINT `FKmk6i6pb4mvo0gf26cwqh4tlpo` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

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
  `tags` varchar(255) DEFAULT NULL
);
ALTER TABLE `refsetedithistory_tags` ADD INDEX `FKp3ibc0vuk4awosxcicl40d9mf` (`RefsetEditHistory_id`);
ALTER TABLE `refsetedithistory_tags` ADD CONSTRAINT `FKp3ibc0vuk4awosxcicl40d9mf` FOREIGN KEY (`RefsetEditHistory_id`) REFERENCES `refset_history` (`id`);

CREATE TABLE `refset_history_definition_clauses_history` (
  `RefsetEditHistory_id` varchar(64) NOT NULL,
  `definitionClauses_id` varchar(64) NOT NULL  
);
ALTER TABLE `refset_history_definition_clauses_history` ADD UNIQUE KEY `UK_i1trc62t28cd2ktnwm5mw83n3` (`definitionClauses_id`);
ALTER TABLE `refset_history_definition_clauses_history` ADD INDEX `FKwyybq7oy7spmr11a78ix5b4p` (`RefsetEditHistory_id`);
ALTER TABLE `refset_history_definition_clauses_history` ADD CONSTRAINT `FKo21bax91dosoykcp70vg8709n` FOREIGN KEY (`definitionClauses_id`) REFERENCES `definition_clauses_history` (`id`);
ALTER TABLE `refset_history_definition_clauses_history` ADD CONSTRAINT `FKwyybq7oy7spmr11a78ix5b4p` FOREIGN KEY (`RefsetEditHistory_id`) REFERENCES `refset_history` (`id`);

CREATE TABLE `upgrade_inactive_concepts` (
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

CREATE TABLE `upgrade_replacement_concepts` (
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

CREATE TABLE `upgrade_inactive_concepts_upgrade_replacement_concepts` (
  `UpgradeInactiveConcept_id` varchar(64) NOT NULL,
  `replacementConcepts_id` varchar(64) NOT NULL
);
ALTER TABLE `upgrade_inactive_concepts_upgrade_replacement_concepts` ADD UNIQUE KEY `UK_gvw8w97h67cp3wamn80hv4279` (`replacementConcepts_id`);
ALTER TABLE `upgrade_inactive_concepts_upgrade_replacement_concepts` ADD INDEX `FKr832qlbgsqdr1o9do0qseqm4m` (`UpgradeInactiveConcept_id`);
ALTER TABLE `upgrade_inactive_concepts_upgrade_replacement_concepts` ADD CONSTRAINT `FKju78x6kjym3y2y90mfmon9lge` FOREIGN KEY (`replacementConcepts_id`) REFERENCES `upgrade_replacement_concepts` (`id`);
ALTER TABLE `upgrade_inactive_concepts_upgrade_replacement_concepts` ADD CONSTRAINT `FKr832qlbgsqdr1o9do0qseqm4m` FOREIGN KEY (`UpgradeInactiveConcept_id`) REFERENCES `upgrade_inactive_concepts` (`id`);

CREATE TABLE `teams` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `primaryContactEmail` varchar(255) DEFAULT NULL,
  `organization_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`)
);
ALTER TABLE `teams` ADD INDEX `FK5i52bhmm0nbq6lrbur63anlmc` (`organization_id`);
ALTER TABLE `teams` ADD CONSTRAINT `FK5i52bhmm0nbq6lrbur63anlmc` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`);

CREATE TABLE `team_members` (
  `Team_id` varchar(64) NOT NULL,
  `members` varchar(255) DEFAULT NULL
);
ALTER TABLE `team_members` ADD INDEX `FKdsgfqq0ak2pnhrs36ayw3flp9` (`Team_id`);
ALTER TABLE `team_members` ADD CONSTRAINT `FKdsgfqq0ak2pnhrs36ayw3flp9` FOREIGN KEY (`Team_id`) REFERENCES `teams` (`id`);

CREATE TABLE `team_roles` (
  `Team_id` varchar(64) NOT NULL,
  `roles` varchar(255) DEFAULT NULL
);
ALTER TABLE `team_roles` ADD INDEX `FKhfbftl8bipxam7c60hojoghjl` (`Team_id`);
ALTER TABLE `team_roles` ADD CONSTRAINT `FKhfbftl8bipxam7c60hojoghjl` FOREIGN KEY (`Team_id`) REFERENCES `teams` (`id`);

CREATE TABLE `project_teams` (
  `Project_id` varchar(64) NOT NULL,
  `teams` varchar(255) DEFAULT NULL
);
ALTER TABLE `project_teams` ADD INDEX `FKg82gm3p0ykivqyitgacrko9n4` (`Project_id`);
ALTER TABLE `project_teams` ADD CONSTRAINT `FKg82gm3p0ykivqyitgacrko9n4` FOREIGN KEY (`Project_id`) REFERENCES `projects` (`id`);

CREATE TABLE `discussion_posts` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `message` varchar(4000) NOT NULL,
  `privatePost` bit(1) NOT NULL,
  `visibility` varchar(64) NOT NULL,
  `User_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`)
);
ALTER TABLE `discussion_posts` ADD CONSTRAINT `FKryuftl8bipivqyitga72angpb` FOREIGN KEY (`User_id`) REFERENCES `users` (`id`);

CREATE TABLE `discussion_threads` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `refsetInternalId` varchar(64) NOT NULL,
  `conceptId` varchar(64) DEFAULT NULL,
  `privateThread` bit(1) NOT NULL,
  `visibility` varchar(64) NOT NULL,
  `status` varchar(64) NOT NULL,
  `resolvedBy` varchar(250) DEFAULT NULL,
  `subject` varchar(4000) NOT NULL,
  `type` varchar(20) NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `discussion_threads_discussion_posts` (
  `DiscussionThread_id` varchar(64) NOT NULL,
  `posts_id` varchar(64) NOT NULL
);
ALTER TABLE `discussion_threads_discussion_posts` ADD UNIQUE KEY `UK_tdj8q5rjs9c9ivyuryqc4vq3a` (`posts_id`);
ALTER TABLE `discussion_threads_discussion_posts` ADD INDEX `FK9e03dufefyllv3bea2wcfdj56` (`DiscussionThread_id`);
ALTER TABLE `discussion_threads_discussion_posts` ADD CONSTRAINT `FK9e03dufefyllv3bea2wcfdj56` FOREIGN KEY (`DiscussionThread_id`) REFERENCES `discussion_threads` (`id`);
ALTER TABLE `discussion_threads_discussion_posts` ADD CONSTRAINT `FKjb4q7pwfdqv0kf8wsk6mmi1t0` FOREIGN KEY (`posts_id`) REFERENCES `discussion_posts` (`id`);

CREATE TABLE `audit_entries` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `entityType` varchar(64) DEFAULT NULL,
  `entityId` varchar(64) DEFAULT NULL,
  `details` varchar(4000) DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `artifacts` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `entityType` varchar(64) DEFAULT NULL,
  `entityId` varchar(64) DEFAULT NULL,
  `fileName` varchar(500) DEFAULT NULL,
  `fileType` varchar(10) DEFAULT NULL,
  `description` varchar(4000) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

