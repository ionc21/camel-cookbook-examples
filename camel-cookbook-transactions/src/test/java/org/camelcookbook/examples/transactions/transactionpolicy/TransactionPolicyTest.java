package org.camelcookbook.examples.transactions.transactionpolicy;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sql.SqlComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.camelcookbook.examples.transactions.dao.AuditLogDao;
import org.camelcookbook.examples.transactions.dao.MessageDao;
import org.camelcookbook.examples.transactions.utils.EmbeddedDataSourceFactory;
import org.junit.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * Demonstrates the use of policies to scope transactions.
 */
public class TransactionPolicyTest extends CamelTestSupport {

    private AuditLogDao auditLogDao;
    private MessageDao messageDao;
    private DataSource dataSource;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new TransactionPolicyRouteBuilder();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        dataSource = EmbeddedDataSourceFactory.getDataSource("sql/schema.sql");

        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        registry.put("transactionManager", transactionManager);

        SpringTransactionPolicy propagationRequired = new SpringTransactionPolicy();
        propagationRequired.setTransactionManager(transactionManager);
        propagationRequired.setPropagationBehaviorName("PROPAGATION_REQUIRED");
        registry.put("PROPAGATION_REQUIRED", propagationRequired);

        auditLogDao = new AuditLogDao(dataSource);
        messageDao = new MessageDao(dataSource);

        CamelContext camelContext = new DefaultCamelContext(registry);
        SqlComponent sqlComponent = new SqlComponent();
        sqlComponent.setDataSource(dataSource);
        camelContext.addComponent("sql", sqlComponent);
        return camelContext;
    }

    @Test
    public void testFailureMock1() throws InterruptedException {
        String message = "ping";
        assertEquals(0, auditLogDao.getAuditCount(message));

        MockEndpoint mockOut1 = getMockEndpoint("mock:out1");
        mockOut1.whenAnyExchangeReceived(new ExceptionThrowingProcessor());
        mockOut1.message(0).body().isEqualTo(message);

        MockEndpoint mockOut2 = getMockEndpoint("mock:out2");
        mockOut2.setExpectedMessageCount(0);

        try {
            template.sendBody("direct:policies", message);
            fail();
        } catch (Exception e) {
            assertEquals("boom!", ExceptionUtils.getRootCause(e).getMessage());
        }

        assertMockEndpointsSatisfied();
        assertEquals(0, auditLogDao.getAuditCount(message));
        assertEquals(0, messageDao.getMessageCount(message));
    }


    @Test
    public void testFailureMock2() throws InterruptedException {
        String message = "ping";
        assertEquals(0, auditLogDao.getAuditCount(message));

        MockEndpoint mockOut1 = getMockEndpoint("mock:out1");
        mockOut1.setExpectedMessageCount(1);
        mockOut1.message(0).body().isEqualTo(message);

        MockEndpoint mockOut2 = getMockEndpoint("mock:out2");
        mockOut2.whenAnyExchangeReceived(new ExceptionThrowingProcessor());

        try {
            template.sendBody("direct:policies", message);
            fail();
        } catch (Exception e) {
            assertEquals("boom!", ExceptionUtils.getRootCause(e).getMessage());
        }

        assertMockEndpointsSatisfied();
        assertEquals(1, auditLogDao.getAuditCount(message));
        assertEquals(0, messageDao.getMessageCount(message));
    }

    @Test
    public void testSuccess() throws InterruptedException {
        String message = "ping";
        assertEquals(0, auditLogDao.getAuditCount(message));

        MockEndpoint mockOut1 = getMockEndpoint("mock:out1");
        mockOut1.setExpectedMessageCount(1);
        mockOut1.message(0).body().isEqualTo(message);

        MockEndpoint mockOut2 = getMockEndpoint("mock:out2");
        mockOut2.setExpectedMessageCount(1);
        mockOut2.message(0).body().isEqualTo(message);

        template.sendBody("direct:policies", message);

        assertMockEndpointsSatisfied();
        assertEquals(1, auditLogDao.getAuditCount(message));
        assertEquals(1, messageDao.getMessageCount(message));
    }

}
