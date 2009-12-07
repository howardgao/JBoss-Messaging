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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.jboss.jms.client.JBossMessageProducer;
import org.jboss.jms.message.JBossMessage;

/**
 * This is additional tests for ordering group feature in 1.4. Other tests of ordering group
 * are scattered in tests elsewhere that suitable for their test categories.
 * 
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 *
 */

public class OrderingGroupGeneralTest extends JMSTestCase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------
   public OrderingGroupGeneralTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------
   
   /**
    * nP --> 1C (multiple producers vs single client)
    * create four producers from two connections and all send some message belonging
    * to one same ordering group. check the receiving end that they are received orderly.
    */
   public void testMultipleProducersSingleClient() throws Exception
   {
      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;
      
      try
      {
         conn1 = cf.createConnection();         
         conn2 = cf.createConnection();         
         conn3 = cf.createConnection();         
         
         conn1.start();
         conn2.start();
         conn3.start();
         
         //two producers on conn1 and two more on conn2
         JBossMessageProducer[] prods = new JBossMessageProducer[4];
         Session sess1 = conn1.createSession(false, 0);
         prods[0] = (JBossMessageProducer)sess1.createProducer(queue1);
         prods[1] = (JBossMessageProducer)sess1.createProducer(queue1);
         //now we do on different sessions 
         Session sess2 = conn2.createSession(false, 0);
         prods[2] = (JBossMessageProducer)sess2.createProducer(queue1);
         Session sess3 = conn2.createSession(false, 0);
         prods[3] = (JBossMessageProducer)sess3.createProducer(queue1);
         
         //enable ordering group
         final String ORDER_GROUP_NAME = "SameOneGroup";
         
         prods[0].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[1].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[2].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[3].enableOrderingGroup(ORDER_GROUP_NAME);
         
         //creating and messages.
         final int NUM_MSG = 200;
         TextMessage[] testMsgs = new TextMessage[NUM_MSG];
         Random rand = new Random();
         for (int i = 0; i < NUM_MSG; i++)
         {
            testMsgs[i] = sess1.createTextMessage("ordering" + i);
            //sending, in real world the order of sending may not be the order of arriving
            //as the sending can be performed on different threads, processes or machines.
            //but in test we need to know beforehand the order so that we can verify.
            int indx = rand.nextInt(4);
            int priority = rand.nextInt(10);
            prods[indx].send(testMsgs[i], DeliveryMode.PERSISTENT, priority, 0);
         }
         
         //receiving and verifying
         Session session = conn3.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = session.createConsumer(queue1);
         
         for (int i = 0; i < NUM_MSG; i++)
         {
            TextMessage rm = (TextMessage)cons.receive(500);
            assertNotNull(rm);
            assertEquals("ordering" + i, rm.getText());
         }

         checkEmpty(queue1);
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
         if (conn3 != null)
         {
            conn3.close();
         }
      }
   }

   /**
    * 1P --> nC (multiple producers vs multiple clients)
    * create one producer send some messages belonging
    * to one same ordering group. Receive them by two clients of different modes.
    * check the receiving end that they are received orderly.
    */
   public void testOneProducersMultipleClient() throws Exception
   {
      Connection conn1 = null;

      Connection conn2 = null;
      Connection conn3 = null;
      
      try
      {
         conn1 = cf.createConnection();         

         conn2 = cf.createConnection();         
         conn3 = cf.createConnection();         
         
         conn1.start();

         
         //two producers on conn1 and two more on conn2
         JBossMessageProducer[] prods = new JBossMessageProducer[1];
         Session sess1 = conn1.createSession(false, 0);
         prods[0] = (JBossMessageProducer)sess1.createProducer(queue1);
         
         //enable ordering group
         final String ORDER_GROUP_NAME = "SameOneGroup";
         
         prods[0].enableOrderingGroup(ORDER_GROUP_NAME);
         
         //creating and messages.
         final int NUM_MSG = 200;
         TextMessage[] testMsgs = new TextMessage[NUM_MSG];
         Random rand = new Random();
         for (int i = 0; i < NUM_MSG; i++)
         {
            testMsgs[i] = sess1.createTextMessage("ordering" + i);
            int priority = rand.nextInt(10);
            prods[0].send(testMsgs[i], DeliveryMode.PERSISTENT, priority, 0);
         }
         
         //receiving and verifying
         Session session = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons1 = session.createConsumer(queue1);
         cons1.setMessageListener(new MsgOrderListener(true));
         
         Session session2 = conn3.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer cons2 = session2.createConsumer(queue1);
         cons2.setMessageListener(new MsgOrderListener(false));
         
         conn2.start();
         conn3.start();

         //delay for 10 sec, let delivery over
         delay(10000);
         
         checkEmpty(queue1);
         
         //check the order now.
         assertEquals(NUM_MSG, rcvBuffer.size());
         for (int i = 0; i < NUM_MSG; i++)
         {
            assertEquals("ordering" + i, rcvBuffer.get(i).getText());
         }
         
         rcvBuffer.clear();
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
         if (conn3 != null)
         {
            conn3.close();
         }
      }
   }

   /**
    * nP --> nC (multiple producers vs multiple clients)
    * create four producers from two connections and all send some message belonging
    * to one same ordering group. Receive them by two clients of different modes.
    * check the receiving end that they are received orderly.
    */
   public void testMultipleProducersMultipleClients() throws Exception
   {
      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;
      Connection conn4 = null;
      
      try
      {
         conn1 = cf.createConnection();         
         conn2 = cf.createConnection();         
         conn3 = cf.createConnection();         
         conn4 = cf.createConnection();         
         
         conn1.start();
         conn2.start();
         
         //two producers on conn1 and two more on conn2
         JBossMessageProducer[] prods = new JBossMessageProducer[4];
         Session sess1 = conn1.createSession(false, 0);
         prods[0] = (JBossMessageProducer)sess1.createProducer(queue1);
         prods[1] = (JBossMessageProducer)sess1.createProducer(queue1);
         //now we do on different sessions 
         Session sess2 = conn2.createSession(false, 0);
         prods[2] = (JBossMessageProducer)sess2.createProducer(queue1);
         Session sess3 = conn2.createSession(false, 0);
         prods[3] = (JBossMessageProducer)sess3.createProducer(queue1);
         
         //enable ordering group
         final String ORDER_GROUP_NAME = "SameOneGroup";
         
         prods[0].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[1].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[2].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[3].enableOrderingGroup(ORDER_GROUP_NAME);
         
         //creating and messages.
         final int NUM_MSG = 200;
         TextMessage[] testMsgs = new TextMessage[NUM_MSG];
         Random rand = new Random();
         for (int i = 0; i < NUM_MSG; i++)
         {
            testMsgs[i] = sess1.createTextMessage("ordering" + i);
            //sending, in real world the order of sending may not be the order of arriving
            //as the sending can be performed on different threads, processes or machines.
            //but in test we need to know beforehand the order so that we can verify.
            int indx = rand.nextInt(4);
            int priority = rand.nextInt(10);
            prods[indx].send(testMsgs[i], DeliveryMode.PERSISTENT, priority, 0);
         }
         
         //receiving and verifying
         Session session = conn3.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons1 = session.createConsumer(queue1);
         cons1.setMessageListener(new MsgOrderListener(true));
         
         Session session2 = conn4.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer cons2 = session2.createConsumer(queue1);
         cons2.setMessageListener(new MsgOrderListener(false));
         
         conn3.start();
         conn4.start();

         //delay for 10 sec, let delivery over
         delay(10000);
         
         checkEmpty(queue1);
         
         //check the order now.
         assertEquals(NUM_MSG, rcvBuffer.size());
         for (int i = 0; i < NUM_MSG; i++)
         {
            assertEquals("ordering" + i, rcvBuffer.get(i).getText());
         }
         
         rcvBuffer.clear();
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
         if (conn3 != null)
         {
            conn3.close();
         }
         if (conn4 != null)
         {
            conn4.close();
         }
      }
   }
   
   /**
    * four producers, two ordering groups, one client
    */
   public void testMultipleProducersInMultiGroups() throws Exception
   {
      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;
      
      try
      {
         conn1 = cf.createConnection();         
         conn2 = cf.createConnection();         
         conn3 = cf.createConnection();         
         
         conn1.start();
         conn2.start();
         
         //two producers on conn1 and two more on conn2
         JBossMessageProducer[] prods = new JBossMessageProducer[4];
         Session sess1 = conn1.createSession(false, 0);
         prods[0] = (JBossMessageProducer)sess1.createProducer(queue1);
         prods[1] = (JBossMessageProducer)sess1.createProducer(queue1);
         //now we do on different sessions 
         Session sess2 = conn2.createSession(false, 0);
         prods[2] = (JBossMessageProducer)sess2.createProducer(queue1);
         Session sess3 = conn2.createSession(false, 0);
         prods[3] = (JBossMessageProducer)sess3.createProducer(queue1);
         
         //enable ordering group
         final String ORDER_GROUP_NAME1 = "SameOneGroup1";
         final String ORDER_GROUP_NAME2 = "SameOneGroup2";
         
         prods[0].enableOrderingGroup(ORDER_GROUP_NAME1);
         prods[1].enableOrderingGroup(ORDER_GROUP_NAME2);
         prods[2].enableOrderingGroup(ORDER_GROUP_NAME1);
         prods[3].enableOrderingGroup(ORDER_GROUP_NAME2);
         
         //creating and messages.
         final int NUM_MSG = 100;
         TextMessage[] testMsgs1 = new TextMessage[NUM_MSG];
         TextMessage[] testMsgs2 = new TextMessage[NUM_MSG];

         Random rand = new Random();
         int i1 = 0;
         int i2 = 0;
         boolean dowork1 = true;
         boolean dowork2 = true;
         
         while( dowork1 || dowork2 )
         {
            //sending, make sending random, to see if two groups interfere each other

            boolean first = rand.nextBoolean();
            int indx = rand.nextInt(2);
            int priority = rand.nextInt(10);

            if (first && dowork1)
            {
               testMsgs1[i1] = sess1.createTextMessage("ordering" + i1);
               //sending group 1
               if (indx == 0)
               {
                  prods[0].send(testMsgs1[i1], DeliveryMode.PERSISTENT, priority, 0);
               }
               else
               {
                  prods[2].send(testMsgs1[i1], DeliveryMode.PERSISTENT, priority, 0);
               }
               i1++;
               if (i1 >= NUM_MSG)
               {
                  dowork1 = false;
               }
            }
            else if (dowork2)
            {
               testMsgs2[i2] = sess1.createTextMessage("ordering" + i2);
               //sending group2
               if (indx == 0)
               {
                  prods[1].send(testMsgs2[i2], DeliveryMode.PERSISTENT, priority, 0);
               }
               else
               {
                  prods[3].send(testMsgs2[i2], DeliveryMode.PERSISTENT, priority, 0);
               }
               i2++;
               if (i2 >= NUM_MSG)
               {
                  dowork2 = false;
               }
            }
         }

         //receiving and verifying
         Session session = conn3.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = session.createConsumer(queue1);

         conn3.start();
         
         ArrayList<TextMessage> rGroup1 = new ArrayList<TextMessage>();
         ArrayList<TextMessage> rGroup2 = new ArrayList<TextMessage>();

         for (int i = 0; i < NUM_MSG*2; i++)
         {
            TextMessage rm = (TextMessage)cons.receive(2000);
            assertNotNull(rm);
            
            if (isGroup(rm, ORDER_GROUP_NAME1))
            {
               rGroup1.add(rm);
            }
            else if (isGroup(rm, ORDER_GROUP_NAME2))
            {
               rGroup2.add(rm);
            }
            else
            {
               fail("message " + rm.getText() + " doesn't belong any ordering group.");
            }
         }
         
         //check ordering
         assertEquals(NUM_MSG, rGroup1.size());
         assertEquals(NUM_MSG, rGroup2.size());

         for (int i = 0; i < NUM_MSG; i++)
         {
            TextMessage rm1 = rGroup1.get(i);
            TextMessage rm2 = rGroup2.get(i);
            
            assertEquals("ordering" + i, rm1.getText());
            assertEquals("ordering" + i, rm2.getText());
         }
         
         checkEmpty(queue1);
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
         if (conn3 != null)
         {
            conn3.close();
         }
      }      
   }

   /**
    * nP --> nC (multiple producers vs multiple clients in transaction mode)
    * create four producers from two connections and all send some message belonging
    * to one same ordering group. Receive them by two clients of different modes.
    * check the receiving end that they are received orderly.
    */
   public void testMultipleProducersMultipleTxClients() throws Exception
   {
      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;
      Connection conn4 = null;
      
      try
      {
         conn1 = cf.createConnection();         
         conn2 = cf.createConnection();         
         conn3 = cf.createConnection();         
         conn4 = cf.createConnection();         
         
         conn1.start();
         conn2.start();
         
         //two producers on conn1 and two more on conn2
         JBossMessageProducer[] prods = new JBossMessageProducer[4];
         Session sess1 = conn1.createSession(false, 0);
         prods[0] = (JBossMessageProducer)sess1.createProducer(queue1);
         prods[1] = (JBossMessageProducer)sess1.createProducer(queue1);
         //now we do on different sessions 
         Session sess2 = conn2.createSession(false, 0);
         prods[2] = (JBossMessageProducer)sess2.createProducer(queue1);
         Session sess3 = conn2.createSession(false, 0);
         prods[3] = (JBossMessageProducer)sess3.createProducer(queue1);
         
         //enable ordering group
         final String ORDER_GROUP_NAME = "SameOneGroup";
         
         prods[0].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[1].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[2].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[3].enableOrderingGroup(ORDER_GROUP_NAME);
         
         //creating and messages.
         final int NUM_MSG = 200;
         TextMessage[] testMsgs = new TextMessage[NUM_MSG];
         Random rand = new Random();
         for (int i = 0; i < NUM_MSG; i++)
         {
            testMsgs[i] = sess1.createTextMessage("ordering" + i);
            //sending, in real world the order of sending may not be the order of arriving
            //as the sending can be performed on different threads, processes or machines.
            //but in test we need to know beforehand the order so that we can verify.
            int indx = rand.nextInt(4);
            int priority = rand.nextInt(10);
            prods[indx].send(testMsgs[i], DeliveryMode.PERSISTENT, priority, 0);
         }
         
         //receiving and verifying
         MsgReceiverThread thread1 = new MsgReceiverThread(conn3, queue1, true, 1);
         MsgReceiverThread thread2 = new MsgReceiverThread(conn4, queue1, true, 20); //slow one
         
         conn3.start();
         conn4.start();
         
         thread1.start();
         thread2.start();

         thread1.join();
         thread2.join();
         
         checkEmpty(queue1);
         
         //check the order now.
         assertEquals(NUM_MSG, rcvBuffer1.size());
         for (int i = 0; i < NUM_MSG; i++)
         {
            assertEquals("ordering" + i, rcvBuffer1.get(i).getText());
         }
         
         rcvBuffer1.clear();
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
         if (conn3 != null)
         {
            conn3.close();
         }
         if (conn4 != null)
         {
            conn4.close();
         }
      }
   }

   /**
    * nP --> nC (multiple producers vs multiple clients in client-ack mode)
    * create four producers from two connections and all send some message belonging
    * to one same ordering group. Receive them by two clients of different modes.
    * check the receiving end that they are received orderly.
    */
   public void testMultipleProducersMultipleClientAckClients() throws Exception
   {
      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;
      Connection conn4 = null;
      
      try
      {
         conn1 = cf.createConnection();         
         conn2 = cf.createConnection();         
         conn3 = cf.createConnection();         
         conn4 = cf.createConnection();         
         
         conn1.start();
         conn2.start();
         
         //two producers on conn1 and two more on conn2
         JBossMessageProducer[] prods = new JBossMessageProducer[4];
         Session sess1 = conn1.createSession(false, 0);
         prods[0] = (JBossMessageProducer)sess1.createProducer(queue1);
         prods[1] = (JBossMessageProducer)sess1.createProducer(queue1);
         //now we do on different sessions 
         Session sess2 = conn2.createSession(false, 0);
         prods[2] = (JBossMessageProducer)sess2.createProducer(queue1);
         Session sess3 = conn2.createSession(false, 0);
         prods[3] = (JBossMessageProducer)sess3.createProducer(queue1);
         
         //enable ordering group
         final String ORDER_GROUP_NAME = "SameOneGroup";
         
         prods[0].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[1].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[2].enableOrderingGroup(ORDER_GROUP_NAME);
         prods[3].enableOrderingGroup(ORDER_GROUP_NAME);
         
         //creating and messages.
         final int NUM_MSG = 200;
         TextMessage[] testMsgs = new TextMessage[NUM_MSG];
         Random rand = new Random();
         for (int i = 0; i < NUM_MSG; i++)
         {
            testMsgs[i] = sess1.createTextMessage("ordering" + i);
            //sending, in real world the order of sending may not be the order of arriving
            //as the sending can be performed on different threads, processes or machines.
            //but in test we need to know beforehand the order so that we can verify.
            int indx = rand.nextInt(4);
            int priority = rand.nextInt(10);
            prods[indx].send(testMsgs[i], DeliveryMode.PERSISTENT, priority, 0);
         }
         
         //receiving and verifying
         MsgReceiverThread thread1 = new MsgReceiverThread(conn3, queue1, false, 1);
         MsgReceiverThread thread2 = new MsgReceiverThread(conn4, queue1, false, 10); //slow one
         
         conn3.start();
         conn4.start();
         
         thread1.start();
         thread2.start();

         thread1.join();
         thread2.join();
         
         checkEmpty(queue1);
         
         //check the order now.
         assertEquals(NUM_MSG, rcvBuffer1.size());
         for (int i = 0; i < NUM_MSG; i++)
         {
            assertEquals("ordering" + i, rcvBuffer1.get(i).getText());
         }
         
         rcvBuffer1.clear();
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
         if (conn3 != null)
         {
            conn3.close();
         }
         if (conn4 != null)
         {
            conn4.close();
         }
      }
   }

   /**
    * @param rm
    * @param order_group_name1
    * @return
    * @throws JMSException 
    */
   private boolean isGroup(TextMessage rm, String name) throws JMSException
   {
      String grpName = rm.getStringProperty(JBossMessage.JBOSS_MESSAGING_ORDERING_GROUP_ID);
      assertNotNull(grpName);
      
      return grpName.equals(name);
   }

   public void testMultipleProducersInOneGroupTx()
   {
      
   }
   
   public void testMultipleProducersInMultiGroupsTx()
   {
      
   }
   
   public void delay(long t)
   {
      try
      {
         Thread.sleep(t);
      }
      catch (InterruptedException e)
      {
         e.printStackTrace();
      }
   }
   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
   private static ArrayList<TextMessage> rcvBuffer = new ArrayList<TextMessage>();
   private static ArrayList<TextMessage> rcvBuffer1 = new ArrayList<TextMessage>();
   
   public class MsgOrderListener implements MessageListener
   {
      
      private boolean autoAck;

      public MsgOrderListener(boolean isAutoAck)
      {
         autoAck = isAutoAck;
      }

      public void onMessage(Message msg)
      {
         synchronized(rcvBuffer)
         {
            rcvBuffer.add((TextMessage)msg);
         }
         
         if (!autoAck)
         {
            delay(50);
            try
            {
               msg.acknowledge();
            }
            catch (JMSException e)
            {
               fail("Acknowledge failed. ");
            }
         }
      }
   }
   
   public class MsgReceiverThread extends Thread
   {

      Connection conn;
      Queue dest;
      boolean isTx;
      int delay;
      
      public MsgReceiverThread(Connection conn, Queue dest, boolean isTx, int delay)
      {
         this.conn = conn;
         this.dest = dest;
         this.isTx = isTx;
         this.delay = delay;
      }
      
      //receiving message, it rollbacks somtimes.
      //stop when timeout
      public void run()
      {
         Session session;
         try
         {
            Random rand = new Random();
            
            session = conn.createSession(isTx, isTx ? Session.SESSION_TRANSACTED : Session.CLIENT_ACKNOWLEDGE);
            MessageConsumer cons = session.createConsumer(dest);

            HashMap<String, TextMessage> redelivered = new HashMap<String, TextMessage>();
            
            //if we can't receive a msg in 10 sec, we think no message available
            //either all messages has been received, or something wrong.
            TextMessage rm = (TextMessage)cons.receive(10000);
            while (rm != null)
            {
               delay(delay);//indicate how fast a receiver can be
               if (isTx)
               {
                  int rbIdx = rand.nextInt(4);
                  //%25 change of rollback. only rollback once for each msg to avoid possible going to DLQ
                  if ((rbIdx == 1) && (redelivered.get(rm.getText()) == null))
                  {
                     //roll back
                     session.rollback();
                     redelivered.put(rm.getText(), rm);
                  }
                  else
                  {
                     synchronized(rcvBuffer1)
                     {
                        rcvBuffer1.add(rm);
                     }
                     session.commit();
                  }
               }
               else
               {
                  int recoverIdx = rand.nextInt(4);
                  //%25 chance of recover. only recover once for each msg to avoid possible going to DLQ
                  if ((recoverIdx == 0) && (redelivered.get(rm.getText()) == null))
                  {
                     //redeliver
                     session.recover();
                     redelivered.put(rm.getText(), rm);
                  }
                  else
                  {
                     //ok
                     synchronized(rcvBuffer1)
                     {
                        rcvBuffer1.add(rm);
                     }
                     rm.acknowledge();
                  }
               }
               
               rm = (TextMessage)cons.receive(10000);
            }
            redelivered.clear();
            //receive finish, thread ends.
         }
         catch (JMSException e)
         {
            e.printStackTrace();
            fail("Exception in MsgReceiverThread");
         }
      }
   }

}
