/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.messaging.jms.clustering;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.management.ObjectName;
import javax.naming.InitialContext;

import org.jboss.jms.client.FailoverEvent;
import org.jboss.jms.client.JBossConnection;
import org.jboss.jms.client.delegate.ClientConnectionDelegate;
import org.jboss.jms.client.remoting.JMSRemotingConnection;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.aop.PoisonInterceptor;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 * @version <tt>$Revision: 6165 $</tt>
 *
 * $Id: FailoverTest.java 6165 2009-03-25 16:28:10Z gaohoward $
 */
public class FailoverTest extends ClusteringTestBase
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   public FailoverTest(String name)
   {
      super(name);
   }

   // Public ---------------------------------------------------------------------------------------

   //https://jira.jboss.org/jira/browse/JBMESSAGING-1547
   //the dead lock happens when node0 is trying to deliver while node1 
   //was shutdown.
   public void testMessageSuckerStopDeadLock() throws Exception
   {
      Connection conn = null;

      try 
      {
         conn = createConnectionOnServer(cf, 0);
         conn.start();

         Session session0 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         //killing node 1
         ServerManagement.kill(1);

         //this will cause the MessagingQueue.informSuckers() called.
         //there is a chance when nodeLeft is detected, MessageSucker.stop() is being called
         //just to collide with the informSuckers() call.
         for (int i = 0; i < 10000; i++)
         {
            MessageConsumer consumer1 = session0.createConsumer(queue[0]);
            consumer1.close();
            Thread.yield();
         }
         
         //if the deak lock happens, server1 won't be delivering any messages.
         MessageProducer prod = session0.createProducer(queue[0]);
         TextMessage msg = session0.createTextMessage("deadlock");
         prod.send(msg);
         
         MessageConsumer cons = session0.createConsumer(queue[0]);
         msg = (TextMessage)cons.receive(5000);
         
         assertNotNull(msg);
         assertEquals("deadlock", msg.getText());
         
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }
   
   public void testSimpleConnectionFailover() throws Exception
   {
   	//We need to sleep and relookup the connection factory due to http://jira.jboss.com/jira/browse/JBMESSAGING-1038
   	//remove this when this task is complete
   	Thread.sleep(2000);

   	ConnectionFactory theCF = (ConnectionFactory)ic[0].lookup("/ClusteredConnectionFactory");

      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(theCF, 1);
         conn.start();

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         assertEquals(0, getServerId(conn));
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testSessionFailover() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);
         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         // use the old session to send/receive a message
         session.createProducer(queue[0]).send(session.createTextMessage("blik"));

         TextMessage m = (TextMessage)session.createConsumer(queue[0]).receive(5000);

         assertNotNull(m);

         assertEquals("blik", m.getText());
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testProducerFailover() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = session.createProducer(queue[1]);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         assertEquals(0, getServerId(conn));

         // send a message, send it with the failed over producer and make sure I can receive it
         Message m = session.createTextMessage("clik");
         prod.send(m);

         MessageConsumer cons = session.createConsumer(queue[0]);
         TextMessage tm = (TextMessage)cons.receive(2000);

         assertNotNull(tm);
         assertEquals("clik", tm.getText());
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testConsumerFailoverWithConnectionStopped() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = session.createConsumer(queue[1]);
         MessageProducer prod = session.createProducer(queue[1]);

         // send a message (connection is stopped, so it will stay on the server), and I expect
         // to receive it with the failed-over consumer after crash

         Message m = session.createTextMessage("plik");
         prod.send(m);


         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         // activate the failed-over consumer
         conn.start();

         TextMessage rm = (TextMessage)cons.receive(2000);
         assertNotNull(rm);
         assertEquals("plik", rm.getText());
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testConsumerFailoverWithConnectionStarted() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = session.createConsumer(queue[1]);
         MessageProducer prod = session.createProducer(queue[1]);

         // start the connection, so the message makes it to the client-side MessageCallbackHandler
         // buffer

         conn.start();

         Message m = session.createTextMessage("nik");
         prod.send(m);

         // wait a bit so the message makes it to the client
         Thread.sleep(2000);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         TextMessage rm = (TextMessage)cons.receive(2000);
         assertNotNull(rm);
         assertEquals("nik", rm.getText());

      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testBrowserFailoverSendMessagesPreFailure() throws Exception
   {
      Connection conn = null;

      try
      {
         // create a connection to node 1
         conn = createConnectionOnServer(cf, 1);

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         QueueBrowser browser = session.createBrowser(queue[1]);

         Enumeration en = browser.getEnumeration();
         assertFalse(en.hasMoreElements());

         // send one persistent and one non-persistent message

         MessageProducer prod = session.createProducer(queue[1]);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);
         prod.send(session.createTextMessage("click"));
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.send(session.createTextMessage("clack"));

         //Give time for the NP message to arrive
         Thread.sleep(2000);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         en = browser.getEnumeration();

         // we expect to only be able to browse the persistent message
         assertTrue(en.hasMoreElements());
         TextMessage tm = (TextMessage)en.nextElement();
         assertEquals("click", tm.getText());

         assertFalse(en.hasMoreElements());

         removeAllMessages(queue[1].getQueueName(), true, 0);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testBrowserFailoverSendMessagesPostFailure() throws Exception
   {
      Connection conn = null;

      try
      {
         // create a connection to node 1
         conn = createConnectionOnServer(cf, 1);

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         QueueBrowser browser = session.createBrowser(queue[1]);

         Enumeration en = browser.getEnumeration();
         assertFalse(en.hasMoreElements());

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         // send one persistent and one non-persistent message

         MessageProducer prod = session.createProducer(queue[1]);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);
         prod.send(session.createTextMessage("click"));
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.send(session.createTextMessage("clack"));

         //Give time for the NP message to arrive
         Thread.sleep(2000);

         en = browser.getEnumeration();

         // we expect to be able to browse persistent and non-persistent messages
         Set texts = new HashSet();

         assertTrue(en.hasMoreElements());
         TextMessage tm = (TextMessage)en.nextElement();
         texts.add(tm.getText());

         assertTrue(en.hasMoreElements());
         tm = (TextMessage)en.nextElement();
         texts.add(tm.getText());

         assertFalse(en.hasMoreElements());

         assertTrue(texts.contains("click"));
         assertTrue(texts.contains("clack"));

         removeAllMessages(queue[1].getQueueName(), true, 0);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   /**
    * Sending one persistent message.
    */
   public void testSessionWithOneTransactedPersistentMessageFailover() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         conn.start();

         Session session = conn.createSession(true, Session.SESSION_TRANSACTED);

         // send 2 transacted messages (one persistent and one non-persistent) but don't commit
         MessageProducer prod = session.createProducer(queue[1]);

         prod.setDeliveryMode(DeliveryMode.PERSISTENT);
         prod.send(session.createTextMessage("clik-persistent"));

         // close the producer
         prod.close();

         log.debug("producer closed");

         // create a consumer on the same local queue (creating a consumer AFTER failover will end
         // up getting messages from a local queue, not a failed over queue; at least until
         // redistribution is implemented.

         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = session2.createConsumer(queue[1]);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         // commit the failed-over session
         session.commit();

         // make sure messages made it to the queue

         TextMessage tm = (TextMessage)cons.receive(2000);
         assertNotNull(tm);
         assertEquals("clik-persistent", tm.getText());
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   /**
    * Sending one non-persistent message.
    */
   public void testSessionWithOneTransactedNonPersistentMessageFailover() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         conn.start();

         Session session = conn.createSession(true, Session.SESSION_TRANSACTED);

         MessageProducer prod = session.createProducer(queue[1]);

         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.send(session.createTextMessage("clik-non-persistent"));

         // close the producer
         prod.close();

         log.debug("producer closed");

         // create a consumer on the same local queue (creating a consumer AFTER failover will end
         // up getting messages from a local queue, not a failed over queue; at least until
         // redistribution is implemented.

         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = session2.createConsumer(queue[1]);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete

         assertEquals(0, getServerId(conn));

         // commit the failed-over session
         session.commit();

         // make sure messages made it to the queue

         TextMessage tm = (TextMessage)cons.receive(2000);
         assertNotNull(tm);
         assertEquals("clik-non-persistent", tm.getText());
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   /**
    * Sending 2 non-persistent messages.
    */
   public void testSessionWithTwoTransactedNonPersistentMessagesFailover() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         conn.start();

         Session session = conn.createSession(true, Session.SESSION_TRANSACTED);

         // send 2 transacted messages (one persistent and one non-persistent) but don't commit
         MessageProducer prod = session.createProducer(queue[1]);

         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.send(session.createTextMessage("clik-non-persistent"));
         prod.send(session.createTextMessage("clak-non-persistent"));

         // close the producer
         prod.close();

         log.debug("producer closed");

         // create a consumer on the same local queue (creating a consumer AFTER failover will end
         // up getting messages from a local queue, not a failed over queue; at least until
         // redistribution is implemented.

         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = session2.createConsumer(queue[1]);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         // commit the failed-over session
         session.commit();

         // make sure messages made it to the queue

         TextMessage tm = (TextMessage)cons.receive(2000);
         assertNotNull(tm);
         assertEquals("clik-non-persistent", tm.getText());

         tm = (TextMessage)cons.receive(2000);
         assertNotNull(tm);
         assertEquals("clak-non-persistent", tm.getText());
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   /**
    * Sending 2 persistent messages.
    */
   public void testSessionWithTwoTransactedPersistentMessagesFailover() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         conn.start();

         Session session = conn.createSession(true, Session.SESSION_TRANSACTED);

         // send 2 transacted messages (one persistent and one non-persistent) but don't commit
         MessageProducer prod = session.createProducer(queue[1]);

         prod.setDeliveryMode(DeliveryMode.PERSISTENT);
         prod.send(session.createTextMessage("clik-persistent"));
         prod.send(session.createTextMessage("clak-persistent"));

         // close the producer
         prod.close();

         log.debug("producer closed");

         // create a consumer on the same local queue (creating a consumer AFTER failover will end
         // up getting messages from a local queue, not a failed over queue; at least until
         // redistribution is implemented.

         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = session2.createConsumer(queue[1]);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         // commit the failed-over session
         session.commit();

         // make sure messages made it to the queue

         TextMessage tm = (TextMessage)cons.receive(2000);
         assertNotNull(tm);
         assertEquals("clik-persistent", tm.getText());

         tm = (TextMessage)cons.receive(2000);
         assertNotNull(tm);
         assertEquals("clak-persistent", tm.getText());

      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   /**
    * Sending a mix of persistent and non-persistent messages.
    */
   public void testSessionWithTwoTransactedMixedMessagesFailover() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         conn.start();

         Session session = conn.createSession(true, Session.SESSION_TRANSACTED);

         // send 2 transacted messages (one persistent and one non-persistent) but don't commit
         MessageProducer prod = session.createProducer(queue[1]);

         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.send(session.createTextMessage("clik-non-persistent"));

         prod.setDeliveryMode(DeliveryMode.PERSISTENT);
         prod.send(session.createTextMessage("clak-persistent"));

         // close the producer
         prod.close();

         log.debug("producer closed");

         // create a consumer on the same local queue (creating a consumer AFTER failover will end
         // up getting messages from a local queue, not a failed over queue; at least until
         // redistribution is implemented.

         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = session2.createConsumer(queue[1]);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         // commit the failed-over session
         session.commit();

         // make sure messages made it to the queue

         TextMessage tm = (TextMessage)cons.receive(2000);
         assertNotNull(tm);
         assertEquals("clik-non-persistent", tm.getText());

         tm = (TextMessage)cons.receive(2000);
         assertNotNull(tm);
         assertEquals("clak-persistent", tm.getText());
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testSessionWithAcknowledgmentsFailover() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         conn.start();

         Session session = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         // send 2 messages (one persistent and one non-persistent)

         MessageProducer prod = session.createProducer(queue[1]);

         prod.setDeliveryMode(DeliveryMode.PERSISTENT);
         prod.send(session.createTextMessage("clik-persistent"));
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.send(session.createTextMessage("clak-non-persistent"));

         // close the producer
         prod.close();

         // create a consumer and receive messages, but don't acknowledge

         MessageConsumer cons = session.createConsumer(queue[1]);
         TextMessage clik = (TextMessage)cons.receive(2000);
         assertEquals("clik-persistent", clik.getText());
         TextMessage clak = (TextMessage)cons.receive(2000);
         assertEquals("clak-non-persistent", clak.getText());

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         // acknowledge the messages
         clik.acknowledge();
         clak.acknowledge();

         // make sure no messages are left in the queue
         checkEmpty(queue[1], 0);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testTransactedSessionWithAcknowledgmentsCommitOnFailover() throws Exception
   {
      Connection conn = null;

      try
      {
         // create a connection to node 1
         conn = this.createConnectionOnServer(cf, 1);

         conn.start();

         assertEquals(1, getServerId(conn));

         Session session = conn.createSession(true, Session.SESSION_TRANSACTED);

         // send 2 messages (one persistent and one non-persistent)

         MessageProducer prod = session.createProducer(queue[1]);

         prod.setDeliveryMode(DeliveryMode.PERSISTENT);
         prod.send(session.createTextMessage("clik-persistent"));
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.send(session.createTextMessage("clak-non-persistent"));

         session.commit();

         // close the producer

         prod.close();

         // create a consumer and receive messages, but don't acknowledge

         MessageConsumer cons = session.createConsumer(queue[1]);
         TextMessage clik = (TextMessage)cons.receive(2000);
         assertEquals("clik-persistent", clik.getText());
         TextMessage clak = (TextMessage)cons.receive(2000);
         assertEquals("clak-non-persistent", clak.getText());

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         // acknowledge the messages
         session.commit();

         // make sure no messages are left in the queue
         checkEmpty(queue[1], 0);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }


   public void testTransactedSessionWithAcknowledgmentsRollbackOnFailover() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         conn.start();

         Session session = conn.createSession(true, Session.SESSION_TRANSACTED);

         // send 2 messages (one persistent and one non-persistent)

         MessageProducer prod = session.createProducer(queue[1]);

         prod.setDeliveryMode(DeliveryMode.PERSISTENT);
         prod.send(session.createTextMessage("clik-persistent"));
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.send(session.createTextMessage("clak-non-persistent"));

         session.commit();

         // close the producer
         prod.close();

         // create a consumer and receive messages, but don't acknowledge

         MessageConsumer cons = session.createConsumer(queue[1]);
         TextMessage clik = (TextMessage)cons.receive(2000);
         assertEquals("clik-persistent", clik.getText());
         TextMessage clak = (TextMessage)cons.receive(2000);
         assertEquals("clak-non-persistent", clak.getText());

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         session.rollback();

         TextMessage m = (TextMessage)cons.receive(2000);
         assertNotNull(m);
         assertEquals("clik-persistent", m.getText());

         session.commit();

         checkEmpty(queue[1], 0);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }


   public void testFailoverListener() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);
         conn.start();

         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         // kill node 1

         ServerManagement.kill(1);

         FailoverEvent event = failoverListener.getEvent(30000);

         assertNotNull(event);
         assertEquals(FailoverEvent.FAILURE_DETECTED, event.getType());

         event = failoverListener.getEvent(30000);

         assertNotNull(event);
         assertEquals(FailoverEvent.FAILOVER_STARTED, event.getType());

         event = failoverListener.getEvent(30000);

         assertNotNull(event);
         assertEquals(FailoverEvent.FAILOVER_COMPLETED, event.getType());
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
 }

   public void testFailoverMessageOnServer() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);
         conn.start();

         SimpleFailoverListener listener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(listener);

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = session.createProducer(queue[1]);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);
         MessageConsumer cons = session.createConsumer(queue[0]);

         // send a message

         prod.send(session.createTextMessage("blip"));

         // kill node 1

         ServerManagement.kill(1);

         // wait until the failure (not the completion of client-side failover) is detected

         while(true)
         {
            FailoverEvent event = listener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_STARTED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_STARTED event");
            }
         }

         // start to receive the very next moment the failure is detected. This way, we also
         // test the client-side failover valve

         TextMessage tm = (TextMessage)cons.receive(60000);

         assertNotNull(tm);
         assertEquals("blip", tm.getText());

         tm = (TextMessage)cons.receive(1000);
         assertNull(tm);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testFailoverMessageOnServer2() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);
         conn.start();

         SimpleFailoverListener listener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(listener);

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = session.createProducer(queue[1]);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         // send a message

         prod.send(session.createTextMessage("blip"));

         // kill node 1

         ServerManagement.kill(1);

         // wait until the failure (not the completion of client-side failover) is detected

         assertEquals(FailoverEvent.FAILURE_DETECTED, listener.getEvent(60000).getType());

         // create a consumer the very next moment the failure is detected. This way, we also
         // test the client-side failover valve

         MessageConsumer cons = session.createConsumer(queue[1]);

         // we must receive the message

         TextMessage tm = (TextMessage)cons.receive(60000);
         assertEquals("blip", tm.getText());
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testSimpleFailover() throws Exception
   {
      simpleFailover(null, null);
   }

   public void testSimpleFailoverUserPassword() throws Exception
   {
      final String testTopicConf =
         "<security>" +
            "<role name=\"guest\" read=\"true\" write=\"true\"/>" +
            "<role name=\"publisher\" read=\"true\" write=\"true\" create=\"false\"/>" +
            "<role name=\"durpublisher\" read=\"true\" write=\"true\" create=\"true\"/>" +
         "</security>";

      ServerManagement.configureSecurityForDestination(0, "testDistributedQueue", testTopicConf);
      ServerManagement.configureSecurityForDestination(1, "testDistributedQueue", testTopicConf);

      simpleFailover("john", "needle");
   }

   public void testMethodSmackingIntoFailure() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         // we "cripple" the remoting connection by removing ConnectionListener. This way, failures
         // cannot be "cleanly" detected by the client-side pinger, and we'll fail on an invocation
         JMSRemotingConnection rc = ((ClientConnectionDelegate)((JBossConnection)conn).
            getDelegate()).getRemotingConnection();
         rc.removeConnectionListener();

         ServerManagement.kill(1);

         conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testFailureInTheMiddleOfAnInvocation() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         // we "cripple" the remoting connection by removing ConnectionListener. This way, failures
         // cannot be "cleanly" detected by the client-side pinger, and we'll fail on an invocation
         JMSRemotingConnection rc = ((ClientConnectionDelegate)((JBossConnection)conn).
            getDelegate()).getRemotingConnection();
         rc.removeConnectionListener();

         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         // poison the server
         ServerManagement.poisonTheServer(1, PoisonInterceptor.TYPE_CREATE_SESSION);

         // this invocation will halt the server ...
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // ... and hopefully it be failed over

         MessageConsumer cons = session.createConsumer(queue[0]);
         MessageProducer prod = session.createProducer(queue[0]);

         prod.send(session.createTextMessage("after-poison"));

         conn.start();

         TextMessage tm = (TextMessage)cons.receive(2000);

         assertNotNull(tm);
         assertEquals("after-poison", tm.getText());

      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testSimpleFailoverWithRemotingListenerEnabled() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);
         conn.start();

         Session s1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer c1 = s1.createConsumer(queue[1]);
         MessageProducer p1 = s1.createProducer(queue[1]);
         p1.setDeliveryMode(DeliveryMode.PERSISTENT);

         // send a message

         p1.send(s1.createTextMessage("blip"));

         // kill node 1

         ServerManagement.kill(1);

         try
         {
            ic[1].lookup("queue"); // looking up anything
            fail("The server still alive, kill didn't work yet");
         }
         catch (Exception e)
         {
         }

         // we must receive the message

         TextMessage tm = (TextMessage)c1.receive(3000);
         assertEquals("blip", tm.getText());

      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testFailureRightAfterACK() throws Exception
   {
      failureOnInvocation(PoisonInterceptor.FAIL_AFTER_ACKNOWLEDGE_DELIVERY);
   }

   public void testFailureRightBeforeACK() throws Exception
   {
      failureOnInvocation(PoisonInterceptor.FAIL_BEFORE_ACKNOWLEDGE_DELIVERY);
   }

   public void testFailureRightBeforeSend() throws Exception
   {
      failureOnInvocation(PoisonInterceptor.FAIL_BEFORE_SEND);
   }

   public void testFailureRightAfterSend() throws Exception
   {
      failureOnInvocation(PoisonInterceptor.FAIL_AFTER_SEND);
   }

   public void testFailureRightAfterSendTransaction() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = this.createConnectionOnServer(cf, 1);

         assertEquals(1, getServerId(conn));

         // we "cripple" the remoting connection by removing ConnectionListener. This way, failures
         // cannot be "cleanly" detected by the client-side pinger, and we'll fail on an invocation
         JMSRemotingConnection rc = ((ClientConnectionDelegate)((JBossConnection)conn).
            getDelegate()).getRemotingConnection();
         rc.removeConnectionListener();

         // poison the server
         ServerManagement.poisonTheServer(1, PoisonInterceptor.FAIL_AFTER_SENDTRANSACTION);

         Session session = conn.createSession(true, Session.SESSION_TRANSACTED);

         conn.start();

         MessageProducer producer = session.createProducer(queue[0]);

         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         MessageConsumer consumer = session.createConsumer(queue[0]);

         producer.send(session.createTextMessage("before-poison1"));
         producer.send(session.createTextMessage("before-poison2"));
         producer.send(session.createTextMessage("before-poison3"));
         session.commit();

         Thread.sleep(2000);

         for (int i = 1; i <= 3; i++)
         {
            TextMessage tm = (TextMessage) consumer.receive(5000);

            assertNotNull(tm);

            assertEquals("before-poison" + i, tm.getText());
         }

         assertNull(consumer.receive(3000));

         session.commit();
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testFailureRightBeforeSendTransaction() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = this.createConnectionOnServer(cf, 1);

         assertEquals(1, getServerId(conn));

         // we "cripple" the remoting connection by removing ConnectionListener. This way, failures
         // cannot be "cleanly" detected by the client-side pinger, and we'll fail on an invocation
         JMSRemotingConnection rc = ((ClientConnectionDelegate)((JBossConnection)conn).
            getDelegate()).getRemotingConnection();
         rc.removeConnectionListener();

         // poison the server
         ServerManagement.poisonTheServer(1, PoisonInterceptor.FAIL_BEFORE_SENDTRANSACTION);

         Session session = conn.createSession(true, Session.SESSION_TRANSACTED);

         conn.start();

         MessageProducer producer = session.createProducer(queue[0]);

         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         MessageConsumer consumer = session.createConsumer(queue[0]);

         producer.send(session.createTextMessage("before-poison1"));
         producer.send(session.createTextMessage("before-poison2"));
         producer.send(session.createTextMessage("before-poison3"));
         session.commit();

         Thread.sleep(2000);

         for (int i = 1; i <= 3; i++)
         {
            TextMessage tm = (TextMessage) consumer.receive(5000);

            assertNotNull(tm);

            assertEquals("before-poison" + i, tm.getText());
         }

         assertNull(consumer.receive(3000));

         session.commit();
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }



   public void testCloseConsumer() throws Exception
   {
      Connection conn0 = null;
      Connection conn1 = null;

      try
      {
         conn0 = createConnectionOnServer(cf, 0);

         // Objects Server1
         conn1 = createConnectionOnServer(cf, 1);

         assertEquals(1, getServerId(conn1));

         JMSRemotingConnection rc = ((ClientConnectionDelegate)((JBossConnection)conn1).
            getDelegate()).getRemotingConnection();
         rc.removeConnectionListener();

         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer consumer = session1.createConsumer(queue[1]);

         ServerManagement.kill(1);

         consumer.close();
      }
      finally
      {
         if (conn1 != null)
         {
            conn1.close();
         }

         if (conn0 != null)
         {
            conn0.close();
         }
      }
   }

   public void testCloseBrowser() throws Exception
   {
      Connection conn0 = null;
      Connection conn1 = null;

      try
      {
         conn0 = createConnectionOnServer(cf, 0);

         // Objects Server1
         conn1 = createConnectionOnServer(cf, 1);

         assertEquals(1, getServerId(conn1));

         JMSRemotingConnection rc = ((ClientConnectionDelegate)((JBossConnection)conn1).
            getDelegate()).getRemotingConnection();
         rc.removeConnectionListener();

         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         QueueBrowser browser = session1.createBrowser(queue[1]);

         ServerManagement.kill(1);

         browser.close();
      }
      finally
      {
         if (conn1 != null)
         {
            conn1.close();
         }

         if (conn0 != null)
         {
            conn0.close();
         }
      }
   }


   public void testCloseSession() throws Exception
   {
      Connection conn0 = null;
      Connection conn1 = null;

      try
      {
         conn0 = createConnectionOnServer(cf, 0);

         conn1 = createConnectionOnServer(cf, 1);

         assertEquals(1, getServerId(conn1));

         JMSRemotingConnection rc = ((ClientConnectionDelegate)((JBossConnection)conn1).
            getDelegate()).getRemotingConnection();
         rc.removeConnectionListener();

         Session session = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         ServerManagement.kill(1);

         session.close();
      }
      finally
      {
         if (conn1 != null)
         {
            conn1.close();
         }

         if (conn0 != null)
         {
            conn0.close();
         }
      }
   }

   public void testCloseConnection() throws Exception
   {
      Connection conn0 = null;
      Connection conn1 = null;

      try
      {
         conn0 = createConnectionOnServer(cf, 0);

         conn1 = createConnectionOnServer(cf, 1);

         assertEquals(1, getServerId(conn1));

         JMSRemotingConnection rc = ((ClientConnectionDelegate)((JBossConnection)conn1).
            getDelegate()).getRemotingConnection();
         rc.removeConnectionListener();

         ServerManagement.kill(1);

         conn1.close();
      }
      finally
      {
         if (conn0 != null)
         {
            conn0.close();
         }
      }
   }



   public void testDurableSubscriptionFailover() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         conn.setClientID("myclientid1");

         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         MessageConsumer cons = session.createDurableSubscriber(topic[1], "mysub1");

         MessageProducer prod = session.createProducer(topic[1]);

         for (int i = 0; i < 5; i++)
         {
            TextMessage tm = session.createTextMessage("message" + i);

            prod.send(tm);
         }

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         for (int i = 5; i < 10; i++)
         {
            TextMessage tm = session.createTextMessage("message" + i);

            prod.send(tm);
         }

         for (int i = 0; i < 10; i++)
         {
            TextMessage tm = (TextMessage)cons.receive(30000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         cons.close();

         session.unsubscribe("mysub1");
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testDurableSubscriptionFailoverTwosubscribers() throws Exception
   {
      Connection conn = null;

      Connection conn0 = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         conn.setClientID("myclientid1");

         conn.start();

         conn0 = this.createConnectionOnServer(cf, 0);

         //same client id
         conn0.setClientID("myclientid1");

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session session0 = conn0.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         MessageConsumer cons = session.createDurableSubscriber(topic[1], "mysub1");

         //Durable sub on different node with same client id and sub name
         MessageConsumer cons0 = session0.createDurableSubscriber(topic[0], "mysub1");

         MessageProducer prod = session.createProducer(topic[1]);

         for (int i = 0; i < 5; i++)
         {
            TextMessage tm = session.createTextMessage("message" + i);

            prod.send(tm);
         }

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         for (int i = 5; i < 10; i++)
         {
            TextMessage tm = session.createTextMessage("message" + i);

            prod.send(tm);
         }

         for (int i = 0; i < 10; i++)
         {
            TextMessage tm = (TextMessage)cons.receive(30000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         cons.close();

         cons0.close();

         session.unsubscribe("mysub1");
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }

         if (conn0 != null)
         {
            conn0.close();
         }
      }
   }


   public void testDurableSubscriptionFailoverWithClientIDOnConnectionFactory() throws Exception
   {
      Connection conn = null;

      final String clientID = "ooble";

      ServerManagement.getServer(0).deployConnectionFactory("jboss.messaging.connectionfactory:service=WibbleConnectionFactory",
                                               new String[] { "/WibbleCF"},
                                               true, true, clientID);
      ServerManagement.getServer(1).deployConnectionFactory("jboss.messaging.connectionfactory:service=WibbleConnectionFactory",
            new String[] { "/WibbleCF"},
            true, true, clientID);

      ConnectionFactory myCF = (ConnectionFactory)ic[0].lookup("/WibbleCF");

      try
      {
         conn = createConnectionOnServer(myCF, 1);

         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         MessageConsumer cons = session.createDurableSubscriber(topic[1], "mysub1");

         MessageProducer prod = session.createProducer(topic[1]);

         for (int i = 0; i < 5; i++)
         {
            TextMessage tm = session.createTextMessage("message" + i);

            prod.send(tm);
         }

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete
         assertEquals(0, getServerId(conn));

         for (int i = 5; i < 10; i++)
         {
            TextMessage tm = session.createTextMessage("message" + i);

            prod.send(tm);
         }

         for (int i = 0; i < 10; i++)
         {
            TextMessage tm = (TextMessage)cons.receive(30000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         cons.close();

         session.unsubscribe("mysub1");
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }

         ServerManagement.getServer(0).undeployConnectionFactory(new ObjectName("jboss.messaging.connectionfactory:service=WibbleConnectionFactory"));
      }
   }

   public void testFailoverDeliveryRecoveryTransacted() throws Exception
   {
      Connection conn0 = null;
      Connection conn1 = null;

      try
      {
         conn0 = this.createConnectionOnServer(cf, 0);

         assertEquals(0, ((JBossConnection)conn0).getServerID());

         // Objects Server1
         conn1 = this.createConnectionOnServer(cf, 1);

         assertEquals(1, ((JBossConnection)conn1).getServerID());

         Session session1 = conn1.createSession(true, Session.SESSION_TRANSACTED);

         Session session2 = conn1.createSession(true, Session.SESSION_TRANSACTED);

         MessageConsumer cons1 = session1.createConsumer(queue[1]);

         MessageConsumer cons2 = session2.createConsumer(queue[1]);

         MessageProducer prod = session1.createProducer(queue[1]);

         conn1.start();

         TextMessage tm1 = session1.createTextMessage("message1");

         TextMessage tm2 = session1.createTextMessage("message2");

         TextMessage tm3 = session1.createTextMessage("message3");

         prod.send(tm1);

         prod.send(tm2);

         prod.send(tm3);

         session1.commit();

         TextMessage rm1 = (TextMessage)cons1.receive(3000);

         assertNotNull(rm1);

         assertEquals(tm1.getText(), rm1.getText());

         TextMessage rm2 = (TextMessage)cons2.receive(3000);

         assertNotNull(rm2);

         assertEquals(tm2.getText(), rm2.getText());

         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn1).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }

         // failover complete

         //now commit

         session1.commit();

         session2.commit();

         session1.close();

         session2.close();;

         Session session3 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons3 = session3.createConsumer(queue[0]);

         TextMessage rm3 = (TextMessage)cons3.receive(2000);

         assertNotNull(rm3);

         assertEquals(tm3.getText(), rm3.getText());

         checkEmpty(queue[1], 0);
      }
      finally
      {
         if (conn1 != null)
         {
            conn1.close();
         }

         if (conn0 != null)
         {
            conn0.close();
         }
      }
   }

   /**
    * deploy a distributed queue on only one node and then perform failover
    * The failover for other nodes should not be affected.
    * https://jira.jboss.org/jira/browse/JBMESSAGING-1457
    */
   public void testFailoverWithSingleDistributedTargetNoMessage() throws Exception
   {
      Connection conn = null;

      try
      {
         ServerManagement.deployQueue("singleDeployedDistributedQueue", 1);

         conn = createConnectionOnServer(cf, 1);

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = session.createProducer(queue[1]);

         Message m = session.createTextMessage("blimey");
         prod.send(m);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }
         
         MessageConsumer cons = session.createConsumer(queue[1]);

         // failover complete
         assertEquals(0, getServerId(conn));

         conn.start();
         
         TextMessage rm = (TextMessage)cons.receive(2000);
         assertNotNull(rm);
         assertEquals("blimey", rm.getText());

      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   /**
    * deploy a distributed queue on only one node and send some messages, then perform failover
    * The failover for other nodes should not be affected.
    * https://jira.jboss.org/jira/browse/JBMESSAGING-1457
    */
   public void testFailoverWithSingleDistributedTargetWithMessage() throws Exception
   {
      Connection conn = null;
      Connection conn1 = null;

      try
      {
         ServerManagement.deployQueue("singleDeployedDistributedQueue", 1);
         Queue lameClusteredQueue = (Queue)ic[1].lookup("queue/singleDeployedDistributedQueue");

         conn = createConnectionOnServer(cf, 1);

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = session.createProducer(queue[1]);
         MessageProducer prod1 = session.createProducer(lameClusteredQueue);

         Message m = session.createTextMessage("blimey");
         prod.send(m);
         Message m1 = session.createTextMessage("message-to-lose");
         prod1.send(m1);

         // register a failover listener
         SimpleFailoverListener failoverListener = new SimpleFailoverListener();
         ((JBossConnection)conn).registerFailoverListener(failoverListener);

         ServerManagement.kill(1);

         // wait for the client-side failover to complete

         while(true)
         {
            FailoverEvent event = failoverListener.getEvent(30000);
            if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
            {
               break;
            }
            if (event == null)
            {
               fail("Did not get expected FAILOVER_COMPLETED event");
            }
         }
         
         MessageConsumer cons = session.createConsumer(queue[1]);

         // failover complete
         assertEquals(0, getServerId(conn));

         conn.start();
         
         TextMessage rm = (TextMessage)cons.receive(2000);
         assertNotNull(rm);
         assertEquals("blimey", rm.getText());
         
         //simulate restarting node 1, do not clean db
         ServerManagement.start(1, config, overrides, false);
         ServerManagement.deployQueue("singleDeployedDistributedQueue", 1);
         conn1 = createConnectionOnServer(cf, 1);
         ic[1] = new InitialContext(ServerManagement.getJNDIEnvironment(1));
         
         lameClusteredQueue = (Queue)ic[1].lookup("queue/singleDeployedDistributedQueue");
         
         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         conn1.start();

         MessageConsumer cons1 = session1.createConsumer(lameClusteredQueue);
         TextMessage rm1 = (TextMessage)cons1.receive(2000);
         assertNotNull(rm1);
         assertEquals("message-to-lose", rm1.getText());
         
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
         if (conn1 != null)
         {
            conn1.close();
         }
      }
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected void setUp() throws Exception
   {
      nodeCount = 2;

      super.setUp();
   }

   // Private --------------------------------------------------------------------------------------

   private void simpleFailover(String userName, String password) throws Exception
   {
      Connection conn = null;

      try
      {
         if (userName!=null)
         {
            conn = createConnectionOnServer(cf, 1, userName, password);
         }
         else
         {
            conn = createConnectionOnServer(cf, 1);
         }

         conn.start();

         // Disable Lease for this test.. as the ValveAspect should capture this
         getConnectionState(conn).getRemotingConnection().removeConnectionListener();

         // make sure we're connecting to node 1

         int nodeID = getServerId(conn);

         assertEquals(1, nodeID);

         Session s1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer c1 = s1.createConsumer(queue[1]);
         MessageProducer p1 = s1.createProducer(queue[1]);
         p1.setDeliveryMode(DeliveryMode.PERSISTENT);

         // send a message

         p1.send(s1.createTextMessage("blip"));

         // kill node 1

         ServerManagement.kill(1);

         try
         {
            ic[1].lookup("queue"); // looking up anything
            fail("The server still alive, kill didn't work yet");
         }
         catch (Exception e)
         {
         }

         // we must receive the message

         TextMessage tm = (TextMessage)c1.receive(3000);
         assertNotNull(tm);
         assertEquals("blip", tm.getText());

      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   // Used for both testFailureRightAfterACK and  testFailureRightBeforeACK
   private void failureOnInvocation(int typeOfFailure) throws Exception
   {
      Connection conn = null;
      Connection conn0 = null;

      try
      {
         conn = createConnectionOnServer(cf, 1);

         assertEquals(1, getServerId(conn));

         // we "cripple" the remoting connection by removing ConnectionListener. This way, failures
         // cannot be "cleanly" detected by the client-side pinger, and we'll fail on an invocation
         JMSRemotingConnection rc = ((ClientConnectionDelegate)((JBossConnection)conn).
            getDelegate()).getRemotingConnection();
         rc.removeConnectionListener();

         // poison the server
         ServerManagement.poisonTheServer(1, typeOfFailure);

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         conn.start();

         MessageProducer producer = session.createProducer(queue[0]);

         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         MessageConsumer consumer = session.createConsumer(queue[0]);

         producer.send(session.createTextMessage("before-poison"));

         TextMessage tm = (TextMessage)consumer.receive(5000);

         if(typeOfFailure == PoisonInterceptor.FAIL_AFTER_ACKNOWLEDGE_DELIVERY)
         {
         	//With auto_ack we won't the message - remember auto ack is "at most once"
         	assertNull(tm);
         }
         else
         {
            assertNotNull(tm);

            assertEquals("before-poison", tm.getText());
         }

         checkEmpty(queue[1], 0);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
         if (conn0 != null)
         {
            conn0.close();
         }
      }
   }


   // Inner classes --------------------------------------------------------------------------------
}
