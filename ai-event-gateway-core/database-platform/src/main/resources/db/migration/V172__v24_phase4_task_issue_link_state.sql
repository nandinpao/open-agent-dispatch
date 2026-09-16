-- v24 Phase 4: TaskIssueLink state is not Issue policy state and not provider sync state.
alter table task_issue_links add column if not exists link_state varchar(32);
-- The existing phase0g_task_issue_sync_guard() applies to every UPDATE and requires
-- resource_version to advance exactly once. Migration backfills must obey the same
-- optimistic-locking contract rather than disabling the runtime guard.
update task_issue_links
set link_state=case
      when coalesce(nullif(btrim(external_issue_id),''),nullif(btrim(external_issue_key),''),nullif(btrim(external_issue_url),'')) is not null
      then 'EXTERNAL_CONFIRMED'
      else 'INTERNAL_LINK'
    end,
    resource_version=resource_version+1
where link_state is null or btrim(link_state)='';
alter table task_issue_links alter column link_state set default 'INTERNAL_LINK';
alter table task_issue_links alter column link_state set not null;
do $$ begin
  if not exists(select 1 from pg_constraint where conname='ck_task_issue_link_state') then
    alter table task_issue_links add constraint ck_task_issue_link_state check(link_state in ('INTERNAL_LINK','EXTERNAL_CONFIRMED'));
  end if;
end $$;
create index if not exists idx_task_issue_links_link_state on task_issue_links(tenant_id,task_id,link_state,updated_at desc);
comment on column task_issue_links.link_state is 'Internal Task-to-Issue link materialization state only. Policy and provider sync state are separate authorities.';
