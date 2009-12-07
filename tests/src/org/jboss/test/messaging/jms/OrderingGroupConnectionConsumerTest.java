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
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.ServerSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.TextMessage;

import EDU.oswego.cs.dl.util.concurrent.Latch;

import org.jboss.jms.client.JBossConnectionConsumer;
import org.jboss.jms.client.JBossMessageProducer;
import org.jboss.test.messaging.tools.ServerManagement;

/**
 * A OrderingGroupConnectionConsumerTest
 *
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 * 
 * Created Oct 29, 2008 2:45:38 PM
 *
 *
 */
public class OrderingGroupConnectionConsumerTest extends JMSTestCase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------
   public OrderingGroupConnectionConsumerTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------
   public void setUp() throws Exception
   {
      super.setUp();
   }

   public void tearDown() throws Exception
   {
      super.tearDown();
      mList.clear();
   }

   /*
    * Make sure the ordering group messages are received in order
    * thru a ConnectionConsumer.
    */
   public void testSimpleReceive() throws Exception
   {
      if (ServerManagement.isRemote())
         return;

      Connection consumerConn = null;

      Connection producerConn = null;

      try
      {
         consumerConn = cf.createConnection();

         consumerConn.start();

         Session sessCons1 = consumerConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         OrderingGroupMessageListener listener = new OrderingGroupMessageListener(this);

         sessCons1.setMessageListener(listener);

         ServerSessionPool pool = new OrderingServerSessionPool(sessCons1);

         JBossConnectionConsumer cc = (JBossConnectionConsumer)consumerConn.createConnectionConsumer(queue1,
                                                                                                     null,
                                                                                                     pool,
                                                                                                     5);

         producerConn = cf.createConnection();

         Session sessProd = producerConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sessProd.createProducer(queue1);
         prod.enableOrderingGroup(null);

         forceGC();

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage m = sessProd.createTextMessage("testing" + i);
            prod.send(m, Message.DEFAULT_DELIVERY_MODE, i % 10, Message.DEFAULT_TIME_TO_LIVE);
         }

         // waiting enough time to allow delivery complete.
         msgLatch.attempt(10000);

         // check the order
         assertEquals(NUM_MESSAGES, mList.size());

         for (int i = 0; i < NUM_MESSAGES; ++i)
         {
            TextMessage txm = mList.get(i);
            assertEquals(txm.getText(), "testing" + i);
         }

         // allow consumer thread gracefully shutdown
         doze(3000);

         cc.close();

         consumerConn.close();
         consumerConn = null;
         producerConn.close();
         producerConn = null;
      }
      finally
      {
         if (consumerConn != null)
            consumerConn.close();
         if (producerConn != null)
            producerConn.close();

      }
   }

   /**
    * @param i
    */
   private void doze(long nt)
   {
      try
      {
         Thread.sleep(nt);
      }
      catch (InterruptedException e)
      {
      }
   }

   /*
    * Make sure the ordering group messages are received in order
    * thru a ConnectionConsumer in transaction mode.
    */

   public void testTransactedReceive() throws Exception
   {
      if (ServerManagement.isRemote())
         return;

      Connection consumerConn = null;

      Connection producerConn = null;

      try
      {
         consumerConn = cf.createConnection();

         consumerConn.start();

         Session sessCons1 = consumerConn.createSession(true, Session.SESSION_TRANSACTED);

         TxOrderingGroupMessageListener listener1 = new TxOrderingGroupMessageListener(this, sessCons1);

         sessCons1.setMessageListener(listener1);

         ServerSessionPool pool = new MockServerSessionPool(sessCons1);

         JBossConnectionConsumer cc = (JBossConnectionConsumer)consumerConn.createConnectionConsumer(queue1,
                                                                                                     null,
                                                                                                     pool,
                                                                                                     5);

         producerConn = cf.createConnection();

         Session sessProd = producerConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sessProd.createProducer(queue1);
         prod.enableOrderingGroup(null);

         forceGC();

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage m = sessProd.createTextMessage("testing" + i);
            prod.send(m, Message.DEFAULT_DELIVERY_MODE, i % 10, Message.DEFAULT_TIME_TO_LIVE);
         }

         // waiting enough time to allow delivery complete.
         msgLatch.attempt(10000);

         // check the order
         assertEquals(NUM_MESSAGES, mList.size());

         for (int i = 0; i < NUM_MESSAGES; ++i)
         {
            TextMessage txm = mList.get(i);
            assertEquals(txm.getText(), "testing" + i);
         }

         doze(3000);

         cc.close();

         consumerConn.close();
         consumerConn = null;
         producerConn.close();
         producerConn = null;
      }
      finally
      {
         if (consumerConn != null)
            consumerConn.close();
         if (producerConn != null)
            producerConn.close();

      }
   }

   /*
    * Make sure the ordering group messages are received in order
    * thru a ConnectionConsumer in transaction mode, by two listeners.
    */
   public void testTransactedConcurrentReceive() throws Exception
   {
      if (ServerManagement.isRemote())
         return;

      Connection consumerConn = null;

      Connection producerConn = null;

      try
      {
         consumerConn = cf.createConnection();

         consumerConn.start();

         Session sessCons1 = consumerConn.createSession(true, Session.SESSION_TRANSACTED);
         Session sessCons2 = consumerConn.createSession(true, Session.SESSION_TRANSACTED);

         TxOrderingGroupMessageListener listener1 = new TxOrderingGroupMessageListener(this, sessCons1);
         TxOrderingGroupMessageListener listener2 = new TxOrderingGroupMessageListener(this, sessCons2);

         sessCons1.setMessageListener(listener1);
         sessCons2.setMessageListener(listener2);

         OrderingServerSessionPool pool = new OrderingServerSessionPool(sessCons1, sessCons2);

         JBossConnectionConsumer cc = (JBossConnectionConsumer)consumerConn.createConnectionConsumer(queue1,
                                                                                                     null,
                                                                                                     pool,
                                                                                                     5);

         producerConn = cf.createConnection();

         Session sessProd = producerConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         JBossMessageProducer prod = (JBossMessageProducer)sessProd.createProducer(queue1);
         prod.enableOrderingGroup(null);

         forceGC();

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage m = sessProd.createTextMessage("testing" + i);
            prod.send(m, Message.DEFAULT_DELIVERY_MODE, i % 10, Message.DEFAULT_TIME_TO_LIVE);
         }

         // waiting enough time to allow delivery complete.
         msgLatch.attempt(10000);

         // check the order
         assertEquals(NUM_MESSAGES, mList.size());

         for (int i = 0; i < NUM_MESSAGES; ++i)
         {
            TextMessage txm = mList.get(i);
            assertEquals(txm.getText(), "testing" + i);
         }

         doze(3000);

         cc.close();

         Session nSess = consumerConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer nCon = nSess.createConsumer(queue1);

         Message mx = nCon.receive(500);
         assertNull(mx);

         consumerConn.close();
         consumerConn = null;
         producerConn.close();
         producerConn = null;
      }
      finally
      {
         if (consumerConn != null)
            consumerConn.close();
         if (producerConn != null)
            producerConn.close();

      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------
   Latch msgLatch = new Latch();

   ArrayList<TextMessage> mList = new ArrayList<TextMessage>();

   final int NUM_MESSAGES = 100;

   // Inner classes -------------------------------------------------
   class OrderingGroupMessageListener implements MessageListener
   {

      OrderingGroupConnectionConsumerTest owner;

      OrderingGroupMessageListener(OrderingGroupConnectionConsumerTest theTest)
      {
         owner = theTest;
      }

      public synchronized void onMessage(Message message)
      {
         try
         {
            owner.addReceived((TextMessage)message);
         }
         catch (Exception e)
         {
            log.error(e);
         }
      }
   }

   class TxOrderingGroupMessageListener implements MessageListener
   {

      OrderingGroupConnectionConsumerTest owner;

      long counter = 0;

      Session sessRef;

      TxOrderingGroupMessageListener(OrderingGroupConnectionConsumerTest theTest, Session sess)
      {
         owner = theTest;
         sessRef = sess;
      }

      public synchronized void onMessage(Message message)
      {
         try
         {
            // roll back once for every 5 messages
            if (counter % 5 == 0)
            {
               sessRef.rollback();
            }
            else
            {
               owner.addReceived((TextMessage)message);
               sessRef.commit();
            }
            counter++;
         }
         catch (Exception e)
         {
            log.error(e);
         }
      }
   }

   class OrderingServerSessionPool implements ServerSessionPool
   {
      private ServerSession serverSession1;

      private ServerSession serverSession2;

      private long counter;

      OrderingServerSessionPool(Session sess1, Session sess2)
      {
         serverSession1 = new MockServerSession(sess1);
         serverSession2 = new MockServerSession(sess2);
         counter = 0L;
      }

      /**
       * @param sessCons1
       */
      public OrderingServerSessionPool(Session sessCons1)
      {
         serverSession1 = new MockServerSession(sessCons1);
         serverSession2 = null;
      }

      public synchronized ServerSession getServerSession() throws JMSException
      {
         if (serverSession2 == null)
         {
            return serverSession1;
         }

         ServerSession result = serverSession2;
         if (counter % 2 == 0)
         {
            result = serverSession1;
         }
         counter++;
         return result;
      }
   }

   /**
    * here we do not synchronize because this is called from onMessage().
    * It is guaranteed that next message won't arrive until the previous 
    * message processing is finished (meaning onMessage() returns in auto-ack mode)
    */
   public void addReceived(TextMessage message)
   {
      mList.add(message);
      if (mList.size() == NUM_MESSAGES)
      {
         msgLatch.release();
      }
   }

}
