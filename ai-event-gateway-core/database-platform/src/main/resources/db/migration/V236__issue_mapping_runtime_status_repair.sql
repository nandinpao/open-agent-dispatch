-- HF15: repair Project Mapping rows produced by the Quick Setup governance path before
-- ProjectMappingGovernanceService synchronized mapping_status with lifecycle validation.
--
-- Historical defect:
--   DRAFT -> validate() changed lifecycle_status to VALID but left mapping_status=DRAFT
--         -> publish() changed lifecycle_status to ACTIVE/enabled=true but still left mapping_status=DRAFT
-- Route B correctly requires ACTIVE + VALID, therefore these successfully validated/published
-- mappings were invisible to Task -> Issue binding and every Task stopped at MAPPING_NOT_FOUND.
--
-- Do NOT promote V57 legacy synthetic metadata. Those mappings were intentionally marked as
-- unverified and must be repaired through provider metadata discovery/validation.
update integration_project_mappings m
   set mapping_status = 'VALID',
       version = m.version + 1,
       updated_at = now()
 where m.enabled = true
   and m.lifecycle_status = 'ACTIVE'
   and m.mapping_status = 'DRAFT'
   and m.validated_at is not null
   and m.published_at is not null
   and nullif(trim(coalesce(m.metadata_snapshot_id,'')), '') is not null
   and nullif(trim(coalesce(m.metadata_schema_hash,'')), '') is not null
   and m.metadata_snapshot_id not like 'legacy-p3c-%'
   and m.metadata_schema_hash not like 'legacy-%'
   and exists (
       select 1
         from integration_provider_metadata_snapshots s
        where s.tenant_id = m.tenant_id
          and s.mapping_id = m.mapping_id
          and s.snapshot_id = m.metadata_snapshot_id
          and s.schema_hash = m.metadata_schema_hash
   );
