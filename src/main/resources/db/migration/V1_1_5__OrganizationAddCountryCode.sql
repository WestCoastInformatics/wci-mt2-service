ALTER TABLE `organizations` ADD COLUMN `countryCode` CHAR(4) DEFAULT false;
UPDATE `organizations` SET `countryCode` = 'N/A';






