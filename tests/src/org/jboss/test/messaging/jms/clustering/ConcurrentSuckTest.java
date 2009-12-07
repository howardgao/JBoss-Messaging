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

import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.jboss.test.messaging.tools.ServerManagement;

/**
 * 
 * A ConcurrentSuckTest
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 */
public class ConcurrentSuckTest extends ClusteringTestBase
{

   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public ConcurrentSuckTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------
   
   public void testConcurrent() throws Exception
   {
   	Connection conn0 = null;
      Connection conn1 = null;
            
      //Deploy three non clustered queues with same name on different nodes
          
      try
      {      	
      	for (int i = 0; i < nodeCount; i++)
      	{         	         
            ServerManagement.deployQueue("queue-concurrent-a", "queue-concurrent-a", 200000, 2000, 2000, i, true);
            
            ServerManagement.deployQueue("queue-concurrent-b", "queue-concurrent-b", 200000, 2000, 2000, i, true);
            
            ServerManagement.deployQueue("queue-concurrent-c", "queue-concurrent-c", 200000, 2000, 2000, i, true);
            
            ServerManagement.deployQueue("queue-concurrent-d", "queue-concurrent-d", 200000, 2000, 2000, i, true);
            
            ServerManagement.deployQueue("queue-concurrent-e", "queue-concurrent-e", 200000, 2000, 2000, i, true);
      	}
      	
      	Queue queuea = (Queue)ic[0].lookup("/queue-concurrent-a");
         Queue queueb = (Queue)ic[0].lookup("/queue-concurrent-b");
         Queue queuec = (Queue)ic[0].lookup("/queue-concurrent-c");
         Queue queued = (Queue)ic[0].lookup("/queue-concurrent-d");
         Queue queuee = (Queue)ic[0].lookup("/queue-concurrent-e");
         
         conn0 = createConnectionOnServer(cf, 0);
         
         conn1 = createConnectionOnServer(cf, 1);
         
         Session sessSenda = conn0.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sessSendb = conn0.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sessSendc = conn0.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sessSendd = conn0.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sessSende = conn0.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageProducer prodSenda = sessSenda.createProducer(queuea);
         MessageProducer prodSendb = sessSendb.createProducer(queueb);
         MessageProducer prodSendc = sessSendc.createProducer(queuec);
         MessageProducer prodSendd = sessSendd.createProducer(queued);
         MessageProducer prodSende = sessSende.createProducer(queuee);
         
         Session sessConsumea = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sessConsumeb = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sessConsumec = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sessConsumed = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sessConsumee = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         final int numMessages = 500;
         
         final CountDownLatch latch = new CountDownLatch(5);
                  
         
         class MyListener implements MessageListener
         {
         	volatile int count;
         	
         	volatile boolean failed;

				public void onMessage(Message msg)
				{
					try
					{
						log.trace(this + " got message " + count);
   					if (count != msg.getIntProperty("count"))
   					{
   						log.error("Not expected count");
   						failed = true;
   					}
   					count++;
   					
   					if (count == numMessages)
   					{
   						latch.countDown();
   					}
					}
					catch (JMSException e)
					{
						log.error("Caught JMSException", e);
						failed = true;
					}
				}         	
         }
         
         MyListener listenera = new MyListener();
         MyListener listenerb = new MyListener();
         MyListener listenerc = new MyListener();
         MyListener listenerd = new MyListener();
         MyListener listenere = new MyListener();
         
         MessageConsumer consa = sessConsumea.createConsumer(queuea);
         MessageConsumer consb = sessConsumea.createConsumer(queueb);
         MessageConsumer consc = sessConsumea.createConsumer(queuec);
         MessageConsumer consd = sessConsumea.createConsumer(queued);
         MessageConsumer conse = sessConsumea.createConsumer(queuee);
         
         consa.setMessageListener(listenera);
         consb.setMessageListener(listenerb);
         consc.setMessageListener(listenerc);
         consd.setMessageListener(listenerd);
         conse.setMessageListener(listenere);
         
         conn1.start();
         
         class ProducerThread extends Thread
         {
         	private Session session;
         	private MessageProducer producer;
         	private volatile boolean failed;
         	
         	ProducerThread(Session session, MessageProducer producer)
         	{
         		this.session = session;
         		
         		this.producer = producer;
         	}
         	
         	public void run()
         	{
         		try
         		{
            		for (int i = 0; i < numMessages; i++)
            		{
            			Message message = session.createMessage();
            			
            			message.setIntProperty("count",i);
            			
            			producer.send(message);
            			
            			log.trace(this + " sent message " + i);
            		}            		            		
         		}
         		catch (JMSException e)
         		{
         			log.error("Failed to send", e);
         			
         			failed = true;
         		}
         	}
         }
         
         ProducerThread prod1 = new ProducerThread(sessSenda, prodSenda);
         ProducerThread prod2 = new ProducerThread(sessSendb, prodSendb);
         ProducerThread prod3 = new ProducerThread(sessSendc, prodSendc);
         ProducerThread prod4 = new ProducerThread(sessSendd, prodSendd);
         ProducerThread prod5 = new ProducerThread(sessSende, prodSende);
         
         prod1.start();
         prod2.start();
         prod3.start();
         prod4.start();
         prod5.start();
         
         prod1.join();
         prod2.join();
         prod3.join();
         prod4.join();
                
         latch.await();
         
         assertFalse(prod1.failed);
         assertFalse(prod2.failed);
         assertFalse(prod3.failed);
         assertFalse(prod4.failed);
         assertFalse(prod5.failed);
         
         assertFalse(listenera.failed);
         assertFalse(listenerb.failed);
         assertFalse(listenerc.failed);
         assertFalse(listenerd.failed);
         assertFalse(listenere.failed);
      }
      finally
      {
      	for (int i = 0; i < nodeCount; i++)
      	{         	                     
      		ServerManagement.undeployQueue("queue-concurrent-a", i);
      		
      		ServerManagement.undeployQueue("queue-concurrent-b", i);
      		
      		ServerManagement.undeployQueue("queue-concurrent-c", i);
      		
      		ServerManagement.undeployQueue("queue-concurrent-d", i);
      		
      		ServerManagement.undeployQueue("queue-concurrent-e", i);                       
      	}
      	
      	if (conn0 != null)
         {
            conn0.close();
         }

         if (conn1 != null)
         {
            conn1.close();
         }
      }
   }
         
   
   
   
   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
   
}

