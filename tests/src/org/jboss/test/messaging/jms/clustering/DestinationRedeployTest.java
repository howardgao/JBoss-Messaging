/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2009, Red Hat Middleware LLC, and individual contributors
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


package org.jboss.test.messaging.jms.clustering;

import java.util.HashMap;
import java.util.Iterator;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.jms.client.JBossConnectionFactory;
import org.jboss.jms.tx.MessagingXid;
import org.jboss.test.messaging.tools.ServerManagement;

/**
 * Test for https://jira.jboss.org/jira/browse/JBMESSAGING-1742
 * 
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 *
 */
public class DestinationRedeployTest extends ClusteringTestBase
{
   //clustered2NonclusteredQueue
   private Queue cQueue;
   
   //nonclustered2ClusteredQueue
   private Queue nQueue;
   
   //clustered2NonclusteredTopic
   private Topic cTopic;
   
   //nonclustered2ClusteredTopic
   private Topic nTopic;
   
   public DestinationRedeployTest(String name)
   {
      super(name);
   }
   
   protected void setUp() throws Exception
   {
      super.setUp();
   }

   protected void tearDown() throws Exception
   {
      super.tearDown();
   }

   //do a redeploy and test the queues work normally by sending some messages and receiving them.
   public void testRedeployQueue() throws Exception
   {
      String msgBase = "testRedeployQueue";
      int numMsg = 50;

      deployDestinations();
      redeployDestinations(true);
      
      sendMessages(0, cQueue, msgBase, numMsg);
      sendMessages(1, nQueue, msgBase, numMsg);
      
      receiveMessages(0, cQueue, msgBase, 0, numMsg, Session.AUTO_ACKNOWLEDGE, true);
      receiveMessages(2, nQueue, msgBase, 0, numMsg, Session.CLIENT_ACKNOWLEDGE, true);
   }

   //do a redeploy and test the topics work normally by sending some messages and receiving them.
   public void testRedeployTopic() throws Exception
   {
      String msgBase = "testRedeployTopic";
      int numMsg = 50;

      deployDestinations();
      redeployDestinations(true);
      
      Connection conn = null;
      Session sess = null;
      try
      {
         conn = createConnectionOnServer(cf, 0);
         conn.setClientID("client-id-0");
         sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageConsumer sub1 = sess.createConsumer(cTopic);
         MessageConsumer sub2 = sess.createDurableSubscriber(nTopic, "sub2");
         
         conn.start();

         sendMessages(0, cTopic, msgBase, numMsg);
         sendMessages(1, nTopic, msgBase, numMsg);
         
         for (int i = 0; i < numMsg; i++)
         {
            TextMessage rm = (TextMessage)sub1.receive(5000);
            assertEquals(msgBase + i, rm.getText());
            rm = (TextMessage)sub2.receive(5000);
            assertEquals(msgBase + i, rm.getText());
         }
         
         sub2.close();
         sess.unsubscribe("sub2");
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }
   
   public void testRedeployTopicNoMessageLoss() throws Exception
   {
      String msgBase = "testRedeployTopicNoMessageLoss";
      int numMsg = 50;

      deployDestinations();
      
      Connection conn = null;
      Session sess = null;
      try
      {
         conn = createConnectionOnServer(cf, 0);
         conn.setClientID("client-id-0");
         sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageConsumer sub1 = sess.createDurableSubscriber(cTopic, "sub1");
         MessageConsumer sub2 = sess.createDurableSubscriber(nTopic, "sub2");
         
         conn.close();

         sendMessages(2, cTopic, msgBase, numMsg);
         sendMessages(0, nTopic, msgBase, numMsg);
         
         redeployDestinations(true);

         conn = createConnectionOnServer(cf, 0);
         conn.setClientID("client-id-0");
         sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         conn.start();

         sub1 = sess.createDurableSubscriber(cTopic, "sub1");

         sub2 = sess.createDurableSubscriber(nTopic, "sub2");
         
         for (int i = 0; i < numMsg; i++)
         {
            TextMessage rm = (TextMessage)sub1.receive(5000);
            log.info("--Message received: " + rm);
            assertEquals(msgBase + i, rm.getText());
            rm = (TextMessage)sub2.receive(5000);
            assertEquals(msgBase + i, rm.getText());
         }
         
         sub1.close();
         sub2.close();
         sess.unsubscribe("sub1");
         sess.unsubscribe("sub2");
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }
   
   public void testRedeployTopicWithMessageLoss() throws Exception
   {
      String msgBase = "testRedeployTopicWithMessageLoss";
      int numMsg = 50;

      deployDestinations();
      
      Connection conn = null;
      Session sess = null;
      try
      {
         conn = createConnectionOnServer(cf, 0);
         conn.setClientID("client-id-0");
         sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         sess.createDurableSubscriber(cTopic, "sub1");
         sess.createDurableSubscriber(nTopic, "sub2");
         
         conn.close();

         sendMessages(2, cTopic, msgBase, numMsg);
         sendMessages(0, nTopic, msgBase, numMsg);
         
         redeployDestinations(false);
         
         checkEmpty(cTopic);
         checkEmpty(nTopic);

         sendMessages(0, cTopic, msgBase, numMsg);
         sendMessages(2, nTopic, msgBase, numMsg);

         conn = createConnectionOnServer(cf, 0);
         conn.setClientID("client-id-0");
         sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         conn.start();
         
         MessageConsumer sub1 = sess.createDurableSubscriber(cTopic, "sub1");
         MessageConsumer sub2 = sess.createDurableSubscriber(nTopic, "sub2");
         
         for (int i = 0; i < numMsg; i++)
         {
            TextMessage rm = (TextMessage)sub1.receive(5000);
            log.info("--Message received: " + rm);
            assertEquals(msgBase + i, rm.getText());
            rm = (TextMessage)sub2.receive(5000);
            assertEquals(msgBase + i, rm.getText());
         }
         
         sub1.close();
         sub2.close();

         sess.unsubscribe("sub1");
         sess.unsubscribe("sub2");
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }
   
   private HashMap<String, TextMessage> msgSet = new HashMap<String, TextMessage>();
   
   //send some messages to topics and receive a few of them. Then do redeploy and try to receive the rest.
   //also this is a valid test for https://jira.jboss.org/jira/browse/JBMESSAGING-1774
   public void testRedeployTopicNoMessageLoss2() throws Exception
   {
      String msgBase = "testRedeployTopicNoMessageLoss2";
      int numMsg = 500;

      deployDestinations();
      
      Connection conn = null;
      Session sess = null;
      try
      {
         conn = createConnectionOnServer(cf, 0);
         conn.setClientID("client-id-0");
         sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageConsumer sub1 = sess.createDurableSubscriber(cTopic, "sub1");
         MessageConsumer sub2 = sess.createDurableSubscriber(nTopic, "sub2");

         sendMessages(2, cTopic, msgBase+"cTopic", numMsg);
         sendMessages(0, nTopic, msgBase+"nTopic", numMsg);
         
         //receive 10
         conn.start();
         
         for (int i = 0; i < 10; i++)
         {
            TextMessage rm = (TextMessage)sub1.receive(5000);
            assertEquals(msgBase + "cTopic" + i, rm.getText());
            msgSet.remove(rm.getText());
            rm = (TextMessage)sub2.receive(5000);
            assertEquals(msgBase + "nTopic" + i, rm.getText());
            msgSet.remove(rm.getText());
         }
         
         conn.close();
         
         redeployDestinations(true);

         conn = createConnectionOnServer(cf, 0);
         conn.setClientID("client-id-0");
         sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         conn.start();

         sub1 = sess.createDurableSubscriber(cTopic, "sub1");

         sub2 = sess.createDurableSubscriber(nTopic, "sub2");
         
         boolean success = true;
         for (int i = 10; i < numMsg; i++)
         {
            TextMessage rm = (TextMessage)sub1.receive(5000);
            log.info("--Message received: " + rm);
            if (rm == null)
            {
               success = false;
            }
            else
            {
               msgSet.remove(rm.getText());
            }
         }
         
         for (int i = 10; i < numMsg; i++)
         {
            TextMessage rm = (TextMessage)sub2.receive(5000);
            log.info("--Message received: " + rm);
            if (rm == null)
            {
               success = false;
            }
            else
            {
               msgSet.remove(rm.getText());
            }
         }
         
         if (!success)
         {
            log.info("=======test failed, missing messages: ");
            Iterator<String> itmsg = msgSet.keySet().iterator();
            while (itmsg.hasNext())
            {
               String key = itmsg.next();
               TextMessage msg = msgSet.get(key);
               log.info("=====> " + key + " <--> " + msg);
            }
         }
         
         sub1.close();
         sub2.close();
         sess.unsubscribe("sub1");
         sess.unsubscribe("sub2");
         
         assertTrue(success);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   //send some messages to queues and receive a few within a tx, then redeply and receive them all.
   public void testRedeployTopicNoMessageLossTX() throws Exception
   {
      String msgBase = "testRedeployTopicNoMessageLossTX";
      int numMsg = 50;

      deployDestinations();
      
      Connection conn = null;
      Session sess1 = null;
      Session sess2 = null;
      
      try
      {
         conn = createConnectionOnServer(cf, 0);
         conn.setClientID("client-id-0");
         sess1 = conn.createSession(true, Session.SESSION_TRANSACTED);
         sess2 = conn.createSession(true, Session.SESSION_TRANSACTED);
         
         MessageConsumer sub1 = sess1.createDurableSubscriber(cTopic, "sub1");
         MessageConsumer sub2 = sess2.createDurableSubscriber(nTopic, "sub2");

         sendMessages(2, cTopic, msgBase, numMsg);
         sendMessages(0, nTopic, msgBase, numMsg);
         
         //receive 10
         conn.start();
         
         for (int i = 0; i < 10; i++)
         {
            TextMessage rm = (TextMessage)sub1.receive(5000);
            log.info("--Message received: " + rm);
            assertEquals(msgBase + i, rm.getText());
            rm = (TextMessage)sub2.receive(5000);
            assertEquals(msgBase + i, rm.getText());
         }
         
         sess1.commit();
         sess2.rollback();
         
         conn.close();
         
         redeployDestinations(true);

         conn = createConnectionOnServer(cf, 0);
         conn.setClientID("client-id-0");
         sess1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         conn.start();

         sub1 = sess1.createDurableSubscriber(cTopic, "sub1");

         sub2 = sess1.createDurableSubscriber(nTopic, "sub2");
         
         for (int i = 10; i < numMsg; i++)
         {
            TextMessage rm = (TextMessage)sub1.receive(5000);
            assertEquals(msgBase + i, rm.getText());
         }
         
         for (int i = 0; i < numMsg; i++)
         {
            TextMessage rm = (TextMessage)sub2.receive(5000);
            assertEquals(msgBase + i, rm.getText());
         }
         
         sub1.close();
         sub2.close();
         sess1.unsubscribe("sub1");
         sess1.unsubscribe("sub2");
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   //send some messages to topics and receive a few within a tx, then redeply and receive them all.
   public void testRedeployTopicNoMessageLossXA() throws Exception
   {
      String msgBase = "testRedeployTopicNoMessageLossXA";
      int numMsg = 50;

      deployDestinations();
      
      XAConnection xaconn1 = null;
      XASession xasess1 = null;
      XAResource xres1 = null;
      Session sess1 = null;

      XAConnection xaconn2 = null;
      XASession xasess2 = null;
      XAResource xres2 = null;
      Session sess2 = null;
      
      Xid xid1 = null;
      Xid xid2 = null;
      
      Connection conn1 = null;
      Connection conn2 = null;
      
      try
      {
         xaconn1 = (XAConnection)this.createXAConnectionOnServer((XAConnectionFactory)cf, 1);
         xaconn1.setClientID("client-id-0");
         xasess1 = xaconn1.createXASession();
         xres1 = xasess1.getXAResource();
         xaconn1.start();
         sess1 = xasess1.getSession();

         xaconn2 = (XAConnection)this.createXAConnectionOnServer((XAConnectionFactory)cf, 0);
         xaconn2.setClientID("client-id-1");
         xasess2 = xaconn2.createXASession();
         xres2 = xasess2.getXAResource();
         xaconn2.start();
         sess2 = xasess2.getSession();

         xid1 = new MessagingXid(("bq1" + cTopic).getBytes(), 42, cTopic.toString().getBytes());
         xid2 = new MessagingXid(("bq1" + nTopic).getBytes(), 42, nTopic.toString().getBytes());

         xres1.start(xid1, XAResource.TMNOFLAGS);
         xres2.start(xid2, XAResource.TMNOFLAGS);
         
         MessageConsumer sub1 = sess1.createDurableSubscriber(cTopic, "sub1");
         MessageConsumer sub2 = sess2.createDurableSubscriber(nTopic, "sub2");

         sendMessages(2, cTopic, msgBase, numMsg);
         sendMessages(0, nTopic, msgBase, numMsg);
                  
         for (int i = 0; i < 10; i++)
         {
            TextMessage rm = (TextMessage)sub1.receive(5000);
            log.info("--Message received: " + rm);
            assertEquals(msgBase + i, rm.getText());
            rm = (TextMessage)sub2.receive(5000);
            assertEquals(msgBase + i, rm.getText());
         }
         
         xres1.end(xid1, XAResource.TMSUCCESS);
         xres2.end(xid2, XAResource.TMSUCCESS);

         xres1.commit(xid1, true);
         xres2.rollback(xid2);
         
         sub1.close();
         sub2.close();
         
         xaconn1.close();
         xaconn2.close();
         
         redeployDestinations(true);

         conn1 = createConnectionOnServer(cf, 0);
         conn1.setClientID("client-id-0");
         sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         conn2 = createConnectionOnServer(cf, 0);
         conn2.setClientID("client-id-1");
         sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         conn1.start();
         conn2.start();

         sub1 = sess1.createDurableSubscriber(cTopic, "sub1");

         sub2 = sess2.createDurableSubscriber(nTopic, "sub2");
         
         for (int i = 10; i < numMsg; i++)
         {
            TextMessage rm = (TextMessage)sub1.receive(5000);
            assertEquals(msgBase + i, rm.getText());
         }
         
         for (int i = 0; i < numMsg; i++)
         {
            TextMessage rm = (TextMessage)sub2.receive(5000);
            assertEquals(msgBase + i, rm.getText());
         }
         
         sub1.close();
         sub2.close();
         sess1.unsubscribe("sub1");
         sess2.unsubscribe("sub2");
      }
      finally
      {
         if (conn1 != null)
         {
            conn1.close();
         }
         if (conn2 != null)
         {
            conn2.close();
         }
      }
   }
   
   //send some messages to queues and do redeploy, then receiving them.
   public void testRedeployQueueNoMessageLoss() throws Exception
   {
      String msgBase = "testRedeployQueueNoMessageLoss";
      int numMsg = 50;

      deployDestinations();
      
      sendMessages(1, cQueue, msgBase, numMsg);
      sendMessages(0, nQueue, msgBase, numMsg);
      
      redeployDestinations(true);
      
      receiveMessages(0, cQueue, msgBase, 0, numMsg, Session.AUTO_ACKNOWLEDGE, true);
      receiveMessages(2, nQueue, msgBase, 0, numMsg, Session.CLIENT_ACKNOWLEDGE, true);
   }
   
   //send some messages to queues and do redeploy, dropping all messages
   public void testRedeployQueueWithMessageLoss() throws Exception
   {
      String msgBase = "testRedeployQueueWithMessageLoss";
      int numMsg = 50;

      deployDestinations();
      
      sendMessages(1, cQueue, msgBase, numMsg);
      sendMessages(0, nQueue, msgBase, numMsg);
      
      redeployDestinations(false);
      
      checkEmpty(cQueue);
      checkEmpty(nQueue);
      
      sendMessages(0, cQueue, msgBase, numMsg);
      sendMessages(1, nQueue, msgBase, numMsg);
      
      receiveMessages(0, cQueue, msgBase, 0, numMsg, Session.AUTO_ACKNOWLEDGE, true);
      receiveMessages(2, nQueue, msgBase, 0, numMsg, Session.CLIENT_ACKNOWLEDGE, true);
   }

   //send some messages to queues and receive a few of them. Then do redeploy and try to receive the rest.
   public void testRedeployQueueNoMessageLoss2() throws Exception
   {
      String msgBase = "testRedeployQueueNoMessageLoss2";
      int numMsg = 50;

      deployDestinations();
      
      sendMessages(1, cQueue, msgBase, numMsg);
      sendMessages(0, nQueue, msgBase, numMsg);
      
      receiveMessages(0, cQueue, msgBase, 0, 10, Session.AUTO_ACKNOWLEDGE, false);
      receiveMessages(0, nQueue, msgBase, 0, 10, Session.AUTO_ACKNOWLEDGE, false);
      
      redeployDestinations(true);
      
      receiveMessages(0, cQueue, msgBase, 10, numMsg - 10, Session.AUTO_ACKNOWLEDGE, true);
      receiveMessages(2, nQueue, msgBase, 10, numMsg - 10, Session.CLIENT_ACKNOWLEDGE, true);
   }
   
   //send some messages to queues and receive a few within a tx, then redeply and receive them all.
   public void testRedeployQueueNoMessageLossTX() throws Exception
   {
      String msgBase = "testRedeployQueueNoMessageLossTX";
      int numMsg = 50;

      deployDestinations();
      
      sendMessages(1, cQueue, msgBase, numMsg);
      sendMessages(0, nQueue, msgBase, numMsg);
      
      receiveMessagesTX(0, cQueue, msgBase, 0, 10, false, "commit", false);
      receiveMessagesTX(0, nQueue, msgBase, 0, 10, false, "rollback", false);

      redeployDestinations(true);
      
      receiveMessages(0, cQueue, msgBase, 10, numMsg - 10, Session.AUTO_ACKNOWLEDGE, true);
      receiveMessages(2, nQueue, msgBase, 0, numMsg, Session.CLIENT_ACKNOWLEDGE, true);
   }

   //send some messages to queues and receive a few within a XA, then redeply and receive them all.
   public void testRedeployQueueNoMessageLossXA() throws Exception
   {
      String msgBase = "testRedeployQueueNoMessageLossXA";
      int numMsg = 50;

      deployDestinations();
      
      sendMessages(1, cQueue, msgBase, numMsg);
      sendMessages(0, nQueue, msgBase, numMsg);
      
      receiveMessagesTX(0, cQueue, msgBase, 0, 10, true, "commit", false);
      receiveMessagesTX(0, nQueue, msgBase, 0, 10, true, "rollback", false);

      redeployDestinations(true);
      
      receiveMessages(0, cQueue, msgBase, 10, numMsg - 10, Session.AUTO_ACKNOWLEDGE, true);
      receiveMessages(2, nQueue, msgBase, 0, numMsg, Session.CLIENT_ACKNOWLEDGE, true);
   }

   //send some messages to queues and receive a few within a XA, then redeply and receive them all.
   public void testRedeployQueueNoMessageLossXA2() throws Exception
   {
      String msgBase = "testRedeployQueueNoMessageLossXA2";
      int numMsg = 50;

      deployDestinations();
      
      sendMessages(0, cQueue, msgBase, numMsg);
      
      receiveMessagesTX(0, cQueue, msgBase, 0, 10, true, "prepared", false);

      redeployDestinations(true);
      
      receiveMessages(0, cQueue, msgBase, 10, numMsg - 10, Session.AUTO_ACKNOWLEDGE, false);
      
      recoverMessages(0, cQueue, msgBase, 0, 10);
   }

   //send some messages to queues and receive a few within a XA, then redeply and receive them all.
   public void testRedeployQueueNoMessageLossXA3() throws Exception
   {
      String msgBase = "testRedeployQueueNoMessageLossXA3";
      int numMsg = 50;

      deployDestinations();
      
      sendMessages(0, nQueue, msgBase, numMsg);
      
      receiveMessagesTX(0, nQueue, msgBase, 0, 15, true, "prepared", false);

      redeployDestinations(true);
      
      receiveMessages(2, nQueue, msgBase, 15, numMsg - 15, Session.CLIENT_ACKNOWLEDGE, true);
      
      //have to recover from node 0.
      recoverMessages(0, nQueue, msgBase, 0, 15);
   }

   //send some messages to queues and receive a few within a XA, then redeply and receive them all.
   public void testRedeployQueueNoMessageLossXA4() throws Exception
   {
      String msgBase = "testRedeployQueueNoMessageLossXA4";
      int numMsg = 50;

      deployDestinations();
      
      sendMessages(1, cQueue, msgBase, numMsg);
      sendMessages(0, nQueue, msgBase, numMsg);
      
      receiveMessagesTX(0, cQueue, msgBase, 0, 10, true, "noaction", false);
      receiveMessagesTX(0, nQueue, msgBase, 0, 10, true, "noaction", false);

      redeployDestinations(true);
      
      receiveMessages(0, cQueue, msgBase, 0, numMsg, Session.AUTO_ACKNOWLEDGE, true);
      receiveMessages(2, nQueue, msgBase, 0, numMsg, Session.CLIENT_ACKNOWLEDGE, true);
   }

   /*
    * Deploy the following destinations:
    * 
    * 1. clustered2NonclusteredQueue : a clustered queue used to be re-deployed as non-clustered.
    * 2. nonclustered2ClusteredQueue : a non-clustered queue (at node0) to be re-deployed as clustered.
    * 3. clustered2NonclusteredTopic : a clustered topic used to be re-deployed as non-clustered.
    * 4. nonclustered2ClusteredTopic : a non-clustered topic (at node0) to be re-deployed as clustered.
    * 
    */
   private void deployDestinations() throws Exception
   {
      for (int i = 0; i < nodeCount; i++)
      {
         ServerManagement.deployQueue("clustered2NonclusteredQueue", i);
         ServerManagement.deployTopic("clustered2NonclusteredTopic", i);
      }
      ServerManagement.deployQueue("nonclustered2ClusteredQueue");
      ServerManagement.deployTopic("nonclustered2ClusteredTopic");

      cQueue = (Queue)ic[0].lookup("queue/clustered2NonclusteredQueue");
      nQueue = (Queue)ic[0].lookup("queue/nonclustered2ClusteredQueue");
      cTopic = (Topic)ic[0].lookup("topic/clustered2NonclusteredTopic");
      nTopic = (Topic)ic[0].lookup("topic/nonclustered2ClusteredTopic");

      Queue anotherQueue = null;
      try
      {
         anotherQueue = (Queue)ic[1].lookup("queue/clustered2NonclusteredQueue");
         assertNotNull(anotherQueue);
      }
      catch (NamingException e)
      {
         fail("The queue " + anotherQueue + " should not exist after redeploy");
      }
      
      Topic anotherTopic = null;
      try
      {
         anotherTopic = (Topic)ic[1].lookup("topic/clustered2NonclusteredTopic");
         assertNotNull(anotherTopic);
      }
      catch (NamingException e)
      {
         fail("The topic " + anotherTopic + " should not exist after redeploy");
      }
   }

   private void redeployDestinations(boolean keepMessage) throws Exception
   {
      if (keepMessage)
      {
         redeployDestinationsWithMessage();
      }
      else
      {
         redeployDestinationsNoMessage();
      }
   }
   
   private void redeployDestinationsNoMessage() throws Exception
   {
      for (int i = 0; i < nodeCount; i++)
      {
         ServerManagement.stop(i);
      }
      
      //Restart nodes
      for (int i = 0; i < nodeCount; i++)
      {
         startDefaultServer(i, overrides, false);
         ic[i] = new InitialContext(ServerManagement.getJNDIEnvironment(i));
      }
      
      //redeploy
      for (int i = 0; i < nodeCount; i++)
      {
         ServerManagement.deployQueue("nonclustered2ClusteredQueue", i, false);
         ServerManagement.deployTopic("nonclustered2ClusteredTopic", i, false);
      }
      ServerManagement.deployQueue("clustered2NonclusteredQueue", false);
      ServerManagement.deployTopic("clustered2NonclusteredTopic", false);      


      cQueue = (Queue)ic[0].lookup("queue/clustered2NonclusteredQueue");
      nQueue = (Queue)ic[0].lookup("queue/nonclustered2ClusteredQueue");
      cTopic = (Topic)ic[0].lookup("topic/clustered2NonclusteredTopic");
      nTopic = (Topic)ic[0].lookup("topic/nonclustered2ClusteredTopic");  
      
      cf = (JBossConnectionFactory)ic[0].lookup("/ClusteredConnectionFactory");

      try
      {
         Queue nonExistQueue = (Queue)ic[1].lookup("queue/clustered2NonclusteredQueue");
         fail("The queue " + nonExistQueue + " should not exist after redeploy");
      }
      catch (NamingException e)
      {
         //ok
      }
      
      try
      {
         Topic nonExistTopic = (Topic)ic[1].lookup("topic/clustered2NonclusteredTopic");
         fail("The topic " + nonExistTopic + " should not exist after redeploy");
      }
      catch (NamingException e)
      {
         //ok
      }


   }
   
   private void redeployDestinationsWithMessage() throws Exception
   {
      for (int i = 0; i < nodeCount; i++)
      {
         ServerManagement.stop(i);
      }
      
      //Restart nodes
      for (int i = 0; i < nodeCount; i++)
      {
         startDefaultServer(i, overrides, false);
         ic[i] = new InitialContext(ServerManagement.getJNDIEnvironment(i));
      }
      
      //redeploy
      for (int i = 0; i < nodeCount; i++)
      {
         ServerManagement.deployQueue("nonclustered2ClusteredQueue", i);
         ServerManagement.deployTopic("nonclustered2ClusteredTopic", i);
      }
      ServerManagement.deployQueue("clustered2NonclusteredQueue");
      ServerManagement.deployTopic("clustered2NonclusteredTopic");      


      cQueue = (Queue)ic[0].lookup("queue/clustered2NonclusteredQueue");
      nQueue = (Queue)ic[0].lookup("queue/nonclustered2ClusteredQueue");
      cTopic = (Topic)ic[0].lookup("topic/clustered2NonclusteredTopic");
      nTopic = (Topic)ic[0].lookup("topic/nonclustered2ClusteredTopic");  
      
      cf = (JBossConnectionFactory)ic[0].lookup("/ClusteredConnectionFactory");

      try
      {
         Queue nonExistQueue = (Queue)ic[1].lookup("queue/clustered2NonclusteredQueue");
         fail("The queue " + nonExistQueue + " should not exist after redeploy");
      }
      catch (NamingException e)
      {
         //ok
      }
      
      try
      {
         Topic nonExistTopic = (Topic)ic[1].lookup("topic/clustered2NonclusteredTopic");
         fail("The topic " + nonExistTopic + " should not exist after redeploy");
      }
      catch (NamingException e)
      {
         //ok
      }


   }

   private void sendMessages(int serverIndex, Destination dest, String msgBase, int numMsg) throws Exception
   {
      Connection conn = null;
      try
      {
         conn = createConnectionOnServer(cf, serverIndex);
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = sess.createProducer(dest);
         log.info("-----Sending messages to: " + dest);
         for (int i = 0; i < numMsg; i++)
         {
            TextMessage msg = sess.createTextMessage(msgBase + i);
            producer.send(msg);
            log.info("----message sent: " + msg.getText());
            msgSet.put(msg.getText(), msg);
         }
      }
      catch (Exception e)
      {
         e.printStackTrace();
         throw e;
      }
      finally
      {
         conn.close();
      }
   }

   private void receiveMessages(int serverIndex, Destination dest, String msgBase, int startIndex,
                                int numMsg, int ack, boolean checkEmpty) throws Exception
   {
      Connection conn = null;
      
      try
      {
         Session sess = null;

         conn = createConnectionOnServer(cf, serverIndex);
         sess = conn.createSession(false, ack);
         conn.start();
         
         MessageConsumer receiver = sess.createConsumer(dest);
         TextMessage msg = null;
         
         for (int i = 0; i < numMsg; i++)
         {
            msg = (TextMessage)receiver.receive(5000);
            assertEquals(msgBase + (startIndex + i), msg.getText());
         }

         if (ack == Session.CLIENT_ACKNOWLEDGE)
         {
            msg.acknowledge();
         }
         
         if (checkEmpty)
         {
            if (dest instanceof Queue)
            {
               checkEmpty((Queue)dest);
            }
            else
            {
               checkEmpty((Topic)dest);
            }
         }
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
    * receive messages transactionally.
    * 
    * outcome values:
    * 
    * commit -- commit the transaction
    * rollback -- rollback the transaction
    * prepared -- parepared the transaction but not commit.
    * 
    */
   private void receiveMessagesTX(int serverIndex, Destination dest, String msgBase, int startIndex, 
                                  int numMsg, boolean isXA, String outcome, boolean checkEmpty) throws Exception
   {
      Connection conn = null;
      XAConnection xaconn = null;
      Session sess = null;
      XASession xasess = null;
      XAResource xres = null;
      Xid xid = null;
      try
      {
      if (isXA)
      {
         xaconn = (XAConnection)this.createXAConnectionOnServer((XAConnectionFactory)cf, serverIndex);
         xasess = xaconn.createXASession();
         xres = xasess.getXAResource();
         xaconn.start();
         sess = xasess.getSession();

         xid = new MessagingXid(("bq1" + dest).getBytes(), 42, dest.toString().getBytes());

         xres.start(xid, XAResource.TMNOFLAGS);
      }
      else
      {
         //local tx
         conn = createConnectionOnServer(cf, serverIndex);
         sess = conn.createSession(true, Session.SESSION_TRANSACTED);
         conn.start();
      }
      
      //starting receiving
      MessageConsumer cons = sess.createConsumer(dest);
      for (int i = 0; i < numMsg; i++)
      {
         TextMessage rm = (TextMessage)cons.receive(5000);
         assertEquals(msgBase + (i + startIndex), rm.getText());
      }
      
      //ending
      if (isXA)
      {
         xres.end(xid, XAResource.TMSUCCESS);
         
         if ("commit".equals(outcome))
         {
            //just one-phase is enough for the test
            xres.commit(xid, true);
         }
         else if ("rollback".equals(outcome))
         {
            xres.rollback(xid);
         }
         else if ("prepared".equals(outcome))
         {
            xres.prepare(xid);
         }
      }
      else
      {
         //local
         if ("commit".equals(outcome))
         {
            sess.commit();
         }
         else if ("rollback".equals(outcome))
         {
            sess.rollback();
         }
      }
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
         if (xaconn != null)
         {
            xaconn.close();
         }
      }
   }

   //recover the messages in transactions by rollback.
   private void recoverMessages(int serverIndex, Destination dest, 
                                String msgBase, int startIndex, int numMsg) throws Exception
   {
      Connection conn = null;
      XAConnection xaconn = null;
      Session sess = null;
      XASession xasess = null;
      XAResource xres = null;

      try
      {
         xaconn = (XAConnection)this.createXAConnectionOnServer((XAConnectionFactory)cf, serverIndex);
         xasess = xaconn.createXASession();
         xres = xasess.getXAResource();
         xaconn.start();
         
         Xid[] xids = xres.recover(XAResource.TMSTARTRSCAN);
         assertEquals(1, xids.length);

         Xid[] xids2 = xres.recover(XAResource.TMENDRSCAN);
         assertEquals(0, xids2.length);
         
         conn = this.createConnectionOnServer(cf, serverIndex);
         sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         conn.start();
         
         MessageConsumer cons = sess.createConsumer(dest);
         TextMessage rm = (TextMessage)cons.receive(5000);
         assertNull(rm);
                  
         xres.rollback(xids[0]);

         conn.stop();
         conn.start();
         for (int i = 0; i < numMsg; i++)
         {
            rm = (TextMessage)cons.receive(5000);
            assertEquals(msgBase + (startIndex + i), rm.getText());
         }

         if (dest instanceof Queue)
         {
            checkEmpty((Queue)dest);
         }
         else
         {
            checkEmpty((Topic)dest);
         }
      }
      finally
      {
         if (xaconn != null)
         {
            xaconn.close();
         }
         if (conn != null)
         {
            conn.close();
         }
      }
   }


}
