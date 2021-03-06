/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.jms.server.destination;

import java.util.ArrayList;
import java.util.List;

import org.jboss.jms.server.JMSCondition;
import org.jboss.jms.server.messagecounter.MessageCounter;
import org.jboss.jms.server.messagecounter.MessageStatistics;
import org.jboss.messaging.core.contract.Binding;
import org.jboss.messaging.core.contract.PostOffice;
import org.jboss.messaging.core.contract.Queue;
import org.jboss.messaging.core.impl.MessagingQueue;
import org.jboss.messaging.util.ExceptionUtil;
import org.jboss.messaging.util.XMLUtil;

import org.jboss.logging.Logger;

/**
 * MBean wrapper around a ManagedQueue
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:alex.fu@novell.com">Alex Fu</a>
 * @version <tt>$Revision: 7883 $</tt>
 *
 * $Id: QueueService.java 7883 2009-10-29 11:15:21Z gaohoward $
 */
public class QueueService extends DestinationServiceSupport implements QueueMBean
{
   // Constants ------------------------------------------------------------------------------------
   
   private static final String QUEUE_MESSAGECOUNTER_PREFIX = "Queue.";
   
   private static final Logger log = Logger.getLogger(QueueService.class);   
   

   // Static ---------------------------------------------------------------------------------------
   
   // Attributes -----------------------------------------------------------------------------------
   
   // Constructors ---------------------------------------------------------------------------------
   
   public QueueService()
   {
      destination = new ManagedQueue();      
   }

   public QueueService(boolean createdProgrammatically)
   {
      super(createdProgrammatically);
      
      destination = new ManagedQueue();      
   }
   
   // ServiceMBeanSupport overrides ----------------------------------------------------------------
   
   public synchronized void startService() throws Exception
   {
      super.startService();
      
      try
      {                           
         // Binding must be added before destination is registered in JNDI otherwise the user could
         // get a reference to the destination and use it while it is still being loaded. Also,
         // binding might already exist.
           
         PostOffice po = serverPeer.getPostOfficeInstance();
                           
         Binding binding = po.getBindingForQueueName(destination.getName());
         
         Queue queue;
         
         if (binding != null)
         {                     
         	queue = binding.queue;
         	
         	if (queue.isActive())
         	{
         		throw new IllegalStateException("Cannot deploy queue " + destination.getName() + " it is already deployed");
         	}
         	
         	//Sanity check - currently it is not possible to change the clustered attribute of a destination
         	//See http://jira.jboss.org/jira/browse/JBMESSAGING-1235
            //See http://jira.jboss.org/jira/browse/JBMESSAGING-1742
            if (po.isClustered())
            {
               if (destination.isClustered() != queue.isClustered())
               {
                  
                  log.warn("Queue " + destination.getName() 
                           + " previous clustered attribute is " + queue.isClustered() 
                           + ". Now re-deploying it with clustered attribute: " + destination.isClustered());

                  queue = po.convertDestination(destination, queue.getName());
               }
            }
         	
            queue.setPagingParams(destination.getFullSize(),
                               destination.getPageSize(),
                               destination.getDownCacheSize());  
            
            queue.load();
               
            // Must be done after load
            queue.setMaxSize(destination.getMaxSize());
            
            queue.activate();           
         }
         else
         {           
            // Create a new queue

            JMSCondition queueCond = new JMSCondition(true, destination.getName());
            
            queue = new MessagingQueue(nodeId, destination.getName(),
            		                     serverPeer.getChannelIDManager().getID(),
                                       serverPeer.getMessageStore(), serverPeer.getPersistenceManagerInstance(),
                                       true,
                                       destination.getMaxSize(), null,
                                       destination.getFullSize(), destination.getPageSize(),
                                       destination.getDownCacheSize(), destination.isClustered(),
                                       serverPeer.getRecoverDeliveriesTimeout());
            po.addBinding(new Binding(queueCond, queue, false), false);         
            
            queue.activate();
         }
         
         ((ManagedQueue)destination).setQueue(queue);
         
         String counterName = QUEUE_MESSAGECOUNTER_PREFIX + destination.getName();
         
         int dayLimitToUse = destination.getMessageCounterHistoryDayLimit();
         if (dayLimitToUse == -1)
         {
            //Use override on server peer
            dayLimitToUse = serverPeer.getDefaultMessageCounterHistoryDayLimit();
         }
         
         MessageCounter counter =
            new MessageCounter(counterName, null, queue, false, false,
                               dayLimitToUse);
         
         ((ManagedQueue)destination).setMessageCounter(counter);
                  
         serverPeer.getMessageCounterManager().registerMessageCounter(counterName, counter);
                       
         serverPeer.getDestinationManager().registerDestination(destination);
        
         log.debug(this + " security configuration: " + (destination.getSecurityConfig() == null ?
            "null" : "\n" + XMLUtil.elementToString(destination.getSecurityConfig())));
         
         started = true;         
         
         //Now we need to trigger a delivery - this is because message suckers might have
         //been create *before* the queue was deployed - this is because message suckers can be
         //created when the clusterpullconnectionfactory deploy is detected which then causes
         //the clusterconnectionmanager to inspect the bindings for queues to create suckers
         //to - but these bindings will exist before the queue or topic is deployed and before
         //it has had its messages loaded
         //Therefore we need to trigger a delivery now so remote suckers get messages
         //See http://jira.jboss.org/jira/browse/JBMESSAGING-1136
         //For JBM we should remove the distinction between activation and deployment to
         //remove these annoyances and edge cases.
         //The post office should load(=deploy) all bindings on startup including loading their
         //state before adding the binding - there should be no separate deployment stage
         //If the queue can be undeployed there should be a separate flag for this on the
         //binding
         queue.deliver();
         
         log.info(this + " started, fullSize=" + destination.getFullSize() +
                  ", pageSize=" + destination.getPageSize() + ", downCacheSize=" + destination.getDownCacheSize());
      }
      catch (Throwable t)
      {
         ExceptionUtil.handleJMXInvocation(t, this + " startService");
      }
   }

   public synchronized void stopService() throws Exception
   {
      try
      {
      	serverPeer.getDestinationManager().unregisterDestination(destination);
      	
         Queue queue = ((ManagedQueue)destination).getQueue();
         
         String counterName = QUEUE_MESSAGECOUNTER_PREFIX + destination.getName();
                  
         MessageCounter counter = serverPeer.getMessageCounterManager().unregisterMessageCounter(counterName);
         
         if (counter == null)
         {
            //https://jira.jboss.org/jira/browse/JBMESSAGING-1698
            log.warn("Cannot find counter to unregister " + counterName);
         }
         
         queue.deactivate();
         
         queue.unload();
         
         started = false;
         
         log.info(this + " stopped");
      }
      catch (Throwable t)
      {
         ExceptionUtil.handleJMXInvocation(t, this + " stopService");
      }
   }

   // JMX managed attributes -----------------------------------------------------------------------
   
   public int getMessageCount() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Queue is stopped");
            return 0;
         }
         
         return ((ManagedQueue)destination).getMessageCount();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " getMessageCount");
      }
   }
   
   public int getDeliveringCount() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Queue is stopped");
            return 0;
         }
         
         return ((ManagedQueue)destination).getDeliveringCount();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " getDeliveringCount");
      }
   }
   
   public int getScheduledMessageCount() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Queue is stopped");
            return 0;
         }
         
         return ((ManagedQueue)destination).getScheduledMessageCount();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " getMessageCount");
      }
   }
   
   public MessageCounter getMessageCounter()
   {
      return ((ManagedQueue)destination).getMessageCounter();
   }
   
   public MessageStatistics getMessageStatistics() throws Exception
   {
      List counters = new ArrayList();
      counters.add(getMessageCounter());
      
      List stats = MessageCounter.getMessageStatistics(counters);
      
      return (MessageStatistics)stats.get(0);
   }
   
   public String listMessageCounterAsHTML()
   {
      return super.listMessageCounterAsHTML(new MessageCounter[] { getMessageCounter() });
   }
   
   public int getConsumerCount() throws Exception
   {
      return ((ManagedQueue)destination).getConsumersCount();
   }
     
   // JMX managed operations -----------------------------------------------------------------------
      
   public void removeAllMessages() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Queue is stopped.");
            return;
         }
         
         ((ManagedQueue)destination).removeAllMessages();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " removeAllMessages");
      } 
   }
   
   public List listAllMessages() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Queue is stopped.");
            return null;
         }
         
         return ((ManagedQueue)destination).listAllMessages(null);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listAllMessages");
      } 
   }
   
   public List listAllMessages(String selector) throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Queue is stopped.");
            return null;
         }
         
         return ((ManagedQueue)destination).listAllMessages(selector);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listAllMessages");
      } 
   }
   
   public List listDurableMessages() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Queue is stopped.");
            return null;
         }
         
         return ((ManagedQueue)destination).listDurableMessages(null);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listDurableMessages");
      } 
   }
   
   public List listDurableMessages(String selector) throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Queue is stopped.");
            return null;
         }
         
         return ((ManagedQueue)destination).listDurableMessages(selector);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listDurableMessages");
      } 
   }
   
   public List listNonDurableMessages() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Queue is stopped.");
            return null;
         }
         
         return ((ManagedQueue)destination).listNonDurableMessages(null);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listNonDurableMessages");
      } 
   }
   
   public List listNonDurableMessages(String selector) throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Queue is stopped.");
            return null;
         }
         
         return ((ManagedQueue)destination).listNonDurableMessages(selector);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listNonDurableMessages");
      } 
   }
            
   public void resetMessageCounter()
   {
      ((ManagedQueue)destination).getMessageCounter().resetCounter();
   }
   
   public String listMessageCounterHistoryAsHTML()
   {
      return super.listMessageCounterHistoryAsHTML(new MessageCounter[] { getMessageCounter() });
   }
 
   public void resetMessageCounterHistory()
   {
      ((ManagedQueue)destination).getMessageCounter().resetHistory();
   }
       
   // Public ---------------------------------------------------------------------------------------

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected boolean isQueue()
   {
      return true;
   }

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------
}
