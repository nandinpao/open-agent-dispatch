package com.opensocket.aievent.core.iam.organization.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Current hierarchy read model used only to validate mutations; historical reporting uses revisions/snapshots. */
public record DepartmentHierarchySnapshot(TenantId tenantId, Map<DepartmentId, DepartmentHierarchyNode> nodes) {
    public DepartmentHierarchySnapshot {
        Objects.requireNonNull(tenantId, "tenantId");
        nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
    }

    public int depthOf(DepartmentId id) {
        DepartmentHierarchyNode node = nodes.get(id);
        if (node == null) throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_PARENT_NOT_FOUND, "Department not found: " + id.value());
        return node.depth();
    }

    public Set<DepartmentId> descendantsOf(DepartmentId ancestor) {
        return nodes.keySet().stream().filter(candidate -> !candidate.equals(ancestor) && isDescendant(candidate, ancestor)).collect(Collectors.toUnmodifiableSet());
    }

    public int subtreeHeight(DepartmentId root) {
        int rootDepth = depthOf(root);
        return nodes.keySet().stream().filter(id -> id.equals(root) || isDescendant(id, root)).mapToInt(id -> depthOf(id) - rootDepth).max().orElse(0);
    }

    public List<DepartmentId> ancestorPath(DepartmentId id) {
        ArrayList<DepartmentId> reverse = new ArrayList<>();
        DepartmentHierarchyNode current = nodes.get(id);
        while (current != null) {
            reverse.add(current.departmentId());
            current = current.parentDepartmentId().map(nodes::get).orElse(null);
        }
        java.util.Collections.reverse(reverse);
        return List.copyOf(reverse);
    }

    private boolean isDescendant(DepartmentId candidate, DepartmentId ancestor) {
        DepartmentHierarchyNode current = nodes.get(candidate);
        int guard = 0;
        while (current != null && current.parentDepartmentId().isPresent()) {
            DepartmentId parent = current.parentDepartmentId().orElseThrow();
            if (parent.equals(ancestor)) return true;
            current = nodes.get(parent);
            if (++guard > nodes.size()) throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_CYCLE_DETECTED, "Hierarchy contains a cycle");
        }
        return false;
    }
}
