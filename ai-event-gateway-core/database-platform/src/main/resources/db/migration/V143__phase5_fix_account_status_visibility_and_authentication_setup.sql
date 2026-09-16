-- Phase 5 Fix3: normalize impossible administrator-created activation state.
-- Tenant membership ACTIVE is independent from iam_users.status. ADMIN_CREATED identities
-- must not remain PENDING_ACTIVATION; password setup is represented by the credential lifecycle.

update iam_users
   set status = 'ACTIVE',
       status_reason = case when coalesce(status_reason,'') = ''
                            then 'Normalized administrator-created identity lifecycle'
                            else status_reason end,
       updated_at = now(),
       updated_by = 'phase5-fix3',
       version = version + 1
 where creation_mode = 'ADMIN_CREATED'
   and status = 'PENDING_ACTIVATION';

alter table iam_users drop constraint if exists ck_iam_users_admin_created_not_pending_activation;
alter table iam_users add constraint ck_iam_users_admin_created_not_pending_activation
  check (creation_mode <> 'ADMIN_CREATED' or status <> 'PENDING_ACTIVATION');
