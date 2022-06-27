-- ***** Org & Users
select id, name, description  from organizations order by name;
select id, name, branch, shortname, defaultLanguageCode from editions order by name;
select id, name, email  from users;




-- ***** Teams (basic & stats)
-- select id, name, description  from teams order by name;
select organization_id, count(*)  from  teams group by organization_id;

-- ***** Projects (basic, stats & basic-join)
-- select id, name, description  from projects order by name;
select organization_id, count(*)  from  projects group by organization_id;
select a.name as org, b.name as project from organizations a, projects b where a.id = b.organization_id and b.name not like '%UAT%' and b.name not like '%Default Project%' order by a.name;
-- select a.name as org, b.name as project from organizations a, projects b where a.id = b.organization_id;


-- ***** J - Project Teams (basic)
select b.name as Project, a.teams from project_teams a, projects b where b.id = a.project_id;





-- ***** J - Org Members (basic)  
--select * from organization_members;
select b.name, c.name from organization_members a, organizations b, users c where b.id = a.organization_id and c.id = a.user_id order by b.name;


-- ***** J - Team Members (basic)  
select * from team_members;
select b.name, a.members from team_members a, teams b where b.id = a.Team_id order by b.name;


-- ***** J - User Roles  (basic)
--select * from user_roles;
select b.name, a.roles  from user_roles a, users b where b.id = a.user_id order by b.name;
-- 





