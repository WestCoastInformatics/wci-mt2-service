
-- Refset to description
select terminologyId, description from refsets order by terminologyId and workflowStatus = 'PUBLISHED';

-- RTT Project Info
select b.id as rtt_project_id, a.terminologyId as refset_sctid, b.name as project_name, b.description as project_desc from refsets a, projects b where a.project_id = b.id and a.workflowStatus = 'PUBLISHED';

-- Refset Tags
select distinct terminologyId, domain from refsets where not domain is null and domain != ""  and workflowStatus = 'PUBLISHED' order by terminologyId;

-- Refset to clauses
select r.terminologyId, d.negated, d.value from refsets r, refsets_definition_clauses rd, definition_clauses d where r.id=rd.refsets_id and rd.definitionClauses_id=d.id and r.workflowStatus = 'PUBLISHED';


-- Refset RTT to SctId
select id, terminologyId from refsets where workflowStatus = 'PUBLISHED';


-- All Refsets, projects, and clauses
select * from refsets where workflowStatus = 'PUBLISHED';
select * from projects;
select * from definition_clauses;
