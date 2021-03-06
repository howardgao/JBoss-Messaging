/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.jms.server.destination;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.jms.JMSException;

import org.jboss.jms.server.JMSCondition;
import org.jboss.jms.server.messagecounter.MessageCounter;
import org.jboss.messaging.core.contract.PostOffice;
import org.jboss.messaging.core.contract.Queue;
import org.jboss.messaging.util.ExceptionUtil;
import org.jboss.messaging.util.MessageQueueNameHelper;
import org.jboss.messaging.util.XMLUtil;

/**
 * A deployable JBoss Messaging topic.
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:alex.fu@novell.com">Alex Fu</a>
 * @author <a href="mailto:juha@jboss.org">Juha Lindfors</a>
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 *
 * @version <tt>$Revision: 7900 $</tt>
 *
 * $Id: TopicService.java 7900 2009-11-16 11:13:20Z gaohoward $
 */
public class TopicService extends DestinationServiceSupport implements TopicMBean
{
   // Constants -----------------------------------------------------
   
   public static final String SUBSCRIPTION_MESSAGECOUNTER_PREFIX = "Subscription.";

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public TopicService()
   {
      destination = new ManagedTopic();      
   }
   
   public TopicService(boolean createdProgrammatically)
   {
      super(createdProgrammatically);
      
      destination = new ManagedTopic();      
   }
   
   // ServiceMBeanSupport overrides ---------------------------------
   
   public synchronized void startService() throws Exception
   {
      super.startService();
      
      try
      {
         PostOffice po = serverPeer.getPostOfficeInstance();
                
         // We deploy any queues corresponding to pre-existing durable subscriptions

         Collection queues = po.getQueuesForCondition(new JMSCondition(false, destination.getName()), true);
      	
         Iterator iter = queues.iterator();

         while (iter.hasNext())
         {
            Queue queue = (Queue)iter.next();
            
            //See http://jira.jboss.org/jira/browse/JBMESSAGING-1742
            if (po.isClustered())
            {
               if (destination.isClustered() != queue.isClustered())
               {
                  log.warn("Topic " + destination.getName() 
                           + " previous clustered attribute is " + queue.isClustered() 
                           + ". Now re-deploying it with clustered attribute: " + destination.isClustered());
                  
                  queue = po.convertDestination(destination, queue.getName());
               }
            }
                     
            //TODO We need to set the paging params this way since the post office doesn't store them
            //instead we should never create queues inside the postoffice - only do it at deploy time
            
            //if the queue is redeployed as clustered from a non-clustered state, the queue is already activated.
            synchronized (queue)
            {
               if (!queue.isActive())
               {

                  queue.setPagingParams(destination.getFullSize(),
                                        destination.getPageSize(),
                                        destination.getDownCacheSize());

                  queue.load();

                  queue.activate();

                  // Must be done after load
                  queue.setMaxSize(destination.getMaxSize());

                  // Create a counter
                  String counterName = SUBSCRIPTION_MESSAGECOUNTER_PREFIX + queue.getName();

                  String subName = MessageQueueNameHelper.createHelper(queue.getName()).getSubName();

                  int dayLimitToUse = destination.getMessageCounterHistoryDayLimit();
                  if (dayLimitToUse == -1)
                  {
                     // Use override on server peer
                     dayLimitToUse = serverPeer.getDefaultMessageCounterHistoryDayLimit();
                  }

                  MessageCounter counter = new MessageCounter(counterName, subName, queue, true, true, dayLimitToUse);

                  serverPeer.getMessageCounterManager().registerMessageCounter(counterName, counter);

                  // Now we need to trigger a delivery - this is because message suckers might have
                  // been create *before* the queue was deployed - this is because message suckers can be
                  // created when the clusterpullconnectionfactory deploy is detected which then causes
                  // the clusterconnectionmanager to inspect the bindings for queues to create suckers
                  // to - but these bindings will exist before the queue or topic is deployed and before
                  // it has had its messages loaded
                  // Therefore we need to trigger a delivery now so remote suckers get messages
                  // See http://jira.jboss.org/jira/browse/JBMESSAGING-1136
                  // For JBM we should remove the distinction between activation and deployment to
                  // remove these annoyances and edge cases.
                  // The post office should load(=deploy) all bindings on startup including loading their
                  // state before adding the binding - there should be no separate deployment stage
                  // If the queue can be undeployed there should be a separate flag for this on the
                  // binding
                  queue.deliver();
               }
            }
         }

         serverPeer.getDestinationManager().registerDestination(destination);
         
         log.debug(this + " security configuration: " + (destination.getSecurityConfig() == null ?
            "null" : "\n" + XMLUtil.elementToString(destination.getSecurityConfig())));
         
         started = true;
         
         log.info(this + " started, fullSize=" + destination.getFullSize() + ", pageSize=" + destination.getPageSize() + ", downCacheSize=" + destination.getDownCacheSize());
         
         
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
         
         //When undeploying a topic, any non durable subscriptions will be removed
         //Any durable subscriptions will survive in persistent storage, but be removed
         //from memory
         
         //First we remove any data for a non durable sub - a non durable sub might have data in the
         //database since it might have paged
         
         PostOffice po = serverPeer.getPostOfficeInstance();
                  
         Collection queues = serverPeer.getPostOfficeInstance().getQueuesForCondition(new JMSCondition(false, destination.getName()), true);
      	
         Iterator iter = queues.iterator();
         
         while (iter.hasNext())            
         {
            Queue queue = (Queue)iter.next();
            
            if (!queue.isRecoverable())
            {
               // Unbind
               po.removeBinding(queue.getName(), false);
            }
                        
            queue.deactivate();
            
            queue.unload();
            
            //unregister counter
            String counterName = SUBSCRIPTION_MESSAGECOUNTER_PREFIX + queue.getName();
            
            serverPeer.getMessageCounterManager().unregisterMessageCounter(counterName);                        
         }
          
         started = false;
         log.info(this + " stopped");
      }
      catch (Throwable t)
      {
         ExceptionUtil.handleJMXInvocation(t, this + " stopService");
      }
   }

   // JMX managed attributes ----------------------------------------
   
   public int getAllMessageCount() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return -1;
         }
         
         return ((ManagedTopic)destination).getAllMessageCount();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " getAllMessageCount");
      } 
   }
   
   
   public int getDurableMessageCount() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return -1;
         }
         
         return ((ManagedTopic)destination).getDurableMessageCount();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " getDurableMessageCount");
      }
   }
   
   public int getNonDurableMessageCount() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return -1;
         }
         
         return ((ManagedTopic)destination).getNonDurableMessageCount();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " getNonDurableMessageCount");
      }
   }
   
   /**
    * All subscription count
    * @return all subscription count
    * @throws JMSException
    */
   public int getAllSubscriptionsCount() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return 0;
         }
   
         return ((ManagedTopic)destination).getAllSubscriptionsCount();        
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " getAllSubscriptionsCount");
      } 
   }

   public int getDurableSubscriptionsCount() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return 0;
         }
         
         return ((ManagedTopic)destination).getDurableSubscriptionsCount();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " getDurableSubscriptionsCount");
      } 
   }
   
   public int getNonDurableSubscriptionsCount() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return 0;
         }
         
         return ((ManagedTopic)destination).getNonDurableSubscriptionsCount();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " getNonDurableSubscriptionsCount");
      } 
   }

   // JMX managed operations ----------------------------------------
      

   /**
    * Remove all messages from subscription's storage.
    */
   public void removeAllMessages() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return;
         }
         
         ((ManagedTopic)destination).removeAllMessages();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " removeAllMessages");
      } 
   }         
   
   public List listAllSubscriptions() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return null;
         }
         
         return ((ManagedTopic)destination).listAllSubscriptions();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listAllSubscriptions");
      } 
   }
   
   public List listDurableSubscriptions() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return null;
         }
         
         return ((ManagedTopic)destination).listDurableSubscriptions();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listDurableSubscriptions");
      } 
   }
   
   public List listNonDurableSubscriptions() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return null;
         }
         
         return ((ManagedTopic)destination).listNonDurableSubscriptions();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listNonDurableSubscriptions");
      } 
   }
   
   public String listAllSubscriptionsAsHTML() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return "";
         }
   
         return ((ManagedTopic)destination).listAllSubscriptionsAsHTML();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listAllSubscriptionsAsHTML");
      } 
   }
   
   public String listDurableSubscriptionsAsHTML() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return "";
         }
   
         return ((ManagedTopic)destination).listDurableSubscriptionsAsHTML();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listDurableSubscriptionsAsHTML");
      } 
   }
   
   public String listNonDurableSubscriptionsAsHTML() throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return "";
         }
   
         return ((ManagedTopic)destination).listNonDurableSubscriptionsAsHTML();
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listNonDurableSubscriptionsAsHTML");
      } 
   }
   
   public List listAllMessages(String subscriptionId) throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return null;
         }
   
         return ((ManagedTopic)destination).listAllMessages(subscriptionId, null);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listMessages");
      }
   }
   
   public List listAllMessages(String subscriptionId, String selector) throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return null;
         }
   
         return ((ManagedTopic)destination).listAllMessages(subscriptionId, selector);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listMessages");
      }
   }
   
   
   public List listDurableMessages(String subscriptionId) throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return null;
         }
   
         return ((ManagedTopic)destination).listDurableMessages(subscriptionId, null);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listDurableMessages");
      }
   }
   
   public List listDurableMessages(String subscriptionId, String selector) throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return null;
         }
   
         return ((ManagedTopic)destination).listDurableMessages(subscriptionId, selector);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listDurableMessages");
      }
   }
   
   public List listNonDurableMessages(String subscriptionId) throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return null;
         }
   
         return ((ManagedTopic)destination).listNonDurableMessages(subscriptionId, null);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listNonDurableMessages");
      }
   }
   
   public List listNonDurableMessages(String subscriptionId, String selector) throws Exception
   {
      try
      {
         if (!started)
         {
            log.warn("Topic is stopped.");
            return null;
         }
   
         return ((ManagedTopic)destination).listNonDurableMessages(subscriptionId, selector);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listNonDurableMessages");
      }
   }
   
   public List getMessageCounters()
      throws Exception
   {
      try
      {
         return ((ManagedTopic)destination).getMessageCounters();       
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " listMessagesNonDurableSub");
      } 
   }
   
   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected boolean isQueue()
   {
      return false;
   }   

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
