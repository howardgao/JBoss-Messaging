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
package org.jboss.test.messaging.jms.bridge;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.InitialContext;

import org.jboss.jms.server.bridge.Bridge;
import org.jboss.jms.server.bridge.ConnectionFactoryFactory;
import org.jboss.jms.server.bridge.DestinationFactory;
import org.jboss.jms.server.bridge.JNDIConnectionFactoryFactory;
import org.jboss.jms.server.bridge.JNDIDestinationFactory;
import org.jboss.logging.Logger;
import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.container.ServiceContainer;

/**
 * 
 * A BridgeTestBase
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 * @version <tt>$Revision: 1.1 $</tt>
 *
 * $Id$
 *
 */
public class BridgeTestBase extends MessagingTestCase
{
   private static final Logger log = Logger.getLogger(BridgeTestBase.class);
   
   protected static ServiceContainer sc;
   
   protected static ConnectionFactoryFactory cff0, cff1;
   
   protected static ConnectionFactory cf0, cf1;
   
   protected static DestinationFactory sourceQueueFactory, targetQueueFactory, localTargetQueueFactory, sourceTopicFactory;
   
   protected static Queue sourceQueue, targetQueue, localTargetQueue;
   
   protected static Topic sourceTopic;
   
   protected static boolean firstTime = true;
   
   protected static Properties sourceProps;
   
   protected static Properties targetProps;
   
   public BridgeTestBase(String name)
   {
      super(name);
   }

   protected void setUp() throws Exception
   {
      super.setUp();
      
      if (firstTime)
      {
      	//Start the servers
      	
      	ServerManagement.start(0, "all", true);

      	ServerManagement.start(1, "all", false);

      	ServerManagement.deployQueue("sourceQueue", 0);

      	ServerManagement.deployTopic("sourceTopic", 0);  

      	ServerManagement.deployQueue("localTargetQueue", 0);

      	ServerManagement.deployQueue("targetQueue", 1);     
      	
      	setUpAdministeredObjects();
      	
      	//We need a local transaction and recovery manager
         //We must start this after the remote servers have been created or it won't
         //have deleted the database and the recovery manager may attempt to recover transactions
         sc = new ServiceContainer("transaction");   
         
         sc.start(false);   
      	
      	firstTime = false;
      }          
            
   }
   
   
   protected void tearDown() throws Exception
   {       
      super.tearDown(); 
                             
      checkEmpty(sourceQueue);
      checkEmpty(localTargetQueue);
      checkEmpty(targetQueue, 1);
      
      // Check no subscriptions left lying around
            
      checkNoSubscriptions(sourceTopic);
   }
   
   protected void setUpAdministeredObjects() throws Exception
   {
      InitialContext ic0 = null, ic1 = null;
      try
      {
         Hashtable props0 = ServerManagement.getJNDIEnvironment(0);
         
         sourceProps = new Properties();
         Enumeration keys = props0.keys();
         while (keys.hasMoreElements())
         {
            Object key = keys.nextElement();
            sourceProps.put(key, props0.get(key));
         }
         
         Hashtable props1 = ServerManagement.getJNDIEnvironment(1);
         targetProps = new Properties();
         keys = props1.keys();
         while (keys.hasMoreElements())
         {
            Object key = keys.nextElement();
            targetProps.put(key, props1.get(key));
         }
         
         cff0 = new JNDIConnectionFactoryFactory(props0, "/ConnectionFactory");
         
         cff1 = new JNDIConnectionFactoryFactory(props1, "/ConnectionFactory");
               
         ic0 = new InitialContext(props0);
         
         ic1 = new InitialContext(props1);
         
         cf0 = (ConnectionFactory)ic0.lookup("/ConnectionFactory");
         
         cf1 = (ConnectionFactory)ic1.lookup("/ConnectionFactory");
         
         sourceQueueFactory = new JNDIDestinationFactory(props0, "/queue/sourceQueue");
         
         sourceQueue = (Queue)sourceQueueFactory.createDestination();
         
         targetQueueFactory = new JNDIDestinationFactory(props1, "/queue/targetQueue");
         
         targetQueue = (Queue)targetQueueFactory.createDestination();
         
         sourceTopicFactory = new JNDIDestinationFactory(props0, "/topic/sourceTopic");
         
         sourceTopic = (Topic)sourceTopicFactory.createDestination();
         
         localTargetQueueFactory = new JNDIDestinationFactory(props0, "/queue/localTargetQueue"); 
         
         localTargetQueue = (Queue)localTargetQueueFactory.createDestination();
      }
      finally
      {
         if (ic0 != null)
         {
            ic0.close();
         }
         if (ic1 != null)
         {
            ic1.close();
         }
      }    
   }
   
   protected void sendMessages(ConnectionFactory cf, Destination dest, int start, int numMessages, boolean persistent)
      throws Exception
   {
      Connection conn = null;
      
      try
      {
         conn = cf.createConnection();
         
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageProducer prod = sess.createProducer(dest);
         
         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
         
         for (int i = start; i < start + numMessages; i++)
         {
            TextMessage tm = sess.createTextMessage("message" + i);
            
            prod.send(tm);
         }
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }
   

   protected void checkMessagesReceived(ConnectionFactory cf, Destination dest, int qosMode,
   		                               int numMessages, boolean longWaitForFirst) throws Exception
   {
      Connection conn = null;
        
      try
      {
         conn = cf.createConnection();
         
         conn.start();
         
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageConsumer cons = sess.createConsumer(dest);
         
         // Consume the messages
         
         Set msgs = new HashSet();
         
         int count = 0;
                           
         //We always wait longer for the first one - it may take some time to arrive especially if we are
         //waiting for recovery to kick in
         while (true)
         {
            TextMessage tm = (TextMessage)cons.receive(count == 0 ? (longWaitForFirst ? 60000 : 10000) : 5000);
              
            if (tm == null)
            {
               break;
            }
            
            //log.info("Got message " + tm.getText());
            
            msgs.add(tm.getText());

            count++;
            
         }
         
         if (qosMode == Bridge.QOS_ONCE_AND_ONLY_ONCE || qosMode == Bridge.QOS_DUPLICATES_OK)
         {            
            //All the messages should be received
            
            for (int i = 0; i < numMessages; i++)
            {
               assertTrue(msgs.contains("message" + i));
            }
            
            //Should be no more
            if (qosMode == Bridge.QOS_ONCE_AND_ONLY_ONCE)
            {
               assertEquals(numMessages, msgs.size());
            }         
         }
         else if (qosMode == Bridge.QOS_AT_MOST_ONCE)
         {
            //No *guarantee* that any messages will be received
            //but you still might get some depending on how/where the crash occurred                 
         }      

         log.trace("Check complete");
         
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }  
   }
   
   
   protected void checkAllMessageReceivedInOrder(ConnectionFactory cf, Destination dest, int start, int numMessages) throws Exception
   {
      Connection conn = null;     
      try
      {
         conn = cf.createConnection();
         
         conn.start();
         
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageConsumer cons = sess.createConsumer(dest);
         
         // Consume the messages
           
         for (int i = 0; i < numMessages; i++)
         {            
            TextMessage tm = (TextMessage)cons.receive(30000);
            
            assertNotNull(tm);
            
            //log.info("Got message " + tm.getText());
              
            assertEquals("message" + (i + start), tm.getText());
         } 
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }  
   }
   
   
   // Inner classes -------------------------------------------------------------------
   
}

