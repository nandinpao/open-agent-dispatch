package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import java.time.ZoneId; import java.util.*;
import com.opensocket.aievent.core.iam.organization.application.port.out.TenantRepository;
import com.opensocket.aievent.core.iam.organization.domain.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamInstanceOrganizationDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DatabaseRepositoryAdapter
public class MybatisTenantRepository implements TenantRepository {
 private final IamInstanceOrganizationDao dao; public MybatisTenantRepository(IamInstanceOrganizationDao dao){this.dao=dao;}
 public Optional<Tenant> findById(TenantId id){return Optional.ofNullable(dao.findTenant(id.value())).map(this::domain);}
 public boolean existsByCode(String code){return dao.countTenantByCode(code)>0;}
 public Tenant save(Tenant t,long expected){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("TENANT_TRANSACTION_REQUIRED: TenantRepository.save");dao.initializeTenantTransactionContext(t.tenantId().value(),t.updatedBy());int rows=expected==0?dao.insertTenant(row(t)):dao.updateTenant(row(t),expected);if(rows!=1)throw new IamOptimisticLockException("Tenant",t.tenantId().value(),expected);return t;}
 private Tenant domain(Map<String,Object> r){return Tenant.reconstitute(new TenantId(string(r,"tenantId")),string(r,"tenantCode"),string(r,"tenantName"),string(r,"legalName"),TenantStatus.valueOf(string(r,"status")),ZoneId.of(string(r,"defaultTimezone")),Locale.forLanguageTag(string(r,"defaultLocale")),string(r,"dataRegion"),instant(r,"createdAt"),instant(r,"updatedAt"),string(r,"updatedBy"),longValue(r,"version"));}
 private Map<String,Object> row(Tenant t){Map<String,Object>m=new HashMap<>();m.put("tenantId",t.tenantId().value());m.put("tenantCode",t.tenantCode());m.put("tenantName",t.tenantName());m.put("legalName",t.legalName());m.put("status",t.status().name());m.put("defaultTimezone",t.defaultTimezone().getId());m.put("defaultLocale",t.defaultLocale().toLanguageTag());m.put("dataRegion",t.dataRegion());m.put("createdAt",t.createdAt());m.put("updatedAt",t.updatedAt());m.put("updatedBy",t.updatedBy());m.put("version",t.version());return m;}
}
