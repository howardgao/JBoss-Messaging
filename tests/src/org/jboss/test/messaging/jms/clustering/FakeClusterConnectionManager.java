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

package org.jboss.test.messaging.jms.clustering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.jboss.jms.client.JBossConnectionFactory;
import org.jboss.jms.destination.JBossQueue;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.contract.Delivery;
import org.jboss.messaging.core.contract.DeliveryObserver;
import org.jboss.messaging.core.contract.Distributor;
import org.jboss.messaging.core.contract.Filter;
import org.jboss.messaging.core.contract.MessageReference;
import org.jboss.messaging.core.impl.clusterconnection.MessageSucker;
import org.jboss.messaging.core.impl.clusterconnection.ClusterConnectionManager.ConnectionInfo;
import org.jboss.messaging.core.impl.tx.Transaction;

/**
 * A FakeClusterConnectionManager
 * 
 * Used to test Message Sucker. We use this one to get rid of the links 
 * between ClusterConnectionManager and the ServerPeer. We only need to 
 * test the 'Client' aspect of message sucker, to see if it can correctly
 * 'suck' messages. We don't care about the sending aspect of the suckers.
 * That makes the test easier.
 *
 * @author howard
 * 
 * Created Sep 14, 2009 1:38:09 PM
 *
 */
public class FakeClusterConnectionManager
{

   public static final long CLOSE_TIMEOUT = 2000;

   private Map connections;

   private boolean started;
   
   private int remoteID;
   
   private int thisID;
   
   private JBossConnectionFactory remoteFactory;
   
   private String suckerUser;
   
   private String suckerPassword;
   
   private int maxRetry;
   
   private int retryInterval;
   
   public FakeClusterConnectionManager(int remoteID,
                                   JBossConnectionFactory theFactory,
                                   String suckerUser,
                                   String suckerPassword,
                                   int maxRetry,
                                   int retryInterval,
                                   int thisID)
   {
      connections = new HashMap();
      
      this.remoteID = remoteID;
      
      this.remoteFactory = theFactory;
      
      this.suckerUser = suckerUser;
      
      this.suckerPassword = suckerPassword;
      
      this.maxRetry = maxRetry;
      
      this.retryInterval = retryInterval;
      
      this.thisID = thisID;
   }

   public synchronized void start() throws Exception
   {
      if (started)
      {
         return;
      }
      
      started = true;
   }
   
   public void createConnectionInfo(boolean updateJMSObject) throws Exception
   {
      FakeConnectionInfo info = new FakeConnectionInfo(remoteFactory, suckerUser, suckerPassword, remoteID == thisID, maxRetry, retryInterval, updateJMSObject);
      connections.put(remoteID, info);
      info.start();
   }
   
   public int getRetryTimes(int node)
   {
      FakeConnectionInfo info = (FakeConnectionInfo)connections.get(new Integer(node));
      return info.getRetryTimes();    
   }

   public String waitForReconnectionOK(int node)
   {
      FakeConnectionInfo info = (FakeConnectionInfo)connections.get(new Integer(node));
      return info.waitForReconnectionOK();      
   }
   
   public void resetFactory(int node, JBossConnectionFactory fact)
   {
      FakeConnectionInfo info = (FakeConnectionInfo)connections.get(new Integer(node));
      info.resetFactory(fact);      
   }

   public void updateQueueInSucker(int node, Queue queue) throws JMSException
   {
      FakeConnectionInfo info = (FakeConnectionInfo)connections.get(new Integer(node));
      info.updateQueueInSucker(queue);      
   }

   public String checkMessageSucked(int node, TextMessage[] messages) throws JMSException
   {
      FakeConnectionInfo info = (FakeConnectionInfo)connections.get(new Integer(node));
      return info.checkMessageSucked(messages);
   }

   public String checkMessageNotSucked(int node) throws JMSException
   {
      FakeConnectionInfo info = (FakeConnectionInfo)connections.get(new Integer(node));
      return info.checkMessageNotSucked();
   }
   
   public String checkConnectionFailureDetected(int node)
   {
      FakeConnectionInfo info = (FakeConnectionInfo)connections.get(new Integer(node));
      return info.checkConnectionFailureDetected();      
   }

   public void createSucker(Queue queue, int nid, boolean beginSuck) throws Exception
   {
      FakeConnectionInfo info = (FakeConnectionInfo)connections.get(new Integer(nid));

      if (!info.hasSucker(queue.getQueueName()))
      {
            FakeMessageSucker sucker = new FakeMessageSucker(new FakeCoreQueue(queue), info.getSession(), 0, info.suckBuffer);

            info.addSucker(sucker);
            
            sucker.start();
            
            sucker.setConsuming(beginSuck);
      }
   }

   class FakeCoreQueue implements org.jboss.messaging.core.contract.Queue
   {

      private String queueName;

      public FakeCoreQueue(Queue queue) throws JMSException
      {
         queueName = queue.getQueueName();
      }

      public String getName()
      {
         return queueName;
      }

      public void addAllToRecoveryArea(int nodeID, Map ids)
      {
      }

      public void addToRecoveryArea(int nodeID, long messageID, String sessionID)
      {
      }

      public int getDownCacheSize()
      {
         return 0;
      }

      public Filter getFilter()
      {
         return null;
      }

      public int getFullSize()
      {
         return 0;
      }

      public Distributor getLocalDistributor()
      {
         return null;
      }

      public int getNodeID()
      {
         return 0;
      }

      public int getPageSize()
      {
         return 0;
      }

      public long getRecoverDeliveriesTimeout()
      {
         return 0;
      }

      public Map getRecoveryArea()
      {
         return null;
      }

      public int getRecoveryMapSize()
      {
         return 0;
      }

      public Distributor getRemoteDistributor()
      {
         return null;
      }

      public Delivery handleMove(MessageReference ref, long sourceChannelID)
      {
         return null;
      }

      public boolean isClustered()
      {
         return false;
      }

      public void mergeIn(long channelID, int nodeID) throws Exception
      {
      }

      public List recoverDeliveries(List messageIds)
      {
         return null;
      }

      public void registerSucker(MessageSucker sucker)
      {
      }

      public void removeAllFromRecoveryArea(int nodeID)
      {
      }

      public void removeFromRecoveryArea(int nodeID, long messageID)
      {
      }

      public void removeStrandedReferences(String sessionID)
      {
      }

      public void setPagingParams(int fullSize, int pageSize, int downCacheSize)
      {
      }

      public boolean unregisterSucker(MessageSucker sucker)
      {
         return false;
      }

      public void activate()
      {
      }

      public List browse(Filter filter)
      {
         return null;
      }

      public void close()
      {
      }

      public void deactivate()
      {
      }

      public void deliver()
      {
      }

      public long getChannelID()
      {
         return 0;
      }

      public int getDeliveringCount()
      {
         return 0;
      }

      public int getMaxSize()
      {
         return 0;
      }

      public int getMessageCount()
      {
         return 0;
      }

      public int getMessagesAdded()
      {
         return 0;
      }

      public int getScheduledCount()
      {
         return 0;
      }

      public boolean isActive()
      {
         return false;
      }

      public boolean isRecoverable()
      {
         return false;
      }

      public void load() throws Exception
      {
      }

      public void removeAllReferences() throws Throwable
      {
      }

      public void setMaxSize(int newSize)
      {
      }

      public void unload() throws Exception
      {
      }

      public void acknowledge(Delivery d, Transaction tx) throws Throwable
      {
      }

      public void acknowledgeNoPersist(Delivery d) throws Throwable
      {
      }

      public void cancel(Delivery d) throws Throwable
      {
      }

      public Delivery handle(DeliveryObserver observer, MessageReference reference, Transaction tx)
      {
         return null;
      }

      public void setClustered(boolean isClustered)
      {
      }

      public void staticMerge(org.jboss.messaging.core.contract.Queue queue) throws Exception
      {
      }
   }

   // Inner classes -------------------------------------------------
   
   //Note: This currently is suitable for test of only one sucker!
   class FakeConnectionInfo extends ConnectionInfo
   {
      private ArrayList<TextMessage> suckBuffer = new ArrayList<TextMessage>();
      
      private Object connFailureLock = new Object();
      
      private boolean connFailed = false;
      
      private Object reconnLock = new Object();
      
      private boolean reconnOK = false;
      
      private boolean updateJMSObjects = true;
      
      private int lastRetryTimes;

      FakeConnectionInfo(JBossConnectionFactory factory, String suckerUser, String suckerPassword, 
                         boolean isLocal, int maxRetry, int retryInterval, boolean updateJMS) throws Exception
      {
         super(factory, suckerUser, suckerPassword, isLocal, maxRetry, retryInterval);
         updateJMSObjects = updateJMS;
      }
      
      public int getRetryTimes()
      {
         return lastRetryTimes;
      }

      public Session getSession()
      {
         return super.session;
      }

      public void updateQueueInSucker(Queue queue) throws JMSException
      {
         Iterator iter = suckers.values().iterator();
         
         while (iter.hasNext())
         {
            FakeMessageSucker sucker = (FakeMessageSucker)iter.next();
            sucker.updateQueue(new JBossQueue(queue.getQueueName()));
         }

      }

      public String waitForReconnectionOK()
      {
         synchronized(reconnLock)
         {
            if (!reconnOK)
            {
               try
               {
                  reconnLock.wait(20000);
               }
               catch (InterruptedException e)
               {
               }
            }
            if (reconnOK)
            {
               reconnOK = false;
               return null;
            }
            return "Reconnection failed after 10 seconds";
         }
      }

      public String checkConnectionFailureDetected()
      {
         synchronized (connFailureLock)
         {
            if (connFailed)
            {
               connFailed = false;
               return null;
            }
            try
            {
               connFailureLock.wait(10000);
            }
            catch (InterruptedException e)
            {
            }
            if (connFailed)
            {
               return null;
            }
            else
            {
               return "Connection Failure not detected with in 10 sec";
            }
         }
      }

      public String checkMessageSucked(TextMessage[] messages) throws JMSException
      {
         String result = null;
         if (messages.length != suckBuffer.size())
         {
            result = "Number of sucked messages not right, expected: " + messages.length + " but was: " + suckBuffer.size();
            return result;
         }
         for (int i = 0; i < messages.length; i++)
         {
            TextMessage msg = suckBuffer.get(i);
            if (!messages[i].getText().equals(msg.getText()))
            {
               result = "Message sucked not right, expected: " + messages[i].getText() + " but was: " + msg.getText();
               break;
            }
         }
         suckBuffer.clear();
         return result;
      }

      public String checkMessageNotSucked() throws JMSException
      {
         String result = null;
         if (suckBuffer.size() > 0)
         {
            result = "Number of sucked messages not right, expected: 0 but was: " + suckBuffer.size();
            return result;
         }
         suckBuffer.clear();
         return result;
      }

      //https://jira.jboss.org/jira/browse/JBMESSAGING-1732
      //on exception, try to recreate all suckers.
      public void onException(JMSException e)
      {
         synchronized(connFailureLock)
         {
            this.connFailed = true;
            connFailureLock.notify();
         }
         super.onException(e);
      }
      
      public synchronized void resetFactory(JBossConnectionFactory newFact)
      {
         connectionFactory = newFact;
         this.notify();
      }
      
      protected synchronized void cleanupConnection()
      {
         if (updateJMSObjects)
         {
            connectionFactory = null;
         }
         super.cleanupConnection();
      }
      
      protected synchronized int retryConnection()
      {         
         //regain factory: this is not the true behavior of reconnection.
         //in real case, the factory should be expected to be accessible. If not, that means the node is crashed or shutdown
         //in which case reconnection no longer apply. New connection info will be created instead.
         while (connectionFactory == null)
         {
            try
            {
               this.wait();
            }
            catch (InterruptedException e)
            {
            }
         }
         
         lastRetryTimes = super.retryConnection();
         
         synchronized(reconnLock)
         {
            reconnOK = super.started;
            reconnLock.notify();
         }
         
         return lastRetryTimes;
      }
      
      public void close()
      {
         super.close();
      }
      
      public void start() throws Exception
      {
         super.start();
      }
      
      public boolean hasSucker(String queueName)
      {
         return super.hasSucker(queueName);
      }
      
      public void addSucker(MessageSucker sucker)
      {
         super.addSucker(sucker);
      }
   }

   public void stop()
   {
      if (!started)
      {
         return;
      }
      
      Iterator iter = connections.values().iterator();
      
      while (iter.hasNext())
      {
         FakeConnectionInfo info = (FakeConnectionInfo)iter.next();
         
         info.close();
      }
      
      connections.clear();
      
      started = false;
   }


}
