'use client';

import Link from 'next/link';
import { AuditReasonSelector, FieldLabel, PopupMultiSelectField, SearchField, SearchSelectField, SelectField, WizardProgress } from '../shared/beginnerUi';
import { STEPS, useAddPersonWizardController, type AddPersonWizardProps, type WizardState } from './useAddPersonWizardController';
import { generateTemporaryPassword } from '@/lib/iam/temporaryPassword';
import { AccessPreviewPanel, ReviewCard, AddPersonWizardHeader } from './AddPersonWizardReview';
export function AddPersonWizard(props: Readonly<AddPersonWizardProps>) {
  const { tenantId, tenantName, onCancel } = props;
  const controller = useAddPersonWizardController(props);
  const {
    step, state, error, submitting, result, accountSource, existingSearch, existingCandidates, existingUserId, searchingExisting, accessPreview, accessPreviewLoading, accessPreviewError,
    selectedExistingPerson, existingNeedsSetupDelivery, existingNeedsMfaOnly, departmentOptions, groupOptions, roleOptions, additionalDepartmentOptions, scopeOptions, selectedRole, selectedScope, selectedPrimaryDepartment, selectedGroups,
    setAccountSource, setExistingUserId, setExistingCandidates, setExistingSearch, setError, update, goNext, goBack, searchExistingPeople, selectExistingPerson, submit,
  } = controller;
  return (
    <section className="rounded-3xl border border-blue-200 bg-white p-5 shadow-xl" aria-labelledby="add-person-title">
      <AddPersonWizardHeader tenantName={tenantName} onClose={onCancel} />

      <div className="mt-5">
        {!result ? <WizardProgress steps={STEPS} currentIndex={step} /> : null}
      </div>

      {error ? <div role="alert" className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-semibold text-rose-800">{error}</div> : null}

      <div className="mt-5 rounded-3xl border border-slate-200 bg-slate-50 p-5">
        {!result && step === 0 ? (
          <div className="space-y-4">
            <FieldLabel htmlFor="person-account-source" label="Person source" help="Create a new sign-in identity, or add an identity that already exists in OpenDispatch." required>
              <SelectField
                id="person-account-source"
                value={accountSource}
                onChange={(value) => {
                  setAccountSource(value as 'NEW' | 'EXISTING');
                  setExistingUserId('');
                  setExistingCandidates([]);
                  setError('');
                }}
                options={[
                  { value: 'NEW', label: 'Create a new person', description: 'Use this when the person does not already have an OpenDispatch identity.' },
                  { value: 'EXISTING', label: 'Add an existing person to this workspace', description: 'Use this when the username or email already exists elsewhere in OpenDispatch.' },
                ]}
                required
              />
            </FieldLabel>

            {accountSource === 'EXISTING' ? (
              <div className="space-y-4 rounded-2xl border border-blue-200 bg-blue-50 p-4">
                <div>
                  <h3 className="font-black text-blue-950">Find an existing person</h3>
                  <p className="mt-1 text-sm leading-6 text-blue-900">Search by name, username or email. People already in this workspace are excluded automatically; a person previously removed from this workspace can be re-admitted here without creating a new sign-in identity.</p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <div className="min-w-0 flex-1"><SearchField id="existing-person-search" value={existingSearch} onChange={setExistingSearch} placeholder="Name, username or email" /></div>
                  <button type="button" disabled={searchingExisting || existingSearch.trim().length < 2} onClick={() => { void searchExistingPeople(); }} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300">{searchingExisting ? 'Searching…' : 'Search'}</button>
                </div>
                <FieldLabel htmlFor="existing-person-select" label="Existing person" required>
                  <SearchSelectField
                    id="existing-person-select"
                    value={existingUserId}
                    onChange={selectExistingPerson}
                    options={existingCandidates.map((person) => ({ value: person.userId, label: person.displayName, description: `${person.username}${person.email ? ` · ${person.email}` : ''} · ${person.status.replaceAll('_', ' ')}` }))}
                    placeholder={existingCandidates.length ? 'Select a person' : 'Search first'}
                    disabled={existingCandidates.length === 0}
                    required
                  />
                </FieldLabel>
                {existingUserId ? <p className="text-xs font-semibold text-blue-900">The global identity is reused. Workspace membership, Department/Group placement and selected responsibilities are re-established; existing password/MFA state is preserved unless sign-in setup is still incomplete.</p> : null}
              </div>
            ) : (
              <div className="grid gap-4 lg:grid-cols-2">
                <FieldLabel htmlFor="person-display-name" label="Display name" help="The name shown throughout OpenDispatch." required>
                  <input id="person-display-name" value={state.displayName} onChange={(event) => update('displayName', event.target.value)} className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm" autoComplete="name" />
                </FieldLabel>
                <FieldLabel htmlFor="person-username" label="Sign-in name" help="Use the organization’s normal account naming convention." required>
                  <input id="person-username" value={state.username} onChange={(event) => update('username', event.target.value)} className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm" autoComplete="username" />
                </FieldLabel>
                <FieldLabel htmlFor="person-email" label="Email address" help="Required only when Email delivery is selected.">
                  <input id="person-email" type="email" value={state.email} onChange={(event) => update('email', event.target.value)} className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm" autoComplete="email" />
                </FieldLabel>
              </div>
            )}
          </div>
        ) : null}

        {!result && step === 1 ? (
          <div className="grid gap-4 lg:grid-cols-2">
            <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 lg:col-span-2">
              <p className="text-xs font-black uppercase tracking-wide text-blue-800">Workspace</p>
              <p className="mt-1 text-lg font-black text-blue-950">{tenantName}</p>
              <p className="mt-2 text-sm text-blue-900">This person is being added to the current workspace. The workspace is already known from your current administration context; there is no extra workspace choice or internal ID to enter.</p>
            </div>
            <FieldLabel htmlFor="person-employee-id" label="Employee or contractor number" help="Optional business identifier.">
              <input id="person-employee-id" value={state.employeeId} onChange={(event) => update('employeeId', event.target.value)} className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm" />
            </FieldLabel>
            <FieldLabel htmlFor="person-membership-duration" label="Workspace membership duration" required>
              <SelectField
                id="person-membership-duration"
                value={state.membershipDuration}
                onChange={(value) => update('membershipDuration', value)}
                options={[
                  { value: 'PERMANENT', label: 'No planned expiry' },
                  { value: '30', label: '30 days' },
                  { value: '90', label: '90 days' },
                  { value: '365', label: '1 year' },
                  { value: 'CUSTOM', label: 'Choose a date' },
                ]}
                required
              />
            </FieldLabel>
            {state.membershipDuration === 'CUSTOM' ? (
              <FieldLabel htmlFor="person-membership-expiry" label="Membership expiry date" required>
                <input id="person-membership-expiry" type="date" value={state.membershipCustomDate} onChange={(event) => update('membershipCustomDate', event.target.value)} className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm" />
              </FieldLabel>
            ) : null}
          </div>
        ) : null}

        {!result && step === 1 ? (
          <div className="space-y-5">
            <FieldLabel htmlFor="person-primary-department" label="Primary Department" help="Choose the person’s main reporting Department. This does not grant access by itself.">
              <SelectField
                id="person-primary-department"
                value={state.primaryDepartmentId}
                onChange={(value) => {
                  update('primaryDepartmentId', value);
                  update('additionalDepartmentIds', state.additionalDepartmentIds.filter((item) => item !== value));
                  if (state.scopeType === 'DEPARTMENT' && !state.scopeId) update('scopeId', value);
                }}
                options={departmentOptions}
                placeholder={departmentOptions.length ? 'Select a primary Department' : 'No active Departments are available'}
              />
            </FieldLabel>
            <p className="text-xs leading-5 text-slate-500">Need to create or reorganize a Department? <Link href={`/admin/tenants/${encodeURIComponent(tenantId)}/organization`} className="font-black text-blue-700 hover:underline">Open Organization</Link>. Your onboarding choices remain on this screen until navigation.</p>
            <div>
              <h3 className="text-sm font-black text-slate-800">Additional Departments</h3>
              <p className="mt-1 text-xs leading-5 text-slate-500">Use only when the person formally belongs to more than one Department.</p>
              <div className="mt-3">
                <PopupMultiSelectField
                  id="person-additional-departments"
                  options={additionalDepartmentOptions}
                  values={state.additionalDepartmentIds}
                  onChange={(values) => update('additionalDepartmentIds', values)}
                  placeholder="Select additional Departments"
                  emptyMessage="No other active Departments are available."
                />
              </div>
            </div>
          </div>
        ) : null}

        {!result && step === 1 ? (
          <div>
            <h3 className="text-sm font-black text-slate-800">Working Groups</h3>
            <p className="mt-1 text-xs leading-5 text-slate-500">Select project, operational or collaboration Groups. Group membership does not grant permission unless the Group has a responsibility assignment.</p>
            <div className="mt-3">
              <PopupMultiSelectField
                id="person-groups"
                options={groupOptions}
                values={state.groupIds}
                onChange={(values) => update('groupIds', values)}
                placeholder="Select Groups (optional)"
                emptyMessage="No active Groups are available."
              />
            </div>
          </div>
        ) : null}

        {!result && step === 2 ? (
          <div className="space-y-5">
            <FieldLabel htmlFor="person-application-access-mode" label="Application access" help="Assign an initial business responsibility now. Choose no access only when the person is intentionally being staged before authorization." required>
              <SelectField
                id="person-application-access-mode"
                value={state.applicationAccessMode}
                onChange={(value) => {
                  const mode = value as WizardState['applicationAccessMode'];
                  update('applicationAccessMode', mode);
                  if (mode === 'DEFER') {
                    update('roleId', '');
                    update('scopeType', 'TENANT');
                    update('scopeId', tenantId);
                  }
                  setError('');
                }}
                options={[
                  { value: 'ASSIGN', label: 'Assign an initial responsibility', description: 'Recommended. The person receives governed workspace access immediately after sign-in setup.' },
                  { value: 'DEFER', label: 'No application access yet', description: 'The identity can complete password and MFA setup, but business Navigator access remains unavailable until an administrator assigns a responsibility.' },
                ]}
                required
              />
            </FieldLabel>

            {state.applicationAccessMode === 'DEFER' ? (
              <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm leading-6 text-amber-950">
                <b className="block">No business workspace access will be granted.</b>
                The person may complete sign-in, password change and MFA, but will land on My Account only. No operational or administration Navigator item will appear until a Responsibility is assigned.
              </div>
            ) : (
              <div className="grid gap-4 lg:grid-cols-2">
                <FieldLabel htmlFor="person-role" label="Initial responsibility" help="Required for normal interactive onboarding. Choose a business responsibility instead of entering permission codes." required>
                  <SelectField
                    id="person-role"
                    value={state.roleId}
                    onChange={(value) => update('roleId', value)}
                    options={roleOptions}
                    placeholder="Select an initial responsibility"
                    required
                  />
                </FieldLabel>
                <FieldLabel htmlFor="person-scope-type" label="Where it applies" help="The selected scope limits the responsibility.">
                  <SelectField
                    id="person-scope-type"
                    value={state.scopeType}
                    disabled={!state.roleId}
                    onChange={(value) => {
                      const scopeType = value as WizardState['scopeType'];
                      update('scopeType', scopeType);
                      update('scopeId', scopeType === 'TENANT' ? tenantId : '');
                    }}
                    options={[
                      { value: 'TENANT', label: `Entire workspace — ${tenantName}` },
                      { value: 'DEPARTMENT', label: 'A Department' },
                      { value: 'GROUP', label: 'A Group' },
                    ]}
                  />
                </FieldLabel>
                {state.roleId ? (
                  <FieldLabel htmlFor="person-scope" label="Specific scope" required>
                    <SelectField
                      id="person-scope"
                      value={state.scopeId}
                      onChange={(value) => update('scopeId', value)}
                      options={scopeOptions}
                      placeholder="Select where the responsibility applies"
                      required
                    />
                  </FieldLabel>
                ) : null}
                <FieldLabel htmlFor="person-access-duration" label="Access duration">
                  <SelectField
                    id="person-access-duration"
                    value={state.accessDuration}
                    disabled={!state.roleId}
                    onChange={(value) => update('accessDuration', value)}
                    options={[
                      { value: 'PERMANENT', label: 'No planned expiry' },
                      { value: '30', label: '30 days' },
                      { value: '90', label: '90 days' },
                      { value: 'CUSTOM', label: 'Choose a date' },
                    ]}
                  />
                </FieldLabel>
                {state.roleId && state.accessDuration === 'CUSTOM' ? (
                  <FieldLabel htmlFor="person-access-expiry" label="Access expiry date" required>
                    <input id="person-access-expiry" type="date" value={state.accessCustomDate} onChange={(event) => update('accessCustomDate', event.target.value)} className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm" />
                  </FieldLabel>
                ) : null}
              </div>
            )}

            {selectedRole && state.applicationAccessMode === 'ASSIGN' ? (
              <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4">
                <p className="font-black text-blue-950">{selectedRole.roleName}</p>
                <p className="mt-1 text-sm leading-6 text-blue-900">{selectedRole.description || 'This responsibility is governed by its Role Template.'}</p>
                <p className="mt-2 text-xs font-semibold text-blue-800">Applies to: {selectedScope}</p>
              </div>
            ) : null}

            {state.applicationAccessMode === 'ASSIGN' && state.roleId ? (
              <AccessPreviewPanel preview={accessPreview} loading={accessPreviewLoading} error={accessPreviewError} />
            ) : null}
          </div>
        ) : null}

        {!result && step === 3 ? (
          <div className="space-y-5">
            {accountSource === 'NEW' ? (
              <FieldLabel htmlFor="person-account-mode" label="How should this person start signing in?" help="Invitation is recommended for normal employees. Temporary password is available for administrator-assisted setup." required>
                <SelectField
                  id="person-account-mode"
                  value={state.creationMode}
                  onChange={(value) => update('creationMode', value as WizardState['creationMode'])}
                  options={[
                    { value: 'INVITATION', label: 'Send an invitation (recommended)', description: 'The person creates their password from a one-time setup link, then completes MFA.' },
                    { value: 'ADMIN_CREATED', label: 'Administrator-assisted temporary password', description: 'Use when email invitation is not available. First sign-in must change the password, then complete MFA.' },
                  ]}
                  required
                />
              </FieldLabel>
            ) : null}
            <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4">
              <p className="text-xs font-black uppercase tracking-wide text-blue-800">Authentication method</p>
              <p className="mt-1 text-lg font-black text-blue-950">Local OpenDispatch account</p>
              <p className="mt-2 text-sm leading-6 text-blue-900">Password credentials belong to the global identity, not to one workspace. Existing identities keep their current password and MFA state when they are re-admitted to this workspace.</p>
            </div>

            {accountSource === 'NEW' && state.creationMode === 'ADMIN_CREATED' ? (
              <>
                <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-950">
                  <b className="block">Administrator-provisioned temporary password</b>
                  OpenDispatch stores only the password hash. The temporary password cannot be retrieved after this step. At first successful sign-in the account receives a restricted session that can only change the password or sign out.
                </div>
                <div className="grid gap-4 lg:grid-cols-2">
                  <FieldLabel htmlFor="person-initial-password" label="Initial password" help="At least 14 characters. Use Generate for a strong temporary value." required>
                    <div className="flex gap-2">
                      <input id="person-initial-password" type="password" autoComplete="new-password" value={state.initialPassword} onChange={(event) => update('initialPassword', event.target.value)} className="min-w-0 flex-1 rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm" />
                      <button type="button" onClick={() => { const generated = generateTemporaryPassword(); update('initialPassword', generated); update('confirmInitialPassword', generated); setError(''); }} className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-xs font-black text-slate-800">Generate</button>
                    </div>
                  </FieldLabel>
                  <FieldLabel htmlFor="person-confirm-initial-password" label="Confirm initial password" required>
                    <input id="person-confirm-initial-password" type="password" autoComplete="new-password" value={state.confirmInitialPassword} onChange={(event) => update('confirmInitialPassword', event.target.value)} className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm" />
                  </FieldLabel>
                </div>
                {state.initialPassword ? <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950"><span>Copy the temporary password before creating the Person. It will not be returned by the API.</span><button type="button" onClick={() => { void navigator.clipboard.writeText(state.initialPassword); }} className="rounded-lg border border-amber-400 bg-white px-3 py-2 text-xs font-black">Copy password</button></div> : null}
                <div className="rounded-2xl border border-slate-200 bg-white p-4">
                  <p className="text-xs font-black uppercase tracking-wide text-slate-500">First sign-in sequence</p>
                  <ol className="mt-2 space-y-2 text-sm text-slate-700">
                    <li>1. Sign in with the temporary password.</li>
                    <li>2. Change the password in the restricted password-change session.</li>
                    <li>3. Sign in with the new password and enroll TOTP MFA.</li>
                    <li>4. Sign in normally; Navigator, Pages, Actions and data scope are resolved from RBAC entitlements.</li>
                  </ol>
                </div>
              </>
            ) : accountSource === 'EXISTING' && !existingNeedsSetupDelivery ? (
              <div className={`rounded-2xl border p-4 text-sm leading-6 ${existingNeedsMfaOnly ? 'border-amber-200 bg-amber-50 text-amber-950' : 'border-emerald-200 bg-emerald-50 text-emerald-900'}`}>
                {existingNeedsMfaOnly
                  ? 'The existing password is retained. This identity still needs MFA enrollment; no password credential is replaced during workspace re-admission.'
                  : 'Existing password and MFA enrollment are retained. Re-admitting the Person never resets global sign-in credentials.'}
              </div>
            ) : (
              <>
                {accountSource === 'EXISTING' ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950">This global identity exists, but sign-in activation is incomplete ({selectedExistingPerson?.status.replaceAll('_', ' ')}). Re-admission preserves the identity and reissues only the still-required setup credential.</div> : null}
                <FieldLabel htmlFor="person-delivery-method" label="Account setup delivery" help="Choose how the one-time activation/setup credential reaches the person." required>
                  <SelectField id="person-delivery-method" value={state.activationDeliveryMethod} onChange={(value) => update('activationDeliveryMethod', value as WizardState['activationDeliveryMethod'])} options={[
                    { value: 'MANUAL', label: 'Manual secure handoff', description: 'Show the setup link once for delivery through a trusted company channel.' },
                    { value: 'EMAIL', label: 'Email', description: 'Send the one-time link through the configured SMTP service.' },
                  ]} required />
                </FieldLabel>
                {state.activationDeliveryMethod === 'EMAIL' ? <div className={`rounded-2xl border p-4 text-sm ${state.email.trim() ? 'border-emerald-200 bg-emerald-50 text-emerald-900' : 'border-amber-200 bg-amber-50 text-amber-950'}`}>{state.email.trim() ? `Setup will be sent to ${state.email.trim()}.` : 'Email delivery requires an email address. Go back to Person or choose manual secure handoff.'}</div> : null}
              </>
            )}
          </div>
        ) : null}

        {!result && step === 4 ? (
          <div className="space-y-5">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <ReviewCard label="Person" value={`${state.displayName} (${state.username})`} />
              <ReviewCard label="Workspace" value={tenantName} />
              <ReviewCard label="Department" value={selectedPrimaryDepartment?.name || 'Not assigned'} />
              <ReviewCard label="Responsibility" value={state.applicationAccessMode === 'DEFER' ? 'No application access yet' : selectedRole ? `${selectedRole.roleName} — ${selectedScope}` : 'Responsibility required'} />
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-4">
              <h3 className="font-black text-slate-950">Organization summary</h3>
              <p className="mt-2 text-sm text-slate-600">
                Groups: {selectedGroups.length ? selectedGroups.map((group) => group.name).join(', ') : 'None'}
              </p>
              <p className="mt-1 text-sm text-slate-600">
                Authentication: {accountSource === 'EXISTING'
                  ? existingNeedsSetupDelivery
                    ? `Reuse the existing sign-in identity and reissue incomplete setup via ${state.activationDeliveryMethod === 'EMAIL' ? `email to ${state.email}` : 'manual secure handoff'}. Existing identity history and Enterprise links are retained.`
                    : 'Keep the existing sign-in identity and current password/MFA state. Enterprise links are workspace-specific and remain intact.'
                  : state.creationMode === 'ADMIN_CREATED'
                    ? 'Temporary password configured · first sign-in must change it · TOTP MFA enrollment follows before normal access.'
                    : `Invitation setup · ${state.activationDeliveryMethod === 'EMAIL' ? `Email to ${state.email}` : 'Manual secure handoff'} · password creation and MFA enrollment are completed by the person.`}
              </p>
            </div>
            {state.applicationAccessMode === 'DEFER' ? (
              <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm leading-6 text-amber-950"><b className="block">Access readiness: NO RESPONSIBILITY</b>This person will have My Account only after sign-in setup. Assign a Responsibility before expecting business Navigator access.</div>
            ) : (
              <AccessPreviewPanel preview={accessPreview} loading={accessPreviewLoading} error={accessPreviewError} title="Expected workspace access after sign-in" />
            )}
            <AuditReasonSelector idPrefix="person-onboarding" value={state.auditReason} onChange={(value) => update('auditReason', value)} />
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-900">
              {accountSource === 'EXISTING'
                ? existingNeedsSetupDelivery
                  ? 'OpenDispatch will re-admit the existing global identity and reissue the still-required activation/password setup in the same governed onboarding transaction. No duplicate global identity is created.'
                  : 'OpenDispatch will add the existing identity to this workspace, organization memberships and responsibility assignments in one governed transaction. Existing sign-in credentials, password and MFA enrollment are preserved.'
                : state.creationMode === 'ADMIN_CREATED'
                  ? 'OpenDispatch will commit the identity, temporary password hash, workspace membership, organization memberships and Responsibility assignment in one governed operation. The temporary password is never returned or persisted in plaintext.'
                  : 'OpenDispatch will commit the identity, workspace membership, organization memberships, Responsibility assignment and invitation setup request in one governed transaction.'}
            </div>
          </div>
        ) : null}

        {result ? (
          <div className="space-y-5 text-center">
            <div className="mx-auto flex size-14 items-center justify-center rounded-full bg-emerald-100 text-2xl font-black text-emerald-800">✓</div>
            <div>
              <h3 className="text-xl font-black text-slate-950">{result?.user.displayName ?? state.displayName} was added</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                {result?.temporaryPasswordConfigured
                  ? 'The temporary password is configured. The person can sign in now, must change that password immediately, and will then be guided through TOTP MFA enrollment before normal access.'
                  : result?.setupIssued
                  ? result.setupDeliveryStatus === 'DELIVERED'
                    ? `The ${accountSource === 'EXISTING' ? 'existing identity was re-admitted and its fresh ' : ''}one-time setup credential was delivered using ${result.setupDeliveryMethod.replaceAll('_', ' ').toLowerCase()}${result.setupDeliveryReference ? ` (${result.setupDeliveryReference})` : ''}. Remaining password/MFA actions must be completed by the person.`
                    : result.setupDeliveryStatus === 'QUEUED'
                      ? `${accountSource === 'EXISTING' ? 'The existing identity was re-admitted and' : 'The account was created and'} the setup email is queued for delivery. Refresh the Person Security view to confirm delivery.`
                      : `${accountSource === 'EXISTING' ? 'The existing identity was re-admitted' : 'The account was created'}, but setup delivery is ${result.setupDeliveryStatus.toLowerCase()}. ${result.setupFailureCode || 'Review the delivery configuration before handoff.'}`
                  : accountSource === 'EXISTING'
                    ? 'The existing identity was re-admitted to this workspace. Existing sign-in credentials and MFA state were kept unchanged.'
                    : 'The account was created, but no setup credential was issued. Open the person record and issue one before handover.'}
              </p>
            </div>
            {result?.setupActionUrl ? (
              <div className="mx-auto max-w-xl rounded-2xl border border-amber-300 bg-amber-50 p-4 text-left">
                <p className="text-xs font-black uppercase tracking-wide text-amber-800">One-time manual setup link</p>
                <p className="mt-2 break-all rounded-xl bg-white p-3 font-mono text-xs text-amber-950">{result.setupActionUrl}</p>
                <p className="mt-2 text-xs leading-5 text-amber-900">Copy this link now and deliver it through a trusted company channel. OpenDispatch does not persist this URL and it will not be shown again.</p>
                <button type="button" onClick={() => { void navigator.clipboard.writeText(result.setupActionUrl); }} className="mt-3 rounded-lg bg-amber-900 px-3 py-2 text-xs font-black text-white">Copy setup link</button>
              </div>
            ) : null}
            {result ? <div className="mx-auto grid max-w-4xl gap-3 text-left sm:grid-cols-4"><div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><p className="text-xs font-black uppercase tracking-wide text-slate-500">Identity account status</p><p className="mt-2 text-sm font-black text-slate-950">{result.user.status.replaceAll('_',' ')}</p></div><div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><p className="text-xs font-black uppercase tracking-wide text-slate-500">Workspace membership status</p><p className="mt-2 text-sm font-black text-slate-950">{result.tenantMembership.status.replaceAll('_',' ')}</p></div><div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><p className="text-xs font-black uppercase tracking-wide text-slate-500">Sign-in home workspace</p><p className="mt-2 text-sm font-black text-slate-950">{result.tenantMembership.primary ? 'Configured' : 'Uses existing default'}</p></div><div className={`rounded-2xl border p-4 ${result.roleBindings.length ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'}`}><p className="text-xs font-black uppercase tracking-wide text-slate-500">Application access</p><p className="mt-2 text-sm font-black text-slate-950">{result.roleBindings.length ? `${selectedRole?.roleName ?? 'Responsibility'} · ${result.roleBindings[0]?.status ?? 'ACTIVE'}` : 'NO RESPONSIBILITY'}</p></div></div> : null}
            <div className="mx-auto max-w-2xl text-left">
              {result?.roleBindings.length ? <AccessPreviewPanel preview={accessPreview} loading={false} error={accessPreviewError} title="Expected Navigator after sign-in setup" /> : <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm leading-6 text-amber-950"><b className="block">No business Navigator is expected yet.</b>The account is valid, but Application Access was explicitly deferred. Assign a Responsibility from the Person record before business use.</div>}
            </div>
            <div className="mx-auto max-w-xl rounded-2xl border border-slate-200 bg-white p-4 text-left">
              <p className="text-xs font-black uppercase tracking-wide text-slate-500">Required next actions</p>
              <ul className="mt-2 space-y-2 text-sm text-slate-700">
                {(result?.requiredActions.length ? result.requiredActions : ['Review the person record and verify access.']).map((action) => (
                  <li key={action} className="rounded-xl bg-slate-50 px-3 py-2">{action.replaceAll('_', ' ')}</li>
                ))}
              </ul>
              <p className="mt-3 text-xs font-semibold text-slate-500">Track sign-in readiness from the person record. A temporary password remains restricted until it is changed, and required MFA enrollment must complete before normal access is ready.</p>
            </div>
            <button type="button" onClick={onCancel} className="rounded-xl bg-slate-950 px-5 py-3 text-sm font-black text-white">Open People workspace</button>
          </div>
        ) : null}
      </div>

      {!result ? (
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
          <button type="button" onClick={step === 0 ? onCancel : goBack} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-black text-slate-700">
            {step === 0 ? 'Cancel' : 'Back'}
          </button>
          {!result && step === 4 ? (
            <button type="button" disabled={submitting} onClick={() => { void submit(); }} className="rounded-xl bg-blue-700 px-5 py-2.5 text-sm font-black text-white disabled:bg-slate-300">
              {submitting ? 'Adding person…' : accountSource === 'EXISTING' ? 'Add existing person' : 'Add person'}
            </button>
          ) : (
            <button type="button" disabled={searchingExisting} onClick={() => { void goNext(); }} className="rounded-xl bg-slate-950 px-5 py-2.5 text-sm font-black text-white disabled:bg-slate-300">{searchingExisting ? 'Checking identity…' : 'Continue'}</button>
          )}
        </div>
      ) : null}
    </section>
  );
}


