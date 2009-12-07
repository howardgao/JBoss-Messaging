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

package org.jboss.test.messaging.jms.clustering;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.jboss.jms.client.JBossConnection;
import org.jboss.jms.client.remoting.JMSRemotingConnection;
import org.jboss.jms.client.delegate.ClientConnectionDelegate;
import org.jboss.logging.Logger;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.aop.PoisonInterceptor;

/**
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @version <tt>$Revision: 3765 $</tt>
 *
 *
 * $Id: MultiThreadFailoverTest.java 3765 2008-02-22 01:45:04Z clebert.suconic@jboss.com $
 */
public class MultiThreadFailoverTest extends ClusteringTestBase
{

   // Constants ------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   volatile int messageCounterConsumer = 0;
   volatile int messageCounterProducer = 0;
   volatile boolean started = false;
   volatile boolean shouldStop = false;

   Object lockReader = new Object();
   Object lockWriter = new Object();

   // Static ---------------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------
   public MultiThreadFailoverTest(String name)
   {
      super(name);
   }


   // Public ---------------------------------------------------------------------------------------


   /**
    * Created per http://jira.jboss.org/jira/browse/JBMESSAGING-790
    */
   public void testMultiThreadOnReceive() throws Exception
   {
      Connection conn1 = cf.createConnection();
      Connection conn2 = cf.createConnection();
      Connection conn3 = cf.createConnection();

      getConnection(new Connection[]{conn1, conn2, conn3}, 0).close();
      getConnection(new Connection[]{conn1, conn2, conn3}, 2).close();

      log.info("Created connections");

      checkConnectionsDifferentServers(new Connection[]{conn1, conn2, conn3});

      Connection conn = getConnection(new Connection[]{conn1, conn2, conn3}, 1);

      conn.start();
      
      CountDownLatch latch = new CountDownLatch(1);

      ReceiveConsumerThread consumerThread = new ReceiveConsumerThread(
         conn.createSession(false,Session.AUTO_ACKNOWLEDGE),queue[1], latch);

      consumerThread.start();

      latch.await();

      Session session = conn.createSession(false,Session.AUTO_ACKNOWLEDGE);

      MessageProducer producer = session.createProducer(queue[1]);

      ServerManagement.kill(1);

      producer.send(session.createTextMessage("Have a nice day!"));

      consumerThread.join();

      if (consumerThread.exception != null)
      {
         throw consumerThread.exception;
      }

      assertNotNull (consumerThread.message);

      assertEquals("Have a nice day!", consumerThread.message.getText());

      conn.close();
   }

   /**
    * This test will open several Consumers at the same Connection and it will kill the server,
    * expecting failover to happen inside the Valve
    */
   public void testMultiThreadFailoverSingleThread() throws Exception
   {
      multiThreadFailover(1, 1, false, true);
   }

   public void testMultiThreadFailoverSingleThreadTransacted() throws Exception
   {
      multiThreadFailover(1, 1, true, true);
   }

   public void testMultiThreadFailoverSingleThreadNonPersistent() throws Exception
   {
      multiThreadFailover(1, 1, false, false);
   }

   public void testMultiThreadFailoverSeveralThreads() throws Exception
   {
      multiThreadFailover(5, 10, false, true);
   }

   public void testMultiThreadFailoverSeveralThreadsTransacted() throws Exception
   {
      multiThreadFailover(5, 10, true, true);
   }

   public void testMultiThreadFailoverNonPersistent() throws Exception
   {
      multiThreadFailover(5, 10, false, false);
   }

   // TODO TEST TEMPORARILY COMMENTED OUT.
   //      MUST BE UNCOMMENTED FOR  1.2.1!
   //      See http://jira.jboss.org/jira/browse/JBMESSAGING-815

   // Crash the Server when you have two clients in receive and send simultaneously
//   public void testFailureOnSendReceiveSynchronized() throws Throwable
//   {
//      Connection conn1 = null;
//      Connection conn2 = null;
//
//      try
//      {
//         conn1 = createConnectionOnServer(cf, 1);
//
//         conn2 = createConnectionOnServer(cf, 2);
//
//         assertEquals(1, ((JBossConnection)conn1).getServerID());
//         assertEquals(2, ((JBossConnection)conn2).getServerID());
//
//         // we "cripple" the remoting connection by removing ConnectionListener. This way, failures
//         // cannot be "cleanly" detected by the client-side pinger, and we'll fail on an invocation
//         JMSRemotingConnection rc = ((ClientConnectionDelegate)((JBossConnection)conn1).
//            getDelegate()).getRemotingConnection();
//         rc.removeConnectionListener();
//
//         // poison the server
//         ServerManagement.poisonTheServer(1, PoisonInterceptor.FAIL_SYNCHRONIZED_SEND_RECEIVE);
//
//         conn1.start();
//
//         Session sessionConsumer2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
//
//         MessageConsumer consumer2 = sessionConsumer2.createConsumer(queue[0]);
//         conn2.start();
//
//         final Session sessionProducer  = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
//
//         final MessageProducer producer = sessionProducer.createProducer(queue[0]);
//
//         final Session sessionConsumer  = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
//
//         final MessageConsumer consumer = sessionConsumer.createConsumer(queue[0]);
//
//         final ArrayList failures = new ArrayList();
//
//         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
//
//
//         Thread t1 = new Thread()
//         {
//            public void run()
//            {
//               try
//               {
//                  producer.send(sessionProducer.createTextMessage("before-poison"));
//               }
//               catch (Throwable e)
//               {
//                  failures.add(e);
//               }
//            }
//         };
//
//         Thread t2 = new Thread()
//         {
//            public void run()
//            {
//               try
//               {
//                  log.info("### Waiting message");
//                  TextMessage text = (TextMessage)consumer.receive();
//                  assertNotNull(text);
//                  assertEquals("before-poison", text.getText());
//
//                  Object obj = consumer.receive(5000);
//                  assertNull(obj);
//               }
//               catch (Throwable e)
//               {
//                  failures.add(e);
//               }
//            }
//         };
//
//         t2.start();
//         Thread.sleep(500);
//         t1.start();
//
//         t1.join();
//         t2.join();
//
//         Object receivedServer2 = consumer2.receive(5000);
//
//         if (receivedServer2 != null)
//         {
//            log.info("### Server2 original message also received ");
//         }
//
//         if (!failures.isEmpty())
//         {
//            throw (Throwable)failures.iterator().next();
//         }
//
//         assertNull(receivedServer2);
//
//      }
//      finally
//      {
//         if (conn1 != null)
//         {
//            conn1.close();
//         }
//         if (conn2 != null)
//         {
//            conn2.close();
//         }
//      }
//
//   }



   // I kept this method on public area on purpose.. just to be easier to read the code
   // As this is the real test being executed by test methods here.
   private void multiThreadFailover(int producerThread, int consumerThread, boolean transacted,
                                    boolean persistent)
      throws Exception
   {
      shouldStop = false;
      started = false;
      messageCounterConsumer = 0;
      messageCounterProducer = 0;

      Connection conn1 = this.createConnectionOnServer(cf, 0);
      Connection conn2 = this.createConnectionOnServer(cf, 1);
      Connection conn3 = this.createConnectionOnServer(cf, 2);

      try
      {
         log.info("Created connections");

         checkConnectionsDifferentServers(new Connection[]{conn1, conn2, conn3});

         // picking connection to server 1
         Connection conn = getConnection(new Connection[]{conn1, conn2, conn3}, 1);

         conn.start();

         for (int i = 0; i < 3; i++)
         {
            JBossConnection connTest = (JBossConnection)
               getConnection(new Connection[]{conn1, conn2, conn3}, i);

            String locator = ((ClientConnectionDelegate) connTest.getDelegate()).
               getRemotingConnection().getRemotingClient().getInvoker().getLocator().getLocatorURI();

            log.info("Server " + i + " has locator=" + locator);

         }

         ArrayList<Thread> threadList = new ArrayList<Thread>();
         CountDownLatch latchStart  = new CountDownLatch(consumerThread + producerThread + 1);

         for (int i = 0; i < producerThread; i++)
         {
            Session session;
            if (transacted)
            {
               session = conn.createSession(true, Session.SESSION_TRANSACTED);
            }
            else
            {
               session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            }
            threadList.add(new LocalThreadProducer(i, session , queue[1],
                                  transacted, 10 * (i+1), persistent, latchStart));
         }

         for (int i = 0; i < consumerThread; i++)
         {
            Session session;
            if (transacted)
            {
               session = conn.createSession(true, Session.SESSION_TRANSACTED);
            }
            else
            {
               session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            }
            threadList.add(new LocalThreadConsumer(i, session, queue[1], transacted, 20 * (i+1), latchStart));
         }

         for (Iterator<Thread> iter = threadList.iterator(); iter.hasNext();)
         {
            Thread t = iter.next();
            t.start();
         }

         latchStart.countDown();
         latchStart.await();

         Thread.sleep(2000); // 2 seconds generating / consuming messages

         log.info("Killing server 1");

         synchronized (lockWriter)
         {
            synchronized (lockReader)
            {
               log.info("messageCounterConsumer=" + messageCounterConsumer + ", messageCounterProducer=" +
                  messageCounterProducer);
            }
         }



         ServerManagement.kill(1);

         int producedRightAfterKill;
         int consumedRightAfterKill;
         synchronized (lockWriter)
         {
            synchronized (lockReader)
            {
               producedRightAfterKill = messageCounterProducer;
               consumedRightAfterKill = messageCounterConsumer;
            }
         }

         Thread.sleep(15000); // 2 seconds after

         synchronized (lockWriter)
         {
            synchronized (lockReader)
            {
               log.info("messageCounterConsumer=" + messageCounterConsumer + ", messageCounterProducer=" +
                  messageCounterProducer);
               shouldStop = true;
            }
         }

         boolean failed = false;

         for (Iterator iter = threadList.iterator(); iter.hasNext();)
         {
            log.info("Waiting to join");

            LocalThread t = (LocalThread) iter.next();

            t.join();

            if (t.exception != null)
            {
               failed = true;
               log.error("Error: " + t.exception, t.exception);
            }
         }

         if (failed)
         {
            fail ("One of the threads has thrown an exception... Test Fails");
         }

         log.info("messageCounterConsumer=" + messageCounterConsumer + ", messageCounterProducer=" +
            messageCounterProducer);


         /*
         // TODO: Re-enable this assertion when http://jira.jboss.org/jira/browse/JBMESSAGING-815
         //       is fixed
         if (persistent)
         {
            // it only makes sense to test this on persistent messages
            assertEquals(messageCounterProducer, messageCounterConsumer);
         }
         */
         
         // after kill... failover should kick and new messages arrive
         assertTrue(messageCounterConsumer > consumedRightAfterKill);
         assertTrue(messageCounterProducer > producedRightAfterKill);

      }
      finally
      {
         try { if (conn1 != null) conn1.close(); } catch (Exception ignored) {}

         try { if (conn2 != null) conn2.close(); } catch (Exception ignored) {}

         try { if (conn3 != null) conn3.close(); } catch (Exception ignored) {}
      }
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected void setUp() throws Exception
   {
      nodeCount = 3;

      if (ServerManagement.getServer(0) != null)
      {      
         this.removeAllMessages(queue[0].getQueueName(), true, 0);
      }
      if (ServerManagement.getServer(1) != null)
      {
         this.removeAllMessages(queue[1].getQueueName(), true, 1);
      }
      if (ServerManagement.getServer(2) != null)
      {
         this.removeAllMessages(queue[2].getQueueName(), true, 2);
      }
      
      

      super.setUp();
   }

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

   class LocalThread extends Thread
   {
      Exception exception;
      TextMessage message;

      public LocalThread(String name)
      {
         super(name);
      }

   }

   // Inner classes used by testMultiThreadOnReceive -----------------------------------------------

   class ReceiveConsumerThread extends LocalThread
   {

      MessageConsumer consumer;
      Session session;
      CountDownLatch latch;

      public ReceiveConsumerThread(Session session, Destination destination, CountDownLatch latch)
          throws Exception
      {
         super("Consumer Thread");
         this.session = session;
         consumer = session.createConsumer(destination);
         this.latch = latch;
      }


      public void run()
      {
         try
         {
            latch.countDown();
            message = (TextMessage)consumer.receive();
            if (message == null)
            {
               this.exception = new IllegalStateException("message.receive was null");
            }
         }
         catch (Exception e)
         {
            log.error(e,e);
            this.exception = e;
         }
      }

   }

   // Inner classes used by testMultiThreadFailover ------------------------------------------------

   class LocalThreadConsumer extends LocalThread
   {
      private final Logger log = Logger.getLogger(this.getClass());

      int id;
      CountDownLatch latchStart;
      MessageConsumer consumer;
      Session session;
      boolean transacted;
      int commitInterval;

      public LocalThreadConsumer(int id, Session session, Destination destination,
                                 boolean transacted,
                                 int commitInterval, CountDownLatch latchStart) throws Exception
      {
         super("LocalThreadConsumer-" + id);
         consumer = session.createConsumer(destination);
         this.session = session;
         this.id = id;
         this.transacted = transacted;
         this.commitInterval = commitInterval;
         this.latchStart = latchStart;
      }


      public void run()
      {
         try
         {
            
            latchStart.countDown();
            latchStart.await();

            int counter = 0;
            while (true)
            {
               Message message = consumer.receive(5000);
               if (message == null && shouldStop)
               {
                  log.info("Finished execution of thread as shouldStop was true");
                  break;
               }
               if (message != null)
               {
                  synchronized (lockReader)
                  {
                     messageCounterConsumer++;
                     if (counter ++ % 100 == 0)
                     {
                        log.info("Read = " + messageCounterConsumer);
                     }
                  }
                  log.trace("ReceiverID=" + id + " received message " + message);
                  if (transacted)
                  {
                     if (counter % commitInterval == 0)
                     {
                        //log.info("Commit on id=" + id + " counter = " + counter);
                        session.commit();
                     }
                  }
               }
            }

            if (transacted)
            {
               session.commit();
            }
         }
         catch (Exception e)
         {
            this.exception = e;
            log.info("Caught exception... finishing Thread " + id, e);
         }
      }
   }

   class LocalThreadProducer extends LocalThread
   {
      private final Logger log = Logger.getLogger(this.getClass());

      MessageProducer producer;
      Session session;
      int id;
      boolean transacted;
      int commitInterval;
      CountDownLatch latch;

      public LocalThreadProducer(int id, Session session, Destination destination,
                                 boolean transacted, int commitInterval,
                                 boolean persistent, CountDownLatch latch) throws Exception
      {
         super("LocalThreadProducer-" + id);
         this.session = session;
         producer = session.createProducer(destination);
         if (persistent)
         {
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         }
         this.id = id;
         this.transacted = transacted;
         this.commitInterval = commitInterval;
         this.latch = latch;
      }

      public void run()
      {
         try
         {
            latch.countDown();
            latch.await();
            
            int counter = 0;
            while (!shouldStop)
            {
               log.trace("Producer ID=" + id + " send message");
               producer.send(session.createTextMessage("Message from producer " + id + " counter=" + (counter)));

               synchronized (lockWriter)
               {
                  messageCounterProducer++;
                  if (counter ++ % 100 == 0)
                  {
                     log.info("Sent = " + messageCounterProducer);
                  }
               }

               if (transacted)
               {
                  if (counter % commitInterval == 0)
                  {
                     //log.info("Commit on id=" + id + " counter = " + counter);
                     session.commit();
                  }
               }
            }

            if (transacted)
            {
               session.commit();
            }

         }
         catch (Exception e)
         {
            this.exception = e;
            log.info("Caught exception... finishing Thread " + id, e);
         }
      }
   }

}
