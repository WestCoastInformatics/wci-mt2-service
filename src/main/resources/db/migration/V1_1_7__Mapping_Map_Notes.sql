-- Map notes are keyed by map set + source concept (Snowstorm mappings are not DB rows).
DROP TABLE ${pre_if_exists} mappings_map_notes ${post_if_exists};
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
  `user_id` varchar(64) NOT NULL,
  `mapSet_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_map_notes_mapset_concept` (`mapSet_id`, `sourceConceptCode`),
  KEY `FK_map_notes_user` (`user_id`),
  CONSTRAINT `FK_map_notes_user` FOREIGN KEY (`user_id`) REFERENCES `map_users` (`id`),
  CONSTRAINT `FK_map_notes_mapset` FOREIGN KEY (`mapSet_id`) REFERENCES `map_sets` (`id`)
);
