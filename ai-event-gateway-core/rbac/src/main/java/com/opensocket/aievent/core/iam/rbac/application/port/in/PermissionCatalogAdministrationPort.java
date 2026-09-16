package com.opensocket.aievent.core.iam.rbac.application.port.in;

import com.opensocket.aievent.core.iam.rbac.application.command.catalog.CreatePermissionCatalogDraftCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.DeletePermissionAliasCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.DeletePermissionDefinitionCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.PublishPermissionCatalogCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.UpsertPermissionAliasCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.UpsertPermissionDefinitionCommand;
import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogAlias;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogDiff;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogPublication;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogRevision;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogValidationReport;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionCatalogAdministrationPort {
    PermissionCatalogRevision createDraft(CreatePermissionCatalogDraftCommand command);
    List<PermissionCatalogRevision> revisions();
    Optional<PermissionCatalogRevision> activeRevision();
    PermissionCatalogRevision revision(UUID revisionId);
    List<PermissionDefinition> definitions(UUID revisionId);
    PermissionDefinition upsertDefinition(UpsertPermissionDefinitionCommand command);
    void deleteDefinition(DeletePermissionDefinitionCommand command);
    List<PermissionCatalogAlias> aliases(UUID revisionId);
    PermissionCatalogAlias upsertAlias(UpsertPermissionAliasCommand command);
    void deleteAlias(DeletePermissionAliasCommand command);
    PermissionCatalogDiff diff(UUID revisionId);
    PermissionCatalogValidationReport validate(UUID revisionId);
    PermissionCatalogPublication publish(PublishPermissionCatalogCommand command);
    List<PermissionCatalogPublication> publications(int limit);
    Optional<PermissionDefinition> definition(UUID revisionId, PermissionCode code);
}
