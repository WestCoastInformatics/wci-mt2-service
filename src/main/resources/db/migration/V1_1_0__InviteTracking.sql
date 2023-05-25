drop table ${pre_if_exists} invite_requests ${post_if_exists};

CREATE TABLE `invite_requests` (
  `id` varchar(64) NOT NULL,
  `active` bit(1) NOT NULL,
  `created` datetime(6) NOT NULL,
  `modified` datetime(6) NOT NULL,
  `modifiedBy` varchar(256) NOT NULL,
  `action` varchar(256) NOT NULL,
  `requester` varchar(64) NOT NULL,
  `recipientEmail` varchar(128) NOT NULL,
  `payload` varchar(512) NULL,
  `response` varchar(256) NULL,
  `responseDate` datetime(6) NULL,
  PRIMARY KEY (`id`)
);