;
-- ***** Orgs
-- 1
select name, description  from organizations order by name;


-- ***** Editions
-- 2
select name, branch, shortname, defaultLanguageCode from editions order by name;


-- *** Org Modules ***
-- 3
select b.name, a.modules from edition_modules a, editions b where b.id = a.edition_id order by b.name, a.modules;


-- *** All Refsets Summary ***
-- 4
select name, refsetId, count(*) from refsets group by name, refsetId order by name;


-- *** All Refsets Details ***
-- 5
select refsetId, name, versionDate from refsets order by name, refsetId,  versionDate;


-- ***** Proj/Refset/Version Info
-- 6
select c.name as edition_name, b.name as project_name, a.name as refset_name, a.versionDate, a.refsetId from refsets a, projects b, editions c where a.project_id = b.id  and b.edition_id = c.id order by c.name, b.name, a.name, a.versionDate;


-- ***** Org/Teams (basic)
-- 7
select a.name as Organization, b.name as Team from teams b, organizations a where a.id = b.organization_id order by a.name, b.name;


-- ***** organization_members
-- 8
select a.name, b.name from users a, organizations b, organization_members c where a.id = c.user_id and b.id = c.organization_id order by a.name, b.name; 


-- ***** team members
-- 9 
select a.name, b.name from teams a, users b, team_members c where a.id = c.team_id and b.id = c.members order by a.name, b.name;


-- **** team roles
-- 10 
select b.name, a.roles from team_roles a, teams b where b.id = a.team_id order by b.name, a.roles;


-- ***** Project/Teams (basic)
-- 11
select d.name as Edition, b.name as Project, c.name as Team from project_teams a, projects b, teams c, editions d where b.id = a.project_id and c.id = a.teams and d.id = b.edition_id order by d.name, b.name, c.name;


-- ***** user roles
-- 12
select a.name, b.roles from users a, user_roles b where a.id = b.user_id order by a.name, b.roles;






-- drop schema rt2; create schema rt2; use rt2;

