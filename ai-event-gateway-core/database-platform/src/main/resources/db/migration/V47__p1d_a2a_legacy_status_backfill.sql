-- P1D compatibility removal: normalize the retired A2A request status.
-- This migration is intentionally data-only and safe to re-run in controlled repair environments.

update a2a_requests
set request_status = 'CANCELLED_CONFIRMED',
    updated_at = greatest(updated_at, now()),
    version = version + 1
where request_status = 'CANCELLED';

update a2a_state_history
set from_status = 'CANCELLED_CONFIRMED'
where from_status = 'CANCELLED';

update a2a_state_history
set to_status = 'CANCELLED_CONFIRMED'
where to_status = 'CANCELLED';
