-- Map notes are scoped by map product (refSetCode), not map set version UUID.
DROP TABLE ${pre_if_exists} map_notes ${post_if_exists};

CREATE TABLE `map_notes` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `note` varchar(4000) NOT NULL,
  `timestamp` datetime(6) NOT NULL,
  `sourceConceptCode` varchar(255) NOT NULL,
  `refSetCode` varchar(255) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_map_notes_refset_concept` (`refSetCode`, `sourceConceptCode`),
  KEY `FK_map_notes_user` (`user_id`),
  CONSTRAINT `FK_map_notes_user` FOREIGN KEY (`user_id`) REFERENCES `map_users` (`id`)
);
