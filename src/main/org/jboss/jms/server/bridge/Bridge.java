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
package org.jboss.jms.server.bridge;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;

import org.jboss.jms.client.JBossSession;
import org.jboss.jms.client.delegate.DelegateSupport;
import org.jboss.jms.client.state.SessionState;
import org.jboss.jms.message.JBossMessage;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.contract.MessagingComponent;
import org.jboss.tm.TransactionManagerLocator;

/**
 * 
 * A Bridge
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 * @version <tt>$Revision: 7304 $</tt>
 *
 * $Id: Bridge.java 7304 2009-06-11 03:16:45Z gaohoward $
 *
 */
public class Bridge implements MessagingComponent
{
   private static final Logger log;
   
   private static boolean trace;
   
   //Quality of service modes
   
   public static final int QOS_AT_MOST_ONCE = 0;
   
   public static final int QOS_DUPLICATES_OK = 1;
   
   public static final int QOS_ONCE_AND_ONLY_ONCE = 2;
   
   /*
    * 
    * Quality of service (QoS) levels
    * ===============================
    * 
    * QOS_AT_MOST_ONCE
    * ----------------
    * 
    * With this QoS mode messages will reach the destination from the source at most once.
    * The messages are consumed from the source and acknowledged
    * before sending to the destination. Therefore there is a possibility that if failure occurs between
    * removing them from the source and them arriving at the destination they could be lost. Hence delivery
    * will occur at most once.
    * This mode is avilable for both persistent and non persistent messages.
    * 
    * QOS_DUPLICATES_OK
    * -----------------
    * 
    * With this QoS mode, the messages are consumed from the source and then acknowledged
    * after they have been successfully sent to the destination. Therefore there is a possibility that if
    * failure occurs after sending to the destination but before acknowledging them, they could be sent again
    * when the system recovers. I.e. the destination might receive duplicates after a failure.
    * This mode is available for both persistent and non persistent messages.
    * 
    * QOS_ONCE_AND_ONLY_ONCE
    * ----------------------
    * 
    * This QoS mode ensures messages will reach the destination from the source once and only once.
    * (Sometimes this mode is known as "exactly once").
    * If both the source and the destination are on the same JBoss Messaging server instance then this can 
    * be achieved by sending and acknowledging the messages in the same local transaction.
    * If the source and destination are on different servers this is achieved by enlisting the sending and consuming
    * sessions in a JTA transaction. The JTA transaction is controlled by JBoss Transactions JTA implementation which
    * is a fully recovering transaction manager, thus providing a very high degree of durability.
    * If JTA is required then both supplied connection factories need to be XAConnectionFactory implementations.
    * This mode is only available for persistent messages.
    * This is likely to be the slowest mode since it requires extra persistence for the transaction logging.
    * 
    * Note:
    * For a specific application it may possible to provide once and only once semantics without using the
    * QOS_ONCE_AND_ONLY_ONCE QoS level. This can be done by using the QOS_DUPLICATES_OK mode and then checking for
    * duplicates at the destination and discarding them. Some JMS servers provide automatic duplicate message detection
    * functionality, or this may be possible to implement on the application level by maintaining a cache of received
    * message ids on disk and comparing received messages to them. The cache would only be valid
    * for a certain period of time so this approach is not as watertight as using QOS_ONCE_AND_ONLY_ONCE but
    * may be a good choice depending on your specific application.
    * 
    * 
    */
   
   static
   {
      log = Logger.getLogger(Bridge.class);
      
      trace = log.isTraceEnabled();
   }
   
   private String name;

   private String sourceUsername;
   
   private String sourcePassword;
   
   private String targetUsername;
   
   private String targetPassword;
   
   private TransactionManager tm;
   
   private String selector;
   
   private long failureRetryInterval;
   
   private int maxRetries;
   
   private int qualityOfServiceMode;
   
   private int maxBatchSize;
   
   private long maxBatchTime;
   
   private String subName;
   
   private String clientID;
   
   private volatile boolean addMessageIDInHeader;
   
   private boolean started;
   
   private LinkedList<Message> messages;
   
   private Object lock;
   
   private String sourceCffName;
   
   private String targetCffName;
   
   private Properties sourceProviderProperties;
   
   private Properties targetProviderProperties;

   private String sourceDestName;
   
   private String targetDestName;
   
   private boolean isSameSourceAndTarget;
   
   private String sourceQueueCffName;
   
   private String sourceTopicCffName;
   
   private String targetQueueCffName;
   
   private String targetTopicCffName;
   
   private ConnectionFactoryFactory sourceCff;
   
   private ConnectionFactoryFactory targetCff;
   
   private DestinationFactory sourceDestinationFactory;
   
   private DestinationFactory targetDestinationFactory;
   
   private Connection sourceConn; 
   
   private Connection targetConn;
   
   private Destination sourceDestination;
   
   private Destination targetDestination;
   
   private Session sourceSession;
   
   private Session targetSession;
   
   private MessageConsumer consumer;
   
   private MessageProducer producer;
        
   private BatchTimeChecker timeChecker;
   
   private Thread checkerThread;
   
   private long batchExpiryTime;
   
   private boolean paused;         
   
   private Transaction tx;  
   
   private boolean failed;
   
   private int forwardMode;
   
   private volatile boolean forceStop;
   
   private static final int FORWARD_MODE_XA = 0;
   
   private static final int FORWARD_MODE_LOCALTX = 1;
   
   private static final int FORWARD_MODE_NONTX = 2;
   
   /*
    * Constructor for MBean
    */
   public Bridge()
   {
      this.messages = new LinkedList<Message>();      
      
      this.lock = new Object();
   }
   
   public Bridge(boolean sameSourceNTarget, Properties srcProps, Properties tarProps,
                 String srcDestName, String tarDestName, String qCffName, String tCffName,
                 String sourceUsername, String sourcePassword,
                 String targetUsername, String targetPassword,
                 String selector, long failureRetryInterval,
                 int maxRetries,
                 int qosMode,
                 int maxBatchSize, long maxBatchTime,
                 String subName, String clientID,
                 boolean addMessageIDInHeader)
   {
      this();
      
      this.isSameSourceAndTarget = sameSourceNTarget;
      this.sourceProviderProperties = srcProps;
      this.targetProviderProperties = tarProps;
      this.sourceDestName = srcDestName;
      this.targetDestName = tarDestName;
      this.sourceQueueCffName = qCffName;
      this.sourceTopicCffName = qCffName;
      this.targetQueueCffName = tCffName;
      this.targetTopicCffName = tCffName;
      this.sourceUsername = sourceUsername;
      this.sourcePassword = sourcePassword;
      this.targetUsername = targetUsername;
      this.targetPassword = targetPassword;
      this.selector = selector;
      this.failureRetryInterval = failureRetryInterval;
      this.maxRetries = maxRetries;
      this.qualityOfServiceMode = qosMode;
      this.maxBatchSize = maxBatchSize;
      this.maxBatchTime = maxBatchTime;
      this.subName = subName;
      this.clientID = clientID;
      this.addMessageIDInHeader = addMessageIDInHeader;
      
      this.name = "Bridge-" + System.identityHashCode(this);
              
      if (trace)
      {
         log.trace("Created " + this);
      }
   }
      
   // MessagingComponent overrides --------------------------------------------------
        
   public synchronized void start() throws Exception
   {
      if (started)
      {
         log.warn(name + " Attempt to start, but is already started");
         return;
      }
      
      if (trace) { log.trace("Starting " + this); }         
      
      checkParams();
      
      TransactionManager tm = getTm();
      
      //There may already be a JTA transaction associated to the thread
      
      boolean ok;
      
      Transaction toResume = null;
      try
      {
         toResume = tm.suspend();
         
         ok = setupJMSObjects();
      }
      finally
      {
         if (toResume != null)
         {
            tm.resume(toResume);
         }
      }
      
      if (ok)
      {         
         //start the source connection
         startSourceConnectionAndChecker();
      }
      else
      {
         log.warn(name + " Failed to start bridge");
         handleFailureOnStartup();
      }
   }
   
   private synchronized void startSourceConnectionAndChecker() throws Exception
   {
         sourceConn.start();

         started = true;

         if (maxBatchTime != -1)
         {
            if (checkerThread != null && checkerThread.isAlive()) 
            {
               log.warn("The time checker thread is running!");
            } 
            else 
            {
                if (trace) { log.trace("Starting time checker thread"); }

                timeChecker = new BatchTimeChecker();

                checkerThread = new Thread(timeChecker);

                batchExpiryTime = System.currentTimeMillis() + maxBatchTime;

                checkerThread.start();

                if (trace) { log.trace("Started time checker thread"); }
            }
         }

         if (trace) { log.trace("Started " + this); }
   }
   
   public synchronized void stop() throws Exception
   {
      forceStop = true;
      
      if (!started)
      {
         log.warn(name + " Attempt to stop, but is already stopped");
         return;
      }
      
      if (trace) { log.trace("Stopping " + this); }
      
      synchronized (lock)
      {
         started = false;          
         
         //This must be inside sync block
         if (checkerThread != null)
         {
            checkerThread.interrupt();
         }
      }
            
      //This must be outside sync block
      if (checkerThread != null)
      {  
         if (trace) { log.trace("Waiting for checker thread to finish");}
         
         checkerThread.join();
         
         if (trace) { log.trace("Checker thread has finished"); }
      }
      
      if (tx != null)
      {
         //Terminate any transaction
         if (trace) { log.trace("Rolling back remaining tx"); }
         
         try
         {
            tx.rollback();
         }
         catch (Exception ignore)
         {
            if (trace) { log.trace("Failed to rollback", ignore); }
         }
         
         if (trace) { log.trace("Rolled back remaining tx"); }
      }
      
      try
      {
         sourceConn.close();
      }
      catch (Exception ignore)
      {
         if (trace) { log.trace("Failed to close source conn", ignore); }
      }
      
      if (targetConn != null)
      {
         try
         {
            targetConn.close();
         }
         catch (Exception ignore)
         {
            if (trace) { log.trace("Failed to close target conn", ignore); }
         }
      }
            
      if (trace) { log.trace("Stopped " + this); }
   }
   
   // Public ---------------------------------------------------------------------------
   
   public String getName()
   {
      return this.name;
   }
   
   public void setName(String name)
   {
      this.name = name;
   }
   
   public synchronized void pause() throws Exception
   {
      if (trace) { log.trace("Pausing " + this); }
      
      synchronized (lock)
      {
         paused = true;
         
         sourceConn.stop();
      }
      
      if (trace) { log.trace("Paused " + this); }
   }
   
   public synchronized void resume() throws Exception
   {
      if (trace) { log.trace("Resuming " + this); }
      
      synchronized (lock)
      {
         paused = false;
         
         sourceConn.start();
      }
      
      if (trace) { log.trace("Resumed " + this); }
   }
      
   public DestinationFactory getSourceDestinationFactory()
   {
   	return sourceDestinationFactory;
   }

   public void setSourceDestinationFactory(DestinationFactory dest)
   {
   	if (started)
   	{
   		log.warn(name + " Cannot set SourceDestinationFactory while bridge is started");
   		return;
   	}
   	sourceDestinationFactory = dest;
   }
   
   public DestinationFactory getTargetDestinationFactory()
   {
   	return targetDestinationFactory;
   }

   public void setTargetDestinationFactory(DestinationFactory dest)
   {
   	if (started)
   	{
   		log.warn(name + " Cannot set TargetDestinationFactory while bridge is started");
   		return;
   	}
   	targetDestinationFactory = dest;
   }
   
   public String getSourceUsername()
   {
      return sourceUsername;
   }
   
   public synchronized void setSourceUsername(String name)
   {
      if (started)
      {
         log.warn(name + " Cannot set SourceUsername while bridge is started");
         return;
      }
      sourceUsername = name;
   }
   
   public synchronized String getSourcePassword()
   {
      return sourcePassword;
   }
   
   public synchronized void setSourcePassword(String pwd)
   {
      if (started)
      {
         log.warn(name + " Cannot set SourcePassword while bridge is started");
         return;
      }
      sourcePassword = pwd;
   }
      
   public synchronized String getDestUsername()
   {
      return targetUsername;
   }
   
   public synchronized void setDestUserName(String name)
   {
      if (started)
      {
         log.warn(name + " Cannot set DestUserName while bridge is started");
         return;
      }
      this.targetUsername = name;
   }
   
   public synchronized String getDestPassword()
   {
      return this.targetPassword;
   }
   
   public synchronized void setDestPassword(String pwd)
   {
      if (started)
      {
         log.warn(name + " Cannot set DestPassword while bridge is started");
         return;
      }
      this.targetPassword = pwd;
   }
      
   public synchronized String getSelector()
   {
      return selector;
   }
   
   public synchronized void setSelector(String selector)
   {
      if (started)
      {
         log.warn(name + " Cannot set Selector while bridge is started");
         return;
      }
      this.selector = selector;
   }
   
   public synchronized long getFailureRetryInterval()
   {
      return failureRetryInterval;
   }
   
   public synchronized void setFailureRetryInterval(long interval)
   {
      if (started)
      {
         log.warn(name + " Cannot set FailureRetryInterval while bridge is started");
         return;
      }
      
      this.failureRetryInterval = interval;
   }    
   
   public synchronized int getMaxRetries()
   {
      return maxRetries;
   }
   
   public synchronized void setMaxRetries(int retries)
   {
      if (started)
      {
         log.warn(name + " Cannot set MaxRetries while bridge is started");
         return;
      }
      
      this.maxRetries = retries;
   }
      
   public synchronized int getQualityOfServiceMode()
   {
      return qualityOfServiceMode;
   }
   
   public synchronized void setQualityOfServiceMode(int mode)
   {
      if (started)
      {
         log.warn(name + " Cannot set QualityOfServiceMode while bridge is started");
         return;
      }
      
      qualityOfServiceMode = mode;
   }   
   
   public synchronized int getMaxBatchSize()
   {
      return maxBatchSize;
   }
   
   public synchronized void setMaxBatchSize(int size)
   {
      if (started)
      {
         log.warn(name + " Cannot set MaxBatchSize while bridge is started");
         return;
      }
      
      maxBatchSize = size;
   }
   
   public synchronized long getMaxBatchTime()
   {
      return maxBatchTime;
   }
   
   public synchronized void setMaxBatchTime(long time)
   {
      if (started)
      {
         log.warn(name + " Cannot set MaxBatchTime while bridge is started");
         return;
      }
      
      maxBatchTime = time;
   }
      
   public synchronized String getSubName()
   {
      return this.subName;
   }
   
   public synchronized void setSubName(String subname)
   {
      if (started)
      {
         log.warn(name + " Cannot set SubName while bridge is started");
         return;
      }
      
      this.subName = subname; 
   }
      
   public synchronized String getClientID()
   {
      return clientID;
   }
   
   public synchronized void setClientID(String clientID)
   {
      if (started)
      {
         log.warn(name + " Cannot set ClientID while bridge is started");
         return;
      }
      
      this.clientID = clientID; 
   }
   
   public boolean isAddMessageIDInHeader()
   {
   	return this.addMessageIDInHeader;
   }
   
   public void setAddMessageIDInHeader(boolean value)
   {
   	this.addMessageIDInHeader = value;
   }
      
   public synchronized boolean isPaused()
   {
      return paused;
   }
   
   public synchronized boolean isFailed()
   {
      return failed;
   }
   
   public synchronized boolean isStarted()
   {
      return started;
   }
   
   public void setSourceCFFName(String name)
   {
      sourceCffName = name;
   }
   
   public void setTargetCFFName(String name)
   {
      targetCffName = name;
   }

   public void setSourceProps(Properties sourceProps)
   {
      sourceProviderProperties = sourceProps;
   }

   public void setTargetProps(Properties targetProps)
   {
      targetProviderProperties = targetProps;
   }

   public void setSourceDestinationName(String sourceDestinationLookup)
   {
      sourceDestName = sourceDestinationLookup;
   }

   public void setTargetDestinationName(String targetDestinationLookup)
   {
      targetDestName = targetDestinationLookup;
   }

   public void setIsSameSourceAndTarget(boolean sameSourceAndTarget)
   {
      isSameSourceAndTarget = sameSourceAndTarget;
   }

   public void setSourceQueueCffName(String name)
   {
      sourceQueueCffName = name;
   }   

   public void setSourceTopicCffName(String name)
   {
      sourceTopicCffName = name;
   }   

   public void setTargetQueueCffName(String name)
   {
      targetQueueCffName = name;
   }   

   public void setTargetTopicCffName(String name)
   {
      targetTopicCffName = name;
   }   
   
   // Private -------------------------------------------------------------------
   
   private void checkParams()
   {
      if (failureRetryInterval < 0 && failureRetryInterval != -1)
      {
         throw new IllegalArgumentException("failureRetryInterval must be > 0 or -1 to represent no retry");
      }
      if (maxRetries < 0 && maxRetries != -1)
      {
         throw new IllegalArgumentException("maxRetries must be >= 0 or -1 to represent infinite retries");
      }
      if (failureRetryInterval == -1 && maxRetries > 0)
      {
         throw new IllegalArgumentException("If failureRetryInterval == -1 maxRetries must be 0");
      }
      if (!(maxBatchSize >= 1))
      {
         throw new IllegalArgumentException("maxBatchSize must be >= 1");
      }
      if (!(maxBatchTime == -1 || maxBatchTime >=1))
      {
         throw new IllegalArgumentException("maxBatchTime must be >= 1 or -1 to represent unlimited batch time");
      }
      if (qualityOfServiceMode != QOS_AT_MOST_ONCE && qualityOfServiceMode != QOS_DUPLICATES_OK && qualityOfServiceMode != QOS_ONCE_AND_ONLY_ONCE)
      {
         throw new IllegalArgumentException("Invalid quality of service mode " + qualityOfServiceMode);
      }
   }
   
   private void enlistResources(Transaction tx) throws Exception
   {
      if (trace) { log.trace("Enlisting resources in tx"); }
      
      XAResource resSource = ((XASession)sourceSession).getXAResource();
      
      tx.enlistResource(resSource);
      
      XAResource resDest = ((XASession)targetSession).getXAResource();
      
      tx.enlistResource(resDest);
      
      if (trace) { log.trace("Enlisted resources in tx"); }
   }
   
   private void delistResources(Transaction tx) throws Exception
   {
      if (trace) { log.trace("Delisting resources from tx"); }
      
      XAResource resSource = ((XASession)sourceSession).getXAResource();
      
      tx.delistResource(resSource, XAResource.TMSUCCESS);
      
      XAResource resDest = ((XASession)targetSession).getXAResource();
      
      tx.delistResource(resDest, XAResource.TMSUCCESS);
      
      if (trace) { log.trace("Delisted resources from tx"); }
   }
   
   private Transaction startTx() throws Exception
   {
      if (trace) { log.trace("Starting JTA transaction"); }
      
      TransactionManager tm = getTm();
      
      //Set timeout to a large value since we do not want to time out while waiting for messages
      //to arrive - 10 years should be enough
      tm.setTransactionTimeout(60 * 60 * 24 * 365 * 10);
      
      tm.begin();
         
      Transaction tx = tm.getTransaction();
      
      //Remove the association between current thread - we don't want it
      //we will be committing /rolling back directly on the transaction object
      
      tm.suspend();
      
      if (trace) { log.trace("Started JTA transaction"); }
      
      return tx;
   }
   
   private TransactionManager getTm()
   {
      if (tm == null)
      {
         tm = TransactionManagerLocator.getInstance().locate();
         
         if (tm == null)
         {
            throw new IllegalStateException("Cannot locate a transaction manager");
         }         
      }
      
      return tm;
   }
         
   private Connection createConnection(String username, String password, ConnectionFactoryFactory cff)
      throws Exception
   {
      Connection conn;
      
      ConnectionFactory cf = cff.createConnectionFactory();
      
      if (qualityOfServiceMode == QOS_ONCE_AND_ONLY_ONCE &&
          !(cf instanceof XAConnectionFactory))
      {
         throw new IllegalArgumentException("Connection factory must be XAConnectionFactory");
      }
      
      if (username == null)
      {
         if (qualityOfServiceMode == QOS_ONCE_AND_ONLY_ONCE)
         {
            if (trace) { log.trace("Creating an XA connection"); }
            conn = ((XAConnectionFactory)cf).createXAConnection();
         }
         else
         {
            if (trace) { log.trace("Creating a non XA connection"); }
            conn = cf.createConnection();            
         }
      }
      else
      {
         if (qualityOfServiceMode == QOS_ONCE_AND_ONLY_ONCE)
         {
            if (trace) { log.trace("Creating an XA connection"); }
            conn = ((XAConnectionFactory)cf).createXAConnection(username, password);
         }
         else
         {
            if (trace) { log.trace("Creating a non XA connection"); }
            conn = cf.createConnection(username, password);            
         }  
      }
      
      return conn;
   }
    
   /*
    * Source and target on same server
    * --------------------------------
    * If the source and target destinations are on the same server (same resource manager) then,
    * in order to get QOS_ONCE_AND_ONLY_ONCE, we simply need to consuming and send in a single
    * local JMS transaction.
    * 
    * We actually use a single local transacted session for the other QoS modes too since this
    * is more performant than using DUPS_OK_ACKNOWLEDGE or AUTO_ACKNOWLEDGE session ack modes, so effectively
    * the QoS is upgraded.
    * 
    * Source and target on different server
    * -------------------------------------
    * If the source and target destinations are on a different servers (different resource managers) then:
    * 
    * If desired QoS is QOS_ONCE_AND_ONLY_ONCE, then we start a JTA transaction and enlist the consuming and sending
    * XAResources in that.
    * 
    * If desired QoS is QOS_DUPLICATES_OK then, we use CLIENT_ACKNOWLEDGE for the consuming session and
    * AUTO_ACKNOWLEDGE (this is ignored) for the sending session if the maxBatchSize == 1, otherwise we
    * use a local transacted session for the sending session where maxBatchSize > 1, since this is more performant
    * When bridging a batch, we make sure to manually acknowledge the consuming session, if it is CLIENT_ACKNOWLEDGE
    * *after* the batch has been sent
    * 
    * If desired QoS is QOS_AT_MOST_ONCE then, if maxBatchSize == 1, we use AUTO_ACKNOWLEDGE for the consuming session,
    * and AUTO_ACKNOWLEDGE for the sending session.
    * If maxBatchSize > 1, we use CLIENT_ACKNOWLEDGE for the consuming session and a local transacted session for the
    * sending session.
    * 
    * When bridging a batch, we make sure to manually acknowledge the consuming session, if it is CLIENT_ACKNOWLEDGE
    * *before* the batch has been sent
    * 
    */
   
   private boolean setupJMSObjects()
   {
      try
      {  
      	//Lookup the destinations
         sourceDestinationFactory = new JNDIDestinationFactory(sourceProviderProperties, sourceDestName);
         sourceDestination = sourceDestinationFactory.createDestination();
      	
         targetDestinationFactory = new JNDIDestinationFactory(targetProviderProperties, targetDestName);
         targetDestination = targetDestinationFactory.createDestination();
         
         //factories
         if(sourceDestination instanceof Topic)
         {
            sourceCffName = sourceTopicCffName;
         }
         else
         {
            sourceCffName = sourceQueueCffName;
         }
         
         sourceCff = new JNDIConnectionFactoryFactory(sourceProviderProperties, sourceCffName);

         if (isSameSourceAndTarget)
         {
            targetCff = sourceCff;
         }
         else
         {
            if(targetDestination instanceof Topic)
            {
               targetCffName = targetTopicCffName;
            }
            else
            {
               targetCffName = targetQueueCffName;
            }
            
            targetCff = new JNDIConnectionFactoryFactory(targetProviderProperties, targetCffName);
         }
      	      
         if (sourceCff == targetCff)
         {
            //Source and target destinations are on the server - we can get once and only once
            //just using a local transacted session
         	//everything becomes once and only once
         	
         	forwardMode = FORWARD_MODE_LOCALTX;
         }
         else
         {
         	//Different servers
         	if (qualityOfServiceMode == QOS_ONCE_AND_ONLY_ONCE)
         	{
         		//Use XA
         		
         		forwardMode = FORWARD_MODE_XA;
         	}
         	else
         	{
         		forwardMode = FORWARD_MODE_NONTX;
         	}
         }
         
         sourceConn = createConnection(sourceUsername, sourcePassword, sourceCff);
         
         if (forwardMode != FORWARD_MODE_LOCALTX)
         {
            targetConn = createConnection(targetUsername, targetPassword, targetCff);
            
            targetConn.setExceptionListener(new BridgeExceptionListener());            
         }
                  
         if (clientID != null)
         {
            sourceConn.setClientID(clientID);
         }
         
         sourceConn.setExceptionListener(new BridgeExceptionListener());         
          
         Session sess;
         
         if (forwardMode == FORWARD_MODE_LOCALTX)
         {
            //We simply use a single local transacted session for consuming and sending      
            
            sourceSession = sourceConn.createSession(true, Session.SESSION_TRANSACTED);
            
            sess = sourceSession;
         }
         else
         {
            if (forwardMode == FORWARD_MODE_XA)
            {
               //Create an XASession for consuming from the source
               if (trace) { log.trace("Creating XA source session"); }
               
               sourceSession = ((XAConnection)sourceConn).createXASession();
               
               sess = ((XASession)sourceSession).getSession();
            }
            else
            {
               if (trace) { log.trace("Creating non XA source session"); }
               
               //Create a standard session for consuming from the source
               
               //We use ack mode client ack
                              
               sourceSession = sourceConn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
               
               sess = sourceSession;
            }
         }
         
         if (forwardMode == FORWARD_MODE_XA && sourceSession instanceof JBossSession)
         {
         	JBossSession jsession = (JBossSession)sourceSession;
         	
         	SessionState sstate = (SessionState)((DelegateSupport)jsession.getDelegate()).getState();
            
         	sstate.setTreatAsNonTransactedWhenNotEnlisted(false);
         }
            
         if (subName == null)
         {
            if (selector == null)
            {
               consumer = sess.createConsumer(sourceDestination);
            }
            else
            {
               consumer = sess.createConsumer(sourceDestination, selector, false);
            }
         }
         else
         {
            //Durable subscription
            if (selector == null)
            {
               consumer = sess.createDurableSubscriber((Topic)sourceDestination, subName);
            }
            else
            {
               consumer = sess.createDurableSubscriber((Topic)sourceDestination, subName, selector, false);
            }
         }
         
         //Now the sending session
         
         
         if (forwardMode != FORWARD_MODE_LOCALTX)
         {            
            if (forwardMode == FORWARD_MODE_XA)
            {
               if (trace) { log.trace("Creating XA dest session"); }
               
               //Create an XA sesion for sending to the destination
               
               targetSession = ((XAConnection)targetConn).createXASession();
               
               sess = ((XASession)targetSession).getSession();
            }
            else
            {
               if (trace) { log.trace("Creating non XA dest session"); }
               
               //Create a standard session for sending to the target
                                             
               //If batch size > 1 we use a transacted session since is more efficient
               
               boolean transacted = maxBatchSize > 1;
               
               targetSession = targetConn.createSession(transacted, transacted ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
               
               sess = targetSession;
            }       
         }
         
         if (forwardMode == FORWARD_MODE_XA)
         {
            if (trace) { log.trace("Starting JTA transaction"); }
            
            tx = startTx();
            
            enlistResources(tx);                  
         }
         
         producer = sess.createProducer(null);
                          
         consumer.setMessageListener(new SourceListener());
         
         return true;
      }
      catch (Exception e)
      {
         log.warn(name + " Failed to set up connections", e);
         
         //If this fails we should attempt to cleanup or we might end up in some weird state
         
         cleanup();
         
         return false;
      }
   }
   
   private void cleanup()
   {
   	
      //Close the old objects
      try
      {
         sourceConn.close();
      }
      catch (Throwable ignore)
      {            
      	if (trace) { log.trace("Failed to close source connection", ignore); }
      }
      try
      {
         if (targetConn != null)
         {
            targetConn.close();
         }
      }
      catch (Throwable ignore)
      {    
      	if (trace) { log.trace("Failed to close target connection", ignore); }
      }

   	
      if (tx != null)
      {
         try
         {
            delistResources(tx);
         }
         catch (Throwable ignore)
         {
         	if (trace) { log.trace("Failed to delist resources", ignore); }
         } 
         try
         {
            //Terminate the tx
            tx.rollback();
         }
         catch (Throwable ignore)
         {
         	if (trace) { log.trace("Failed to rollback", ignore); }
         } 
      }      
   }
   
   private void pause(long interval)
   {
      long start = System.currentTimeMillis();
      while (System.currentTimeMillis() - start < failureRetryInterval)
      {
         try
         {
            Thread.sleep(failureRetryInterval);
         }
         catch (InterruptedException ex)
         {                  
         }
      }
   }
   
   private boolean setupJMSObjectsWithRetry()
   {
      if (trace) { log.trace("Setting up connections"); }
      
      int count = 0;
      
      while (true)
      {
         boolean ok = setupJMSObjects();
         
         if (ok)
         {
            return true;
         }
         
         count++;
         
         if (maxRetries != -1 && count == maxRetries)
         {
            break;
         }
         
         log.warn(name + " Failed to set up connections, will retry after a pause of " + failureRetryInterval + " ms");
         
         pause(failureRetryInterval);
         
         if (forceStop)
         {
            break;
         }
      }
      
      //If we get here then we exceed maxRetries
      return false;      
   }
      
   private void sendBatchNonTransacted()
   {
   	try
      {         
   		if (qualityOfServiceMode == QOS_AT_MOST_ONCE)
   		{
   			//We client ack before sending
   			
            if (trace) { log.trace("Client acking source session"); }
               			
            ((Message)messages.getLast()).acknowledge();
            
            if (trace) { log.trace("Client acked source session"); }            
   		}
   		   		
         sendMessages();
         
         if (maxBatchSize > 1)
         {
         	//The sending session is transacted - we need to commit it
         	
            if (trace) { log.trace("Committing target session"); }
                     	
         	targetSession.commit();
         	
            if (trace) { log.trace("Committed source session"); }            
         }
         
         if (qualityOfServiceMode == QOS_DUPLICATES_OK)
   		{
   			//We client ack after sending
         	
         	//Note we could actually use Session.DUPS_OK_ACKNOWLEDGE here
         	//For a slightly less strong delivery guarantee
   		
            if (trace) { log.trace("Client acking source session"); }
               			
            ((Message)messages.getLast()).acknowledge();
            
            if (trace) { log.trace("Client acked source session"); }            
   		}
                                         
         //Clear the messages
         messages.clear();            
      }
      catch (Exception e)
      {
         log.warn(name + " Failed to send + acknowledge batch, closing JMS objects", e);
      
         handleFailureOnSend();                                                 
      }	
   }
   
   private void sendBatchXA()
   {
   	try
      {         
         sendMessages();
         
         //Commit the JTA transaction and start another
                                 
         delistResources(tx);
            
         if (trace) { log.trace("Committing JTA transaction"); }
         
         tx.commit();

         if (trace) { log.trace("Committed JTA transaction"); }
         
         tx = startTx();  
         
         enlistResources(tx);
                  
         //Clear the messages
         messages.clear();            
      }
      catch (Exception e)
      {
         log.warn(name + " Failed to send + acknowledge batch, closing JMS objects", e);
      
         handleFailureOnSend();                                                 
      }
   }
   
   private void sendBatchLocalTx()
   {
   	try
      {         
         sendMessages();
                     
         if (trace) { log.trace("Committing source session"); }
         
         sourceSession.commit();
         
         if (trace) { log.trace("Committed source session"); }     
         
         //Clear the messages
         messages.clear();           
      }
      catch (Exception e)
      {
         log.warn(name + " Failed to send + acknowledge batch, closing JMS objects", e);
      
         handleFailureOnSend();                                                 
      }
   }
   
   private void sendBatch() 
   {
      if (trace) { log.trace("Sending batch of " + messages.size() + " messages"); }
        
      if (paused)
      {
         //Don't send now
         if (trace) { log.trace("Paused, so not sending now"); }
         
         return;            
      }
      
      if (forwardMode == FORWARD_MODE_LOCALTX)
      {
      	sendBatchLocalTx();
      }
      else if (forwardMode == FORWARD_MODE_XA)      	
      {
      	sendBatchXA();
      }
      else
      {
      	sendBatchNonTransacted();
      }
   }
   
   private void sendMessages() throws Exception
   {
      Iterator iter = messages.iterator();
      
      Message msg = null;
      
      while (iter.hasNext())
      {
      	msg = (Message)iter.next();
      	
      	if (addMessageIDInHeader)
         {
         	addMessageIDInHeader(msg);            	
         }
         
         if (trace) { log.trace("Sending message " + msg); }
         
         //Make sure the correct time to live gets propagated
         
         long timeToLive = msg.getJMSExpiration();
         
   		if (timeToLive != 0)
   		{
   			timeToLive -=  System.currentTimeMillis();
   			
   			if (timeToLive <= 0)
   			{
   				timeToLive = 1; //Should have already expired - set to 1 so it expires when it is consumed or delivered
   			}
   		}
         
   		producer.send(targetDestination, msg, msg.getJMSDeliveryMode(), msg.getJMSPriority(), timeToLive);
   		
         if (trace) { log.trace("Sent message " + msg); }     
      }
   }
   
   private void handleFailureOnSend()
   {
      handleFailure(new FailureHandler());
   }
   
   private void handleFailureOnStartup()
   {
      handleFailure(new StartupFailureHandler());
   }
   
   private void handleFailure(Runnable failureHandler)
   {
      failed = true;

      //Failure must be handled on a separate thread to the calling thread (either onMessage or start).
      //In the case of onMessage we can't close the connection from inside the onMessage method
      //since it will block waiting for onMessage to complete. In the case of start we want to return
      //from the call before the connections are reestablished so that the caller is not blocked unnecessarily.
      Thread t = new Thread(failureHandler);
      
      t.start();    
      
      t.interrupt();
   }
   
   private void addMessageIDInHeader(Message msg) throws Exception
   {
   	//We concatenate the old message id as a header in the message
   	//This allows the target to then use this as the JMSCorrelationID of any response message
   	//thus enabling a distributed request-response pattern.
   	//Each bridge (if there are more than one) in the chain can concatenate the message id
   	//So in the case of multiple bridges having routed the message this can be used in a multi-hop
   	//distributed request/response
   	if (trace) { log.trace("Adding old message id in Message header"); }
   	
   	//Now JMS is really dumb and does not let you add a property on received message without first
   	//calling clearProperties, so we need to save and re-add all the old properties so we
   	//don't lose them!!
   	
   	Enumeration en = msg.getPropertyNames();
   	
   	Map<String, Object> oldProps = null;
   	
   	while (en.hasMoreElements())
   	{
   		String propName = (String)en.nextElement();
   		
   		if (oldProps == null)
   		{
   			oldProps = new HashMap<String, Object>();
   		}
   		
   		oldProps.put(propName, msg.getObjectProperty(propName));
   	}
   	            	
   	msg.clearProperties();
   	
   	if (oldProps != null)
   	{
   		Iterator iter2 = oldProps.entrySet().iterator();
   		
   		while (iter2.hasNext())
   		{
   			Map.Entry entry = (Map.Entry)iter2.next();

   			String propName = (String)entry.getKey();

   			msg.setObjectProperty(propName, entry.getValue());   			
   		}
   	}

   	String val = null;
   	
   	val = msg.getStringProperty(JBossMessage.JBOSS_MESSAGING_BRIDGE_MESSAGE_ID_LIST);
   	
   	if (val == null)
   	{
   		val = msg.getJMSMessageID();
   	}
   	else
   	{
   		StringBuffer sb = new StringBuffer(val);
   		
   		sb.append(",").append(msg.getJMSMessageID());
   		
   		val = sb.toString();
   	}
   	
   	msg.setStringProperty(JBossMessage.JBOSS_MESSAGING_BRIDGE_MESSAGE_ID_LIST, val);            	   
   }
   
   // Inner classes ---------------------------------------------------------------
   
   private class FailureHandler implements Runnable
   {
      /**
       * Start the source connection - note the source connection must not be started before
       * otherwise messages will be received and ignored
       */
      protected void startSourceConnection()
      {
         try
         {
            sourceConn.start();
         }
         catch (JMSException e)
         {
            log.error("Failed to start source connection", e);
         }
      }

      protected void succeeded()
      {
         log.info(name + " Succeeded in reconnecting to servers");
         
         synchronized (lock)
         {
            failed = false;

            startSourceConnection();
         }
      }
      
      protected void failed()
      {
         //We haven't managed to recreate connections or maxRetries = 0
         log.warn(name + " Unable to set up connections, bridge will be stopped");
         
         try
         {                  
            stop();
         }
         catch (Exception ignore)
         {                  
         }
      }

      public void run()
      {
      	if (trace) { log.trace("Failure handler running"); }
      	
         // Clear the messages
         messages.clear();

         cleanup();
         
         boolean ok = false;
         
         if (maxRetries > 0 || maxRetries == -1)
         {
            log.warn(name + " Will retry after a pause of " + failureRetryInterval + " ms");
            
            pause(failureRetryInterval);
            
            //Now we try
            ok = setupJMSObjectsWithRetry();
         }
         
         if (!ok)
         {
            failed();
         }
         else
         {
            succeeded();
         }    
      }
   }
   
   private class StartupFailureHandler extends FailureHandler
   {
      protected void failed()
      {
         // Don't call super
         log.warn(name + " Unable to set up connections, bridge will not be started");
      }
      
      protected void succeeded()
      {
         // Don't call super - a bit ugly in this case but better than taking the lock twice.
         log.info(name + " Succeeded in connecting to servers");
         
         synchronized (lock)
         {
            failed = false;
            started = true;

            //Start the source connection - note the source connection must not be started before
            //otherwise messages will be received and ignored

            try {
                // alsa: if the bridge has failed to start then it must be started here
                // not only the sourceConn but the whole bridge
                startSourceConnectionAndChecker();
            } catch (Exception e) {
                log.error("Failed to start source connection", e);
            }
         }
      }
   }
   
   private class BatchTimeChecker implements Runnable
   {
      public void run()
      {
         if (trace) { log.trace(this + " running"); }
         
         synchronized (lock)
         {
            while (started)
            {
               long toWait = batchExpiryTime - System.currentTimeMillis();
               
               if (toWait <= 0)
               {
                  if (trace) { log.trace(this + " waited enough"); }
                  
                  synchronized (lock)
                  {              
                     if (!failed && !messages.isEmpty())
                     {
                        if (trace) { log.trace(this + " got some messages so sending batch"); }
                        
                        sendBatch();                     
                        
                        if (trace) { log.trace(this + " sent batch"); }
                     }
                  }
                  
                  batchExpiryTime = System.currentTimeMillis() + maxBatchTime;
               }
               else
               {                    
                  try
                  {
                     if (trace) { log.trace(this + " waiting for " + toWait); }
                     
                     lock.wait(toWait);
                     
                     if (trace) { log.trace(this + " woke up"); }
                  }
                  catch (InterruptedException e)
                  {
                     //Ignore
                     if (trace) { log.trace(this + " thread was interrupted"); }
                  }
                  
               }
            }        
         }
      }      
   }  
   
   private class SourceListener implements MessageListener
   {
      public void onMessage(Message msg)
      {
         synchronized (lock)
         {
            if (failed)
            {
               //Ignore the message
               if (trace) { log.trace("Bridge has failed so ignoring message"); }
                              
               return;
            }
            
            if (trace) { log.trace(this + " received message " + msg); }
            
            messages.add(msg);
            
            batchExpiryTime = System.currentTimeMillis() + maxBatchTime;            
            
            if (trace) { log.trace(this + " rescheduled batchExpiryTime to " + batchExpiryTime); }
            
            if (maxBatchSize != -1 && messages.size() >= maxBatchSize)
            {
               if (trace) { log.trace(this + " maxBatchSize has been reached so sending batch"); }
               
               sendBatch();
               
               if (trace) { log.trace(this + " sent batch"); }
            }                        
         }
      }      
   }   
   
   private class BridgeExceptionListener implements ExceptionListener
   {
		public void onException(JMSException e)
		{
			log.warn(name + " Detected failure on connection", e);
			
			synchronized (lock)
			{
				if (failed)
				{
					//The failure has already been detected and is being handled
					if (trace) { log.trace("Failure recovery already in progress"); }
				}
				else
			   {				
					handleFailure(new FailureHandler());
			   }
			}
		}   	
   }

}
