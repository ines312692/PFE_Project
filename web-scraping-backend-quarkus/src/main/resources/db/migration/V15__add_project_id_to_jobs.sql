ALTER TABLE jobs
    ADD COLUMN project_id UUID REFERENCES projects(id) ON DELETE SET NULL;

CREATE INDEX idx_jobs_project_id ON jobs(project_id) WHERE project_id IS NOT NULL;
