'use client';

import { PRODUCT_ROUTES } from '@/lib/navigation/productRoutes';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { ErrorNotice, Panel, SuccessNotice, TenantRequired } from '../ui';
import { AuditReasonSelector, ContextLink, FieldLabel, HierarchySelectField, HumanStatus, MultiSelectCards, SearchField, SearchSelectField, SelectField, isAuditReasonValid } from '../shared/beginnerUi';
import { WorkspaceModal } from '../shared/workspaceUi';
import { BlockerRecoveryPanel, GuidedEmptyState, ReadOnlyBoundary } from '../shared/acceptanceUi';
import type { PeopleBulkActionResult } from '@/lib/iam/types';
import { departmentDepth, groupDepth, humanAuditEvent, humanize, groupTypeOptions, generateUniqueCode } from './organizationWorkspaceModel';
import { useOrganizationWorkspaceController, type OrganizationTab } from './useOrganizationWorkspaceController';

export function OrganizationWorkspace() {
  const controller = useOrganizationWorkspaceController();
  if (controller.mode === 'tenant-required') return <TenantRequired />;
  const { scopeTenantId, tenantName, departments, groups, people, roles, bindings, members, audit, eligibleManagers, selection, tab, treeSearch, memberSearch, collapsedDepartments, collapsedGroups, loading, detailLoading, busy, error, recoveryErrorCode, notice, dialog, retirementPreview, retirementLoading, draftName, draftCode, draftParentId, draftOwnerDepartmentId, draftGroupType, draftDescription, draftStatus, selectedPeople, memberCandidates, memberCandidateCursor, memberCandidateHasMore, memberSearchLoading, memberBulkResult, memberRole, memberExpiresAt, managerUserId, grantManagerAccess, reason, selectedDepartment, selectedGroup, selectedName, selectedStatus, selectedMembership, departmentOptions, manageableDepartmentOptions, manageableGroupOptions, canManageDepartment, canManageGroup, canManageDepartmentTenant, canManageGroupTenant, canManageSelectedDepartment, canManageSelectedGroup, canManageSelectedMembership, canManageSelected, canOpenSourceSystems, canOpenDispatch, canOpenA2AGovernance, editableGroupParentOptions, movableDepartmentOptions, groupParentChangeAuthorized, departmentMoveAuthorized, filteredDepartments, filteredGroups, scopedBindings, selectedMemberPerson, departmentChildCount, departmentDescendantCount, groupChildCount, ownedGroupCount, activeScopedAssignments, deleteBlocked, departmentsWithoutManager, setTreeSearch, setMemberSearch, setCollapsedDepartments, setCollapsedGroups, setRecoveryErrorCode, setDraftName, setDraftCode, setDraftParentId, setDraftOwnerDepartmentId, setDraftGroupType, setDraftDescription, setDraftStatus, setSelectedPeople, setMemberRole, setMemberExpiresAt, setManagerUserId, setGrantManagerAccess, setReason, choose, changeTab, closeDialog, openCreateDepartment, openCreateGroup, openEdit, openMove, openLifecycle, openDelete, openManager, openAddMembers, openMember, loadMemberCandidates, createDepartment, createGroup, updateSelected, moveDepartment, saveManager, addMembers, updateMembership, removeMembership, changeLifecycle, deleteOrganizationItem } = controller;
  return (
    <div className="space-y-5">
      <ErrorNotice message={error} />
      <SuccessNotice message={notice} />
      {recoveryErrorCode === 'DEPARTMENT_DELETE_BLOCKED' || recoveryErrorCode === 'GROUP_DELETE_BLOCKED' || recoveryErrorCode === 'DEPARTMENT_DISABLE_BLOCKED' || recoveryErrorCode === 'GROUP_DISABLE_BLOCKED' ? <BlockerRecoveryPanel tone={recoveryErrorCode.includes('DELETE') ? 'danger' : 'warning'} title="Resolve dependencies before retrying" description="The server found governed relationships that are not fully represented by the local tree counts. Use the nearby workspaces below, then retry the lifecycle action." items={[
        { label: 'People and hierarchy', description: 'Move members, child Departments / Groups, or Group ownership.', action: <button type="button" onClick={() => { setRecoveryErrorCode(''); if (selection) changeTab('MEMBERS'); }} className="text-xs font-black text-slate-900 hover:underline">Review organization →</button> },
        { label: 'Responsibilities', description: 'Revoke or re-scope active Role Bindings attached to this organization scope.', action: <button type="button" onClick={() => { setRecoveryErrorCode(''); if (selection) changeTab('ACCESS'); }} className="text-xs font-black text-slate-900 hover:underline">Review access →</button> },
        ...(canOpenSourceSystems ? [{ label: 'Source Systems', description: 'Re-scope Source Systems owned by this Department / Group.', action: <Link href="/source-systems" className="text-xs font-black text-slate-900 hover:underline">Open Source Systems →</Link> }] : []),
        ...(canOpenDispatch ? [{ label: 'Dispatch resources', description: 'Re-scope Source Flows and Agent Pools inherited from the organization owner.', action: <Link href={PRODUCT_ROUTES.dispatch} className="text-xs font-black text-slate-900 hover:underline">Open Dispatch →</Link> }] : []),
        ...(canOpenA2AGovernance ? [{ label: 'Legacy A2A Archive', description: 'Directional A2A policies are retired and read-only. They no longer need re-scoping before organization retirement.', action: <Link href="/a2a-governance" className="text-xs font-black text-slate-900 hover:underline">Open archive →</Link> }] : []),
      ]} /> : null}

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="text-xs font-black uppercase tracking-[.18em] text-blue-700">Organization</p>
            <h2 className="mt-1 text-xl font-black text-slate-950">Departments and Groups in {tenantName}</h2>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">
              Manage the organization from one workspace. Select an item on the left, then use the nearby actions on the right. Access assignments stay explicit and auditable.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {canManageDepartment ? <button type="button" onClick={() => openCreateDepartment()} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white">+ Department</button> : null}
            {canManageGroup ? <button type="button" onClick={() => openCreateGroup()} className="rounded-xl border border-blue-300 bg-white px-4 py-2.5 text-sm font-black text-blue-800">+ Group</button> : null}
          </div>
        </div>
        {!canManageDepartment && !canManageGroup ? <div className="mt-4"><ReadOnlyBoundary description="You can inspect the organization within your effective scope. Department and Group changes require organization management authority at the target scope." /></div> : null}
        <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <SummaryCard label="Departments" value={departments.length} note={departmentsWithoutManager ? `${departmentsWithoutManager} need a Manager` : 'All active Departments have a Manager'} />
          <SummaryCard label="Groups" value={groups.length} note="Security, project and operational teams" />
          <SummaryCard label="People" value={people.filter((item) => item.status === 'ACTIVE').length} note="Active Tenant identities" />
          <SummaryCard label="Scoped assignments" value={bindings.filter((item) => item.status === 'ACTIVE' && (item.scopeType === 'DEPARTMENT' || item.scopeType === 'GROUP')).length} note="Access remains separate from membership" />
        </div>
      </section>

      <div className="grid gap-5 xl:grid-cols-[minmax(300px,.68fr)_minmax(0,1.32fr)]">
        <Panel title="Organization tree" description="Search once, then select a Department or Group to manage it without leaving this page.">
          <SearchField id="organization-search" value={treeSearch} onChange={setTreeSearch} placeholder="Search Departments and Groups" />
          <div className="mt-3 flex flex-wrap gap-2">
            <button type="button" onClick={() => { setCollapsedDepartments([]); setCollapsedGroups([]); }} className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-black text-slate-700">Expand all</button>
            <button type="button" onClick={() => { setCollapsedDepartments(departments.map((item) => item.departmentId)); setCollapsedGroups(groups.map((item) => item.groupId)); }} className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-black text-slate-700">Collapse all</button>
          </div>
          <div className="mt-4 space-y-4">
            <OrganizationSection title="Departments" count={filteredDepartments.length} action={canManageDepartment ? <button type="button" onClick={() => openCreateDepartment()} className="text-xs font-black text-blue-700">Add →</button> : null}>
              {filteredDepartments.map((department) => (
                <OrganizationButton
                  key={department.departmentId}
                  active={selection?.type === 'DEPARTMENT' && selection.id === department.departmentId}
                  title={department.name}
                  subtitle={`${department.code} · ${humanize(department.status)}`}
                  depth={departmentDepth(department, departments)}
                  expandable={departments.some((item) => item.parentDepartmentId === department.departmentId)}
                  collapsed={collapsedDepartments.includes(department.departmentId)}
                  onToggle={() => setCollapsedDepartments((current) => current.includes(department.departmentId) ? current.filter((id) => id !== department.departmentId) : [...current, department.departmentId])}
                  onClick={() => choose({ type: 'DEPARTMENT', id: department.departmentId })}
                />
              ))}
              {!loading && !filteredDepartments.length ? treeSearch.trim() ? <GuidedEmptyState title="No Departments match this search" description="No Department in your effective organization scope matches the current search." nextStep="Clear the search or try the business name/code used by your organization." primaryAction={<button type="button" onClick={() => setTreeSearch('')} className="rounded-lg bg-slate-950 px-3 py-2 text-xs font-black text-white">Clear search</button>} /> : <GuidedEmptyState title="No Departments yet" description="Departments define the business hierarchy used for people placement and scoped administration." nextStep={canManageDepartment ? 'Create the first Department, then place people and assign an Official Manager.' : 'A Department administrator must create the first Department before organization placement is available.'} primaryAction={canManageDepartment ? <button type="button" onClick={() => openCreateDepartment()} className="rounded-lg bg-blue-700 px-3 py-2 text-xs font-black text-white">Create first Department</button> : undefined} /> : null}
            </OrganizationSection>
            <OrganizationSection title="Groups" count={filteredGroups.length} action={canManageGroup ? <button type="button" onClick={() => openCreateGroup()} className="text-xs font-black text-blue-700">Add →</button> : null}>
              {filteredGroups.map((group) => (
                <OrganizationButton
                  key={group.groupId}
                  active={selection?.type === 'GROUP' && selection.id === group.groupId}
                  title={group.name}
                  subtitle={`${group.type ? humanize(group.type) : 'Group'} · ${humanize(group.status)}`}
                  depth={groupDepth(group, groups)}
                  expandable={groups.some((item) => item.parentGroupId === group.groupId)}
                  collapsed={collapsedGroups.includes(group.groupId)}
                  onToggle={() => setCollapsedGroups((current) => current.includes(group.groupId) ? current.filter((id) => id !== group.groupId) : [...current, group.groupId])}
                  onClick={() => choose({ type: 'GROUP', id: group.groupId })}
                />
              ))}
              {!loading && !filteredGroups.length ? treeSearch.trim() ? <GuidedEmptyState title="No Groups match this search" description="No Group in your effective organization scope matches the current search." nextStep="Clear the search or try a Group name/code." primaryAction={<button type="button" onClick={() => setTreeSearch('')} className="rounded-lg bg-slate-950 px-3 py-2 text-xs font-black text-white">Clear search</button>} /> : <GuidedEmptyState title="No Groups yet" description="Groups model project, security or operational teams without replacing the Department hierarchy." nextStep={canManageGroup ? 'Create a Group only when people need a shared team boundary or responsibility scope.' : 'A Group administrator can create one when the business needs a cross-cutting team boundary.'} primaryAction={canManageGroup ? <button type="button" onClick={() => openCreateGroup()} className="rounded-lg border border-blue-300 bg-white px-3 py-2 text-xs font-black text-blue-800">Create first Group</button> : undefined} /> : null}
            </OrganizationSection>
          </div>
        </Panel>

        <Panel
          title={selectedName || 'Organization details'}
          description={selection ? `${selection.type === 'DEPARTMENT' ? 'Department' : 'Group'} · ${members.length} member${members.length === 1 ? '' : 's'}` : 'Select a Department or Group.'}
          actions={selection ? (
            <div className="flex flex-wrap justify-end gap-2">
              {canManageSelected ? <button type="button" onClick={openEdit} className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-xs font-black text-slate-700">Edit</button> : null}
              {canManageSelectedMembership ? <button type="button" onClick={openAddMembers} className="rounded-xl border border-blue-300 bg-white px-3 py-2 text-xs font-black text-blue-800">Add people</button> : null}
              {canManageSelected ? <button type="button" onClick={() => { void openDelete(); }} className="rounded-xl border border-rose-300 bg-white px-3 py-2 text-xs font-black text-rose-800">Delete…</button> : null}
              <Link href={`/admin/tenants/${encodeURIComponent(scopeTenantId)}/access?scopeType=${selection.type}&scopeId=${encodeURIComponent(selection.id)}`} className="rounded-xl bg-slate-950 px-3 py-2 text-xs font-black text-white">Manage access</Link>
            </div>
          ) : undefined}
        >
          {!selection ? <GuidedEmptyState title="Choose a Department or Group" description="Select an organization item from the tree to manage people, lifecycle, access and audit evidence without leaving this workspace." nextStep="Start with a Department for reporting structure; use Groups for cross-cutting project or operational teams." /> : (
            <div>
              <div className="flex flex-wrap gap-2 border-b border-slate-200 pb-3" role="tablist" aria-label="Organization details">
                {(['OVERVIEW', 'MEMBERS', 'ACCESS', 'AUDIT'] as OrganizationTab[]).map((item) => (
                  <button key={item} type="button" role="tab" aria-selected={tab === item} onClick={() => changeTab(item)} className={`rounded-xl px-3 py-2 text-xs font-black ${tab === item ? 'bg-slate-950 text-white' : 'bg-slate-100 text-slate-700'}`}>
                    {item.charAt(0) + item.slice(1).toLowerCase()}
                  </button>
                ))}
              </div>

              {detailLoading ? <div className="mt-4 rounded-2xl bg-slate-50 p-5 text-sm text-slate-500">Loading organization details…</div> : null}

              {!detailLoading && tab === 'OVERVIEW' ? (
                <div className="mt-4 space-y-4">
                  <div className="grid gap-3 md:grid-cols-2">
                    <InfoCard label="Type" value={selection.type === 'DEPARTMENT' ? 'Department' : humanize(selectedGroup?.type || 'GROUP')} />
                    <InfoCard label="Status" value={<HumanStatus value={selectedStatus || 'UNKNOWN'} />} action={canManageSelected ? <button type="button" onClick={openLifecycle} className="text-xs font-black text-blue-700">Change →</button> : null} />
                    {selectedDepartment ? (
                      <>
                        <InfoCard label="Parent Department" value={departments.find((item) => item.departmentId === selectedDepartment.parentDepartmentId)?.name || 'Top level'} action={canManageSelectedDepartment ? <button type="button" onClick={openMove} className="text-xs font-black text-blue-700">Move →</button> : null} />
                        <InfoCard label="Official Manager" value={people.find((person) => person.userId === selectedDepartment.managerUserId)?.displayName || 'Not assigned'} action={canManageSelectedDepartment ? <button type="button" onClick={openManager} className="text-xs font-black text-blue-700">Change →</button> : null} />
                        <InfoCard label="Child Departments" value={`${departmentChildCount} direct · ${departmentDescendantCount} total descendants`} action={canManageSelectedDepartment ? <button type="button" onClick={() => openCreateDepartment(selectedDepartment.departmentId)} className="text-xs font-black text-blue-700">Add child →</button> : null} />
                        <InfoCard label="People" value={`${members.length} member${members.length === 1 ? '' : 's'}`} action={canManageSelectedMembership ? <button type="button" onClick={() => changeTab('MEMBERS')} className="text-xs font-black text-blue-700">Manage →</button> : null} />
                      </>
                    ) : null}
                    {selectedGroup ? (
                      <>
                        <InfoCard label="Owner Department" value={departments.find((item) => item.departmentId === selectedGroup.ownerDepartmentId)?.name || 'Not assigned'} action={canManageSelectedGroup ? <button type="button" onClick={openEdit} className="text-xs font-black text-blue-700">Change →</button> : null} />
                        <InfoCard label="Parent Group" value={groups.find((item) => item.groupId === selectedGroup.parentGroupId)?.name || 'Top level'} action={canManageSelectedGroup ? <button type="button" onClick={openEdit} className="text-xs font-black text-blue-700">Change →</button> : null} />
                        <InfoCard label="Child Groups" value={`${groupChildCount} direct`} action={canManageSelectedGroup ? <button type="button" onClick={() => openCreateGroup(selectedGroup.groupId)} className="text-xs font-black text-blue-700">Add child →</button> : null} />
                        <InfoCard label="People" value={`${members.length} member${members.length === 1 ? '' : 's'}`} action={canManageSelectedMembership ? <button type="button" onClick={() => changeTab('MEMBERS')} className="text-xs font-black text-blue-700">Manage →</button> : null} />
                      </>
                    ) : null}
                    <InfoCard label="Direct access assignments" value={`${activeScopedAssignments} active`} action={<button type="button" onClick={() => changeTab('ACCESS')} className="text-xs font-black text-blue-700">Review →</button>} />
                  </div>

                  <section className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <h3 className="font-black text-slate-950">What this organization item controls</h3>
                        <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-600">
                          Membership describes where people belong. Roles and permissions are granted separately in Access, so moving a person or changing a Manager does not silently create unrelated permissions.
                        </p>
                      </div>
                      <ContextLink href={`/admin/tenants/${encodeURIComponent(scopeTenantId)}/access?scopeType=${selection.type}&scopeId=${encodeURIComponent(selection.id)}`}>Manage scoped access</ContextLink>
                    </div>
                  </section>
                </div>
              ) : null}

              {!detailLoading && tab === 'MEMBERS' ? (
                <div className="mt-4 space-y-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div><h3 className="font-black text-slate-950">People in {selectedName}</h3><p className="mt-1 text-xs text-slate-500">Membership is an organization relationship. Access is managed separately.</p></div>
                    {canManageSelectedMembership ? <button type="button" onClick={openAddMembers} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white">Add people</button> : null}
                  </div>
                  <section className="space-y-2">
                    {members.map((membership) => {
                      const person = people.find((item) => item.userId === membership.userId);
                      return (
                        <div key={membership.membershipId} className="rounded-2xl border border-slate-200 p-4">
                          <div className="flex flex-wrap items-start justify-between gap-3">
                            <div>
                              <p className="font-black text-slate-950">{person?.displayName ?? membership.userId}</p>
                              <p className="mt-1 text-xs text-slate-500">
                                {selection.type === 'DEPARTMENT'
                                  ? membership.primary ? 'Primary Department' : humanize(membership.role || 'MEMBER')
                                  : membership.role === 'LEAD' ? 'Group lead' : 'Group member'}
                                {membership.expiresAt ? ` · Until ${new Date(membership.expiresAt).toLocaleDateString()}` : ''}
                              </p>
                            </div>
                            <div className="flex flex-wrap gap-2">
                              <ContextLink href={`/admin/tenants/${encodeURIComponent(scopeTenantId)}/people?userId=${encodeURIComponent(membership.userId)}`}>Open person</ContextLink>
                              {canManageSelectedMembership ? <button type="button" onClick={() => openMember(membership)} className="rounded-lg border border-slate-300 px-2.5 py-1 text-xs font-black text-slate-700">Manage</button> : null}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                    {!members.length ? <GuidedEmptyState title="No people assigned here" description={`No active Person is currently a member of this ${selection?.type === 'DEPARTMENT' ? 'Department' : 'Group'}.`} nextStep={canManageSelectedMembership ? 'Add people here, or open a Person and change organization from the People workspace.' : 'A membership administrator can place people in this organization scope.'} primaryAction={canManageSelectedMembership ? <button type="button" onClick={openAddMembers} className="rounded-lg bg-blue-700 px-3 py-2 text-xs font-black text-white">Add people</button> : undefined} /> : null}
                  </section>
                </div>
              ) : null}

              {!detailLoading && tab === 'ACCESS' ? (
                <div className="mt-4 space-y-3">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div><h3 className="font-black text-slate-950">Access applied here</h3><p className="mt-1 text-xs text-slate-500">These are explicit Role Bindings scoped to this Department or Group.</p></div>
                    <ContextLink href={`/admin/tenants/${encodeURIComponent(scopeTenantId)}/access?scopeType=${selection.type}&scopeId=${encodeURIComponent(selection.id)}`}>Assign access here</ContextLink>
                  </div>
                  {scopedBindings.map((binding) => {
                    const role = roles.find((item) => item.roleId === binding.roleId);
                    const principal = binding.principalType === 'USER'
                      ? people.find((item) => item.userId === binding.principalId)?.displayName
                      : groups.find((item) => item.groupId === binding.principalId)?.name;
                    return (
                      <div key={binding.bindingId} className="rounded-2xl border border-slate-200 p-4">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div>
                            <p className="font-black text-slate-950">{role?.roleName ?? binding.roleId}</p>
                            <p className="mt-1 text-sm text-slate-600">Assigned to {principal ?? binding.principalId}</p>
                            <p className="mt-1 text-xs text-slate-500">Applies to {selectedName}</p>
                          </div>
                          <HumanStatus value={binding.status} />
                        </div>
                        <div className="mt-3"><ContextLink href={`/admin/tenants/${encodeURIComponent(scopeTenantId)}/access?bindingId=${encodeURIComponent(binding.bindingId)}`}>Open assignment</ContextLink></div>
                      </div>
                    );
                  })}
                  {!scopedBindings.length ? <GuidedEmptyState title="No direct responsibilities at this scope" description="People may still receive Effective Access from Tenant, another Department, Group or direct Person assignment." nextStep="Use Manage access to assign a Responsibility here, or Explain Effective Access before adding another assignment." primaryAction={<Link href={`/admin/tenants/${encodeURIComponent(scopeTenantId)}/access?scopeType=${selection?.type}&scopeId=${encodeURIComponent(selection?.id ?? '')}&assign=1`} className="rounded-lg bg-slate-950 px-3 py-2 text-xs font-black text-white">Assign responsibility</Link>} secondaryAction={<Link href={`/admin/tenants/${encodeURIComponent(scopeTenantId)}/access?view=EFFECTIVE`} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-black text-slate-700">Explain Effective Access</Link>} /> : null}
                </div>
              ) : null}

              {!detailLoading && tab === 'AUDIT' ? (
                <div className="mt-4 space-y-2">
                  {audit.map((event) => (
                    <div key={event.eventId} className="rounded-2xl border border-slate-200 p-4">
                      <p className="text-sm font-black text-slate-950">{humanAuditEvent(event)}</p>
                      <p className="mt-1 text-xs text-slate-500">{new Date(event.occurredAt).toLocaleString()} · by {event.actorId}</p>
                      {event.reason ? <p className="mt-2 text-sm text-slate-600">Reason: {event.reason}</p> : null}
                      <details className="mt-2"><summary className="cursor-pointer text-xs font-black text-slate-500">Technical evidence</summary><p className="mt-2 break-all text-xs text-slate-500">Correlation: {event.correlationId}</p></details>
                    </div>
                  ))}
                  {!audit.length ? <GuidedEmptyState title="No organization audit activity yet" description="No governed change has been recorded for this item in the current audit window." nextStep="Create, membership, manager, lifecycle and access changes will appear here automatically." /> : null}
                </div>
              ) : null}
            </div>
          )}
        </Panel>
      </div>

      <WorkspaceModal open={dialog === 'CREATE_DEPARTMENT'} title="Create Department" description="Name the Department and choose its place in the organization. OpenDispatch generates the internal ID and a suggested business code." onClose={closeDialog}>
        <div className="space-y-4">
          <FieldLabel htmlFor="r1-department-name" label="Department name" required><input id="r1-department-name" value={draftName} onChange={(event) => setDraftName(event.target.value)} placeholder="Example: Information Technology" className="w-full rounded-xl border border-slate-300 px-3 py-2.5" /></FieldLabel>
          <FieldLabel htmlFor="r1-department-parent" label="Parent Department" help={canManageDepartmentTenant ? "Choose from the organization hierarchy. Leave empty for a top-level Department." : "Choose a Department you are authorized to manage; creating a top-level Department requires Tenant-wide organization access."}><HierarchySelectField id="r1-department-parent" value={draftParentId} onChange={setDraftParentId} options={manageableDepartmentOptions} placeholder={canManageDepartmentTenant ? "Top level" : "Choose an authorized parent Department"} required={!canManageDepartmentTenant} /></FieldLabel>
          <GeneratedCodeEditor kind="Department" name={draftName} value={draftCode} generated={generateUniqueCode(draftName, departments.map((item) => item.code), 'DEPT')} onChange={setDraftCode} />
          <AuditReasonSelector idPrefix="r1-create-department" value={reason} onChange={setReason} />
        </div>
        <DialogActions busy={busy} valid={Boolean(draftName.trim() && isAuditReasonValid(reason, 'ROUTINE') && (canManageDepartmentTenant || draftParentId))} onCancel={closeDialog} onConfirm={() => { void createDepartment(); }} label="Create Department" />
      </WorkspaceModal>

      <WorkspaceModal open={dialog === 'CREATE_GROUP'} title="Create Group" description="Groups are flexible teams. Choose their type, optional parent Group and optional owner Department; access remains a separate assignment." onClose={closeDialog}>
        <div className="grid gap-4 md:grid-cols-2">
          <div className="md:col-span-2"><FieldLabel htmlFor="r1-group-name" label="Group name" required><input id="r1-group-name" value={draftName} onChange={(event) => setDraftName(event.target.value)} placeholder="Example: ERP Administrators" className="w-full rounded-xl border border-slate-300 px-3 py-2.5" /></FieldLabel></div>
          <FieldLabel htmlFor="r1-group-type" label="Group type"><SelectField id="r1-group-type" value={draftGroupType} onChange={setDraftGroupType} options={groupTypeOptions()} /></FieldLabel>
          <FieldLabel htmlFor="r1-group-owner" label="Owner Department"><HierarchySelectField id="r1-group-owner" value={draftOwnerDepartmentId} onChange={setDraftOwnerDepartmentId} options={departmentOptions} placeholder="No owner Department" /></FieldLabel>
          <div className="md:col-span-2"><FieldLabel htmlFor="r1-group-parent" label="Parent Group" help={canManageGroupTenant ? "Leave empty for a top-level Group." : "Choose a Group you are authorized to manage; creating a top-level Group requires Tenant-wide organization access."}><HierarchySelectField id="r1-group-parent" value={draftParentId} onChange={setDraftParentId} options={manageableGroupOptions} placeholder={canManageGroupTenant ? "Top level" : "Choose an authorized parent Group"} required={!canManageGroupTenant} /></FieldLabel></div>
          <div className="md:col-span-2"><FieldLabel htmlFor="r1-group-description" label="Description"><textarea id="r1-group-description" value={draftDescription} onChange={(event) => setDraftDescription(event.target.value)} placeholder="Explain what this team is for" className="min-h-24 w-full rounded-xl border border-slate-300 px-3 py-2.5" /></FieldLabel></div>
          <div className="md:col-span-2"><GeneratedCodeEditor kind="Group" name={draftName} value={draftCode} generated={generateUniqueCode(draftName, groups.map((item) => item.code), 'GROUP')} onChange={setDraftCode} /></div>
          <div className="md:col-span-2"><AuditReasonSelector idPrefix="r1-create-group" value={reason} onChange={setReason} /></div>
        </div>
        <DialogActions busy={busy} valid={Boolean(draftName.trim() && isAuditReasonValid(reason, 'ROUTINE') && (canManageGroupTenant || draftParentId))} onCancel={closeDialog} onConfirm={() => { void createGroup(); }} label="Create Group" />
      </WorkspaceModal>

      <WorkspaceModal open={dialog === 'EDIT'} title={`Edit ${selectedName || 'organization item'}`} description="Update business-facing details here. Internal IDs remain server-managed." onClose={closeDialog}>
        <div className="grid gap-4 md:grid-cols-2">
          <div className="md:col-span-2"><FieldLabel htmlFor="r1-edit-name" label={selection?.type === 'DEPARTMENT' ? 'Department name' : 'Group name'} required><input id="r1-edit-name" value={draftName} onChange={(event) => setDraftName(event.target.value)} className="w-full rounded-xl border border-slate-300 px-3 py-2.5" /></FieldLabel></div>
          {selectedGroup ? <>
            <FieldLabel htmlFor="r1-edit-group-type" label="Group type"><SelectField id="r1-edit-group-type" value={draftGroupType} onChange={setDraftGroupType} options={groupTypeOptions()} /></FieldLabel>
            <FieldLabel htmlFor="r1-edit-group-owner" label="Owner Department"><HierarchySelectField id="r1-edit-group-owner" value={draftOwnerDepartmentId} onChange={setDraftOwnerDepartmentId} options={departmentOptions} placeholder="No owner Department" /></FieldLabel>
            <div className="md:col-span-2"><FieldLabel htmlFor="r1-edit-group-parent" label="Parent Group"><HierarchySelectField id="r1-edit-group-parent" value={draftParentId} onChange={setDraftParentId} options={editableGroupParentOptions} placeholder={canManageGroupTenant ? "Top level" : "Choose an authorized parent Group"} /></FieldLabel></div>
            <div className="md:col-span-2"><FieldLabel htmlFor="r1-edit-description" label="Description"><textarea id="r1-edit-description" value={draftDescription} onChange={(event) => setDraftDescription(event.target.value)} className="min-h-24 w-full rounded-xl border border-slate-300 px-3 py-2.5" /></FieldLabel></div>
          </> : null}
          <div className="md:col-span-2"><details className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><summary className="cursor-pointer text-sm font-black text-slate-800">Advanced business code</summary><p className="mt-2 text-xs leading-5 text-slate-500">Most administrators should leave the existing code unchanged. It is a business identifier, not the internal database ID.</p><input value={draftCode} onChange={(event) => setDraftCode(event.target.value)} className="mt-3 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5" /></details></div>
          <div className="md:col-span-2"><AuditReasonSelector idPrefix="r1-edit" value={reason} onChange={setReason} /></div>
        </div>
        <DialogActions busy={busy} valid={Boolean(draftName.trim() && isAuditReasonValid(reason, 'ROUTINE') && (!selectedGroup || groupParentChangeAuthorized))} onCancel={closeDialog} onConfirm={() => { void updateSelected(); }} label="Save changes" />
      </WorkspaceModal>

      <WorkspaceModal open={dialog === 'MOVE'} title={`Move ${selectedDepartment?.name ?? 'Department'}`} description="Choose the new parent from the hierarchy. The Department itself keeps the same internal identity." onClose={closeDialog}>
        <ImpactPreview title="Move impact" items={[
          `${departmentDescendantCount} descendant Department(s) move with this Department.`,
          `${members.length} member relationship(s) remain attached.`,
          `${activeScopedAssignments} direct access assignment(s) remain scoped to this Department.`,
          `The Official Manager remains ${people.find((item) => item.userId === selectedDepartment?.managerUserId)?.displayName || 'unassigned'}.`,
        ]} />
        <div className="mt-4 space-y-4">
          <FieldLabel htmlFor="r1-move-parent" label="New parent Department"><HierarchySelectField id="r1-move-parent" value={draftParentId} onChange={setDraftParentId} options={movableDepartmentOptions} placeholder={canManageDepartmentTenant ? "Top level" : "Choose an authorized parent Department"} /></FieldLabel>
          <AuditReasonSelector idPrefix="r1-move" value={reason} onChange={setReason} />
        </div>
        <DialogActions busy={busy} valid={isAuditReasonValid(reason, 'ROUTINE') && departmentMoveAuthorized} onCancel={closeDialog} onConfirm={() => { void moveDepartment(); }} label="Move Department" />
      </WorkspaceModal>

      <WorkspaceModal open={dialog === 'MANAGER'} title={`Change Manager for ${selectedDepartment?.name ?? 'Department'}`} description="Choose from current Department members. The organization relationship and RBAC responsibility remain separate records." onClose={closeDialog}>
        <div className="space-y-4">
          <FieldLabel htmlFor="r1-manager" label="Official Manager"><SearchSelectField id="r1-manager" value={managerUserId} onChange={setManagerUserId} options={eligibleManagers.map((person) => ({ value: person.userId, label: person.displayName, description: `${person.username}${person.email ? ` · ${person.email}` : ''}` }))} placeholder="Search Department members" /></FieldLabel><div className="flex justify-end"><button type="button" onClick={() => setManagerUserId('')} className="text-xs font-black text-slate-600 hover:text-blue-700">Clear Manager</button></div>
          <label className="flex items-start gap-2 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm font-semibold text-blue-950"><input type="checkbox" checked={grantManagerAccess} onChange={(event) => setGrantManagerAccess(event.target.checked)} className="mt-1 size-4" /><span><b className="block">Also grant Department Manager responsibility</b><span className="mt-1 block text-xs font-medium leading-5">Recommended when the Official Manager should administer this Department. The Role Binding remains independently visible and auditable in Access.</span></span></label>
          <AuditReasonSelector idPrefix="r1-manager" value={reason} onChange={setReason} tier="ELEVATED" />
        </div>
        <DialogActions busy={busy} valid={isAuditReasonValid(reason, 'ELEVATED')} onCancel={closeDialog} onConfirm={() => { void saveManager(); }} label="Save Manager" />
      </WorkspaceModal>

      <WorkspaceModal open={dialog === 'ADD_MEMBERS'} title={memberBulkResult ? `Membership operation ${memberBulkResult.bulkOperationId}` : `Add people to ${selectedName || 'organization item'}`} description="Search the Tenant directory on the server and submit one governed bulk command. The browser never loops through Person mutation APIs." onClose={closeDialog} width="max-w-4xl">
        {memberBulkResult ? <OrganizationBulkResult result={memberBulkResult} onDone={closeDialog} /> : <>
        <div className="space-y-4">
          <SearchField id="r1-member-search" value={memberSearch} onChange={setMemberSearch} placeholder="Search people by name, username or email" />
          {memberSearchLoading && !memberCandidates.length ? <p className="text-xs font-semibold text-slate-500">Searching Tenant people…</p> : null}
          <MultiSelectCards options={memberCandidates.map((person) => ({ value: person.userId, label: person.displayName, description: `${person.username}${person.email ? ` · ${person.email}` : ''}` }))} selected={selectedPeople} onChange={setSelectedPeople} emptyMessage="No eligible Tenant people match this search." />
          {memberCandidateHasMore ? <button type="button" disabled={memberSearchLoading} onClick={() => { void loadMemberCandidates(memberCandidateCursor, true); }} className="rounded-xl border border-slate-300 px-3 py-2 text-xs font-black text-slate-700 disabled:opacity-50">{memberSearchLoading ? 'Loading…' : 'Load more people'}</button> : null}
          {selection?.type === 'GROUP' ? <FieldLabel htmlFor="r1-add-member-role" label="Relationship for selected people"><SelectField id="r1-add-member-role" value={memberRole} onChange={setMemberRole} options={[{ value: 'MEMBER', label: 'Member' }, { value: 'LEAD', label: 'Lead' }]} /></FieldLabel> : <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs leading-5 text-slate-600">People are added as regular Department members. Use People → Bulk action → Move Primary Department when several people need the same organizational transfer, or change one Person from their profile.</div>}
          <AuditReasonSelector idPrefix="r1-add-members" value={reason} onChange={setReason} />
        </div>
        <DialogActions busy={busy} valid={selectedPeople.length > 0 && isAuditReasonValid(reason, 'ROUTINE')} onCancel={closeDialog} onConfirm={() => { void addMembers(); }} label={`Add ${selectedPeople.length || ''} ${selectedPeople.length === 1 ? 'person' : 'people'}`.trim()} />
        </>}
      </WorkspaceModal>

      <WorkspaceModal open={dialog === 'MEMBER'} title={`Manage ${selectedMemberPerson?.displayName ?? 'membership'}`} description={`Update the organization relationship with ${selectedName}. Access roles are managed separately.`} onClose={closeDialog}>
        {selectedMembership ? <div className="space-y-4">
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><p className="text-sm font-black text-slate-950">{selectedMemberPerson?.displayName ?? selectedMembership.userId}</p><p className="mt-1 text-xs text-slate-500">{selectedMembership.primary ? 'Primary Department · ' : ''}{humanize(selectedMembership.status)}</p></div>
          <FieldLabel htmlFor="r1-member-relationship" label="Organization relationship"><SelectField id="r1-member-relationship" value={memberRole} onChange={setMemberRole} options={selection?.type === 'DEPARTMENT' ? [{ value: 'MEMBER', label: 'Member' }, { value: 'DELEGATE', label: 'Delegate' }] : [{ value: 'MEMBER', label: 'Member' }, { value: 'LEAD', label: 'Lead' }]} /></FieldLabel>
          <FieldLabel htmlFor="r1-member-expiry" label="Valid until" help="Leave empty for no expiry."><input id="r1-member-expiry" type="datetime-local" value={memberExpiresAt} onChange={(event) => setMemberExpiresAt(event.target.value)} className="w-full rounded-xl border border-slate-300 px-3 py-2.5" /></FieldLabel>
          {selection?.type === 'DEPARTMENT' && selectedMembership.primary ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950"><b className="block">Primary Department will become Unassigned</b><p className="mt-1 text-xs leading-5">Removing this Primary Department relationship keeps the Person and their sign-in identity. They will have no Primary Department until another Department is assigned, and Department-inherited authority is recalculated immediately.</p><div className="mt-3"><ContextLink href={`/admin/tenants/${encodeURIComponent(scopeTenantId)}/people?userId=${encodeURIComponent(selectedMembership.userId)}`}>Review person profile</ContextLink></div></div> : null}
          <AuditReasonSelector idPrefix="r1-member" value={reason} onChange={setReason} tier="ELEVATED" />
          <div className="flex flex-wrap justify-between gap-2 border-t border-slate-200 pt-4">
            <button type="button" disabled={busy} onClick={() => { void removeMembership(); }} className="rounded-xl border border-rose-300 px-4 py-2.5 text-sm font-black text-rose-800 disabled:cursor-not-allowed disabled:opacity-40">Remove from {selection?.type === 'DEPARTMENT' ? 'Department' : 'Group'}</button>
            <button type="button" disabled={busy || !isAuditReasonValid(reason, 'ELEVATED')} onClick={() => { void updateMembership(); }} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300">Save membership</button>
          </div>
        </div> : null}
      </WorkspaceModal>

      <WorkspaceModal open={dialog === 'LIFECYCLE'} title={`Change lifecycle for ${selectedName || 'organization item'}`} description="Disable is reversible. People and organization relationships are retained, while the disabled Department / Group stops contributing inherited authority. Governed business resources may still require re-scoping." onClose={closeDialog}>
        <div className="space-y-4">
          <ImpactPreview title="Lifecycle impact" items={selection?.type === 'DEPARTMENT' ? [
            `${members.length} people remain attached for reversible organization history.`,
            `${departmentDescendantCount} descendant Department(s) remain in the hierarchy.`,
            `${ownedGroupCount} Group(s) remain associated with this Department.`,
            `${activeScopedAssignments} scoped responsibility assignment(s) stop being effective while the Department is disabled.`,
            'Source Systems, Dispatch resources or A2A Policies still owned by this scope remain server-authoritative blockers until re-scoped.',
          ] : [
            `${members.length} people remain attached for reversible organization history.`,
            `${groupChildCount} child Group(s) remain in the hierarchy.`,
            `${activeScopedAssignments} scoped responsibility assignment(s) stop being effective while the Group is disabled.`,
            'Source Systems, Dispatch resources or A2A Policies still owned by this scope remain server-authoritative blockers until re-scoped.',
          ]} />
          <FieldLabel htmlFor="r1-lifecycle" label="Lifecycle status" help="People do not need to be removed before Disable. Memberships remain for recovery, while effective Department / Group authority is suspended because disabled organization principals are not expanded."><SelectField id="r1-lifecycle" value={draftStatus} onChange={setDraftStatus} options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'DISABLED', label: 'Disabled' }]} /></FieldLabel>
          <AuditReasonSelector idPrefix="r1-lifecycle" value={reason} onChange={setReason} tier="ELEVATED" />
        </div>
        <DialogActions busy={busy} valid={Boolean(draftStatus && isAuditReasonValid(reason, 'ELEVATED'))} onCancel={closeDialog} onConfirm={() => { void changeLifecycle(); }} label="Update lifecycle" />
      </WorkspaceModal>

      <WorkspaceModal open={dialog === 'DELETE'} title={`Retire ${selectedName || 'organization item'}`} description="Retirement removes this Department / Group from active administration without deleting People or erasing audit history. OpenDispatch calculates the impact before allowing the change." onClose={closeDialog} width="max-w-4xl">
        <div className="space-y-4">
          {retirementLoading ? <div className="rounded-2xl bg-slate-50 p-5 text-sm text-slate-500">Calculating organization retirement impact…</div> : null}
          {!retirementLoading && retirementPreview ? <>
            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950">
              <b className="block">People are preserved</b>
              <p className="mt-1">Deleting this {selection?.type === 'DEPARTMENT' ? 'Department' : 'Group'} does not delete any Person, sign-in identity, password or MFA. Organization memberships end and inherited authority is recalculated.</p>
            </div>
            <ImpactPreview title="Automatic retirement actions" items={selection?.type === 'DEPARTMENT' ? [
              `${retirementPreview.peopleCount} Department membership(s) will end.`,
              `${retirementPreview.primaryPeopleCount} Primary Department assignment(s) will become Unassigned unless another Primary Department is assigned later.`,
              `${retirementPreview.childCount} direct child Department(s) will move to this Department's parent.`,
              `${retirementPreview.ownedGroupCount} owned Group(s) will inherit this Department's parent owner, or become unowned at the top level.`,
              `${retirementPreview.activeRoleBindingCount} Department-principal / scoped responsibility binding(s) will be revoked automatically.`,
            ] : [
              `${retirementPreview.peopleCount} Group membership(s) will end; People remain in the workspace.`,
              `${retirementPreview.childCount} child Group(s) will move to this Group's parent.`,
              `${retirementPreview.activeRoleBindingCount} Group-principal / scoped responsibility binding(s) will be revoked automatically.`,
              'People with no remaining Group membership will show “No groups assigned”.',
            ]} />
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <InfoCard label="Source Systems" value={String(retirementPreview.sourceSystemCount)} />
              <InfoCard label="Dispatch Flows" value={String(retirementPreview.dispatchFlowCount)} />
              <InfoCard label="Agent Pools" value={String(retirementPreview.agentPoolCount)} />
              <InfoCard label="Active legacy A2A policies" value={String(retirementPreview.a2aPolicyCount)} />
            </div>
            {!retirementPreview.canDelete ? <BlockerRecoveryPanel tone="danger" title="Governed resource ownership must be reassigned" description="People, memberships, hierarchy and Role Bindings are handled automatically. These business resources are intentionally not converted to Tenant-wide ownership because that could widen data access." items={[
              { label: 'Source Systems', count: retirementPreview.sourceSystemCount, description: 'Assign an active Department / Group owner before retirement.', action: canOpenSourceSystems ? <ContextLink href="/source-systems">Open Source Systems</ContextLink> : undefined },
              { label: 'Dispatch Flows', count: retirementPreview.dispatchFlowCount, description: 'Move the owning Source System or Dispatch scope first.', action: canOpenDispatch ? <ContextLink href={PRODUCT_ROUTES.dispatch}>Open Dispatch</ContextLink> : undefined },
              { label: 'Agent Pools', count: retirementPreview.agentPoolCount, description: 'Re-scope the owning Source System / Pool before retirement.', action: canOpenDispatch ? <ContextLink href={PRODUCT_ROUTES.dispatch}>Open Dispatch</ContextLink> : undefined },
              { label: 'Active legacy A2A policies', count: retirementPreview.a2aPolicyCount, description: 'Retired directional A2A policies are historical archive rows and do not block organization retirement.', action: canOpenA2AGovernance ? <ContextLink href="/a2a-governance">Open archive</ContextLink> : undefined },
            ]} /> : <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-950"><b className="block">Ready to retire</b><p className="mt-1">There are no governed Source/Dispatch/A2A ownership blockers. The automatic organization changes above will be applied atomically with the logical delete.</p></div>}
          </> : null}
          <AuditReasonSelector idPrefix="r1-delete" value={reason} onChange={setReason} tier="HIGH_RISK" />
        </div>
        <DialogActions busy={busy || retirementLoading} valid={isAuditReasonValid(reason, 'HIGH_RISK') && !deleteBlocked} onCancel={closeDialog} onConfirm={() => { void deleteOrganizationItem(); }} label={`Retire ${selection?.type === 'DEPARTMENT' ? 'Department' : 'Group'}`} danger />
      </WorkspaceModal>
    </div>
  );
}

function OrganizationSection({ title, count, action, children }: Readonly<{ title: string; count: number; action?: ReactNode; children: ReactNode }>) {
  return <section><div className="mb-2 flex items-center justify-between gap-2"><div className="flex items-center gap-2"><h3 className="text-xs font-black uppercase tracking-wide text-slate-500">{title}</h3><span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-black text-slate-600">{count}</span></div>{action}</div><div className="space-y-1.5">{children}</div></section>;
}

function OrganizationButton({ active, title, subtitle, depth, expandable = false, collapsed = false, onToggle, onClick }: Readonly<{ active: boolean; title: string; subtitle: string; depth: number; expandable?: boolean; collapsed?: boolean; onToggle?: () => void; onClick: () => void }>) {
  return <div className="flex items-stretch gap-1">{expandable ? <button type="button" aria-label={`${collapsed ? 'Expand' : 'Collapse'} ${title}`} onClick={onToggle} className="w-8 rounded-lg border border-slate-200 text-xs font-black text-slate-600">{collapsed ? '＋' : '−'}</button> : <span className="w-8" aria-hidden="true" />}<button type="button" onClick={onClick} style={{ paddingLeft: `${12 + Math.min(depth, 4) * 16}px` }} className={`min-w-0 flex-1 rounded-xl border py-2.5 pr-3 text-left transition ${active ? 'border-blue-500 bg-blue-50' : 'border-slate-200 bg-white hover:border-blue-300'}`}><span className="block text-sm font-black text-slate-950">{title}</span><span className="mt-0.5 block truncate text-xs text-slate-500">{subtitle}</span></button></div>;
}

function OrganizationBulkResult({ result, onDone }: { result: PeopleBulkActionResult; onDone: () => void }) {
  return <div className="space-y-4">
    <div className="grid gap-3 sm:grid-cols-3"><InfoCard label="Succeeded" value={String(result.succeededCount)} /><InfoCard label="Skipped" value={String(result.skippedCount)} /><InfoCard label="Failed" value={String(result.failedCount)} /></div>
    <div className="max-h-[420px] divide-y divide-slate-100 overflow-y-auto rounded-2xl border border-slate-200">{result.results.map((item) => <div key={item.userId} className="grid gap-2 px-4 py-3 md:grid-cols-[minmax(0,1fr)_110px_minmax(0,1.5fr)]"><div><p className="font-black text-slate-900">{item.displayName}</p><p className="text-xs text-slate-500">{item.userId}</p></div><HumanStatus value={item.outcome} /><div><p className="text-sm text-slate-700">{item.message}</p>{item.code ? <p className="mt-1 font-mono text-xs text-slate-500">{item.code}</p> : null}{item.remediation ? <p className="mt-2 text-xs font-semibold text-blue-800">Next: {item.remediation}</p> : null}</div></div>)}</div>
    <div className="flex justify-end"><button type="button" onClick={onDone} className="rounded-xl bg-slate-950 px-4 py-2.5 text-sm font-black text-white">Done</button></div>
  </div>;
}

function InfoCard({ label, value, action }: Readonly<{ label: string; value: ReactNode; action?: ReactNode }>) {
  return <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="flex items-center justify-between gap-3"><p className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</p>{action}</div><div className="mt-2 text-sm font-black text-slate-950">{value}</div></div>;
}

function SummaryCard({ label, value, note }: Readonly<{ label: string; value: number; note: string }>) {
  return <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><p className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</p><p className="mt-1 text-2xl font-black text-slate-950">{value}</p><p className="mt-1 text-xs leading-5 text-slate-500">{note}</p></div>;
}

function GeneratedCodeEditor({ kind, name, value, generated, onChange }: Readonly<{ kind: string; name: string; value: string; generated: string; onChange: (value: string) => void }>) {
  return <details className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><summary className="cursor-pointer text-sm font-black text-slate-800">Advanced business code</summary><p className="mt-2 text-xs leading-5 text-slate-500">OpenDispatch will use <b>{generated || 'a code generated from the name'}</b>. Most administrators can leave this unchanged.</p><FieldLabel htmlFor={`r1-${kind.toLowerCase()}-code`} label={`${kind} code`}><input id={`r1-${kind.toLowerCase()}-code`} value={value} onChange={(event) => onChange(event.target.value.toUpperCase().replace(/[^A-Z0-9_-]/g, '_'))} placeholder={name ? generated : 'Generated from name'} className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5" /></FieldLabel></details>;
}

function ImpactPreview({ title, items }: Readonly<{ title: string; items: string[] }>) {
  return <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4"><h3 className="font-black text-amber-950">{title}</h3><ul className="mt-2 space-y-1 text-sm text-amber-900">{items.map((item) => <li key={item}>• {item}</li>)}</ul></section>;
}

function DialogActions({ busy, valid, onCancel, onConfirm, label, danger = false }: Readonly<{ busy: boolean; valid: boolean; onCancel: () => void; onConfirm: () => void; label: string; danger?: boolean }>) {
  return <div className="mt-6 flex justify-end gap-2"><button type="button" onClick={onCancel} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-black">Cancel</button><button type="button" disabled={busy || !valid} onClick={onConfirm} className={`rounded-xl px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300 ${danger ? 'bg-rose-700' : 'bg-blue-700'}`}>{label}</button></div>;
}

