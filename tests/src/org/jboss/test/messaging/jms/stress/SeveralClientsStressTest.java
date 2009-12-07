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

package org.jboss.test.messaging.jms.stress;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;

import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;
import EDU.oswego.cs.dl.util.concurrent.SynchronizedInt;
import org.jboss.logging.Logger;
import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.ServerManagement;

/**
 * In order for this test to run, you will need to edit /etc/security/limits.conf and change your max sockets to something bigger than 1024
 *
 * It's required to re-login after this change.
 *
 * For Windows you need also to increase this limit (max opened files) somehow.
 *
 *
Example of /etc/security/limits.confg:
#<domain>      <type>  <item>         <value>
clebert        hard    nofile          10240


 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @version <tt>$Revision: 3392 $</tt>
 *          $Id: SeveralClientsStressTest.java 3392 2007-12-01 00:23:37Z clebert.suconic@jboss.com $
 */
public class SeveralClientsStressTest extends MessagingTestCase
{

   // Constants ------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   protected boolean info=false;
   protected boolean startServer=true;

   // Static ---------------------------------------------------------------------------------------

   protected static long PRODUCER_ALIVE_FOR=60000; // originalValue=60000
   protected static long CONSUMER_ALIVE_FOR=60000; // originalValue=60000
   protected static long TEST_ALIVE_FOR=5 * 60 * 1000; // originalValue = 5 * 60 * 1000
   protected static int NUMBER_OF_PRODUCERS=50;
   protected static int NUMBER_OF_CONSUMERS=50;

   protected static ConcurrentHashMap<String, TextMessage> hashMessages = new ConcurrentHashMap<String, TextMessage>(); 


   // a producer should have a long wait between each message sent?
   protected static boolean LONG_WAIT_ON_PRODUCERS=false;

   protected static SynchronizedInt producedMessages = new SynchronizedInt(0);
   protected static SynchronizedInt readMessages = new SynchronizedInt(0);


   protected Context createContext() throws Exception
   {
      return new InitialContext(ServerManagement.getJNDIEnvironment());
   }

   // Constructors ---------------------------------------------------------------------------------

   public SeveralClientsStressTest(String name)
   {
      super(name);
   }

   // Public ---------------------------------------------------------------------------------------

   public void testQueue() throws Exception
   {
      HashSet threads = new HashSet();

      // A chhanel of communication between workers and the test method
      LinkedQueue testChannel = new LinkedQueue();


      for (int i=0; i< NUMBER_OF_PRODUCERS; i++)
      {
         threads.add(new SeveralClientsStressTest.Producer(i, testChannel));
      }

      for (int i=0; i< NUMBER_OF_CONSUMERS; i++)
      {
         threads.add(new SeveralClientsStressTest.Consumer(i, testChannel));
      }


      for (Iterator iter = threads.iterator(); iter.hasNext();)
      {
         Worker worker = (Worker)iter.next();
         worker.start();
      }

      long timeToFinish = System.currentTimeMillis() + TEST_ALIVE_FOR;

      int numberOfProducers = NUMBER_OF_PRODUCERS +1;
      int numberOfConsumers = NUMBER_OF_CONSUMERS +1;

      while (threads.size()>0)
      {
         SeveralClientsStressTest.InternalMessage msg = (SeveralClientsStressTest.InternalMessage)testChannel.poll(2000);

         log.info("Produced:" + producedMessages.get() + " and Consumed:" + readMessages.get() + " messages");

         if (msg!=null)
         {
            if (info) log.info("Received message " + msg);
            if (msg instanceof SeveralClientsStressTest.WorkerFailed)
            {
               fail("Worker " + msg.getWorker() + " has failed");
            }
            else
            if (msg instanceof SeveralClientsStressTest.WorkedFinishedMessages)
            {
               SeveralClientsStressTest.WorkedFinishedMessages finished = (SeveralClientsStressTest.WorkedFinishedMessages)msg;
               if (threads.remove(finished.getWorker()))
               {
                  if (System.currentTimeMillis() < timeToFinish)
                  {
                     if (finished.getWorker() instanceof SeveralClientsStressTest.Producer)
                     {
                        if (info) log.info("Scheduling new Producer " + numberOfProducers);
                        SeveralClientsStressTest.Producer producer = new SeveralClientsStressTest.Producer(numberOfProducers++, testChannel);
                        threads.add(producer);
                        producer.start();
                     }
                     else
                     if (finished.getWorker() instanceof SeveralClientsStressTest.Consumer)
                     {
                        if (info) log.info("Scheduling new Consumer " + numberOfConsumers);
                        SeveralClientsStressTest.Consumer consumer = new SeveralClientsStressTest.Consumer(numberOfConsumers++, testChannel);
                        threads.add(consumer);
                        consumer.start();
                     }
                  }
               }
               else
               {
                  log.warn(finished.getWorker() + " was not available on threads HashSet");
               }
            }
         }
      }

      log.info("Produced:" + producedMessages.get() + " and Consumed:" + readMessages.get() + " messages");

      clearMessages(true);

      log.info("Produced:" + producedMessages.get() + " and Consumed:" + readMessages.get() + " messages");

      if (hashMessages.size() != 0)
      {
         log.info("There are " + hashMessages.size() + " non processed messages");
         // This is to validate if the testcase is valid.
         assertEquals(producedMessages.get(), readMessages.get() + hashMessages.size());
      }

      for (TextMessage msgNonProcessed: hashMessages.values())
      {
         log.warn("Message non processed, txt=\"" + msgNonProcessed.getText() + "\", msgToString=[" + msgNonProcessed + "]");
      }

      assertEquals(producedMessages.get(), readMessages.get());
   }


   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected void clearMessages(boolean validate) throws Exception
   {
      Context ctx = createContext();
      ConnectionFactory cf = (ConnectionFactory) ctx.lookup("/ClusteredConnectionFactory");
      Connection conn = cf.createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue = (Queue )ctx.lookup("queue/testQueue");
      MessageConsumer consumer = sess.createConsumer(queue);

      conn.start();


      TextMessage msg = null;

      while ((msg = (TextMessage)consumer.receive(1000)) != null)
      {
         log.info("Received JMS message on clearMessages");

         if (validate)
         {
            readMessages.increment();
            processReceiveOnMessage(msg);
         }
      }

      conn.close();
   }

   protected void tearDown() throws Exception
   {
      super.tearDown();
   }

   protected void setUp() throws Exception
   {
      super.setUp();


      if (startServer)
      {
         ServerManagement.start(0, "all", null, true);
         ServerManagement.deployQueue("testQueue");
      }

      clearMessages(false);
      hashMessages.clear();
      producedMessages = new SynchronizedInt(0);
      readMessages = new SynchronizedInt(0);
   }


   protected void processReceiveOnMessage(ArrayList<TextMessage> messageList)
           throws JMSException
   {
      for (TextMessage tmpMsg: messageList)
      {
         processReceiveOnMessage(tmpMsg);
      }
   }

   protected void processReceiveOnMessage(TextMessage tmpMsg)
           throws JMSException
   {
      if (hashMessages.remove(tmpMsg.getText()) == null)
      {
         throw new IllegalStateException("Message txt=\"" + tmpMsg.getText() + "\"msg.toString()=[" + tmpMsg.toString() + "]  wasn't found on hashMap. Receive duplicated!");
      }
   }

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------


   class Worker extends Thread
   {

      protected Logger log = Logger.getLogger(getClass());

      private boolean failed=false;
      private int workerId;
      private Exception ex;

      LinkedQueue  messageQueue;


      public int getWorkerId()
      {
         return workerId;
      }


      public Exception getException()
      {
         return ex;
      }

      public boolean isFailed()
      {
         return failed;
      }

      protected synchronized void setFailed(boolean failed, Exception ex)
      {
         this.failed = failed;
         this.ex = ex;

         log.info("Sending Exception", ex);

         sendInternalMessage(new SeveralClientsStressTest.WorkerFailed(this));

      }

      protected void sendInternalMessage(SeveralClientsStressTest.InternalMessage msg)
      {
         if (info) log.info("Sending message " + msg);
         try
         {
            messageQueue.put(msg);
         }
         catch (Exception e)
         {
            log.error(e, e);
            setFailed(true, e);
         }
      }


      public Worker(String name, int workerId, LinkedQueue  messageQueue)
      {
         super(name);
         this.workerId = workerId;
         this.messageQueue = messageQueue;
         this.setDaemon(true);
      }

      public String toString()
      {
         return this.getClass().getName() + ":" + getWorkerId();
      }
   }

   class Producer extends SeveralClientsStressTest.Worker
   {
      public Producer(int producerId, LinkedQueue messageQueue)
      {
         super("Producer:" + producerId, producerId, messageQueue);
      }

      Random random = new Random();

      public void run()
      {
         try
         {
            Context ctx = createContext();

            ConnectionFactory cf = (ConnectionFactory) ctx.lookup("/ClusteredConnectionFactory");

            Queue queue = (Queue )ctx.lookup("queue/testQueue");

            if (info) log.info("Creating connection and producer");
            Connection conn = cf.createConnection();
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer prod = sess.createProducer(queue);

            if (info) log.info("Persistent Producer was created");
            prod.setDeliveryMode(DeliveryMode.PERSISTENT);

            long timeToFinish = System.currentTimeMillis() + PRODUCER_ALIVE_FOR;

            try
            {
               int messageSent=0;
               while(System.currentTimeMillis() < timeToFinish)
               {
                  messageSent++;

                  TextMessage msg = sess.createTextMessage("msg prod#" + getWorkerId() + " seq#" + messageSent);

                  // It has to be set on hashSets before sending, as sending can be faster than the hashmap
                  hashMessages.put(msg.getText(), msg);
                  prod.send(msg);
                  producedMessages.increment();

                  if (messageSent%50==0)
                  {
                     if (info) log.info("Sent " + messageSent + " Messages");
                  }

                  if (LONG_WAIT_ON_PRODUCERS)
                  {
                     int waitTime = random.nextInt()%2 + 1;
                     if (waitTime<0) waitTime*=-1;
                     Thread.sleep(waitTime*1000); // wait 1 or 2 seconds
                  }
                  else
                  {
                     sleep(100);
                  }
               }
            }
            finally
            {
               conn.close();
            }

         }
         catch (Exception e)
         {
            log.error(e, e);
            setFailed(true, e);
         }
         finally
         {
            if (!isFailed())
            {
               sendInternalMessage(new SeveralClientsStressTest.WorkedFinishedMessages(this));
            }
         }

      }
   }

   class Consumer extends SeveralClientsStressTest.Worker
   {
      public Consumer(int consumerId, LinkedQueue messageQueue)
      {
         super("Consumer:" + consumerId, consumerId, messageQueue);
      }

      public void run()
      {
         try
         {
            Context ctx = createContext();

            ConnectionFactory cf = (ConnectionFactory) ctx.lookup("/ClusteredConnectionFactory");

            Queue queue = (Queue )ctx.lookup("queue/testQueue");

            if (info) log.info("Creating connection and consumer");
            Connection conn = cf.createConnection();
            Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
            MessageConsumer consumer = sess.createConsumer(queue);
            if (info) log.info("Consumer was created");

            conn.start();

            int msgs = 0;

            int transactions = 0;

            long timeToFinish = System.currentTimeMillis() + CONSUMER_ALIVE_FOR;

            ArrayList<TextMessage> msgReceived = new ArrayList<TextMessage>();

            try
            {
               while(System.currentTimeMillis() < timeToFinish)
               {
                  TextMessage msg = (TextMessage)consumer.receive(1000);
                  if (msg != null)
                  {
                     msgReceived.add(msg);

                     msgs ++;
                     if (msgs>=50)
                     {
                        transactions++;
                        if (transactions%2==0)
                        {
                           if (info) log.info("Commit transaction");
                           sess.commit();
                           readMessages.add(msgs);

                           processReceiveOnMessage(msgReceived);
                        }
                        else
                        {
                           if (info) log.info("Rollback transaction");
                           sess.rollback();
                        }
                        msgReceived.clear();
                        msgs=0;
                     }
                  }
                  else
                  {
                     break;
                  }
               }

               processReceiveOnMessage(msgReceived);
               msgReceived.clear();

               readMessages.add(msgs);
               sess.commit();

            }
            finally
            {
               consumer.close();
               conn.close();
            }

         }
         catch (Exception e)
         {
            log.error(e);
            setFailed(true, e);
         }
         finally
         {
            if (!isFailed())
            {
               sendInternalMessage(new SeveralClientsStressTest.WorkedFinishedMessages(this));
            }
         }
      }

   }

   // Objects used on the communication between  Workers and the test
   static class InternalMessage
   {
      SeveralClientsStressTest.Worker worker;


      public InternalMessage(SeveralClientsStressTest.Worker worker)
      {
         this.worker = worker;
      }


      public SeveralClientsStressTest.Worker getWorker()
      {
         return worker;
      }

      public String toString()
      {
         return this.getClass().getName() + " worker-> " + worker.toString();
      }
   }

   static class WorkedFinishedMessages extends SeveralClientsStressTest.InternalMessage
   {


      public WorkedFinishedMessages(SeveralClientsStressTest.Worker worker)
      {
         super(worker);
      }

   }

   static class WorkerFailed extends SeveralClientsStressTest.InternalMessage
   {
      public WorkerFailed(SeveralClientsStressTest.Worker worker)
      {
         super(worker);
      }
   }

}
