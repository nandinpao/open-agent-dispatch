#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const repoRoot = path.resolve(root, '..');
function read(file) {
  const abs = path.resolve(root, file);
  if (!fs.existsSync(abs)) throw new Error(`Missing ${file}`);
  return fs.readFileSync(abs, 'utf8');
}
function readRepo(file) {
  const abs = path.resolve(repoRoot, file);
  if (!fs.existsSync(abs)) throw new Error(`Missing ${file}`);
  return fs.readFileSync(abs, 'utf8');
}
function assertIncludes(text, needle, file) {
  if (!text.includes(needle)) throw new Error(`${file} must include ${needle}`);
}

const requestFile = '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/event/EventIntakeRequest.java';
const request = read(requestFile);
for (const token of [
  'private String eventStage;',
  'private String originSourceSystem;',
  'private String targetSystem;',
  'private String requestedSkill;',
  'private String handoffMode;',
  'private String correlationId;',
  'private String parentTaskId;',
]) assertIncludes(request, token, requestFile);

const normalizerFile = '../ai-event-gateway-core/event-processing/src/main/java/com/opensocket/aievent/core/normalize/EventNormalizer.java';
const normalizer = read(normalizerFile);
for (const token of [
  'normalizeEventStage(request.getEventStage())',
  'case "A2A", "RESULT", "ISSUE", "CALLBACK" -> stage;',
  'default -> "EXTERNAL"',
  'request.getRequestedSkill()',
  'request.getParentTaskId()',
]) assertIncludes(normalizer, token, normalizerFile);

const normalizedFile = '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/event/NormalizedEvent.java';
const normalized = read(normalizedFile);
for (const token of [
  'String eventStage,',
  'String originSourceSystem,',
  'String targetSystem,',
  'String requestedSkill,',
  'String handoffMode,',
  'String correlationId,',
  'String parentTaskId,',
]) assertIncludes(normalized, token, normalizedFile);

const responseFile = '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/decision/EventIntakeDecisionResponse.java';
const response = read(responseFile);
for (const token of [
  'String eventStage,',
  'String originSourceSystem,',
  'String targetSystem,',
  'String requestedSkill,',
  'String handoffMode,',
  'String correlationId,',
  'String parentTaskId',
]) assertIncludes(response, token, responseFile);

const decisionEngineFile = '../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/decision/DecisionEngine.java';
const decisionEngine = read(decisionEngineFile);
for (const token of [
  'event.eventStage()',
  'event.originSourceSystem()',
  'event.targetSystem()',
  'event.requestedSkill()',
  'event.handoffMode()',
  'event.correlationId()',
  'event.parentTaskId()',
]) assertIncludes(decisionEngine, token, decisionEngineFile);

const taskRecordFile = '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskRecord.java';
const taskRecord = read(taskRecordFile);
for (const token of [
  'private String eventStage;',
  'private String originSourceSystem;',
  'private String targetSystem;',
  'private String requestedSkill;',
  'private String handoffMode;',
  'private String correlationId;',
  'private String parentTaskId;',
  'private String routingPath;',
]) assertIncludes(taskRecord, token, taskRecordFile);

const taskDecisionFile = '../ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java';
const taskDecision = read(taskDecisionFile);
for (const token of [
  'task.setEventStage(event.eventStage())',
  'task.setOriginSourceSystem(event.originSourceSystem())',
  'task.setTargetSystem(event.targetSystem())',
  'task.setRequestedSkill(event.requestedSkill())',
  'task.setHandoffMode(event.handoffMode())',
  'task.setCorrelationId(event.correlationId())',
  'task.setParentTaskId(event.parentTaskId())',
  'LEGACY_ROUTING_R3_ENVELOPE_ONLY',
]) assertIncludes(taskDecision, token, taskDecisionFile);

const assignmentServiceFile = '../ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/TaskAssignmentService.java';
const assignmentService = read(assignmentServiceFile);
for (const token of [
  'copyR3EnvelopeTrace(task, assignment)',
  'assignment.setEventStage(task.getEventStage())',
  'assignment.setOriginSourceSystem(task.getOriginSourceSystem())',
  'assignment.setTargetSystem(task.getTargetSystem())',
  'assignment.setRequestedSkill(task.getRequestedSkill())',
  'assignment.setHandoffMode(task.getHandoffMode())',
  'assignment.setCorrelationId(task.getCorrelationId())',
  'assignment.setParentTaskId(task.getParentTaskId())',
]) assertIncludes(assignmentService, token, assignmentServiceFile);

const migrationFile = '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V96__r3_event_stage_a2a_envelope.sql';
const migration = read(migrationFile);
for (const token of [
  'origin_source_system',
  'dispatch_r3_envelope_trace_report',
  'idx_tasks_r3_envelope_stage',
  'idx_tasks_r3_a2a_correlation',
  'idx_task_assignments_r3_envelope_stage',
]) assertIncludes(migration, token, migrationFile);

const taskMapperFile = '../ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/task/TaskDao.xml';
const taskMapper = read(taskMapperFile);
for (const token of [
  'event_stage',
  'origin_source_system',
  'target_system',
  'requested_skill',
  'handoff_mode',
  'correlation_id',
  'parent_task_id',
  'routing_path',
]) assertIncludes(taskMapper, token, taskMapperFile);

const assignmentMapperFile = '../ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/task/TaskAssignmentDao.xml';
const assignmentMapper = read(assignmentMapperFile);
for (const token of [
  'event_stage',
  'origin_source_system',
  'target_system',
  'requested_skill',
  'handoff_mode',
  'correlation_id',
  'parent_task_id',
  'routing_path',
]) assertIncludes(assignmentMapper, token, assignmentMapperFile);

const typesFile = 'lib/types/core.ts';
const types = read(typesFile);
for (const token of [
  'CoreEventIntakeEnvelope',
  'CoreEventIntakeDecisionResponse',
  'eventStage?: CoreEventIntakeStage',
  'requestedSkill?: string',
  'parentTaskId?: string',
]) assertIncludes(types, token, typesFile);

for (const file of [
  '../docs/R3_EVENT_STAGE_A2A_ENVELOPE/README.md',
  '../docs/R3_EVENT_STAGE_A2A_ENVELOPE/r3_change_log.md',
  '../docs/R3_EVENT_STAGE_A2A_ENVELOPE/r3_to_r4_handoff.md',
  'docs/R3_EVENT_STAGE_A2A_ENVELOPE.md',
]) {
  const content = read(file);
  assertIncludes(content, 'R3', file);
}

const packageJson = JSON.parse(read('package.json'));
if (!packageJson.scripts['verify:r3-event-stage-a2a-envelope']) throw new Error('package.json missing verify:r3-event-stage-a2a-envelope');

const makefile = readRepo('Makefile');
assertIncludes(makefile, 'verify-r3-event-stage-a2a-envelope', 'Makefile');

console.log('OK R3 eventStage / intake2 / A2A envelope DTOs, persistence trace, UI types, docs, and verification gate are present.');
