/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
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
package org.jboss.example.jms.ordering;

import java.util.ArrayList;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Message;
import javax.naming.InitialContext;

import org.jboss.example.jms.common.ExampleSupport;
import org.jboss.jms.client.JBossMessageProducer;

/**
 * This example creates a JMS Connection to a JBoss Messaging instance and uses it to create a
 * session and two message producers, one normal producer called 'producer1', the other producer
 * called 'producer' is used to produce ordering group messages. Then both producers send 5 messages
 * (producer1 sending 2 and producer 3) to "queue/testQueue". The example then starts two consumer 
 * threads to receive these messages. Once the messages are all received, it check the results.
 * 
 * This example illustrates that ordering group messages will be delivered exactly in the same
 * order as they are sent, even if the messages has different priorities and are consumed by
 * multiple consumers at the same time.
 *
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 *
 */
public class OrderingGroupExample extends ExampleSupport
{
   public static final String NORMAL_MSG = "Hello-normal";
   public static final String NORMAL_MSG1 = "Hello-normal-1";
   public static final String ORDERING_MSG1 = "Hello!";
   public static final String ORDERING_MSG2 = "Hello-1!";
   public static final String ORDERING_MSG3 = "Hello-2!";

   private ArrayList<String> deliveryRecords = new ArrayList<String>();

   private void checkResults() throws Exception
   {
      int len = deliveryRecords.size();
      System.out.println("len: " + len);
      assertEquals(5, len);

      int n1 = deliveryRecords.indexOf(ORDERING_MSG1);
      int n2 = deliveryRecords.indexOf(ORDERING_MSG2);
      int n3 = deliveryRecords.indexOf(ORDERING_MSG3);

      int flag = 1;
      if ( (n1 < n2) && (n2 < n3) )
      {
         flag = 0;
      }
      assertEquals(0, flag);
   }

   public boolean allReceived()
   {
      synchronized(deliveryRecords)
      {
         return deliveryRecords.size() == 5;
      }
   }

   public void example() throws Exception
   {
      String destinationName = getDestinationJNDIName();
      
      InitialContext ic = null;
      ConnectionFactory cf = null;
      Connection connection =  null;

      try
      {         
         ic = new InitialContext();
         
         cf = (ConnectionFactory)ic.lookup("/ConnectionFactory");
         Queue queue = (Queue)ic.lookup(destinationName);
         log("Queue " + destinationName + " exists");
         
         connection = cf.createConnection();
         Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
        
         JBossMessageProducer producer1 = (JBossMessageProducer)session.createProducer(queue);
         TextMessage normalMsg = session.createTextMessage(NORMAL_MSG);
         TextMessage normalMsg1 = session.createTextMessage(NORMAL_MSG1);

         log("Sending mormal message with lower priority 1");
         producer1.send(normalMsg1, Message.DEFAULT_DELIVERY_MODE, 1, Message.DEFAULT_TIME_TO_LIVE);

         JBossMessageProducer producer = (JBossMessageProducer)session.createProducer(queue);
         producer.enableOrderingGroup("MyOrderingGroup");
         
         TextMessage message = session.createTextMessage(ORDERING_MSG1);
         message.setJMSPriority(0);
         log("Sending message with priority 0");
         producer.send(message, Message.DEFAULT_DELIVERY_MODE, 5, Message.DEFAULT_TIME_TO_LIVE);
         
         TextMessage message2 = session.createTextMessage(ORDERING_MSG2);
         log("Sending message2 with priority 8");
         producer.send(message2, Message.DEFAULT_DELIVERY_MODE, 6, Message.DEFAULT_TIME_TO_LIVE);

         log("Sending normal message with priority 7");
         producer1.send(normalMsg, Message.DEFAULT_DELIVERY_MODE, 7, Message.DEFAULT_TIME_TO_LIVE);
         
         TextMessage message3 = session.createTextMessage(ORDERING_MSG3);
         log("Sending message3 with priority 5");
         producer.send(message3, Message.DEFAULT_DELIVERY_MODE, 8, Message.DEFAULT_TIME_TO_LIVE);
         
         log("The message was successfully sent to the " + queue.getQueueName() + " queue");
         
         connection.start();
         session.commit();

         ConsumerThread client1 = new ConsumerThread("Client-1", this, cf, queue);
         ConsumerThread client2 = new ConsumerThread("Client-2", this, cf, queue);

         client1.start();
         client2.start();

         client1.join();
         client2.join();

         checkResults();

         displayProviderInfo(connection.getMetaData());
                 
      }
      finally
      {         
         if(ic != null)
         {
            try
            {
               ic.close();
            }
            catch(Exception e)
            {
               throw e;
            }
         }
         
         // ALWAYS close your connection in a finally block to avoid leaks.
         // Closing connection also takes care of closing its related objects e.g. sessions.
         closeConnection(connection);
      }
   }
   
   private void closeConnection(Connection con)
   {      
      try
      {
         if (con != null)
         {
            con.close();
         }         
      }
      catch(JMSException jmse)
      {
         log("Could not close connection " + con +" exception was " + jmse);
      }
   }
      
   protected boolean isQueueExample()
   {
      return true;
   }
   
   public static void main(String[] args)
   {
      new OrderingGroupExample().run();
   }

   public void reportReceive(TextMessage msg) throws JMSException
   {
      synchronized(deliveryRecords)
      {
         deliveryRecords.add(msg.getText());
      }
   }

   public int getNumMessages()
   {
      return 5;
   }
      
}
