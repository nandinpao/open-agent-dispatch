package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import java.util.*;
import com.opensocket.aievent.core.iam.identity.application.port.out.RootIdentityRepository;
import com.opensocket.aievent.core.iam.identity.domain.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamIdentityDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class MybatisRootIdentityRepository implements RootIdentityRepository {
    private final IamIdentityDao dao; public MybatisRootIdentityRepository(IamIdentityDao dao){this.dao=dao;}
    public Optional<RootIdentity> find(){return Optional.ofNullable(dao.findRoot()).map(r->RootIdentity.reconstitute(RootIdentityStatus.valueOf(string(r,"status")),instant(r,"createdAt"),instant(r,"updatedAt"),string(r,"updatedBy"),string(r,"reason"),longValue(r,"version")));}
    public RootIdentity save(RootIdentity root,long expected){Map<String,Object> m=new HashMap<>();m.put("rootIdentityId",root.rootIdentityId().value());m.put("status",root.status().name());m.put("reason",root.reason());m.put("createdAt",root.createdAt());m.put("updatedAt",root.updatedAt());m.put("updatedBy",root.updatedBy());m.put("version",root.version());int rows=expected==0?dao.insertRoot(m):dao.updateRoot(m,expected);if(rows!=1)throw new IamOptimisticLockException("RootIdentity","root",expected);return root;}
}
