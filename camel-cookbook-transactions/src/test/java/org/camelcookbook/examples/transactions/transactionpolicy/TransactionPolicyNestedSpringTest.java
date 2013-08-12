package org.camelcookbook.examples.transactions.transactionpolicy;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sql.SqlComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.camelcookbook.examples.transactions.dao.AuditLogDao;
import org.camelcookbook.examples.transactions.dao.MessageDao;
import org.camelcookbook.examples.transactions.utils.EmbeddedDataSourceFactory;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * Demonstrates the use of nested transaction policies.
 */
public class TransactionPolicyNestedSpringTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("META-INF/spring/transactionPolicyNested-context.xml");
    }


    @Test
    public void testFailureMock1() throws InterruptedException {
        AuditLogDao auditLogDao = getMandatoryBean(AuditLogDao.class, "auditLogDao");
        MessageDao messageDao = getMandatoryBean(MessageDao.class, "messageDao");

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
        AuditLogDao auditLogDao = getMandatoryBean(AuditLogDao.class, "auditLogDao");
        MessageDao messageDao = getMandatoryBean(MessageDao.class, "messageDao");
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
        assertEquals(0, auditLogDao.getAuditCount(message));
        assertEquals(1, messageDao.getMessageCount(message));
    }

    @Test
    public void testSuccess() throws InterruptedException {
        AuditLogDao auditLogDao = getMandatoryBean(AuditLogDao.class, "auditLogDao");
        MessageDao messageDao = getMandatoryBean(MessageDao.class, "messageDao");

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
