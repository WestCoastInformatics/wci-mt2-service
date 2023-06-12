ALTER TABLE `editions` ADD COLUMN `maintainerType` VARCHAR(255) DEFAULT NULL;
UPDATE `editions` SET `maintainerType` = 'something';
ALTER TABLE `editions` MODIFY `maintainerType` VARCHAR(255) NOT NULL;






