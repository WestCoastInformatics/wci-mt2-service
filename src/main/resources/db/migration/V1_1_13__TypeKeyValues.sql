drop table ${pre_if_exists} type_key_values ${post_if_exists};

CREATE TABLE `type_key_values` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `typeField` varchar(1000) DEFAULT NULL,
  `keyField` varchar(4000) DEFAULT NULL,
  `valueField` varchar(4000) DEFAULT NULL,
  PRIMARY KEY (`id`)
);