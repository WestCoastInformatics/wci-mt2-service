
ALTER TABLE `teams` ADD COLUMN `type` CHAR(1) DEFAULT NULL;
UPDATE `teams` SET `type` = CASE WHEN NAME LIKE 'Administrator(s) for organization%' THEN 'O' ELSE 'P' END;
ALTER TABLE `teams` MODIFY `type` CHAR(1) NOT NULL;