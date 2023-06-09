
ALTER TABLE `editions` ADD COLUMN `modifierType` VARCHAR(255) DEFAULT NULL;
UPDATE `editions` SET `modifierType` = 'P'  ;
ALTER TABLE `editions` MODIFY `modifierType` VARCHAR(255) NOT NULL;