package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import java.util.*;
import com.opensocket.aievent.core.iam.identity.application.port.out.HumanUserRepository;
import com.opensocket.aievent.core.iam.identity.application.query.*;
import com.opensocket.aievent.core.iam.identity.domain.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamIdentityDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class MybatisHumanUserRepository implements HumanUserRepository {
    private final IamIdentityDao dao;
    public MybatisHumanUserRepository(IamIdentityDao dao){this.dao=dao;}
    public Optional<HumanUser> findById(UserId id){return Optional.ofNullable(dao.findUserById(id.value())).map(this::domain);}
    public Optional<HumanUser> findByNormalizedUsername(String value){return Optional.ofNullable(dao.findUserByNormalizedUsername(value)).map(this::domain);}
    public Optional<HumanUser> findByNormalizedEmail(String value){return Optional.ofNullable(dao.findUserByNormalizedEmail(value)).map(this::domain);}
    public boolean existsByNormalizedUsername(String value){return dao.countUserByNormalizedUsername(value)>0;}
    public boolean existsByNormalizedEmail(String value){return dao.countUserByNormalizedEmail(value)>0;}
    public HumanUser save(HumanUser user,long expected){int rows=expected==0?dao.insertUser(row(user)):dao.updateUser(row(user),expected); if(rows!=1)throw new IamOptimisticLockException("HumanUser",user.userId().value(),expected); return user;}
    public HumanUserPage search(SearchHumanUsersQuery q){var rows=dao.searchUsers(q.text(),q.status().map(Enum::name).orElse(null),q.cursor().orElse(null),q.limit()+1); boolean more=rows.size()>q.limit(); var used=more?rows.subList(0,q.limit()):rows; var items=used.stream().map(this::domain).toList(); return new HumanUserPage(items,more?Optional.of(items.get(items.size()-1).userId().value()):Optional.empty());}
    private HumanUser domain(Map<String,Object> r){String email=string(r,"email"); return HumanUser.reconstitute(new UserId(string(r,"userId")),new Username(string(r,"username")),email==null?Optional.empty():Optional.of(new EmailAddress(email)),string(r,"displayName"),AccountStatus.valueOf(string(r,"status")),UserCreationMode.valueOf(string(r,"creationMode")),instant(r,"createdAt"),instant(r,"updatedAt"),string(r,"createdBy"),string(r,"updatedBy"),string(r,"statusReason"),longValue(r,"version"));}
    private Map<String,Object> row(HumanUser u){Map<String,Object> m=new HashMap<>();m.put("userId",u.userId().value());m.put("username",u.username().value());m.put("normalizedUsername",u.username().normalizedValue());m.put("email",u.email().map(EmailAddress::value).orElse(null));m.put("normalizedEmail",u.email().map(EmailAddress::normalizedValue).orElse(null));m.put("displayName",u.displayName());m.put("status",u.status().name());m.put("creationMode",u.creationMode().name());m.put("statusReason",u.statusReason());m.put("createdAt",u.createdAt());m.put("updatedAt",u.updatedAt());m.put("createdBy",u.createdBy());m.put("updatedBy",u.updatedBy());m.put("version",u.version());return m;}
}
