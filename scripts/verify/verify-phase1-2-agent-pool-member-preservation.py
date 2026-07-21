#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx'
DOC = ROOT / 'docs/current/development/phase1-2-agent-pool-member-preservation.md'
CHANGELOG = ROOT / 'docs/current/phase1-2-change-log.md'


def fail(message: str) -> None:
    print(f'Phase 1-2 agent pool member preservation verification failed: {message}', file=sys.stderr)
    sys.exit(1)

for path in [UI, DOC, CHANGELOG]:
    if not path.exists():
        fail(f'missing required file: {path.relative_to(ROOT)}')
    if path.stat().st_size == 0:
        fail(f'empty required file: {path.relative_to(ROOT)}')

ui_text = UI.read_text(encoding='utf-8')
required_ui_tokens = [
    'type EditablePoolMember = CoreAgentPoolMemberView',
    'members: EditablePoolMember[]',
    'function editorFromPool(pool: CoreAgentPoolView): PoolEditorState',
    'metadata: member.metadata ?? {}',
    'function memberViews(editor: PoolEditorState, agents: CoreDispatchFlowAgentOptionView[]): CoreAgentPoolMemberView[]',
    'function newMemberForAgent(agent: CoreDispatchFlowAgentOptionView, editor: PoolEditorState): EditablePoolMember',
    "memberSource: 'ADMIN_CONFIGURATION'",
    "routingModel: 'AGENT_POOL_FIRST'",
    "adminUiEditor: 'AGENT_POOL_MEMBER_PRESERVING'",
    'updateMember(agent.agentId, { priority:',
    'updateMember(agent.agentId, { weight:',
    'updateMember(agent.agentId, { memberStatus:',
    'Metadata：保留',
]
for token in required_ui_tokens:
    if token not in ui_text:
        fail(f'missing UI preservation token: {token}')

for forbidden in [
    'memberIds:',
    'editor.memberIds',
    'phase32gPoolMember',
    'phase32gAgentPoolAdminUi',
    'metadata: { phase32gPoolMember: true }',
]:
    if forbidden in ui_text:
        fail(f'forbidden old member reset pattern remains in UI: {forbidden}')

# Only newly added members should assign the default weight/priority together.
new_member_section = ui_text.split('function newMemberForAgent', 1)[1].split('function PoolEditorDialog', 1)[0]
if 'priority: 100' not in new_member_section or 'weight: 1' not in new_member_section:
    fail('new members must explicitly receive the documented priority/weight defaults')
rest_without_new_member = ui_text.replace(new_member_section, '')
if 'metadata: {\n      memberSource' in rest_without_new_member:
    fail('memberSource metadata must only be assigned when creating a new member')

doc_text = DOC.read_text(encoding='utf-8')
for token in [
    'members: EditablePoolMember[]',
    'Existing members keep their existing metadata map',
    'Only newly added members receive defaults',
    'WEIGHTED_SCORE -> member.weight participates in candidate scoring',
]:
    if token not in doc_text:
        fail(f'missing document anchor: {token}')

print('Phase 1-2 agent pool member preservation contract verified.')
