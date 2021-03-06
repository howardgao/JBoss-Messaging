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
package org.jboss.jms.client.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.jms.MessageListener;
import javax.jms.Session;

import org.jboss.jms.client.container.ClientConsumer;
import org.jboss.jms.client.delegate.ClientBrowserDelegate;
import org.jboss.jms.client.delegate.ClientConsumerDelegate;
import org.jboss.jms.client.delegate.ClientProducerDelegate;
import org.jboss.jms.client.delegate.ClientSessionDelegate;
import org.jboss.jms.client.delegate.DelegateSupport;
import org.jboss.jms.delegate.DeliveryInfo;
import org.jboss.jms.delegate.DeliveryRecovery;
import org.jboss.jms.delegate.SessionDelegate;
import org.jboss.jms.destination.JBossDestination;
import org.jboss.jms.tx.MessagingXAResource;
import org.jboss.jms.tx.ResourceManager;
import org.jboss.logging.Logger;
import org.jboss.messaging.util.CompatibleExecutor;
import org.jboss.messaging.util.ExecutorFactory;
import org.jboss.messaging.util.JBMThreadFactory;
import org.jboss.messaging.util.OrderedExecutorFactory;
import org.jboss.messaging.util.Version;

/**
 * State corresponding to a session. This state is acessible inside aspects/interceptors.
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 * @version <tt>$Revision: 7896 $</tt>
 *
 * $Id: SessionState.java 7896 2009-11-09 04:09:58Z gaohoward $
 */
public class SessionState extends HierarchicalStateSupport
{

   // Constants ------------------------------------------------------------------------------------

   protected static Logger log = Logger.getLogger(SessionState.class);

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   private ConnectionState parent;
   private SessionDelegate delegate;

   private String sessionID;
   private int acknowledgeMode;
   private boolean transacted;
   private boolean xa;

   private MessagingXAResource xaResource;
   private Object currentTxId;

   // ExecutorFactory used for executing onMessage methods
   private static final ExecutorFactory executorFactory = new OrderedExecutorFactory(
           Executors.newCachedThreadPool(new JBMThreadFactory("session-state")));
   private CompatibleExecutor executor;

   private boolean recoverCalled;
   
   // List<DeliveryInfo>
   private List clientAckList;

   private DeliveryInfo autoAckInfo;
   private Map callbackHandlers;
   
   private int dupsOKBatchSize;
   
   private LinkedList asfMessages = new LinkedList();
   
   //The distinguished message listener - for ASF
   private MessageListener sessionListener;
   
   /* Holding sequential counters for ordering groups.
    * Each ordering group has a sequence counter used to generate sequence numbers for
    * its messages. The sequence number of a message indicates the order in which the message
    * is to be delivered.
    */
   private HashMap<String, OrderingGroupSeq> orderingGrpSeqMap = new HashMap<String, OrderingGroupSeq>();
   
   //This is somewhat strange - but some of the MQ and TCK tests expect an XA session to behaviour as AUTO_ACKNOWLEDGE when not enlisted in
   //a transaction
   //This is the opposite behaviour as what is required when the XA session handles MDB delivery or when using the message bridge.
   //In that case we want it to act as transacted, so when the session is subsequently enlisted the work can be converted into the
   //XA transaction
   private boolean treatAsNonTransactedWhenNotEnlisted = true;
   
   private long npSendSequence;
   
   private boolean enableOrderingGroup;
   
   private String defaultOrderingGroupName;
   
   // Constructors ---------------------------------------------------------------------------------

   public SessionState(ConnectionState parent, ClientSessionDelegate delegate,
                       boolean transacted, int ackMode, boolean xa,
                       int dupsOKBatchSize, boolean enableOrderingGroup, String defaultOrderingGroupName)
   {
      super(parent, (DelegateSupport)delegate);

      this.sessionID = delegate.getID();

      children = new HashSet();
      this.acknowledgeMode = ackMode;
      this.transacted = transacted;
      this.xa = xa;
      
      this.dupsOKBatchSize = dupsOKBatchSize;

      if (xa)
      {
         // Create an XA resource
         xaResource = new MessagingXAResource(parent.getResourceManager(), this);
      }

      // Note we create the transaction even if XA - XA transactions must behave like
      // local tx when not enlisted in a global tx

      if (transacted)
      {
         // Create a local tx
         currentTxId = parent.getResourceManager().createLocalTx();
      }

      clientAckList = new ArrayList();

      // TODO could optimise this to use the same map of callbackmanagers (which holds refs
      // to callbackhandlers) in the connection, instead of maintaining another map
      callbackHandlers = new HashMap();
      
      executor = executorFactory.getExecutor("jbm-client-session-threads");
      
      this.setEnableOrderingGroup(enableOrderingGroup);
      
      this.setDefaultOrderingGroupName(defaultOrderingGroupName);
   }

   // HierarchicalState implementation -------------------------------------------------------------

   public DelegateSupport getDelegate()
   {
      return (DelegateSupport)delegate;
   }

   public void setDelegate(DelegateSupport delegate)
   {
      this.delegate = (SessionDelegate)delegate;
   }

   public HierarchicalState getParent()
   {
      return parent;
   }

   public void setParent(HierarchicalState parent)
   {
      this.parent = (ConnectionState)parent;
   }

   public Version getVersionToUse()
   {
      return parent.getVersionToUse();
   }
   
   public int getDupsOKBatchSize()
   {
      return dupsOKBatchSize;
   }
   
   public MessageListener getDistinguishedListener()
   {
      return this.sessionListener;
   }
   
   public void setDistinguishedListener(MessageListener listener)
   {
      this.sessionListener = listener;
   }
   
   public LinkedList getASFMessages()
   {
      return asfMessages;
   }

   // HierarchicalStateSupport overrides -----------------------------------------------------------

   public void synchronizeWith(HierarchicalState ns) throws Exception
   {
      SessionState newState = (SessionState)ns;

      String oldSessionID = sessionID;
      sessionID = newState.sessionID;
      
      npSendSequence = 0;
      
      // We need to clear anything waiting in the session executor - since there may be messages
      // from before failover waiting in there and we don't want them to get delivered after
      // failover.
      executor.clearAllExceptCurrentTask();
      
      ClientSessionDelegate newDelegate = (ClientSessionDelegate)newState.getDelegate();

      for (Iterator i = getChildren().iterator(); i.hasNext(); )
      {
         HierarchicalState child = (HierarchicalState)i.next();

         if (child instanceof ConsumerState)
         {
            ConsumerState consState = (ConsumerState)child;
            ClientConsumerDelegate consDelegate = (ClientConsumerDelegate)consState.getDelegate();

            // create a new consumer over the new session for each consumer on the old session
            ClientConsumerDelegate newConsDelegate = (ClientConsumerDelegate)newDelegate.
               createConsumerDelegate((JBossDestination)consState.getDestination(),
                                      consState.getSelector(),
                                      consState.isNoLocal(),
                                      consState.getSubscriptionName(),
                                      consState.isConnectionConsumer(), true);
            log.trace(this + " created new consumer " + newConsDelegate);

            consDelegate.synchronizeWith(newConsDelegate);
            log.trace(this + " synchronized failover consumer " + consDelegate);
         }
         else if (child instanceof ProducerState)
         {
            ProducerState prodState = (ProducerState)child;
            ClientProducerDelegate prodDelegate = (ClientProducerDelegate)prodState.getDelegate();

            // create a new producer over the new session for each producer on the old session
            ClientProducerDelegate newProdDelegate = (ClientProducerDelegate)newDelegate.
               createProducerDelegate((JBossDestination)prodState.getDestination());
            log.trace(this + " created new producer " + newProdDelegate);

            prodDelegate.synchronizeWith(newProdDelegate);
            log.trace(this + " synchronized failover producer " + prodDelegate);
         }
         else if (child instanceof BrowserState)
         {
            BrowserState browserState = (BrowserState)child;
            ClientBrowserDelegate browserDelegate =
               (ClientBrowserDelegate)browserState.getDelegate();

            // create a new browser over the new session for each browser on the old session
            ClientBrowserDelegate newBrowserDelegate = (ClientBrowserDelegate)newDelegate.
               createBrowserDelegate(browserState.getJmsDestination(),
                                     browserState.getMessageSelector());
            log.trace(this + " created new browser " + newBrowserDelegate);

            browserDelegate.synchronizeWith(newBrowserDelegate);
            log.trace(this + " synchronized failover browser " + browserDelegate);
         }
      }

      ConnectionState connState = (ConnectionState)getParent();
      ResourceManager rm = connState.getResourceManager();

      // We need to failover from one session ID to another in the resource manager
      rm.handleFailover(connState.getServerID(), oldSessionID, newState.sessionID);

      List ackInfos = Collections.EMPTY_LIST;

      if (!isTransacted() || (isXA() && getCurrentTxId() == null))
      {
         // TODO - the check "(isXA() && getCurrentTxId() == null)" shouldn't be necessary any more
         // since xa sessions no longer fall back to non transacted
         
         // Non transacted session or an XA session with no transaction set (it falls back
         // to AUTO_ACKNOWLEDGE)

         log.trace(this + " is not transacted (or XA with no transaction set), " +
                   "retrieving deliveries from session state");

         // We remove any unacked non-persistent messages - this is because we don't want to ack
         // them since the server won't know about them and will get confused

         if (acknowledgeMode == Session.CLIENT_ACKNOWLEDGE)
         {
            for(Iterator i = getClientAckList().iterator(); i.hasNext(); )
            {
               DeliveryInfo info = (DeliveryInfo)i.next();
               if (!info.getMessageProxy().getMessage().isReliable())
               {
                  i.remove();
                  log.trace("removed non persistent delivery " + info);
               }
            }

            ackInfos = getClientAckList();
         }
         else
         {
            DeliveryInfo autoAck = getAutoAckInfo();
            if (autoAck != null)
            {
               if (!autoAck.getMessageProxy().getMessage().isReliable())
               {
                  // unreliable, discard
                  setAutoAckInfo(null);
               }
               else
               {
                  // reliable
                  ackInfos = new ArrayList();
                  ackInfos.add(autoAck);
               }
            }
         }

         log.trace(this + " retrieved " + ackInfos.size() + " deliveries");
      }
      else
      {
         // Transacted session - we need to get the acks from the resource manager. BTW we have
         // kept the old resource manager.

         ackInfos = rm.getDeliveriesForSession(getSessionID());
      }

      List recoveryInfos = new ArrayList();
      if (!ackInfos.isEmpty())
      {         
         for (Iterator i = ackInfos.iterator(); i.hasNext(); )
         {
            DeliveryInfo del = (DeliveryInfo)i.next();
            DeliveryRecovery recInfo =
               new DeliveryRecovery(del.getMessageProxy().getDeliveryId(),
                                    del.getMessageProxy().getMessage().getMessageID(),
                                    del.getQueueName());

            recoveryInfos.add(recInfo);        
         }         
      }
      
      log.trace(this + " sending delivery recovery " + recoveryInfos + " on failover");
      
      //Note we only recover sessions that are transacted or client ack
      if (transacted || xa || acknowledgeMode == Session.CLIENT_ACKNOWLEDGE)
      {
         //Note! We ALWAYS call recoverDeliveries even if there are no deliveries since it also does other stuff
         //like remove from recovery Area refs corresponding to messages in client consumer buffers
         
      	newDelegate.recoverDeliveries(recoveryInfos, oldSessionID);
      }
   }
   
   // Public ---------------------------------------------------------------------------------------
   
   public void setTreatAsNonTransactedWhenNotEnlisted(boolean b)
   {
   	treatAsNonTransactedWhenNotEnlisted = b;
   }
   
   public boolean getTreatAsNonTransactedWhenNotEnlisted()
   {
   	return treatAsNonTransactedWhenNotEnlisted;
   }
   
   /**
    * @return List<AckInfo>
    */
   public List getClientAckList()
   {
      return clientAckList;
   }
   
   public void setClientAckList(List list)
   {
      this.clientAckList = list;
   }

   public DeliveryInfo getAutoAckInfo()
   {
      return autoAckInfo;
   }

   public void setAutoAckInfo(DeliveryInfo info)
   {
      if (info != null && autoAckInfo != null)
      {
         throw new IllegalStateException("There is already a delivery set for auto ack " + System.identityHashCode(this) +
               " xa: " + this.xa);
      }
      autoAckInfo = info;
   }

   public int getAcknowledgeMode()
   {
      return acknowledgeMode;
   }

   public boolean isTransacted()
   {
      return transacted;
   }

   public boolean isXA()
   {
      return xa;
   }

   public MessagingXAResource getXAResource()
   {
      return xaResource;
   }

   public CompatibleExecutor getExecutor()
   {
      return executor;
   }

   public Object getCurrentTxId()
   {
      return currentTxId;
   }

   public boolean isRecoverCalled()
   {
      return recoverCalled;
   }

   public void setCurrentTxId(Object id)
   {
      this.currentTxId = id;
   }

   public void setRecoverCalled(boolean recoverCalled)
   {
      this.recoverCalled = recoverCalled;
   }

   public ClientConsumer getCallbackHandler(String consumerID)
   {
      return (ClientConsumer)callbackHandlers.get(consumerID);
   }

   public void addCallbackHandler(ClientConsumer handler)
   {
      callbackHandlers.put(handler.getConsumerId(), handler);
   }

   public void removeCallbackHandler(ClientConsumer handler)
   {
      callbackHandlers.remove(handler.getConsumerId());
   }

   public String getSessionID()
   {
      return sessionID;
   }
   
   public long getNPSendSequence()
   {
   	return npSendSequence;
   }
   
   public void incNpSendSequence()
   {
   	npSendSequence++;
   }
   
   public String toString()
   {
      return "SessionState[" + sessionID + "]";
   }
   
   //remove the ordering group from map.
   public void removeOrderingGroup(String orderingGroupName)
   {
      orderingGrpSeqMap.remove(orderingGroupName);
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------
   
   /**
    * @param enableOrderingGroup the enableOrderingGroup to set
    */
   public void setEnableOrderingGroup(boolean enableOrderingGroup)
   {
      this.enableOrderingGroup = enableOrderingGroup;
   }

   /**
    * @return the enableOrderingGroup
    */
   public boolean isEnableOrderingGroup()
   {
      return enableOrderingGroup;
   }

   /**
    * @param defaultOrderingGroupName the defaultOrderingGroupName to set
    */
   public void setDefaultOrderingGroupName(String defaultOrderingGroupName)
   {
      this.defaultOrderingGroupName = defaultOrderingGroupName;
   }

   /**
    * @return the defaultOrderingGroupName
    */
   public String getDefaultOrderingGroupName()
   {
      return defaultOrderingGroupName;
   }

   //A sequence generator class. sequence starts from 1
   public static class OrderingGroupSeq
   {
      private long counter;
      
      public OrderingGroupSeq()
      {
         counter = 0;
      }

      public long getNext()
      {
         counter++;
         return counter;
      }
   }

}

