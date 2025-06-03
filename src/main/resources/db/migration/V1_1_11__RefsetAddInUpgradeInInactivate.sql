ALTER TABLE `refsets` ADD COLUMN `inUpgrade` BOOLEAN DEFAULT false;
ALTER TABLE `refsets` ADD COLUMN `inInactivate` BOOLEAN DEFAULT false;
ALTER TABLE `refset_history` ADD COLUMN `inUpgrade` BOOLEAN DEFAULT false;
ALTER TABLE `refset_history` ADD COLUMN `inInactivate` BOOLEAN DEFAULT false;




