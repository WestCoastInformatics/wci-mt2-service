ALTER TABLE `refsets` ADD COLUMN `baseContentVersion`  CHAR(255) DEFAULT false;
ALTER TABLE `refsets` ADD COLUMN `internationalContentVersion`  CHAR(255) DEFAULT false;
ALTER TABLE `refsets` DROP COLUMN `dependencyModuleName`;
ALTER TABLE `refsets` DROP COLUMN `baseVersionDate`;