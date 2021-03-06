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
package org.jboss.jms.server.endpoint;

import javax.jms.IllegalStateException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;

import org.jboss.jms.delegate.ConsumerEndpoint;
import org.jboss.jms.destination.JBossDestination;
import org.jboss.jms.message.JBossMessage;
import org.jboss.jms.server.ServerPeer;
import org.jboss.jms.server.destination.ManagedDestination;
import org.jboss.jms.server.destination.TopicService;
import org.jboss.jms.server.messagecounter.MessageCounter;
import org.jboss.jms.server.selector.Selector;
import org.jboss.jms.wireformat.Dispatcher;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.contract.Delivery;
import org.jboss.messaging.core.contract.DeliveryObserver;
import org.jboss.messaging.core.contract.Message;
import org.jboss.messaging.core.contract.MessageReference;
import org.jboss.messaging.core.contract.PostOffice;
import org.jboss.messaging.core.contract.Queue;
import org.jboss.messaging.core.contract.Receiver;
import org.jboss.messaging.core.contract.Replicator;
import org.jboss.messaging.core.impl.SimpleDelivery;
import org.jboss.messaging.core.impl.tx.Transaction;
import org.jboss.messaging.util.ExceptionUtil;

/**
 * Concrete implementation of ConsumerEndpoint. Lives on the boundary between Messaging Core and the
 * JMS Facade. Handles delivery of messages from the server to the client side consumer.
 * 
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 3873 $</tt> $Id: ServerConsumerEndpoint.java 3873 2008-03-12 21:36:34Z timfox $
 */
public class ServerConsumerEndpoint implements Receiver, ConsumerEndpoint
{
   // Constants ------------------------------------------------------------------------------------

   private static final Logger log = Logger.getLogger(ServerConsumerEndpoint.class);

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   private boolean trace = log.isTraceEnabled();

   private String id;

   private Queue messageQueue;

   private String queueName;

   private ServerSessionEndpoint sessionEndpoint;

   private boolean noLocal;

   private Selector messageSelector;

   private JBossDestination destination;

   private Queue dlq;

   private Queue expiryQueue;

   private long redeliveryDelay;
   
   private int maxDeliveryAttempts;

   private boolean started;

   // This lock protects starting and stopping
   private Object startStopLock;

   // Must be volatile
   private volatile boolean clientAccepting;

   private boolean retainDeliveries;
   
   private long lastDeliveryID = -1;
   
   private boolean remote;
   
   private boolean preserveOrdering;
   
   private boolean replicating;
   
   private volatile boolean dead;
   
   private int prefetchSize;
   
   private int sendCount;
   
   private boolean firstTime = true;
   
   
   // Constructors ---------------------------------------------------------------------------------

   ServerConsumerEndpoint(String id, Queue messageQueue, String queueName,
					           ServerSessionEndpoint sessionEndpoint, String selector,
					           boolean noLocal, JBossDestination dest, Queue dlq,
					           Queue expiryQueue, long redeliveryDelay, int maxDeliveryAttempts,
					           boolean remote, boolean replicating, int prefetchSize) throws InvalidSelectorException
   {
      if (trace)
      {
         log.trace("constructing consumer endpoint " + id);
      }
      
      this.id = id;

      this.messageQueue = messageQueue;

      this.queueName = queueName;

      this.sessionEndpoint = sessionEndpoint;

      this.noLocal = noLocal;

      this.destination = dest;

      this.dlq = dlq;

      this.redeliveryDelay = redeliveryDelay;

      this.expiryQueue = expiryQueue;
      
      this.maxDeliveryAttempts = maxDeliveryAttempts;

      // Always start as false - wait for consumer to initiate.
      this.clientAccepting = false;
      
      this.remote = remote;

      this.startStopLock = new Object();

      this.preserveOrdering = sessionEndpoint.getConnectionEndpoint().getServerPeer().isDefaultPreserveOrdering();
      
      this.replicating = replicating;
            
      this.prefetchSize = prefetchSize;
      
      boolean slow = sessionEndpoint.getConnectionEndpoint().getConnectionFactoryEndpoint().isSlowConsumers();
      
      if (slow)
      {
         //Slow is same as setting prefetch size to 1 - can deprecate this in 2.0
         prefetchSize = 1;
      }      
      
      if (dest.isTopic() && !messageQueue.isRecoverable())
      {
         // This is a consumer of a non durable topic subscription. We don't need to store
         // deliveries since if the consumer is closed or dies the refs go too.
         this.retainDeliveries = false;
      }
      else
      {
         this.retainDeliveries = true;
      }
      
      if (selector != null)
      {
         if (trace) { log.trace("creating selector:" + selector); }

         this.messageSelector = new Selector(selector);
         if (trace) { log.trace("created selector"); }
      }

      this.started = this.sessionEndpoint.getConnectionEndpoint().isStarted();
      
      // adding the consumer to the queue
      if (remote)
      {
      	this.messageQueue.getRemoteDistributor().add(this);
      }
      else
      {
      	this.messageQueue.getLocalDistributor().add(this);
      }

      // We don't need to prompt delivery - this will come from the client in a changeRate request

      log.trace(this + " constructed");
   }

   // Receiver implementation ----------------------------------------------------------------------

   /*
    * The queue ensures that handle is never called concurrently by more than
    * one thread.
    */
   public Delivery handle(DeliveryObserver observer, MessageReference ref, Transaction tx)
   {
      if (trace)
      {
         log.trace(this + " receives " + ref + " for delivery");
      }

      // This is ok to have outside lock - is volatile
      if (!clientAccepting)
      {
         if (trace) { log.trace(this + "'s client is NOT accepting messages!"); }

         return null;
      }

      if (ref.getMessage().isExpired())
      {
         SimpleDelivery delivery = new SimpleDelivery(observer, ref, true, false);

         try
         {
            sessionEndpoint.expireDelivery(delivery, expiryQueue);
         }
         catch (Throwable t)
         {
            log.error("Failed to expire delivery: " + delivery, t);
         }

         return delivery;
      }
      
      if (preserveOrdering && remote)
      {
      	//If the header exists it means the message has already been sucked once - so reject.
      	
      	if (ref.getMessage().getHeader(Message.CLUSTER_SUCKED) != null)
      	{
      		if (trace) { log.trace("Message has already been sucked once - not sucking again"); }
      		
      		return null;
      	}      	    
      }

      synchronized (startStopLock)
      {
         // If the consumer is stopped then we don't accept the message, it should go back into the
         // queue for delivery later.
         if (!started)
         {
            if (trace) { log.trace(this + " NOT started!"); }

            return null;
         }
         
         if (trace) { log.trace(this + " has startStopLock lock, preparing the message for delivery"); }

         Message message = ref.getMessage();
         
         boolean selectorRejected = !this.accept(message);
         
         SimpleDelivery delivery = new SimpleDelivery(observer, ref, !selectorRejected, false);

         if (selectorRejected)
         {
            return delivery;
         }
         
         if (noLocal)
         {
            String conId = ((JBossMessage) message).getConnectionID();

            if (trace) { log.trace("message connection id: " + conId + " current connection connection id: " + sessionEndpoint.getConnectionEndpoint().getConnectionID()); }

            if (sessionEndpoint.getConnectionEndpoint().getConnectionID().equals(conId))
            {
            	if (trace) { log.trace("Message from local connection so rejecting"); }
            	
            	try
             	{
             		delivery.acknowledge(null);
             	}
             	catch (Throwable t)
             	{
             		log.error("Failed to acknowledge delivery", t);
             		
             		return null;
             	}
             	
             	return delivery;
            }            
         }
                  
                 
         sendCount++;
         
         int num = prefetchSize;
         
         if (firstTime)
         {
            //We make sure we have a little extra buffer on the client side
            num = num + num / 3 ;
         }
         
         if (sendCount == num)
         {
            clientAccepting = false;
            
            firstTime = false;
         }         
         
         try
         {
         	sessionEndpoint.handleDelivery(delivery, this);
         }
         catch (Exception e)
         {
         	log.error("Failed to handle delivery", e);
         	
         	this.started = false; // DO NOT return null or the message might get delivered more than once
         }
                                           
         return delivery;
      }
   }
      
   // Filter implementation ------------------------------------------------------------------------

   public boolean accept(Message msg)
   {
      boolean accept = true;

      if (destination.isQueue())
      {
         // For subscriptions message selection is handled in the Subscription itself we do not want
         // to do the check twice
         if (messageSelector != null)
         {
            accept = messageSelector.accept(msg);

            if (trace) { log.trace("message selector " + (accept ? "accepts " : "DOES NOT accept ") + "the message"); }
         }
      }
      
      return accept;
   }

   // Closeable implementation ---------------------------------------------------------------------

   public long closing(long sequence) throws JMSException
   {
      try
      {
         if (trace) { log.trace(this + " closing");}

         stop();
         
         return lastDeliveryID;
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " closing");
      }
   }

   public void close() throws JMSException
   {
      try
      {
         if (trace)
         {
            log.trace(this + " close");
         }

         localClose();

         sessionEndpoint.removeConsumer(id);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " close");
      }
   }

   // ConsumerEndpoint implementation --------------------------------------------------------------

   public void changeRate(float newRate) throws JMSException
   {
      if (trace)
      {
         log.trace(this + " changing rate to " + newRate);
      }

      try
      {
      	synchronized (startStopLock)
      	{
            if (newRate > 0)
            {
               sendCount = 0;
               clientAccepting = true;
            }
            else
            {
               clientAccepting = false;
            }
      	}

         if (clientAccepting)
         {
            promptDelivery();
         }
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " changeRate");
      }
   }

   // Public ---------------------------------------------------------------------------------------

   public String toString()
   {
      return "ConsumerEndpoint[" + id + "]";
   }

   public JBossDestination getDestination()
   {
      return destination;
   }

   public ServerSessionEndpoint getSessionEndpoint()
   {
      return sessionEndpoint;
   }

   // Package protected ----------------------------------------------------------------------------
   
   boolean isRemote()
   {
      return this.remote;
   }       
   
   boolean isReplicating()
   {
   	return replicating;
   }
   
   String getID()
   {
   	return this.id;
   }

   boolean isRetainDeliveries()
   {
   	return this.retainDeliveries;
   }
   
   void setLastDeliveryID(long id)
   {
   	this.lastDeliveryID = id;
   }
   
   void setStarted(boolean started)
   {
      //No need to lock since caller already has the lock
      this.started = started;      
   }
   
   void setDead()
   {
      dead = true;
   }
   
   boolean isDead()
   {
      return dead;
   }
   
   Queue getDLQ()
   {
      return dlq;
   }

   Queue getExpiryQueue()
   {
      return expiryQueue;
   }

   long getRedliveryDelay()
   {
      return redeliveryDelay;
   }
   
   int getMaxDeliveryAttempts()
   {
   	return maxDeliveryAttempts;
   }
   
   String getQueueName()
   {
   	return queueName;
   }

   void localClose() throws Throwable
   {
      if (trace) { log.trace(this + " grabbed the main lock in close() " + this); }

      if (remote)
      {
      	messageQueue.getRemoteDistributor().remove(this);
      }
      else
      {
      	messageQueue.getLocalDistributor().remove(this);
      }

      Dispatcher.instance.unregisterTarget(id, this);

      // If this is a consumer of a non durable subscription then we want to unbind the
      // subscription and delete all its data.

      if (destination.isTopic())
      {
         PostOffice postOffice = sessionEndpoint.getConnectionEndpoint().getServerPeer().getPostOfficeInstance();
                  
         ServerPeer sp = sessionEndpoint.getConnectionEndpoint().getServerPeer();
         
         Queue queue = postOffice.getBindingForQueueName(queueName).queue;        
         
         ManagedDestination mDest = sp.getDestinationManager().getDestination(destination.getName(), false);
         
         if (!queue.isRecoverable())
         {
            postOffice.removeBinding(queueName, false);            

            if (!mDest.isTemporary())
            {
	            String counterName = TopicService.SUBSCRIPTION_MESSAGECOUNTER_PREFIX + queueName;
	
	            MessageCounter counter = sp.getMessageCounterManager().unregisterMessageCounter(counterName);
	
	            if (counter == null)
	            {
	               throw new IllegalStateException("Cannot find counter to remove " + counterName);
	            }
            }
         }
         else
         {
         	//Durable sub consumer
         	
         	if (queue.isClustered() && postOffice.isClustered())
            {
            	//Clustered durable sub consumer created - we need to remove this info from the replicator
            	
            	Replicator rep = (Replicator)postOffice;
            	
            	rep.remove(queue.getName());
            }
         }
      }

   }

   void start()
   {
      synchronized (startStopLock)
      {
         if (started)
         {
            return;
         }

         started = true;
      }

      // Prompt delivery
      promptDelivery();
   }

   void stop() throws Throwable
   {
      synchronized (startStopLock)
      {
         if (!started)
         {
            return;
         }

         started = false;
         
         // Any message deliveries already transit to the consumer, will just be ignored by the
         // ClientConsumer since it will be closed.
         //
         // To clarify, the close protocol (from connection) is as follows:
         //
         // 1) ClientConsumer::close() - any messages in buffer are cancelled to the server
         // session, and any subsequent receive messages will be ignored.
         //
         // 2) ServerConsumerEndpoint::closing() causes stop() this flushes any deliveries yet to
         // deliver to the client callback handler.
         //
         // 3) ClientConsumer waits for all deliveries to arrive at client side
         //
         // 4) ServerConsumerEndpoint:close() - endpoint is deregistered.
         //
         // 5) Session.close() - acks or cancels any remaining deliveries in the SessionState as
         // appropriate.
         //
         // 6) ServerSessionEndpoint::close() - cancels any remaining deliveries and deregisters
         // session.
         //
         // 7) Client side session executor is shutdown.
         //
         // 8) ServerConnectionEndpoint::close() - connection is deregistered.
         //
         // 9) Remoting connection listener is removed and remoting connection stopped.

      }
      
      if (replicating)
      {      	
      	sessionEndpoint.waitForDeliveriesFromConsumer(id);
      }
   }

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   private void promptDelivery()
   {
      sessionEndpoint.promptDelivery(messageQueue);
   }

   // Inner classes --------------------------------------------------------------------------------

}
