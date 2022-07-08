
-- ***** Org/Proj/Refset/Version Info
select c.name as org_name, b.name as project_name, a.name as refset_name, a.refsetId, a.versionDate from refsets a, projects b, organizations c where a.project_id = b.id  and b.organization_id = c.id order by c.name, b.name, a.name, a.versionDate;
-- select c.name as Org_Name, c.id as Org_Id, b.name as Project_Name, b.id as Project_id, a.name as Refset_Name, a.refsetId as Refse_Id, a.versionDate from refsets a, projects b, organizations c where a.project_id = b.id  and b.organization_id = c.id order by c.name, b.name, a.name, a.versionDate;
-- These projects don't have refsets
select b.name as Org_Name, a.name as Project_without_refsets from projects a, organizations b where a.id not in (select project_id from refsets) and b.id = a.organization_id order by b.name, a.name;




q- ***** Org & Users
select id, name, description  from organizations order by name;
select id, name, branch, shortname, defaultLanguageCode from editions order by name;
select id, name, email  from users order by name;




-- ***** Teams (basic & stats)
-- select id, name, description  from teams order by name;
select organization_id, count(*) as Num_teams  from  teams group by organization_id;

-- ***** Projects (basic, stats & basic-join)
-- select id, name, description  from projects order by name;
--select organization_id, count(*) as num_projects from  projects group by organization_id;
 select a.id as org_id, a.name as org, b.name as uat-or-default-project, b.id as proj_id from organizations a, projects b where a.id = b.organization_id and (b.name like '%UAT%' or  b.name like '%Default Project%') order by a.name, b.name;
select a.id as org_id, a.name as org, b.name as non-uat-default-project, b.id as proj_id from organizations a, projects b where a.id = b.organization_id and b.name not like '%UAT%' and b.name not like '%Default Project%' order by a.name;
-- select a.name as org, b.name as project from organizations a, projects b where a.id = b.organization_id;


-- ***** J - Project Teams (basic)
select d.name as Org, b.name as Project, c.name as Team from project_teams a, projects b, teams c, organizations d where b.id = a.project_id and c.id = a.teams and d.id = b.organization_id order by d.name, b.name, c.name;






-- ***** J - Org Members (basic)  
--select * from organization_members;
select organization_id, count(*) as num_members  from  organization_members group by organization_id;
select organization_id, count(*)  as num_Members from  organization_members group by organization_id having count(*) != 5 ;
select b.name org_name, c.name user_name from organization_members a, organizations b, users c where b.id = a.organization_id and c.id = a.user_id order by b.name, c.name;
select c.name user_name, b.name org_name from organization_members a, organizations b, users c where b.id = a.organization_id and c.id = a.user_id order by c.name, b.name;


-- ***** J - Team Members (basic)  
--select * from team_members;
select Team_id, count(*) as num_members from  team_members group by Team_id;
select Team_id, count(*) as num_members from  team_members group by Team_id having count(*) != 2 ;
select b.name as Team_Name, c.name as User_Name from team_members a, teams b, users c where b.id = a.Team_id and c.id = a.members order by b.name, c.name;
select c.name as User_Name, b.name as Team_Name from team_members a, teams b, users c where b.id = a.Team_id and c.id = a.members order by c.name, b.name;


-- ***** J - User Roles  (basic)
--select * from user_roles;
select b.name, a.roles  from user_roles a, users b where b.id = a.user_id order by b.name, a.roles;
select a.roles, b.name from user_roles a, users b where b.id = a.user_id order by a.roles, b.name;




-- **** WCI Refses ****
-- select * from refsets where project_id in (select distinct(project_id) from refsets where name like '%WCI%');
select id, name, refsetId, moduleId, narrative, privateRefset, type, project_id from refsets where project_id in (select distinct(project_id) from refsets where name like '%WCI%') order by name;
select id, name, refsetId, latestPublishedVersion,hasVersionInDevelopment, editBranchId, assignedUser, versionDate, versionNotes, versionStatus, workflowStatus from refsets where project_id in (select distinct(project_id) from refsets where name like '%WCI%') order by name;
select id, name, refsetId, created, modified, modifiedBy from refsets where project_id in (select distinct(project_id) from refsets where name like '%WCI%') order by name;




