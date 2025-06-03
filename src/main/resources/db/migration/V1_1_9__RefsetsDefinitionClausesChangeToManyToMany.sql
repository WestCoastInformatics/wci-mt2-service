-- CREATE TEMP TABLE to hold values
DROP TABLE ${pre_if_exists} refsets_definition_clauses_temp ${post_if_exists};
CREATE TABLE `refsets_definition_clauses_temp` (
  `Refset_id` varchar(64) NOT NULL,
  `definitionClauses_id` varchar(64) NOT NULL
);

-- COPY VALUES FROM EXISTING TABLE TO TEMP TABLE
INSERT INTO `refsets_definition_clauses_temp` SELECT * FROM `refsets_definition_clauses`;

-- DROP AND RE-CREATE UPDATED TABLE
DROP TABLE ${pre_if_exists} refsets_definition_clauses ${post_if_exists};

CREATE TABLE `refsets_definition_clauses` (
  `Refset_id` varchar(64) NOT NULL,
  `definitionClauses_id` varchar(64) NOT NULL,
  PRIMARY KEY (`Refset_id`,`definitionClauses_id`)
);

ALTER TABLE `refsets_definition_clauses` ADD INDEX `FKdowc61fwiejkojh1wj7wk0mn0` (`Refset_id`);
ALTER TABLE `refsets_definition_clauses` ADD CONSTRAINT `FKbxe21a6g8xufs1yh5537pya8p` FOREIGN KEY (`definitionClauses_id`) REFERENCES definition_clauses (`id`);
ALTER TABLE `refsets_definition_clauses` ADD CONSTRAINT `FKdowc61fwiejkojh1wj7wk0mn0` FOREIGN KEY (`Refset_id`) REFERENCES refsets (`id`);

-- COPY FROM TEMP TO NEW TABLE
INSERT INTO `refsets_definition_clauses_temp` SELECT * FROM `refsets_definition_clauses`;

-- DROP TEMP TABLE
DROP TABLE ${pre_if_exists} refsets_definition_clauses_temp ${post_if_exists};