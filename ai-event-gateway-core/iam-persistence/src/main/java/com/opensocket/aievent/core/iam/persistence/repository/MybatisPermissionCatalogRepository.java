package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;

import com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao;
import com.opensocket.aievent.core.iam.rbac.application.port.out.PermissionCatalogRepository;
import com.opensocket.aievent.core.iam.rbac.domain.Permission;
import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import com.opensocket.aievent.core.iam.rbac.domain.PermissionStatus;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@DatabaseRepositoryAdapter
public class MybatisPermissionCatalogRepository implements PermissionCatalogRepository {
    private final IamRbacDao dao;

    public MybatisPermissionCatalogRepository(IamRbacDao dao) {
        this.dao = dao;
    }

    @Override
    public Optional<Permission> findByCode(PermissionCode code) {
        return Optional.ofNullable(dao.findPermissionByCode(code.value())).map(this::domain);
    }

    @Override
    public List<Permission> findByCodes(Set<PermissionCode> codes) {
        if (codes == null || codes.isEmpty()) return List.of();
        return dao.findPermissionsByCodes(codes.stream().map(PermissionCode::value).sorted().toList())
                .stream().map(this::domain).toList();
    }

    @Override
    public Set<String> findActiveCodes() {
        return new LinkedHashSet<>(dao.findActivePermissionCodes());
    }

    private Permission domain(java.util.Map<String, Object> row) {
        Set<ScopeType> scopes = new LinkedHashSet<>();
        for (String value : csv(row, "allowedScopes")) {
            if (!value.isBlank()) scopes.add(ScopeType.valueOf(value));
        }
        return new Permission(
                new PermissionCode(string(row, "permissionCode")),
                string(row, "resourceType"),
                string(row, "actionCode"),
                string(row, "description"),
                Permission.RiskLevel.valueOf(string(row, "riskLevel")),
                scopes,
                bool(row, "active") ? PermissionStatus.ACTIVE : PermissionStatus.DISABLED,
                bool(row, "systemManaged"),
                longValue(row, "version"));
    }
}
