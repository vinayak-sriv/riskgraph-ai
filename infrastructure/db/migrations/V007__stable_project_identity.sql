WITH canonical_projects AS (
    SELECT repository, MIN(id) AS canonical_id
    FROM projects
    GROUP BY repository
), duplicate_projects AS (
    SELECT project.id AS duplicate_id, canonical.canonical_id
    FROM projects project
    JOIN canonical_projects canonical USING (repository)
    WHERE project.id <> canonical.canonical_id
)
UPDATE pull_requests pull_request
SET project_id = duplicate.canonical_id
FROM duplicate_projects duplicate
WHERE pull_request.project_id = duplicate.duplicate_id;

WITH canonical_projects AS (
    SELECT repository, MIN(id) AS canonical_id
    FROM projects
    GROUP BY repository
)
DELETE FROM projects project
USING canonical_projects canonical
WHERE project.repository = canonical.repository
  AND project.id <> canonical.canonical_id;

DROP INDEX IF EXISTS idx_projects_repository;
CREATE UNIQUE INDEX uq_projects_repository ON projects(repository);
