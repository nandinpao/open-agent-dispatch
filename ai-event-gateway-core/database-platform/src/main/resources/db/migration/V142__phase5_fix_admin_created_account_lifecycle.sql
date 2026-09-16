-- Phase 5 Fix 2: keep account lifecycle independent from password-credential lifecycle.
-- ADMIN_CREATED identities are active accounts. Initial password change is represented by
-- auth_password_credentials.must_change, not by iam_users.status.

update iam_users
set status = 'ACTIVE',
    status_reason = case
      when coalesce(status_reason, '') = '' then 'Normalized ADMIN_CREATED account lifecycle; password setup state is credential-owned'
      else status_reason
    end,
    updated_at = now(),
    updated_by = 'phase5-fix2-migration',
    version = version + 1
where creation_mode = 'ADMIN_CREATED'
  and status = 'PASSWORD_RESET_REQUIRED';
