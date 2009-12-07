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
import java.util.HashMap;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.management.ObjectName;

import org.jboss.jms.client.JBossMessageProducer;
import org.jboss.jms.message.JBossMessage;
import org.jboss.test.messaging.tools.ServerManagement;

/**
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 */
public class OrderingGroupMiscTest extends JMSTestCase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------
   private HashMap<String, ArrayList<TextMessage>> recvBuffer = new HashMap<String, ArrayList<TextMessage>>();

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------
   public OrderingGroupMiscTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   /*
    * Sending 5 messages and letting the 3rd and 5th messages go to dlq and 
    * the others (1st, 2nd and 4th) should go to the receiver
    */
   public void testOrderingWithDLQ() throws Exception
   {
      if (ServerManagement.isRemote())
      {
         return;
      }

      final int NUM_MESSAGES = 5;

      final int MAX_DELIVERIES = 8;

      ObjectName serverPeerObjectName = ServerManagement.getServerPeerObjectName();

      String testQueueObjectName = "jboss.messaging.destination:service=Queue,name=Queue1";

      Connection conn = null;

      try
      {
         String defaultDLQObjectName = "jboss.messaging.destination:service=Queue,name=Queue2";

         ServerManagement.setAttribute(serverPeerObjectName,
                                       "DefaultMaxDeliveryAttempts",
                                       String.valueOf(MAX_DELIVERIES));

         ServerManagement.setAttribute(serverPeerObjectName, "DefaultDLQ", defaultDLQObjectName);

         ServerManagement.setAttribute(new ObjectName(testQueueObjectName), "DLQ", "");

         conn = cf.createConnection();

         {
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            JBossMessageProducer prod = (JBossMessageProducer)sess.createProducer(queue1);

            for (int i = 0; i < NUM_MESSAGES; i++)
            {
               TextMessage tm = sess.createTextMessage("Message:" + i);

               prod.send(tm);
            }

            Session sess2 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageConsumer cons = sess2.createConsumer(queue1);

            conn.start();

            // first
            TextMessage rm1 = (TextMessage)cons.receive(1000);
            assertNotNull(rm1);
            assertEquals("Message:0", rm1.getText());
            rm1.acknowledge();

            // second
            TextMessage rm2 = (TextMessage)cons.receive(1000);
            assertNotNull(rm2);
            assertEquals("Message:1", rm2.getText());
            rm2.acknowledge();

            // third, leaving a hole
            for (int i = 0; i < MAX_DELIVERIES; i++)
            {
               TextMessage rm3 = (TextMessage)cons.receive(1000);
               assertNotNull(rm3);
               assertEquals("Message:2", rm3.getText());
               sess2.recover();
            }

            // fourth
            TextMessage rm4 = (TextMessage)cons.receive(1000);
            assertNotNull(rm4);
            assertEquals("Message:3", rm4.getText());
            rm4.acknowledge();

            // fifth, leaving another hole
            for (int i = 0; i < MAX_DELIVERIES; i++)
            {
               TextMessage rm5 = (TextMessage)cons.receive(1000);
               assertNotNull(rm5);
               assertEquals("Message:4", rm5.getText());
               sess2.recover();
            }

            TextMessage rmx = (TextMessage)cons.receive(1000);
            assertNull(rmx);

            // At this point all the messages have been delivered exactly MAX_DELIVERIES times

            checkEmpty(queue1);

            // Now should be in default dlq
            MessageConsumer cons3 = sess.createConsumer(queue2);

            TextMessage dm3 = (TextMessage)cons3.receive(1000);
            assertNotNull(dm3);
            assertEquals("Message:2", dm3.getText());

            TextMessage dm5 = (TextMessage)cons3.receive(1000);
            assertNotNull(dm5);
            assertEquals("Message:4", dm5.getText());

            conn.close();
         }
      }
      finally
      {
         ServerManagement.setAttribute(serverPeerObjectName,
                                       "DefaultDLQ",
                                       "jboss.messaging.destination:service=Queue,name=DLQ");

         ServerManagement.setAttribute(new ObjectName(testQueueObjectName), "DLQ", "");

         if (conn != null)
         {
            conn.close();
         }
      }
   }

   /*
    * Sending 5 messages and letting the 3rd and 5th messages expire and 
    * the others (1st, 2nd and 4th) should go to the receiver
    */
   public void testOrderingWithExpiryQueue() throws Exception
   {
      final int NUM_MESSAGES = 5;

      Connection conn = null;

      ObjectName serverPeerObjectName = ServerManagement.getServerPeerObjectName();

      try
      {
         ServerManagement.deployQueue("DefaultExpiry");

         ServerManagement.deployQueue("TestOrderingQueue");

         String defaultExpiryObjectName = "jboss.messaging.destination:service=Queue,name=DefaultExpiry";

         String testQueueObjectName = "jboss.messaging.destination:service=Queue,name=TestOrderingQueue";

         ServerManagement.setAttribute(serverPeerObjectName, "DefaultExpiryQueue", defaultExpiryObjectName);

         ServerManagement.setAttribute(new ObjectName(testQueueObjectName), "ExpiryQueue", "");

         Queue testQueue = (Queue)ic.lookup("/queue/TestOrderingQueue");

         Queue defaultExpiry = (Queue)ic.lookup("/queue/DefaultExpiry");

         conn = cf.createConnection();

         {
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            JBossMessageProducer prod = (JBossMessageProducer)sess.createProducer(testQueue);

            conn.start();

            for (int i = 0; i < NUM_MESSAGES; i++)
            {
               TextMessage tm = sess.createTextMessage("Message:" + i);

               if (i == 2 || i == 4)
               {
                  // Send messages with time to live of 2000 enough time to get to client consumer - so
                  // they won't be expired on the server side
                  prod.send(tm, DeliveryMode.PERSISTENT, 4, 2000);
               }
               else
               {
                  prod.send(tm);
               }
            }

            Session sess2 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageConsumer cons = sess2.createConsumer(testQueue);

            // The messages should now be sitting in the consumer buffer
            // Now give them enough time to expire
            Thread.sleep(2500);

            // this moment, only first message is delivered but waiting for ack.
            // 3rd and 5th message still in queue, but when they are delivered
            // they will be expired and won't be received by this consumer.
            TextMessage rm1 = (TextMessage)cons.receive(1000);
            assertNotNull(rm1);
            assertEquals("Message:0", rm1.getText());
            rm1.acknowledge();

            TextMessage rm2 = (TextMessage)cons.receive(1000);
            assertNotNull(rm2);
            assertEquals("Message:1", rm2.getText());
            rm2.acknowledge();

            TextMessage rm3 = (TextMessage)cons.receive(1000);
            assertNotNull(rm3);
            assertEquals("Message:3", rm3.getText());
            rm3.acknowledge();

            TextMessage rm4 = (TextMessage)cons.receive(1000);
            assertNull(rm4);

            // Message should all be in the default expiry queue - let's check

            MessageConsumer cons3 = sess.createConsumer(defaultExpiry);

            TextMessage dm1 = (TextMessage)cons3.receive(1000);
            assertNotNull(dm1);
            assertEquals("Message:2", dm1.getText());

            TextMessage dm2 = (TextMessage)cons3.receive(1000);
            assertNotNull(dm2);
            assertEquals("Message:4", dm2.getText());

            conn.close();
         }

      }
      finally
      {
         ServerManagement.setAttribute(serverPeerObjectName,
                                       "DefaultExpiryQueue",
                                       "jboss.messaging.destination:service=Queue,name=ExpiryQueue");

         ServerManagement.undeployQueue("DefaultExpiry");

         ServerManagement.undeployQueue("TestOrderingQueue");

         if (conn != null)
         {
            conn.close();
         }
      }
   }

   /*
    * First send 2 normal messages, then send 10 ordering messages with some priority and 
    * then disable ordering, then send 2 more normal messages with high
    * priority. Make sure the normal messages are received first
    * and the ordered messages are received later but ordered.
    */
   public void testOrderingGroupOnOff() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = cf.createConnection();

         Session producerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         JBossMessageProducer producer = (JBossMessageProducer)producerSess.createProducer(queue1);

         conn.start();

         TextMessage tmNormal1 = producerSess.createTextMessage("NoOrdering-1");
         producer.send(tmNormal1, DeliveryMode.PERSISTENT, 6, Message.DEFAULT_TIME_TO_LIVE);
         TextMessage tmNormal2 = producerSess.createTextMessage("NoOrdering-2");
         producer.send(tmNormal2, DeliveryMode.PERSISTENT, 7, Message.DEFAULT_TIME_TO_LIVE);

         producer.enableOrderingGroup(null);
         // sending out ordering messages with priorities ranging from 0 to 5;
         for (int i = 0; i < 10; i++)
         {
            TextMessage tm = producerSess.createTextMessage("Ordering" + i);
            producer.send(tm, DeliveryMode.PERSISTENT, i % 6, Message.DEFAULT_TIME_TO_LIVE);
         }

         producer.disableOrderingGroup();

         TextMessage tmNormal3 = producerSess.createTextMessage("NoOrdering-3");
         producer.send(tmNormal3, DeliveryMode.PERSISTENT, 8, Message.DEFAULT_TIME_TO_LIVE);
         TextMessage tmNormal4 = producerSess.createTextMessage("NoOrdering-4");
         producer.send(tmNormal4, DeliveryMode.PERSISTENT, 9, Message.DEFAULT_TIME_TO_LIVE);

         Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(queue1);

         TextMessage rmNormal = (TextMessage)consumer.receive(1000);
         assertNotNull(rmNormal);
         assertEquals("NoOrdering-4", rmNormal.getText());

         rmNormal = (TextMessage)consumer.receive(1000);
         assertNotNull(rmNormal);
         assertEquals("NoOrdering-3", rmNormal.getText());

         rmNormal = (TextMessage)consumer.receive(1000);
         assertNotNull(rmNormal);
         assertEquals("NoOrdering-2", rmNormal.getText());

         rmNormal = (TextMessage)consumer.receive(1000);
         assertNotNull(rmNormal);
         assertEquals("NoOrdering-1", rmNormal.getText());

         for (int i = 0; i < 10; i++)
         {
            TextMessage rm = (TextMessage)consumer.receive(1000);
            assertNotNull(rm);
            assertEquals("Ordering" + i, rm.getText());
            rm.acknowledge();
         }

         assertNull(consumer.receive(1000));
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
    * create 10 ordering groups, each sending 100 messages
    * make sure the order of each group is guaranteed.
    */
   public void testMultipleOrderingGroups() throws Exception
   {

      final int NUM_PRODUCERS = 10;
      final int NUM_MSG = 100;
      JBossMessageProducer[] prods = new JBossMessageProducer[NUM_PRODUCERS];
      Connection conn = null;

      try
      {
         conn = cf.createConnection();

         Session producerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         for (int i = 0; i < NUM_PRODUCERS; i++)
         {
            prods[i] = (JBossMessageProducer)producerSess.createProducer(queue1);
            prods[i].enableOrderingGroup(null);
         }

         Session consumerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(queue1);
         conn.start();

         // Send some messages
         for (int i = 0; i < NUM_PRODUCERS; i++)
         {
            String key = prepareReceivingBuffer(i);
            for (int j = 0; j < NUM_MSG; j++)
            {
               TextMessage tm = producerSess.createTextMessage(key + ":" + j);
               prods[i].send(tm, DeliveryMode.PERSISTENT, j % 10, Message.DEFAULT_TIME_TO_LIVE);
            }
         }

         assertEquals(NUM_PRODUCERS, recvBuffer.size());

         log.trace("Sent messages");

         while (true)
         {
            TextMessage rm = (TextMessage)consumer.receive(1000);
            if (rm == null)
               break;
            putToBuffer(rm);
         }

         for (int i = 0; i < NUM_PRODUCERS; ++i)
         {
            String key = "ordering-" + i;
            ArrayList<TextMessage> group = recvBuffer.get(key);
            assertEquals(NUM_MSG, group.size());
            for (int j = 0; j < NUM_MSG; ++j)
            {
               TextMessage rm = group.get(j);
               assertEquals(key + ":" + j, rm.getText());
            }
         }

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
   
   //https://jira.jboss.org/jira/browse/JBMESSAGING-1664
   //Deploy a queue, send some messages that will cause the some messages
   //to page to DB. Then kill the server and restart it, let messages
   //loaded from the DB. Check the delivery order.
   public void testOrderingGroupOnPaging() throws Exception
   {
      //can't run invm mode.
      if (!this.isRemote()) return;
            
      ServerManagement.deployQueue("pagingQ", null, 20, 10, 10);
      
      Queue queue = (Queue)ic.lookup("/queue/pagingQ");
      
      Connection conn = null;
      
      try
      {
         conn = cf.createConnection();
         
         conn.start();
                  
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         JBossMessageProducer sender = (JBossMessageProducer)session.createProducer(queue);
         
         sender.enableOrderingGroup("my-group");
         
         final int numMessages = 50;
         
         for (int i = 0; i < numMessages; i++)
         {
            TextMessage m = session.createTextMessage("Hello" + i);
            sender.send(m, DeliveryMode.PERSISTENT, i%9, Message.DEFAULT_TIME_TO_LIVE);
         }
         
         conn.close();
         
         //kill the server
         ServerManagement.kill(0);
         
         //start the server
         ServerManagement.start(0, conf, false);
         deployAndLookupAdministeredObjects();
         ServerManagement.deployQueue("pagingQ", null, 20, 10, 10);
         
         queue = (Queue)ic.lookup("/queue/pagingQ");
         
         //receiving
         conn = cf.createConnection();
         conn.start();
         
         session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = session.createConsumer(queue);
         
         for (int i = 0; i < numMessages; i++)
         {
            TextMessage rm = (TextMessage)cons.receive(5000);
            assertEquals("Hello" + i, rm.getText());
         }
         
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
         
         try
         {
            ServerManagement.undeployQueue("pagingQ");
         }
         catch(Exception ignore)
         {
            
         }
      }
   }
   
   //https://jira.jboss.org/jira/browse/JBMESSAGING-1664
   //Deploy a queue, send some messages that will cause the some messages
   //to page to DB. Consume several messages so as to make the in-memory queue
   //is less then fullSize, but didn't trigger any paging (the key is not to let 
   //the first message in memory be delivered). 
   //Then kill the server and restart it, let messages
   //loaded from the DB. Check the delivery order.
   public void testOrderingGroupOnPaging2() throws Exception
   {
      //can't run invm mode.
      if (!this.isRemote()) return;
            
      ServerManagement.deployQueue("pagingQ", null, 20, 10, 10);
      
      Queue queue = (Queue)ic.lookup("/queue/pagingQ");
      
      Connection conn = null;
      
      try
      {
         conn = cf.createConnection();
         
         conn.start();
                  
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         JBossMessageProducer sender = (JBossMessageProducer)session.createProducer(queue);
         
         sender.enableOrderingGroup("my-group");
         
         final int numMessages = 35;
         
         for (int i = 0; i < numMessages; i++)
         {
            TextMessage m = session.createTextMessage("Hello" + i);
            sender.send(m, DeliveryMode.PERSISTENT, i%10, Message.DEFAULT_TIME_TO_LIVE);
         }
         
         MessageConsumer consumer =  session.createConsumer(queue);

         for (int i = 0; i < 5; i++)
         {
            TextMessage message = (TextMessage)consumer.receive(5000);
            assertNotNull(message);
            assertEquals("Hello" + i, message.getText());
         }
         
         conn.close();
         
         //kill the server
         ServerManagement.kill(0);
         
         //start the server
         ServerManagement.start(0, conf, false);
         deployAndLookupAdministeredObjects();
         ServerManagement.deployQueue("pagingQ", null, 20, 10, 10);
         
         queue = (Queue)ic.lookup("/queue/pagingQ");
                  
         //receiving
         conn = cf.createConnection();
         conn.start();
         
         session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = session.createConsumer(queue);
         
         for (int i = 5; i < numMessages; i++)
         {
            TextMessage rm = (TextMessage)cons.receive(5000);
            assertEquals("Hello" + i, rm.getText());
         }
         
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
         
         try
         {
            ServerManagement.undeployQueue("pagingQ");
         }
         catch(Exception ignore)
         {
            
         }
      }
   }

   //https://jira.jboss.org/jira/browse/JBMESSAGING-11728
   //test ordering while depaging.
   public void testOrderingGroupOnPaging3() throws Exception
   {
      //can't run invm mode.
      if (!this.isRemote()) return;
            
      ServerManagement.deployQueue("pagingQ", null, 5, 2, 2);
      
      Queue queue = (Queue)ic.lookup("/queue/pagingQ");
      
      Connection conn = null;
      
      try
      {
         conn = cf.createConnection();
         
         conn.start();
                  
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         JBossMessageProducer sender = (JBossMessageProducer)session.createProducer(queue);
         
         sender.enableOrderingGroup("my-group");
         
         final int numMessages = 20;
         
         for (int i = 0; i < numMessages; i++)
         {
            TextMessage m = session.createTextMessage("Hello" + i);
            sender.send(m, DeliveryMode.PERSISTENT, 5, Message.DEFAULT_TIME_TO_LIVE);
         }
         
         MessageConsumer consumer =  session.createConsumer(queue);

         TextMessage rm = null;
         for (int i = 0; i < 2; i++)
         {
            rm = (TextMessage)consumer.receive(5000);
            assertNotNull(rm);
            assertEquals("Hello" + i, rm.getText());
         }
         
         //receive 3
         rm = (TextMessage)consumer.receive(5000);
         assertEquals("Hello2", rm.getText());

         //add a new one
         sender.send(session.createTextMessage("NewMessage"));
         
         //receive all left.
         for (int i = 3; i < numMessages; i++)
         {
            rm = (TextMessage)consumer.receive(5000);
            assertNotNull(rm);
            assertEquals("Hello" + i, rm.getText());
         }
         
         //last one
         rm = (TextMessage)consumer.receive(5000);
         assertEquals("NewMessage", rm.getText());
         
         rm = (TextMessage)consumer.receive(5000);
         assertNull(rm);
         
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
         
         try
         {
            ServerManagement.undeployQueue("pagingQ");
         }
         catch(Exception ignore)
         {
            
         }
      }
   }

   //https://jira.jboss.org/jira/browse/JBMESSAGING-11728
   //test ordering while depaging (messages sent with various priorities)
   public void testOrderingGroupOnPaging4() throws Exception
   {
      //can't run invm mode.
      if (!this.isRemote()) return;
            
      ServerManagement.deployQueue("pagingQ", null, 5, 2, 2);
      
      Queue queue = (Queue)ic.lookup("/queue/pagingQ");
      
      Connection conn = null;
      
      try
      {
         conn = cf.createConnection();
         
         conn.start();
                  
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         JBossMessageProducer sender = (JBossMessageProducer)session.createProducer(queue);
         
         sender.enableOrderingGroup("my-group");
         
         final int numMessages = 20;
         
         for (int i = 0; i < numMessages; i++)
         {
            TextMessage m = session.createTextMessage("Hello" + i);
            sender.send(m, DeliveryMode.PERSISTENT, i%10, Message.DEFAULT_TIME_TO_LIVE);
         }
         
         MessageConsumer consumer =  session.createConsumer(queue);

         TextMessage rm = null;
         for (int i = 0; i < 2; i++)
         {
            rm = (TextMessage)consumer.receive(5000);
            assertNotNull(rm);
            assertEquals("Hello" + i, rm.getText());
         }
         
         //receive 3
         rm = (TextMessage)consumer.receive(5000);
         assertEquals("Hello2", rm.getText());

         //add a new one
         sender.send(session.createTextMessage("NewMessage"));
         
         //receive all left.
         for (int i = 3; i < numMessages; i++)
         {
            rm = (TextMessage)consumer.receive(5000);
            assertNotNull(rm);
            assertEquals("Hello" + i, rm.getText());
         }
         
         //last one
         rm = (TextMessage)consumer.receive(5000);
         assertEquals("NewMessage", rm.getText());
         
         rm = (TextMessage)consumer.receive(5000);
         assertNull(rm);
         
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
         
         try
         {
            ServerManagement.undeployQueue("pagingQ");
         }
         catch(Exception ignore)
         {
            
         }
      }
   }

   //https://jira.jboss.org/jira/browse/JBMESSAGING-1726
   //send one ordering group message and receive it, then resend it out
   //using another group name, the group name should not be changed.
   public void testOrderingGroupNameOverride() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = cf.createConnection();

         Session producerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         JBossMessageProducer producer1 = (JBossMessageProducer)producerSess.createProducer(queue1);

         producer1.enableOrderingGroup("MyGroup1");

         JBossMessageProducer producer2 = (JBossMessageProducer)producerSess.createProducer(queue1);

         producer2.enableOrderingGroup("MyGroup2");

         conn.start();

         TextMessage orderMsg1 = producerSess.createTextMessage("ordering-group-1");
         TextMessage orderMsg2 = producerSess.createTextMessage("ordering-group-2");
         producer1.send(orderMsg1, DeliveryMode.PERSISTENT, 4, Message.DEFAULT_TIME_TO_LIVE);

         Session consumerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(queue1);

         TextMessage rm1 = (TextMessage)consumer.receive(2000);
         assertNotNull(rm1);
         assertEquals("ordering-group-1", rm1.getText());
         String groupName = rm1.getStringProperty(JBossMessage.JBOSS_MESSAGING_ORDERING_GROUP_ID);
         assertEquals("MyGroup1", groupName);
         
         producer2.send(orderMsg2, DeliveryMode.PERSISTENT, 4, Message.DEFAULT_TIME_TO_LIVE);
         producer2.send(rm1, DeliveryMode.PERSISTENT, 4, Message.DEFAULT_TIME_TO_LIVE);
         
         TextMessage rm2 = (TextMessage)consumer.receive(2000);
         assertEquals("ordering-group-2", rm2.getText());
         groupName = rm2.getStringProperty(JBossMessage.JBOSS_MESSAGING_ORDERING_GROUP_ID);
         assertEquals("MyGroup2", groupName);
         
         TextMessage rm3 = (TextMessage)consumer.receive(2000);
         assertEquals("ordering-group-1", rm3.getText());
         groupName = rm3.getStringProperty(JBossMessage.JBOSS_MESSAGING_ORDERING_GROUP_ID);
         assertEquals("MyGroup1", groupName);

         assertNull(consumer.receive(2000));
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

   /**
    * @param rm
    * @throws JMSException 
    */
   private void putToBuffer(TextMessage rm) throws JMSException
   {
      String text = rm.getText();
      String[] tokens = text.split(":");
      String key = tokens[0];
      ArrayList<TextMessage> group = recvBuffer.get(key);
      group.add(rm);
   }

   /**
    * initialize a buffer for receiving ordering group messages.
    * @param i
    */
   private String prepareReceivingBuffer(int i)
   {
      String key = "ordering-" + i;
      ArrayList<TextMessage> grpBuffer = recvBuffer.get(key);
      if (grpBuffer == null)
      {
         grpBuffer = new ArrayList<TextMessage>();
         recvBuffer.put(key, grpBuffer);
      }
      return key;
   }

   // Inner classes -------------------------------------------------
}
