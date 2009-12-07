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

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.jboss.jms.client.JBossConnectionFactory;
import org.jboss.jms.server.SecurityStore;
import org.jboss.jms.server.security.SecurityMetadataStore;
import org.jboss.test.messaging.tools.ServerManagement;

/**
 * A MessageSuckerTest
 *
 * @author howard
 * 
 * Created Sep 14, 2009 12:23:30 PM
 *
 *
 */
public class MessageSuckerTest extends ClusteringTestBase
{

   public MessageSuckerTest(String name)
   {
      super(name);
   }

   // https://jira.jboss.org/jira/browse/JBMESSAGING-1732
   // Initiate a Fake ClusterConnectionManager to connection to a node.
   // send messages to the node and check if messages are sucked.
   // then kill the node and restart the node, check if the messages still
   // can be sucked.
   public void testMessageSuckerReconnection() throws Exception
   {
      Connection conn0 = null;
      Connection conn1 = null;
      Connection conn2 = null;

      try
      {
         JBossConnectionFactory factory = (JBossConnectionFactory)ic[0].lookup("/ConnectionFactory");

         FakeClusterConnectionManager clusterConnMgr = new FakeClusterConnectionManager(0,
                                                                                        factory,
                                                                                        SecurityStore.SUCKER_USER,
                                                                                        SecurityMetadataStore.DEFAULT_SUCKER_USER_PASSWORD,
                                                                                        -1,
                                                                                        2000,
                                                                                        5);
         clusterConnMgr.start();

         clusterConnMgr.createConnectionInfo(true);

         clusterConnMgr.createSucker(queue[0], 0, true);

         conn1 = createConnectionOnServer(factory, 0);

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer1 = sess1.createProducer(queue[0]);

         TextMessage[] messages = new TextMessage[1];

         for (int i = 0; i < messages.length; i++)
         {
            messages[i] = sess1.createTextMessage("suck-msg" + i);
            producer1.send(messages[i]);
         }

         try
         {
            Thread.sleep(2000);
         }
         catch (InterruptedException e)
         {
            // ignore.
         }

         String result = clusterConnMgr.checkMessageSucked(0, messages);
         assertNull(result, result);

         // Now kill Node 0
         ServerManagement.stop(0);

         // Sucker connection should receive notification
         clusterConnMgr.checkConnectionFailureDetected(0);
         assertNull(result, result);
         
         //sleep for 10 sec
         try
         {
            Thread.sleep(4000);
         }
         catch(InterruptedException e)
         {
            //ignore
         }

         // Now startup Node 0 again, here we clean up the DB as the
         // message last sent won't be removed because it is sucked, not really received.
         ServerManagement.start(0, "all", true);
         ServerManagement.deployQueue("testDistributedQueue", 0);

         queue[0] = (Queue)ic[0].lookup("queue/testDistributedQueue");

         factory = (JBossConnectionFactory)ic[0].lookup("/ConnectionFactory");

         // to simulate the real case, we need to restore the connection factory and the queue
         // in reality, they don't need to update as the node aren't really dead, so those
         // objects are supposed to be valid.
         clusterConnMgr.resetFactory(0, factory);
         clusterConnMgr.updateQueueInSucker(0, queue[0]);

         result = clusterConnMgr.waitForReconnectionOK(0);
         assertNull(result, result);

         // now send 1 more messages
         conn2 = createConnectionOnServer(factory, 0);

         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer2 = sess2.createProducer(queue[0]);

         for (int i = 0; i < messages.length; i++)
         {
            messages[i] = sess2.createTextMessage("new-suck-msg" + i);
            producer2.send(messages[i]);
         }

         Thread.sleep(2000);

         // should be sucked.
         result = clusterConnMgr.checkMessageSucked(0, messages);
         assertNull(result, result);

         clusterConnMgr.stop();
      }
      finally
      {
         if (conn0 != null)
         {
            conn0.close();
         }
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

   // https://jira.jboss.org/jira/browse/JBMESSAGING-1732
   // Initiate a Fake ClusterConnectionManager to connection to a node.
   // send messages to the node and check if messages are sucked. Set retry times to 2 and retryInterval 1000.
   // then kill the node and restart the node, check if the messages cannot be sucked 
   public void testMessageSuckerReconnection2() throws Exception
   {
      Connection conn0 = null;
      Connection conn1 = null;
      Connection conn2 = null;

      try
      {
         JBossConnectionFactory factory = (JBossConnectionFactory)ic[0].lookup("/ConnectionFactory");

         FakeClusterConnectionManager clusterConnMgr = new FakeClusterConnectionManager(0,
                                                                                        factory,
                                                                                        SecurityStore.SUCKER_USER,
                                                                                        SecurityMetadataStore.DEFAULT_SUCKER_USER_PASSWORD,
                                                                                        2,
                                                                                        1000,
                                                                                        5);
         clusterConnMgr.start();

         clusterConnMgr.createConnectionInfo(false);

         clusterConnMgr.createSucker(queue[0], 0, true);

         conn1 = createConnectionOnServer(factory, 0);

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer1 = sess1.createProducer(queue[0]);

         TextMessage[] messages = new TextMessage[1];

         for (int i = 0; i < messages.length; i++)
         {
            messages[i] = sess1.createTextMessage("suck-msg" + i);
            producer1.send(messages[i]);
         }

         try
         {
            Thread.sleep(2000);
         }
         catch (InterruptedException e)
         {
            // ignore.
         }

         String result = clusterConnMgr.checkMessageSucked(0, messages);
         assertNull(result, result);

         // Now kill Node 0
         ServerManagement.stop(0);

         // Sucker connection should receive notification
         clusterConnMgr.checkConnectionFailureDetected(0);
         assertNull(result, result);
         
         //sleep for 4 sec to let the retry fail
         try
         {
            Thread.sleep(4000);
         }
         catch(InterruptedException e)
         {
            //ignore
         }

         // Now startup Node 0 again, here we clean up the DB as the
         // message last sent won't be removed because it is sucked, not really received.
         ServerManagement.start(0, "all", true);
         ServerManagement.deployQueue("testDistributedQueue", 0);

         queue[0] = (Queue)ic[0].lookup("queue/testDistributedQueue");

         factory = (JBossConnectionFactory)ic[0].lookup("/ConnectionFactory");

         result = clusterConnMgr.waitForReconnectionOK(0);
         assertNotNull(result, result);
         
         int n = clusterConnMgr.getRetryTimes(0);
         assertEquals(2, n);

         // now send 1 more messages
         conn2 = createConnectionOnServer(factory, 0);

         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer2 = sess2.createProducer(queue[0]);

         for (int i = 0; i < messages.length; i++)
         {
            messages[i] = sess2.createTextMessage("new-suck-msg" + i);
            producer2.send(messages[i]);
         }

         Thread.sleep(2000);

         // should be sucked.
         result = clusterConnMgr.checkMessageNotSucked(0);
         assertNull(result, result);

         removeAllMessages(queue[0].getQueueName(), true, 0);

         clusterConnMgr.stop();
      }
      finally
      {
         if (conn0 != null)
         {
            conn0.close();
         }
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

}
