package com.opensocket.aievent.core.iam.persistence.tenant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TenantTransactionMybatisInterceptorTest {
    private final TenantTransactionMybatisInterceptor interceptor = new TenantTransactionMybatisInterceptor();

    @AfterEach
    void cleanup() {
        IamTenantContextHolder.clear();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }


    @Test
    void agentProfileWriteRequiresTenantTransaction() {
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.opensocket.aievent.database.persistence.agent.dao.AgentGovernanceDao.upsertProfile");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, Map.of("profile", new Object())});

        assertThatThrownBy(() -> interceptor.intercept(invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_TRANSACTION_REQUIRED")
                .hasMessageContaining("AgentGovernanceDao.upsertProfile");
    }

    @Test
    void agentProfileWriteBindsRequestTenantForOwnershipTrigger() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.opensocket.aievent.database.persistence.agent.dao.AgentGovernanceDao.upsertProfile");

        Executor executor = mock(Executor.class);
        Transaction transaction = mock(Transaction.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(executor.getTransaction()).thenReturn(transaction);
        when(transaction.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, Map.of("profile", new Object())});
        when(invocation.getTarget()).thenReturn(executor);
        when(invocation.proceed()).thenReturn("ok");

        Object result = IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("tenant-agent-a", "root"),
                () -> {
                    try {
                        return interceptor.intercept(invocation);
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                }
        );

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("ok");
        verify(preparedStatement).setString(1, "tenant-agent-a");
        verify(preparedStatement).setString(2, "root");
        verify(preparedStatement).execute();
        verify(invocation).proceed();
    }

    @Test
    void agentProfileWriteRejectsMissingRequestTenantContextInsideTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.opensocket.aievent.database.persistence.agent.dao.AgentGovernanceDao.upsertProfile");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, Map.of("profile", new Object())});

        assertThatThrownBy(() -> interceptor.intercept(invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_CONTEXT_REQUIRED");
    }

    @Test
    void rejectsTenantMapperOutsideActualTransaction() {
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.example.IamTenantOrganizationDao.findDepartment");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, null});

        assertThatThrownBy(() -> interceptor.intercept(invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_TRANSACTION_REQUIRED");
    }

    @Test
    void requiresServerResolvedTenantContext() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.example.IamTenantOrganizationDao.findDepartment");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, null});

        assertThatThrownBy(() -> interceptor.intercept(invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_CONTEXT_REQUIRED");
    }


    @Test
    void recognizesTenantIdNestedInMybatisRowParamWithoutBindingException() {
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.example.IamRbacDao.insertBinding");

        MapperMethod.ParamMap<Object> parameters = new MapperMethod.ParamMap<>();
        Map<String, Object> row = Map.of("tenantId", "tenant-a", "bindingId", "binding-a");
        parameters.put("row", row);
        parameters.put("param1", row);

        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, parameters});

        assertThatThrownBy(() -> interceptor.intercept(invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_TRANSACTION_REQUIRED")
                .hasMessageContaining("IamRbacDao.insertBinding");
    }

    @Test
    void setsTenantContextForTenantIdNestedInMybatisRowParam() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.example.IamRbacDao.insertBinding");

        Map<String, Object> row = Map.of("tenantId", "tenant-a", "bindingId", "binding-a");
        MapperMethod.ParamMap<Object> parameters = new MapperMethod.ParamMap<>();
        parameters.put("row", row);
        parameters.put("param1", row);

        Executor executor = mock(Executor.class);
        Transaction transaction = mock(Transaction.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(executor.getTransaction()).thenReturn(transaction);
        when(transaction.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, parameters});
        when(invocation.getTarget()).thenReturn(executor);
        when(invocation.proceed()).thenReturn("ok");

        Object result = IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("tenant-a", "root"),
                () -> {
                    try {
                        return interceptor.intercept(invocation);
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                }
        );

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("ok");
        verify(preparedStatement).setString(1, "tenant-a");
        verify(preparedStatement).setString(2, "root");
        verify(preparedStatement).execute();
        verify(invocation).proceed();
    }

    @Test
    void ignoresRowWithoutTenantIdWithoutReadingMissingMybatisAlias() throws Throwable {
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.example.IamRbacDao.insertBinding");

        MapperMethod.ParamMap<Object> parameters = new MapperMethod.ParamMap<>();
        Map<String, Object> row = Map.of("bindingId", "instance-binding");
        parameters.put("row", row);
        parameters.put("param1", row);

        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, parameters});
        when(invocation.proceed()).thenReturn("ok");

        org.assertj.core.api.Assertions.assertThat(interceptor.intercept(invocation)).isEqualTo("ok");
        verify(invocation).proceed();
    }

    @Test
    void setsTransactionLocalTenantAndActorBeforeProceeding() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.example.IamTenantOrganizationDao.findDepartment");
        Executor executor = mock(Executor.class);
        Transaction transaction = mock(Transaction.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(executor.getTransaction()).thenReturn(transaction);
        when(transaction.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, null});
        when(invocation.getTarget()).thenReturn(executor);
        when(invocation.proceed()).thenReturn("ok");

        IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("tenant-a", "user-a"),
                () -> {
                    try {
                        interceptor.intercept(invocation);
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                    return null;
                }
        );

        verify(preparedStatement).setString(1, "tenant-a");
        verify(preparedStatement).setString(2, "user-a");
        verify(preparedStatement).execute();
        verify(invocation).proceed();
    }
    @Test
    void setsInstanceContextForPermissionReadinessMapper() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.example.IamRbacDao.summarizeEntryPoints");
        Executor executor = mock(Executor.class);
        Transaction transaction = mock(Transaction.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(executor.getTransaction()).thenReturn(transaction);
        when(transaction.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, null});
        when(invocation.getTarget()).thenReturn(executor);
        when(invocation.proceed()).thenReturn("ok");

        Object result = IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("INSTANCE", "permission-readiness"),
                () -> {
                    try {
                        return interceptor.intercept(invocation);
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                }
        );

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("ok");
        verify(preparedStatement).setString(1, "INSTANCE");
        verify(preparedStatement).setString(2, "permission-readiness");
        verify(preparedStatement).execute();
        verify(invocation).proceed();
    }


    @Test
    void rejectsUnlistedApiRuntimeProjectionWithTenantIdOutsideTransaction() {
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.example.IamApiRuntimeDao.tenantWorkspaceSummary");

        MapperMethod.ParamMap<Object> parameters = new MapperMethod.ParamMap<>();
        parameters.put("tenantId", "tenant-a");
        parameters.put("param1", "tenant-a");

        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, parameters});

        assertThatThrownBy(() -> interceptor.intercept(invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_TRANSACTION_REQUIRED")
                .hasMessageContaining("IamApiRuntimeDao.tenantWorkspaceSummary");
    }

    @Test
    void setsTenantContextForAnyApiRuntimeProjectionCarryingTenantId() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.example.IamApiRuntimeDao.tenantWorkspaceSummary");

        MapperMethod.ParamMap<Object> parameters = new MapperMethod.ParamMap<>();
        parameters.put("tenantId", "tenant-a");
        parameters.put("param1", "tenant-a");

        Executor executor = mock(Executor.class);
        Transaction transaction = mock(Transaction.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(executor.getTransaction()).thenReturn(transaction);
        when(transaction.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, parameters});
        when(invocation.getTarget()).thenReturn(executor);
        when(invocation.proceed()).thenReturn("ok");

        Object result = IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("tenant-a", "projection:tenant-workspace-summary"),
                () -> {
                    try {
                        return interceptor.intercept(invocation);
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                }
        );

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("ok");
        verify(preparedStatement).setString(1, "tenant-a");
        verify(preparedStatement).setString(2, "projection:tenant-workspace-summary");
        verify(preparedStatement).execute();
        verify(invocation).proceed();
    }

    @Test
    void propagatesExistingTenantContextForNoArgumentCredentialCatalogProjection() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.example.IamApiRuntimeDao.listCredentialApiProducts");

        Executor executor = mock(Executor.class);
        Transaction transaction = mock(Transaction.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(executor.getTransaction()).thenReturn(transaction);
        when(transaction.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, null});
        when(invocation.getTarget()).thenReturn(executor);
        when(invocation.proceed()).thenReturn("ok");

        Object result = IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("tenant-a", "projection:credential-governance-catalog"),
                () -> {
                    try {
                        return interceptor.intercept(invocation);
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                }
        );

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("ok");
        verify(preparedStatement).setString(1, "tenant-a");
        verify(preparedStatement).setString(2, "projection:credential-governance-catalog");
        verify(preparedStatement).execute();
        verify(invocation).proceed();
    }

    @Test
    void leavesContextFreeGlobalApiRuntimeLookupUnchanged() throws Throwable {
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.example.IamApiRuntimeDao.findUserByNormalizedUsername");

        MapperMethod.ParamMap<Object> parameters = new MapperMethod.ParamMap<>();
        parameters.put("normalizedUsername", "root");
        parameters.put("param1", "root");

        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, parameters});
        when(invocation.proceed()).thenReturn("ok");

        org.assertj.core.api.Assertions.assertThat(interceptor.intercept(invocation)).isEqualTo("ok");
        verify(invocation).proceed();
    }

    @Test
    void setsTenantContextForTenantScopedTaskMapper() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.opensocket.aievent.database.persistence.task.dao.TaskDao.insert");
        Executor executor = mock(Executor.class);
        Transaction transaction = mock(Transaction.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(executor.getTransaction()).thenReturn(transaction);
        when(transaction.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, Map.of("task", new Object())});
        when(invocation.getTarget()).thenReturn(executor);
        when(invocation.proceed()).thenReturn("ok");

        Object result = IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("tenant-a", "event-intake"),
                () -> {
                    try {
                        return interceptor.intercept(invocation);
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                });

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("ok");
        verify(preparedStatement).setString(1, "tenant-a");
        verify(preparedStatement).setString(2, "event-intake");
        verify(preparedStatement).execute();
        verify(invocation).proceed();
    }

    @Test
    void doesNotInventTenantContextForInstanceTaskScan() throws Throwable {
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.opensocket.aievent.database.persistence.task.dao.TaskDao.findOpenUpdatedBefore");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, Map.of()});
        when(invocation.proceed()).thenReturn("ok");

        Object result = IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("INSTANCE", "background-scan"),
                () -> {
                    try {
                        return interceptor.intercept(invocation);
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                });

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("ok");
        verify(invocation).proceed();
    }

    @Test
    void agentPolicyVersionBumpBindsTenantBeforeCredentialRotationWrites() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("com.opensocket.aievent.database.persistence.agent.dao.AgentGovernanceDao.bumpProfilePolicyVersion");

        Executor executor = mock(Executor.class);
        Transaction transaction = mock(Transaction.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(executor.getTransaction()).thenReturn(transaction);
        when(transaction.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{statement, Map.of(
                "agentId", "agent-a",
                "tenantId", "tenant-a",
                "expectedPolicyVersion", 7)});
        when(invocation.getTarget()).thenReturn(executor);
        when(invocation.proceed()).thenReturn(1);

        Object result = IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("tenant-a", "root"),
                () -> {
                    try {
                        return interceptor.intercept(invocation);
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                });

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo(1);
        verify(preparedStatement).setString(1, "tenant-a");
        verify(preparedStatement).setString(2, "root");
        verify(preparedStatement).execute();
        verify(invocation).proceed();
    }

}
