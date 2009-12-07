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


package org.jboss.test.messaging.jms;

import java.util.Iterator;
import java.util.Map;

import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.jboss.jms.client.JBossConnection;
import org.jboss.jms.client.JBossConnectionFactory;
import org.jboss.jms.client.delegate.ClientConnectionDelegate;
import org.jboss.jms.client.remoting.JMSRemotingConnection;
import org.jboss.jms.server.ServerPeer;
import org.jboss.jms.server.connectionmanager.SimpleConnectionManager;
import org.jboss.remoting.Client;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.container.Command;
import org.jboss.test.messaging.tools.container.Server;

/**
 * A DeliveryOnConnectionFailureTest
 *
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 * 
 * Created Mar 26, 2009 3:14:28 PM
 *
 */
public class DeliveryOnConnectionFailureTest extends JMSTestCase
{

   public DeliveryOnConnectionFailureTest(String name)
   {
      super(name);
   }

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------
   
   //https://jira.jboss.org/jira/browse/JBMESSAGING-1456
   //Message Stuck means messages are kept in delivering state and never be delivered again
   //unless the server is restarted (for persistent messages).
   //this can happen in the following conditions:
   //1. The client ping timeout and JBM tries to disconnect from the server (this happens in cluster).
   //2. Due to the network/remoting issue, the server will receive a 'normal' disconnection event
   //3. The server assumes the client has already closed it's connection and therefore doesn't clean up
   //4. So the connection at the server is left open, including the sessions created on that connection.
   //5. If the sessions contains messages in delivering, those messages will appear stuck.
   //To fix this, either the server side always tries to clean up the connection whenever a disconnection happens
   //or the remoting layer handle this correctly.
   //
   //Here we simplify the situation. First start the server and get a connection to it. Then
   //we send a message to the server with client ack. We receive it without ack, 
   //next we directly call the client.disconnect() from client without closing the connection
   //the server should cancel the message. Then we receive the message and ack it.
   public void testMessageStuckOnConnectionFailure() throws Exception
   {
      ConnectionFactory cf = (JBossConnectionFactory)ic.lookup("/ConnectionFactory");
      
      JBossConnection conn1 = null;
      JBossConnection conn2 = null;

      try
      {
         //create a connection
         conn1 = (JBossConnection)cf.createConnection();
         Session sess1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageProducer prod1 = sess1.createProducer(queue1);
         TextMessage msg = sess1.createTextMessage("dont-stuck-me!");
         conn1.start();
         
         //send a message
         prod1.send(msg);
         
         //receive the message but not ack
         MessageConsumer cons1 = sess1.createConsumer(queue1);
         TextMessage rm = (TextMessage)cons1.receive(2000);
         
         assertNotNull(rm);
         assertEquals("dont-stuck-me!", rm.getText());
         
         //break connection.
         JMSRemotingConnection jmsConn = ((ClientConnectionDelegate)conn1.getDelegate()).getRemotingConnection();
         Client rmClient = jmsConn.getRemotingClient();
         rmClient.disconnect();
         
         //wait for server side cleanup
         try
         {
            Thread.sleep(5000);
         }
         catch (InterruptedException e)
         {
            //ignore.
         }
         
         //now receive the message 
         conn2 = (JBossConnection)cf.createConnection();
         conn2.start();
         Session sess2 = conn2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer cons2 = sess2.createConsumer(queue1);
         TextMessage rm2 = (TextMessage)cons2.receive(2000);

         assertNotNull(rm2);
         assertEquals("dont-stuck-me!", rm2.getText());
         rm2.acknowledge();
         
         //Message count should be zero.
         //this is checked in tearDown().
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
   
   //https://jira.jboss.org/jira/browse/JBMESSAGING-1456
   //another issue with jira 1456 is negative message count.
   //This test guarantees the message count won't go negative
   //Error Scenario:
   // 1. Server side detects the connection failure (lease timeout) and close the connection/session
   // 2. The session endpoint will cancel the messages being delivered to the queue.
   // 3. At the same time the client side received some of the messages and acknowledge them
   // 4. The acknowledge action will decrease the delivering count of the queue, and the session endpoint
   //    cancel also decrease the delivering count.
   // 5. If not synchronized, one message may be canceled and acked at the same time, so the delivering count
   //    will be decreased twice for each message, resulting in negative message count.
   //
   //The test first creates a connection and send messages, then it receives the messages, then ack the last
   //msg (client-ack), at the same time, simulate the server side connection failure to trigger server side
   //clean up. This will create a possibility that when not properly synchronized, the above 
   //described issue may happen. At the end check the message count, it should always be zero.
   public void testMessageCountOnConnectionFailure() throws Exception
   {
      ConnectionFactory cf = (JBossConnectionFactory)ic.lookup("/ConnectionFactory");
      
      JBossConnection conn1 = null;
      JBossConnection conn2 = null;
      
      try
      {
         conn1 = (JBossConnection)cf.createConnection();
         conn1.start();
         Session sess1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         
         //now send messages
         MessageProducer prod1 = sess1.createProducer(queue1);
         
         final int NUM_MSG = 2000;
         for (int i = 0; i < NUM_MSG; ++i)
         {
            TextMessage tm = sess1.createTextMessage("-m"+i);
            prod1.send(tm);
         }
         
         //receive the messages
         MessageConsumer cons1 = sess1.createConsumer(queue1);
         for (int j = 0; j < NUM_MSG-1; ++j)
         {
            TextMessage rm = (TextMessage)cons1.receive(2000);
            assertNotNull(rm);
            assertEquals("-m"+j, rm.getText());
         }
         
         //last message
         TextMessage lastRm = (TextMessage)cons1.receive(2000);
         assertNotNull(lastRm);
         assertEquals("-m"+(NUM_MSG-1), lastRm.getText());
         
         final ServerClientFailureCommand cmd = new ServerClientFailureCommand();
         
         Thread exeThr = new Thread()
         {
            public void run()
            {
               try
               {
                  ServerManagement.getServer().executeCommand(cmd);
               }
               catch (Exception e)
               {
                  log.error("failed to invoke command", e);
                  fail("failure in executing command.");
               }               
            }
         };
         
         exeThr.start();

         //ack last message, making server side ack happening.
         lastRm.acknowledge();

         //receive possible canceled messages
         TextMessage prm = null;
         conn2 = (JBossConnection)cf.createConnection();
         conn2.start();
         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons2 = sess2.createConsumer(queue1);
         prm = (TextMessage)cons2.receive(2000);
         while (prm != null)
         {
            prm = (TextMessage)cons2.receive(2000);
         }
         
         //check message count
         //tearDown will do the check.
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
   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
   public static class ServerClientFailureCommand implements Command
   {

      private static final long serialVersionUID = 2603154447586447658L;

      public Object execute(Server server) throws Exception
      {
         ServerPeer peer = server.getServerPeer();

         SimpleConnectionManager cm = (SimpleConnectionManager)peer.getConnectionManager();

         Map jmsClients = cm.getClients();
         assertEquals(1, jmsClients.size());
         Map endpoints = (Map)jmsClients.values().iterator().next();
         assertEquals(1, endpoints.size());
         Map.Entry entry = (Map.Entry)endpoints.entrySet().iterator().next();
         String sessId = (String)entry.getKey();

         // triggering server side clean up
         cm.handleClientFailure(sessId);
         return null;
      }
      
   }
}
