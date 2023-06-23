ALTER TABLE `organizations` DROP COLUMN `codeSystemType`;
ALTER TABLE `organizations` ADD COLUMN `affiliate` bit(1) DEFAULT false;
UPDATE `organizations` SET `affiliate` = false;






