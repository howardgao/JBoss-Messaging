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

package org.jboss.messaging.core.impl.clusterconnection;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.jboss.jms.client.JBossSession;
import org.jboss.jms.client.container.ClientConsumer;
import org.jboss.jms.client.delegate.ClientConsumerDelegate;
import org.jboss.jms.client.state.ConsumerState;
import org.jboss.jms.delegate.ProducerDelegate;
import org.jboss.jms.delegate.SessionDelegate;
import org.jboss.jms.destination.JBossQueue;
import org.jboss.jms.message.JBossMessage;
import org.jboss.jms.message.MessageProxy;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.contract.Queue;

/**
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: $</tt>20 Jun 2007
 *
 * $Id: $
 *
 */
public class MessageSucker implements MessageListener
{
   private static final Logger log = Logger.getLogger(MessageSucker.class);
   
   private boolean trace = log.isTraceEnabled();
	 
   private Queue localQueue;
   
	private Session sourceSession;
	
	private Session localSession;
	
	private ProducerDelegate producer;
	
	private volatile boolean started;
	
	private TransactionManager tm;
	
	private boolean consuming;
	
	private ClientConsumerDelegate consumer;
	
	private ClientConsumer clientConsumer;
	
	private boolean preserveOrdering;
	
	private long sourceChannelID;
	
	protected JBossQueue jbq;
	
	private boolean suspended = false;
	
	public String toString()
	{
		return "MessageSucker:" + System.identityHashCode(this) + " queue:" + localQueue.getName();
	}
				
	protected MessageSucker(Queue localQueue, Session sourceSession, Session localSession,
	              boolean preserveOrdering, long sourceChannelID)
   {	
      if (trace) { log.trace("Creating message sucker, localQueue:" + localQueue + " preserveOrdering:" + preserveOrdering); }
      
      this.jbq = new JBossQueue(localQueue.getName(), true);
      
      this.localQueue = localQueue;
      
      this.sourceSession = sourceSession;
      
      this.localSession = localSession;
        
      this.preserveOrdering = preserveOrdering;
      
      this.sourceChannelID = sourceChannelID;
   }
	
	protected synchronized void start() throws Exception
	{
		if (started)
		{
			return;
		}
		
		if (trace) { log.trace(this + " starting"); }
								
		SessionDelegate localdel = ((JBossSession)localSession).getDelegate();
		
		producer = localdel.createProducerDelegate(jbq);
		
		//We create the consumer with autoFlowControl = false
		//In this mode, the consumer does not handle it's own flow control, but it must be handled
		//manually using changeRate() methods
		//The local queue itself will manually send these messages depending on its state - 
		//So effectively the message buffering is handled by the local queue, not the ClientConsumer
		
		SessionDelegate sourcedel = ((JBossSession)sourceSession).getDelegate();
		
		consumer = (ClientConsumerDelegate)sourcedel.createConsumerDelegate(jbq, null, false, null, false, false);
		
		clientConsumer = ((ConsumerState)consumer.getState()).getClientConsumer();
								
		consumer.setMessageListener(this);		
		
		started = true;
		//Register ourselves with the local queue - this queue will handle flow control for us
		
		if (trace) { log.trace(this + " Registering sucker"); }
		
		localQueue.registerSucker(this);
		
		if (trace) { log.trace(this + " Registered sucker"); }
	}
	
   protected void stop()
   {
      localQueue.unregisterSucker(this);

      synchronized (this)
      {
         if (!started)
         {
            return;
         }

         setConsuming(false);

         try
         {
            consumer.closing(-1);
         }
         catch (Throwable t)
         {
            // Ignore
         }
         try
         {
            consumer.close();
         }
         catch (Throwable t)
         {
            // Ignore
         }

         try
         {
            producer.close();
         }
         catch (Throwable t)
         {
            // Ignore
         }

         sourceSession = null;

         localSession = null;

         consumer = null;

         clientConsumer = null;

         producer = null;

         started = false;
      }
   }
   
   //the suspend stops the sucker's receiving end but doesn't unregister the sucker.
   //we only suspend the consumer side.
   public synchronized void suspend()
   {
      if (!started || suspended)
      {
         return;
      }

      boolean oldConsuming = consuming;
      
      setConsuming(false);
      
      consuming = oldConsuming;
      
      suspended = true;

      try
      {
         consumer.closing(-1);
      }
      catch (Throwable t)
      {
         // Ignore
      }
      try
      {
         consumer.close();
      }
      catch (Throwable t)
      {
         // Ignore
      }

      sourceSession = null;

      consumer = null;

      clientConsumer = null;
   }
   
   
   public synchronized void resume(Session srcSession) throws JMSException
   {
      if (!suspended)
      {
         return;
      }

      sourceSession = srcSession;
      
      SessionDelegate sourcedel = ((JBossSession)sourceSession).getDelegate();
      
      consumer = (ClientConsumerDelegate)sourcedel.createConsumerDelegate(jbq, null, false, null, false, false);
      
      clientConsumer = ((ConsumerState)consumer.getState()).getClientConsumer();

      try
      {
         if (consuming)
         {
            if (trace) { log.trace(this + " resuming client consumer"); }
            
            clientConsumer.resume();
         }
         else
         {
            if (trace) { log.trace(this + " pausing client consumer"); }
            
            clientConsumer.pause();
         }
      }
      catch (Exception e)
      {
         e.printStackTrace();
         //We ignore the exception - we might fail to change rate when stoping a sucker for a dead server
      }
      
      consumer.setMessageListener(this);

      suspended = false;
   }

	public String getQueueName()
	{
		return this.localQueue.getName();
	}
	
	public synchronized void setConsuming(boolean consume)
	{
		if (trace) { log.trace(this + " setConsuming " + consume); }
      
		if (!started)
      {
         return;
      }
		
		//for supended, we set the consuming flag and do nothing.
		//later on resume, we force the sucker to be the last set consuming state.
		if (suspended)
		{
		   consuming = consume;
		   return;
		}
		
		try
		{
			if (consume && !consuming)
			{
				if (trace) { log.trace(this + " resuming client consumer"); }
			   
			   clientConsumer.resume();
				
				consuming = true;
			}
			else if (!consume && consuming)
			{
				if (trace) { log.trace(this + " pausing client consumer"); }
			   
			   clientConsumer.pause();
				
				consuming = false;
			}
		}
		catch (Exception e)
		{
			//We ignore the exception - we might fail to change rate when stoping a sucker for a dead server
		}
	}
		
	public void onMessage(Message msg)
	{
		Transaction tx = null;
				
		try
		{
	      if (trace) { log.trace(this + " sucked message " + msg + " JMSDestination - " + msg.getJMSDestination()); }

	      Destination originalDestination = msg.getJMSDestination();
	      
	      org.jboss.messaging.core.contract.Message coreMessage = ((MessageProxy)msg).getMessage();
                  
         if (preserveOrdering)
         {
            //Add a header saying we have sucked the message
            coreMessage.putHeader(org.jboss.messaging.core.contract.Message.CLUSTER_SUCKED, "x");
         }
         
         //Add a header with the node id of the node we sucked from - this is used on the sending end to do
         //the move optimisation
         coreMessage.putHeader(org.jboss.messaging.core.contract.Message.SOURCE_CHANNEL_ID, sourceChannelID);

         long timeToLive = msg.getJMSExpiration();
         if (timeToLive != 0)
         {
            timeToLive -=  System.currentTimeMillis();
            if (timeToLive <= 0)
            {
               timeToLive = 1; //Should have already expired - set to 1 so it expires when it is consumed or delivered
            }
         }
         
         //First we ack it - this ack only occurs in memory even if it is a persistent message
         msg.acknowledge();
         
         if (trace) { log.trace("Acknowledged message"); }
         
         coreMessage.getHeaders().put(JBossMessage.JBOSS_MESSAGING_ORIG_DESTINATION_SUCKER, originalDestination);
         
         synchronized (localSession)
         {
            //Then we send - this causes the ref to be moved (SQL UPDATE) in the database        
            producer.send(null, msg, msg.getJMSDeliveryMode(), msg.getJMSPriority(), timeToLive, true);
            
            if (trace) { log.trace(this + " forwarded message to queue"); }              
         }
		}
		catch (Exception e)
		{
			log.error("Failed to forward message", e);
			
			try
			{
				if (tx != null) tm.rollback();
			}
			catch (Throwable t)
			{
				if (trace) { log.trace("Failed to rollback tx", t); }
			}
		}
	}
}
