ALTER TABLE `projects` ADD COLUMN `lockStatus` BOOLEAN DEFAULT false;
UPDATE `projects` SET `lockStatus` = false;