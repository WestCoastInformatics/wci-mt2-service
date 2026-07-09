CREATE TABLE `mapping_workflow` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `sourceConceptCode` varchar(255) NOT NULL,
  `workflowStatus` varchar(256) NOT NULL,
  `assignedUser` varchar(256) DEFAULT NULL,
  `assignedAt` datetime(6) DEFAULT NULL,
  `leaseExpiresAt` datetime(6) DEFAULT NULL,
  `specialistSlot` int NOT NULL DEFAULT 1,
  `mapSet_id` varchar(64) NOT NULL,
  `map_project_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_mapping_workflow_mapset_concept_slot` (`mapSet_id`, `sourceConceptCode`, `specialistSlot`),
  KEY `IDX_mapping_workflow_mapset_status` (`mapSet_id`, `workflowStatus`),
  KEY `IDX_mapping_workflow_assigned_user_status` (`assignedUser`, `workflowStatus`),
  CONSTRAINT `FK_mapping_workflow_mapset` FOREIGN KEY (`mapSet_id`) REFERENCES `map_sets` (`id`),
  CONSTRAINT `FK_mapping_workflow_mapproject` FOREIGN KEY (`map_project_id`) REFERENCES `map_projects` (`id`)
);

CREATE TABLE `mapping_workflow_history` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `userName` varchar(256) NOT NULL,
  `workflowStatus` varchar(256) DEFAULT NULL,
  `workflowAction` varchar(256) DEFAULT NULL,
  `notes` longtext,
  `mapping_workflow_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_mapping_workflow_history_workflow` (`mapping_workflow_id`),
  CONSTRAINT `FK_mapping_workflow_history_workflow` FOREIGN KEY (`mapping_workflow_id`) REFERENCES `mapping_workflow` (`id`)
);
