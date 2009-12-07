/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.test.messaging.jms;

import java.util.ArrayList;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.QueueConnection;
import javax.jms.QueueReceiver;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.XAConnection;
import javax.jms.XASession;
import javax.management.ObjectName;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.jms.client.JBossMessageProducer;
import org.jboss.jms.tx.MessagingXid;
import org.jboss.jms.tx.ResourceManagerFactory;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.container.ServiceContainer;

/**
 * A OrderingGroupAckTest
 *
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 * 
 * Created Oct 28, 2008 2:30:18 PM
 *
 *
 */
public class OrderingGroupAckTest extends JMSTestCase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------
   public OrderingGroupAckTest(String name)
   {
      super(name);
   }

   // TestCase overrides -------------------------------------------

   protected void setUp() throws Exception
   {
      // if this is not set testMockCoordinatorRecoveryWithJBossTSXids will create an invalid ObjectStore
      ServiceContainer.setupObjectStoreDir();
      super.setUp();
   }

   public void tearDown() throws Exception
   {
      super.tearDown();

      ResourceManagerFactory.instance.clear();
   }

   // Public --------------------------------------------------------

   /*
    * This test shows how ordering group delivery handles transactions.
    * A rollback will cause the first message to be re-delivered.
    * A commit will cause the second message to be available for delivery.
    */
   public void testRollbackCommit() throws Exception
   {
      QueueConnection conn = null;

      try
      {
         conn = cf.createQueueConnection();
         QueueSession sess = conn.createQueueSession(true, 0);
         JBossMessageProducer producer = (JBossMessageProducer)sess.createProducer(queue1);
         producer.enableOrderingGroup(null);

         QueueReceiver cons = sess.createReceiver(queue1);

         conn.start();

         Message m1 = sess.createTextMessage("testing1");
         Message m2 = sess.createTextMessage("testing2");
         producer.send(m1);
         producer.send(m2);

         sess.commit();

         TextMessage mr = (TextMessage)cons.receive(3000);
         assertNotNull(mr);
         assertEquals("testing1", mr.getText());

         sess.rollback();

         mr = (TextMessage)cons.receive(3000);
         assertNotNull(mr);
         assertEquals("testing1", mr.getText());

         // second message cannot be received
         // if the first message is not committed.
         mr = (TextMessage)cons.receive(3000);
         assertNull(mr);

         sess.commit();

         mr = (TextMessage)cons.receive(3000);
         assertNotNull(mr);
         assertEquals("testing2", mr.getText());

         sess.commit();

         checkEmpty(queue1);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   /*
    * This test shows how ordering group handles client acknowledge.
    * the second message will never be sent out unless the first message is acked.
    */
   public void testClientAcknowledge() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = cf.createConnection();

         Session producerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         JBossMessageProducer producer = (JBossMessageProducer)producerSess.createProducer(queue1);
         producer.enableOrderingGroup(null);

         Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(queue1);
         conn.start();

         final int NUM_MSG = 100;

         // Send some messages
         for (int i = 0; i < 100; ++i)
         {
            TextMessage tm = producerSess.createTextMessage("ordering" + i);
            producer.send(tm);
         }

         assertRemainingMessages(NUM_MSG);

         log.trace("Sent messages");

         int count = 0;
         while (true)
         {
            Message m = consumer.receive(400);
            if (m == null)
               break;
            count++;
         }

         assertRemainingMessages(NUM_MSG);

         log.trace("Received " + count + " messages");

         // if ordering group, count should be 1.
         assertEquals(1, count);

         consumerSess.recover();

         assertRemainingMessages(NUM_MSG);

         log.trace("Session recover called");

         TextMessage m = null;

         int i = 0;
         for (; i < 100; ++i)
         {
            m = (TextMessage)consumer.receive();
            log.trace("Received message " + i);
            m.acknowledge();
            assertTrue(m.getText().equals("ordering" + i));
         }

         assertRemainingMessages(0);

         // make sure I don't receive anything else

         checkEmpty(queue1);

      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   /*
    * send 4 messages, start a XA transaction to receive, messages will be received only if
    * the last message is committed. 
    */
   public void testSimpleXATransactionalReceive() throws Exception
   {
      log.trace("starting testSimpleXATransactionalReceive");

      Connection conn1 = null;

      XAConnection xconn1 = null;

      try
      {
         // First send a message to the queue
         conn1 = cf.createConnection();

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sess1.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.enableOrderingGroup("testSimpleXATransactionalReceive");

         TextMessage tm1 = sess1.createTextMessage("tm1");
         TextMessage tm2 = sess1.createTextMessage("tm2");
         TextMessage tm3 = sess1.createTextMessage("tm3");
         TextMessage tm4 = sess1.createTextMessage("tm4");

         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         prod.send(tm4);

         xconn1 = cf.createXAConnection();

         XASession xsess1 = xconn1.createXASession();

         XAResource res1 = xsess1.getXAResource();

         // Pretend to be a transaction manager by interacting through the XAResources
         Xid xid1 = new MessagingXid("bq1".getBytes(), 42, "eemeli".getBytes());

         res1.start(xid1, XAResource.TMNOFLAGS);

         MessageConsumer cons = xsess1.createConsumer(queue1);

         xconn1.start();

         // Consume the message

         TextMessage rm1 = (TextMessage)cons.receive(1000);

         assertNotNull(rm1);

         assertEquals(tm1.getText(), rm1.getText());

         res1.end(xid1, XAResource.TMSUCCESS);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         // prepare the tx
         res1.prepare(xid1);

         res1.commit(xid1, false);

         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm2.getText());

         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm3.getText());

         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm4.getText());

         checkEmpty(queue1);

         conn1.close();

         xconn1.close();

      }
      finally
      {
         if (conn1 != null)
         {
            try
            {
               conn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }

         if (xconn1 != null)
         {
            try
            {
               xconn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }
      }
   }

   /*
    * send 4 messages, start a XA transaction to receive. 
    * In XA recovery, the messages will be re-sent without
    * breaking the original order
    */
   public void testSimpleXATransactionalRecoveryCommitReceive() throws Exception
   {
      log.trace("starting testSimpleXATransactionalRecoveryCommitReceive");

      Connection conn1 = null;

      XAConnection xconn1 = null;

      XAConnection xconn2 = null;

      try
      {
         // First send a message to the queue
         conn1 = cf.createConnection();

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sess1.createProducer(queue1);
         // non-persistent will cause message lost in server failure
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);
         prod.enableOrderingGroup("testSimpleXATransactionalRecoveryCommitReceive");

         TextMessage tm1 = sess1.createTextMessage("tm1");
         TextMessage tm2 = sess1.createTextMessage("tm2");
         TextMessage tm3 = sess1.createTextMessage("tm3");
         TextMessage tm4 = sess1.createTextMessage("tm4");

         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         prod.send(tm4);

         xconn1 = cf.createXAConnection();

         XASession xsess1 = xconn1.createXASession();

         XAResource res1 = xsess1.getXAResource();

         // Pretend to be a transaction manager by interacting through the XAResources
         Xid xid1 = new MessagingXid("bq1".getBytes(), 42, "eemeli".getBytes());

         res1.start(xid1, XAResource.TMNOFLAGS);

         MessageConsumer cons = xsess1.createConsumer(queue1);

         xconn1.start();

         // Consume the message

         TextMessage rm1 = (TextMessage)cons.receive(1000);

         assertNotNull(rm1);

         assertEquals(tm1.getText(), rm1.getText());

         res1.end(xid1, XAResource.TMSUCCESS);

         // prepare the tx

         res1.prepare(xid1);

         conn1.close();

         xconn1.close();

         conn1 = null;

         xconn1 = null;

         // Now "crash" the server

         ServerManagement.stopServerPeer();

         ServerManagement.startServerPeer();

         deployAndLookupAdministeredObjects();

         // Now recover

         xconn2 = cf.createXAConnection();

         XASession xsess2 = xconn2.createXASession();

         MessageConsumer cons2 = xsess2.createConsumer(queue1);

         XAResource res3 = xsess2.getXAResource();

         Xid[] xids = res3.recover(XAResource.TMSTARTRSCAN);
         assertEquals(1, xids.length);

         Xid[] xids2 = res3.recover(XAResource.TMENDRSCAN);
         assertEquals(0, xids2.length);

         assertEquals(xid1, xids[0]);
         
         //before recover and commit the message cannot be received.
         rm1 = (TextMessage)cons2.receive(2000);
         assertNull(rm1);

         // Commit the tx

         res3.commit(xids[0], false);

         // The message should be acknowldged

         xconn2.close();

         conn1 = cf.createConnection();

         sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons1 = sess1.createConsumer(queue1);

         conn1.start();

         // tm2, tm3, tm4 should be received.
         TextMessage tmr = (TextMessage)cons1.receive(1000);
         assertNotNull(tmr);
         assertEquals(tmr.getText(), tm2.getText());

         tmr = (TextMessage)cons1.receive(1000);
         assertNotNull(tmr);
         assertEquals(tmr.getText(), tm3.getText());

         tmr = (TextMessage)cons1.receive(1000);
         assertNotNull(tmr);
         assertEquals(tmr.getText(), tm4.getText());

         checkEmpty(queue1);

      }
      finally
      {
         if (conn1 != null)
         {
            try
            {
               conn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }

         if (xconn1 != null)
         {
            try
            {
               xconn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }
      }
   }
   
   
   /*
    * send 4 messages, start a XA transaction to receive, next message will not
    * be delivered if the XA is in prepared state 
    */
   public void testSimpleXATransactionalRollbackReceive() throws Exception
   {
      log.trace("starting testSimpleXATransactionalRollbackReceive");

      Connection conn1 = null;

      XAConnection xconn1 = null;

      try
      {
         // First send a message to the queue
         conn1 = cf.createConnection();

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sess1.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.enableOrderingGroup("testSimpleXATransactionalRollbackReceive");

         TextMessage tm1 = sess1.createTextMessage("tm1");
         TextMessage tm2 = sess1.createTextMessage("tm2");
         TextMessage tm3 = sess1.createTextMessage("tm3");
         TextMessage tm4 = sess1.createTextMessage("tm4");

         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         prod.send(tm4);

         xconn1 = cf.createXAConnection();

         XASession xsess1 = xconn1.createXASession();

         XAResource res1 = xsess1.getXAResource();

         // Pretend to be a transaction manager by interacting through the XAResources
         Xid xid1 = new MessagingXid("bq1".getBytes(), 42, "eemeli".getBytes());

         res1.start(xid1, XAResource.TMNOFLAGS);

         MessageConsumer cons = xsess1.createConsumer(queue1);

         xconn1.start();

         // Consume the message

         TextMessage rm1 = (TextMessage)cons.receive(1000);

         assertNotNull(rm1);

         assertEquals(tm1.getText(), rm1.getText());

         res1.end(xid1, XAResource.TMSUCCESS);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         // prepare the tx
         res1.prepare(xid1);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);
         
         res1.rollback(xid1);

         //start another Tx, and rollback again.
         Xid xid2 = new MessagingXid("bq2".getBytes(), 42, "eemeli".getBytes());
         res1.start(xid2, XAResource.TMNOFLAGS);

         rm1 = (TextMessage)cons.receive(1000);
         assertNotNull(rm1);
         assertEquals(tm1.getText(), rm1.getText());

         res1.end(xid2, XAResource.TMSUCCESS);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         // prepare the tx
         res1.prepare(xid2);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         res1.rollback(xid2);
         
         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm1.getText());
         
         
         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm2.getText());

         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm3.getText());

         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm4.getText());

         checkEmpty(queue1);

         conn1.close();

         xconn1.close();

      }
      finally
      {
         if (conn1 != null)
         {
            try
            {
               conn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }

         if (xconn1 != null)
         {
            try
            {
               xconn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }
      }
   }
   
   /*
    * send 4 messages, start a XA transaction to receive, first rollback, then commit
    * next message will be delivered only if the XA tx is committed  
    */
   public void testSimpleXATransactionalRollbackCommitReceive() throws Exception
   {
      log.trace("starting testSimpleXATransactionalRollbackReceive");

      Connection conn1 = null;

      XAConnection xconn1 = null;

      try
      {
         // First send a message to the queue
         conn1 = cf.createConnection();

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sess1.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.enableOrderingGroup("testSimpleXATransactionalRollbackReceive");

         TextMessage tm1 = sess1.createTextMessage("tm1");
         TextMessage tm2 = sess1.createTextMessage("tm2");
         TextMessage tm3 = sess1.createTextMessage("tm3");
         TextMessage tm4 = sess1.createTextMessage("tm4");

         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         prod.send(tm4);

         xconn1 = cf.createXAConnection();

         XASession xsess1 = xconn1.createXASession();

         XAResource res1 = xsess1.getXAResource();

         // Pretend to be a transaction manager by interacting through the XAResources
         Xid xid1 = new MessagingXid("bq1".getBytes(), 42, "eemeli".getBytes());

         res1.start(xid1, XAResource.TMNOFLAGS);

         MessageConsumer cons = xsess1.createConsumer(queue1);

         xconn1.start();

         // Consume the message

         TextMessage rm1 = (TextMessage)cons.receive(1000);

         assertNotNull(rm1);

         assertEquals(tm1.getText(), rm1.getText());

         res1.end(xid1, XAResource.TMSUCCESS);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         //roll back directly
         res1.rollback(xid1);

         //start another Tx, and rollback again.
         Xid xid2 = new MessagingXid("bq2".getBytes(), 42, "eemeli".getBytes());
         res1.start(xid2, XAResource.TMNOFLAGS);

         rm1 = (TextMessage)cons.receive(1000);
         assertNotNull(rm1);
         assertEquals(tm1.getText(), rm1.getText());

         res1.end(xid2, XAResource.TMSUCCESS);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         // prepare the tx
         res1.prepare(xid2);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         res1.commit(xid2, false);
         
         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm2.getText());

         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm3.getText());

         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm4.getText());

         checkEmpty(queue1);

         conn1.close();

         xconn1.close();

      }
      finally
      {
         if (conn1 != null)
         {
            try
            {
               conn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }

         if (xconn1 != null)
         {
            try
            {
               xconn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }
      }
   }
   
   /*
    * send 4 messages, start a XA transaction to receive,  then commit without prepare
    * next message will be delivered only if the XA tx is committed  
    */
   public void testSimpleXATransactionalCommitOnephaseReceive() throws Exception
   {
      log.trace("starting testSimpleXATransactionalRollbackReceive");

      Connection conn1 = null;

      XAConnection xconn1 = null;

      try
      {
         // First send a message to the queue
         conn1 = cf.createConnection();

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sess1.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.enableOrderingGroup("testSimpleXATransactionalRollbackReceive");

         TextMessage tm1 = sess1.createTextMessage("tm1");
         TextMessage tm2 = sess1.createTextMessage("tm2");
         TextMessage tm3 = sess1.createTextMessage("tm3");
         TextMessage tm4 = sess1.createTextMessage("tm4");

         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         prod.send(tm4);

         xconn1 = cf.createXAConnection();

         XASession xsess1 = xconn1.createXASession();

         XAResource res1 = xsess1.getXAResource();

         // Pretend to be a transaction manager by interacting through the XAResources
         Xid xid1 = new MessagingXid("bq1".getBytes(), 42, "eemeli".getBytes());

         res1.start(xid1, XAResource.TMNOFLAGS);

         MessageConsumer cons = xsess1.createConsumer(queue1);

         xconn1.start();

         // Consume the message

         TextMessage rm1 = (TextMessage)cons.receive(1000);

         assertNotNull(rm1);

         assertEquals(tm1.getText(), rm1.getText());

         res1.end(xid1, XAResource.TMSUCCESS);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         //roll back directly
         res1.rollback(xid1);

         //start another Tx, and rollback again.
         Xid xid2 = new MessagingXid("bq2".getBytes(), 42, "eemeli".getBytes());
         res1.start(xid2, XAResource.TMNOFLAGS);

         rm1 = (TextMessage)cons.receive(1000);
         assertNotNull(rm1);
         assertEquals(tm1.getText(), rm1.getText());

         res1.end(xid2, XAResource.TMSUCCESS);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         res1.commit(xid2, true);
         
         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm2.getText());

         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm3.getText());

         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm4.getText());

         checkEmpty(queue1);

         conn1.close();

         xconn1.close();

      }
      finally
      {
         if (conn1 != null)
         {
            try
            {
               conn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }

         if (xconn1 != null)
         {
            try
            {
               xconn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }
      }
   }
   
   /*
    * send 4 messages, start a XA transaction to receive, roll back the tx.
    * then start another transaction, receive the 1st message, then try to receive second again.
    * the second shouldn't be received. 
    */
   public void testSimpleXATransactionalRollbackReceive2() throws Exception
   {
      log.trace("starting testSimpleXATransactionalRollbackReceive2");

      Connection conn1 = null;

      XAConnection xconn1 = null;

      try
      {
         // First send a message to the queue
         conn1 = cf.createConnection();

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sess1.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.enableOrderingGroup("testSimpleXATransactionalRollbackReceive");

         TextMessage tm1 = sess1.createTextMessage("tm1");
         TextMessage tm2 = sess1.createTextMessage("tm2");
         TextMessage tm3 = sess1.createTextMessage("tm3");
         TextMessage tm4 = sess1.createTextMessage("tm4");

         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         prod.send(tm4);

         xconn1 = cf.createXAConnection();

         XASession xsess1 = xconn1.createXASession();

         XAResource res1 = xsess1.getXAResource();

         // Pretend to be a transaction manager by interacting through the XAResources
         Xid xid1 = new MessagingXid("bq1".getBytes(), 42, "eemeli".getBytes());

         res1.start(xid1, XAResource.TMNOFLAGS);

         MessageConsumer cons = xsess1.createConsumer(queue1);

         xconn1.start();

         // Consume the message

         TextMessage rm1 = (TextMessage)cons.receive(1000);

         assertNotNull(rm1);

         assertEquals(tm1.getText(), rm1.getText());

         res1.end(xid1, XAResource.TMSUCCESS);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         // prepare the tx
         res1.prepare(xid1);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);
         
         res1.rollback(xid1);

         //start another Tx, and rollback again.
         Xid xid2 = new MessagingXid("bq2".getBytes(), 42, "eemeli".getBytes());
         res1.start(xid2, XAResource.TMNOFLAGS);

         rm1 = (TextMessage)cons.receive(1000);
         assertNotNull(rm1);
         assertEquals(tm1.getText(), rm1.getText());
         
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         res1.end(xid2, XAResource.TMSUCCESS);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         // prepare the tx
         res1.prepare(xid2);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         res1.commit(xid2, false);
         
         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm2.getText());

         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm3.getText());

         rm1 = (TextMessage)cons.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm4.getText());

         checkEmpty(queue1);

         conn1.close();

         xconn1.close();

      }
      finally
      {
         if (conn1 != null)
         {
            try
            {
               conn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }

         if (xconn1 != null)
         {
            try
            {
               xconn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }
      }
   }
   
   /*
    * send 4 messages, start a XA transaction to receive m1, roll back and close the session set.
    * then start another transaction, receive the 1st message, then try to receive second again.
    * the second shouldn't be received. 
    */
   public void testSimpleXATransactionalRollbackReceive3() throws Exception
   {
      log.trace("starting testSimpleXATransactionalRollbackReceive2");

      Connection conn1 = null;

      XAConnection xconn1 = null;

      try
      {
         // First send a message to the queue
         conn1 = cf.createConnection();

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sess1.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.enableOrderingGroup("testSimpleXATransactionalRollbackReceive");

         TextMessage tm1 = sess1.createTextMessage("tm1");
         TextMessage tm2 = sess1.createTextMessage("tm2");
         TextMessage tm3 = sess1.createTextMessage("tm3");
         TextMessage tm4 = sess1.createTextMessage("tm4");

         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         prod.send(tm4);

         xconn1 = cf.createXAConnection();

         XASession xsess1 = xconn1.createXASession();

         XAResource res1 = xsess1.getXAResource();

         // Pretend to be a transaction manager by interacting through the XAResources
         Xid xid1 = new MessagingXid("bq1".getBytes(), 42, "eemeli".getBytes());

         res1.start(xid1, XAResource.TMNOFLAGS);

         MessageConsumer cons = xsess1.createConsumer(queue1);

         xconn1.start();

         // Consume the message

         TextMessage rm1 = (TextMessage)cons.receive(1000);

         assertNotNull(rm1);

         assertEquals(tm1.getText(), rm1.getText());

         res1.end(xid1, XAResource.TMSUCCESS);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);

         // prepare the tx
         res1.prepare(xid1);

         // next message should not be received.
         rm1 = (TextMessage)cons.receive(2000);
         assertNull(rm1);
         
         res1.rollback(xid1);
         
         xsess1.getSession().close();

         XASession xsess2 = xconn1.createXASession();

         XAResource res2 = xsess2.getXAResource();
         
         MessageConsumer cons2 = xsess2.createConsumer(queue1);

         //start another Tx, commit it.
         Xid xid2 = new MessagingXid("bq2".getBytes(), 42, "eemeli".getBytes());
         res2.start(xid2, XAResource.TMNOFLAGS);

         rm1 = (TextMessage)cons2.receive(1000);
         assertNotNull(rm1);
         assertEquals(tm1.getText(), rm1.getText());
         
         rm1 = (TextMessage)cons2.receive(2000);
         assertNull(rm1);

         res2.end(xid2, XAResource.TMSUCCESS);

         // next message should not be received.
         rm1 = (TextMessage)cons2.receive(2000);
         assertNull(rm1);

         // prepare the tx
         res2.prepare(xid2);

         // next message should not be received.
         rm1 = (TextMessage)cons2.receive(2000);
         assertNull(rm1);

         res2.commit(xid2, false);
         
         rm1 = (TextMessage)cons2.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm2.getText());

         rm1 = (TextMessage)cons2.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm3.getText());

         rm1 = (TextMessage)cons2.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), tm4.getText());

         checkEmpty(queue1);

         conn1.close();

         xconn1.close();

      }
      finally
      {
         if (conn1 != null)
         {
            try
            {
               conn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }

         if (xconn1 != null)
         {
            try
            {
               xconn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }
      }
   }
   
   /*
    * https://jira.jboss.org/jira/browse/JBMESSAGING-1643
    */
   public void testSimpleConsumerCloseReceive() throws Exception
   {
      log.trace("starting testSimpleConsumerCloseReceive");

      Connection conn1 = null;

      try
      {
         // First send a message to the queue
         conn1 = cf.createConnection();

         Session sess1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sess1.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.enableOrderingGroup("testSimpleXATransactionalRollbackReceive");

         TextMessage tm1 = sess1.createTextMessage("tm1");
         TextMessage tm2 = sess1.createTextMessage("tm2");
         TextMessage tm3 = sess1.createTextMessage("tm3");
         TextMessage tm4 = sess1.createTextMessage("tm4");

         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         prod.send(tm4);
         
         conn1.start();
         
         MessageConsumer cons1 = sess1.createConsumer(queue1);
         TextMessage rm1 = (TextMessage)cons1.receive(2000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), "tm1");
         
         TextMessage rm2 = (TextMessage)cons1.receive(2000);
         assertNull(rm2);
         
         //Next message not available to another consumer either.
         MessageConsumer cons2 = sess1.createConsumer(queue1);
         rm2 = (TextMessage)cons2.receive(2000);
         assertNull(rm2);
         
         //ack rm1
         rm1.acknowledge();
         
         cons1.close();
         
         rm1 = (TextMessage)cons2.receive(1000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), "tm2");
         rm1.acknowledge();
         
         rm1 = (TextMessage)cons2.receive(1000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), "tm3");
         rm1.acknowledge();
         
         rm1 = (TextMessage)cons2.receive(1000);
         assertNotNull(rm1);
         assertEquals(rm1.getText(), "tm4");
         rm1.acknowledge();
         
         checkEmpty(queue1);
         
      }
      finally
      {
         if (conn1 != null)
         {
            try
            {
               conn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }
         
      }
      
   }

   /*
    * send 4 messages, start a XA transaction to receive, roll back 2nd message until it 
    * goes to DLQ, then continue to receive the messages, check
    * the messages received order. 
    * https://jira.jboss.org/jira/browse/JBMESSAGING-1643
    */
   public void testSimpleXATransactionalRollbackReceive4() throws Exception
   {
      if (ServerManagement.isRemote())
      {
         return;
      }

      log.trace("starting testSimpleXATransactionalRollbackReceive4");

      Connection conn1 = null;

      ObjectName serverPeerObjectName = ServerManagement.getServerPeerObjectName();

      String testQueueObjectName = "jboss.messaging.destination:service=Queue,name=Queue1";
      
      final int MAX_DELIVERIES = 5;

      try
      {
         String defaultDLQObjectName = "jboss.messaging.destination:service=Queue,name=Queue2";

         ServerManagement.setAttribute(serverPeerObjectName,
                                       "DefaultMaxDeliveryAttempts",
                                       String.valueOf(MAX_DELIVERIES));

         ServerManagement.setAttribute(serverPeerObjectName, "DefaultDLQ", defaultDLQObjectName);

         ServerManagement.setAttribute(new ObjectName(testQueueObjectName), "DLQ", "");

         // First send a message to the queue
         conn1 = cf.createConnection();

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sess1.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         prod.enableOrderingGroup("testSimpleXATransactionalRollbackReceive");

         TextMessage tm1 = sess1.createTextMessage("A");
         TextMessage tm2 = sess1.createTextMessage("B");
         TextMessage tm3 = sess1.createTextMessage("C");
         TextMessage tm4 = sess1.createTextMessage("D");

         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         prod.send(tm4);
         
         conn1.start();

         ArrayList<String> rList = new ArrayList<String>();
         int i = 0;
         while (true)
         {
            XAConnection xconn1 = null;
            XASession xsess1 = null;
            MessageConsumer xcons1 = null;
            i++;
            try
            {
               xconn1 = cf.createXAConnection();

               xsess1 = xconn1.createXASession();

               XAResource res1 = xsess1.getXAResource();
               
               String bqStr = "bq" + i;

               // Pretend to be a transaction manager by interacting through the XAResources
               Xid xid1 = new MessagingXid(bqStr.getBytes(), 42, "eemeli".getBytes());

               res1.start(xid1, XAResource.TMNOFLAGS);

               xcons1 = xsess1.createConsumer(queue1);

               xconn1.start();

               TextMessage rm1 = (TextMessage)xcons1.receive(5000);

               if (rm1 == null)
               {
                  break;
               }
               
               rList.add(rm1.getText());

               res1.end(xid1, XAResource.TMSUCCESS);

               // prepare the tx
               res1.prepare(xid1);
               
               if (rm1.getText().equals("B"))
               {
                  res1.rollback(xid1);               
               }
               else
               {
                  res1.commit(xid1, false);               
               }
            }
            finally
            {
               if (xcons1 != null)
               {
                  try
                  {
                     xcons1.close();
                  }
                  catch (Exception e)
                  {
                     // Ignore
                  }
               }
               if (xsess1 != null)
               {
                  try
                  {
                     xsess1.close();
                  }
                  catch (Exception e)
                  {
                     // Ignore
                  }
               }
               if (xconn1 != null)
               {
                  try
                  {
                     xconn1.close();
                  }
                  catch (Exception e)
                  {
                     // Ignore
                  }
               }
            }
         }
         
         //examine result
         assertEquals(rList.size(), 8);
         assertEquals("A", rList.get(0));
         assertEquals("B", rList.get(1));
         assertEquals("B", rList.get(2));
         assertEquals("B", rList.get(3));
         assertEquals("B", rList.get(4));
         assertEquals("B", rList.get(5));
         assertEquals("C", rList.get(6));
         assertEquals("D", rList.get(7));

         MessageConsumer cons1 = sess1.createConsumer(queue2);
         TextMessage dMsg = (TextMessage)cons1.receive(2000);
         assertNotNull(dMsg);
         assertEquals(dMsg.getText(), "B");
         
         checkEmpty(queue1);
         checkEmpty(queue2);
      }
      finally
      {
         ServerManagement.setAttribute(serverPeerObjectName,
                                       "DefaultDLQ",
                                       "jboss.messaging.destination:service=Queue,name=DLQ");

         ServerManagement.setAttribute(new ObjectName(testQueueObjectName), "DLQ", "");
         if (conn1 != null)
         {
            try
            {
               conn1.close();
            }
            catch (Exception e)
            {
               // Ignore
            }
         }
      }
   }
   
   /*
    * https://jira.jboss.org/jira/browse/JBMESSAGING-1727
    * This test sends 5 ordering messages, then in a tx sends one and receive one messages.
    * Then try to receive all the rest messages.
    */
   public void testTransactionalDelivery() throws Exception
   {
      QueueConnection conn = null;

      try
      {
         conn = cf.createQueueConnection();
         QueueSession sess = conn.createQueueSession(true, Session.SESSION_TRANSACTED);
         JBossMessageProducer producer = (JBossMessageProducer)sess.createProducer(queue1);
         producer.enableOrderingGroup(null);

         QueueReceiver cons = sess.createReceiver(queue1);

         conn.start();

         int numMsg = 5;
         TextMessage msg = null;
         for (int i = 0; i < numMsg; i++)
         {
            msg = sess.createTextMessage("tx-delivery" + i);
            producer.send(msg);
         }
         sess.commit();
         
         msg = sess.createTextMessage("tx-delivery" + numMsg);
         producer.send(msg);
         TextMessage rm = (TextMessage)cons.receive(5000);
         assertEquals("tx-delivery0", rm.getText());
         sess.commit();
         
         for (int i = 0; i < numMsg; i++)
         {
            rm = (TextMessage)cons.receive(5000);
            assertEquals("tx-delivery" + (i+1), rm.getText());
            sess.commit();
         }

         checkEmpty(queue1);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
