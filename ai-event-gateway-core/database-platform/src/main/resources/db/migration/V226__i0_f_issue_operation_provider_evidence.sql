-- I0-F: structured provider operation evidence for TaskIssueLink read models.
-- Non-destructive additive migration. Provider permission remains external authority.
alter table task_issue_links add column if not exists provider_failure_code varchar(128);
alter table task_issue_links add column if not exists provider_status_code integer;
alter table task_issue_links add column if not exists provider_health_impact varchar(32);

create index if not exists idx_task_issue_links_provider_failure
  on task_issue_links(tenant_id, provider_failure_code, updated_at desc)
  where provider_failure_code is not null;
