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


package org.jboss.test.messaging.jms.bridge;

import java.io.ByteArrayOutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.management.ObjectName;
import javax.naming.InitialContext;

import org.jboss.jms.server.bridge.Bridge;
import org.jboss.jms.server.bridge.ConnectionFactoryFactory;
import org.jboss.jms.server.bridge.DestinationFactory;
import org.jboss.jms.server.bridge.JNDIConnectionFactoryFactory;
import org.jboss.jms.server.bridge.JNDIDestinationFactory;
import org.jboss.logging.Logger;
import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.container.ServiceAttributeOverrides;
import org.jboss.test.messaging.tools.container.ServiceContainer;

/**
 * A BridgeMBeanExtraTest
 *
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 * 
 * Created Feb 11, 2009 11:31:47 AM
 *
 *
 */
public class BridgeMBeanExtraTest extends MessagingTestCase
{
   private static final Logger log = Logger.getLogger(BridgeMBeanExtraTest.class);
   protected static boolean firstTime = true;
   protected static ServiceContainer sc;
   
   protected static Queue sourceQueue, targetQueue, localTargetQueue;
   protected static ConnectionFactory cf0, cf1;
   protected static DestinationFactory sourceQueueFactory, targetQueueFactory, localTargetQueueFactory, sourceTopicFactory;
   protected static Properties sourceProps;
   protected static Properties targetProps;
   protected static ConnectionFactoryFactory cff0, cff1;
   protected static Topic sourceTopic;   
                                                      
   public BridgeMBeanExtraTest(String name)
   {
      super(name);
   }
   
   private static ObjectName sourceProviderLoader;
   
   private static ObjectName targetProviderLoader;
   
   protected void setUpServers() throws Exception
   {
      
      if (firstTime)
      {
         //Start the servers
         
         ServerManagement.start(0, "all", true);

         ServiceAttributeOverrides overrides = new ServiceAttributeOverrides();
         ObjectName on = new ObjectName("jboss.messaging.connectionfactory:service=ConnectionFactory");
         overrides.put(on, "JNDIBindings", "<bindings><binding>/ConnectionFactory2</binding>"
                       + "<binding>/XAConnectionFactory2</binding>"
                       + "<binding>java:/ConnectionFactory2</binding>"
                       + "<binding>java:/XAConnectionFactory</binding>"
                       + "</bindings>");
         //overrides.put(on, attrName, attrValue);
         
         ServerManagement.start(1, "all", overrides, false);

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
         
         Properties props1 = new Properties();
         props1.putAll(ServerManagement.getJNDIEnvironment(0));
         
         Properties props2 = new Properties();
         props2.putAll(ServerManagement.getJNDIEnvironment(1));
         
         installJMSProviderLoader(0, props1, "/XAConnectionFactory", "adaptor1");
         
         installJMSProviderLoader(0, props2, "/XAConnectionFactory2", "adaptor2");
         
         sourceProviderLoader = new ObjectName("jboss.messaging:service=JMSProviderLoader,name=adaptor1");
         targetProviderLoader = new ObjectName("jboss.messaging:service=JMSProviderLoader,name=adaptor2");
         
         firstTime = false;
      }          
            
   }

   public void setUp() throws Exception
   {      
      super.setUp();
      
      setUpServers();
   }
   
   public void tearDown() throws Exception
   {
      super.tearDown();
   }
   
   /**
    * This test is mainly copied from BridgeMBeanTest with the exception that
    * the FactoryRef Name for the targetProviderLoader is made different from the sourceProviderLoader's.
    * see: https://jira.jboss.org/jira/browse/JBMESSAGING-1502
    * @throws Exception
    */
   public void testProviderConfigParams() throws Exception
   {
      ObjectName on = null;
      Connection connSource = null;
      Connection connTarget = null;

      try
      {
         on = deployBridge(0, "Bridge1", sourceProviderLoader, targetProviderLoader,
                                      "/queue/sourceQueue", "/queue/targetQueue",
                                      null, null, null, null,
                                      Bridge.QOS_AT_MOST_ONCE, null, 1,
                                      -1, null, null, 5000, -1, false);
         log.info("Deployed bridge");
         
         ServerManagement.getServer(0).invoke(on, "create", new Object[0], new String[0]);
         
         log.info("Created bridge");
                      
         connSource = cf0.createConnection();
         
         connTarget = cf1.createConnection();
         
         connTarget.start();
         
         connSource.start();
         
         final int NUM_MESSAGES = 50;
         
         Session sessSource = connSource.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageProducer prod = sessSource.createProducer(sourceQueue);
         
         Session sessTarget = connTarget.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageConsumer cons = sessTarget.createConsumer(targetQueue);
         
         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sessSource.createTextMessage("message" + i);
            
            prod.send(tm);
         }
         
         //It's stopped so no messages should be received
         
         checkEmpty(targetQueue, 1);
         
         //Start it
         
         log.info("Starting bridge");
         ServerManagement.getServer(0).invoke(on, "start", new Object[0], new String[0]);
         log.info("Started bridge");
         
         //Now should receive the messages
                  
         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons.receive(2000);
            
            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }
         
         checkEmpty(targetQueue, 1);
                  
         //Send some more
         
         for (int i = NUM_MESSAGES; i < 2 * NUM_MESSAGES; i++)
         {
            TextMessage tm = sessSource.createTextMessage("message" + i);
            
            prod.send(tm);
         }
         
         //These should be received too
         
         for (int i = NUM_MESSAGES; i < 2 * NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons.receive(2000);
            
            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }
         
         checkEmpty(targetQueue, 1);
         
         //Pause it
         
         ServerManagement.getServer(0).invoke(on, "pause", new Object[0], new String[0]);
         
         boolean isPaused = ((Boolean)ServerManagement.getAttribute(on, "Paused")).booleanValue();
         
         assertTrue(isPaused);
         
         // Send some more
         
         for (int i = 2 * NUM_MESSAGES; i < 3 * NUM_MESSAGES; i++)
         {
            TextMessage tm = sessSource.createTextMessage("message" + i);
            
            prod.send(tm);
         }
         
         //These shouldn't be received
         
         checkEmpty(targetQueue, 1);
         
         // Resume
         
         ServerManagement.getServer(0).invoke(on, "resume", new Object[0], new String[0]);
         
         //Now messages should be received
         
         for (int i = 2 * NUM_MESSAGES; i < 3 * NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons.receive(2000);
            
            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }
         
         checkEmpty(targetQueue, 1);
         
         isPaused = ((Boolean)ServerManagement.getAttribute(on, "Paused")).booleanValue();
         
         assertFalse(isPaused);
         
         //Stop
         
         ServerManagement.getServer(0).invoke(on, "stop", new Object[0], new String[0]);
         
         boolean isStarted = ((Boolean)ServerManagement.getAttribute(on, "Started")).booleanValue();
         
         assertFalse(isStarted); 
      }
      finally
      {         
         if (connSource != null)
         {
            connSource.close();
         }
         
         if (connTarget != null)
         {
            connTarget.close();
         }
         
         try
         {
            if (on != null)
            {
               ServerManagement.getServer(0).invoke(on, "stop", new Object[0], new String[0]);
               ServerManagement.getServer(0).invoke(on, "destroy", new Object[0], new String[0]);
            }
         }
         catch(Exception e)
         {
            //Ignore            
         }
      }

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
         
         cff1 = new JNDIConnectionFactoryFactory(props1, "/ConnectionFactory2");
               
         ic0 = new InitialContext(props0);
         
         ic1 = new InitialContext(props1);
         
         cf0 = (ConnectionFactory)ic0.lookup("/ConnectionFactory");
         
         cf1 = (ConnectionFactory)ic1.lookup("/ConnectionFactory2");
         
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
   
   private ObjectName deployBridge(int server, String bridgeName,
            ObjectName sourceProviderLoader, ObjectName targetProviderLoader,
            String sourceDestLookup, String targetDestLookup,
            String sourceUsername, String sourcePassword,
            String targetUsername, String targetPassword,
            int qos, String selector, int maxBatchSize,
            long maxBatchTime, String subName, String clientID,
            long failureRetryInterval, int maxRetries, boolean addMessageIDInHeader) throws Exception
   {
      String config = 
         "<mbean code=\"org.jboss.jms.server.bridge.BridgeService\" " +
         "name=\"jboss.messaging:service=Bridge,name=" + bridgeName + "\" " +
         "xmbean-dd=\"xmdesc/Bridge-xmbean.xml\">" +      
         "<attribute name=\"SourceProviderLoader\">" + sourceProviderLoader + "</attribute>"+      
         "<attribute name=\"TargetProviderLoader\">" + targetProviderLoader + "</attribute>"+     
         "<attribute name=\"SourceDestinationLookup\">" + sourceDestLookup + "</attribute>"+     
         "<attribute name=\"TargetDestinationLookup\">" + targetDestLookup + "</attribute>";
      if (sourceUsername != null)
      {
         config += "<attribute name=\"SourceUsername\">" + sourceUsername + "</attribute>";
      }
      if (sourcePassword != null)
      {
         config += "<attribute name=\"SourcePassword\">" + sourcePassword +"</attribute>";
      }
      if (targetUsername != null)
      {
         config +=  "<attribute name=\"TargetUsername\">" + targetUsername +"</attribute>";
      }
      if (targetPassword != null)
      {
         config += "<attribute name=\"TargetPassword\">" + targetPassword + "</attribute>";
      }
      config += "<attribute name=\"QualityOfServiceMode\">" + qos +"</attribute>";
      if (selector != null)
      {
         config += "<attribute name=\"Selector\">" + selector + "</attribute>";
      }
      config += "<attribute name=\"MaxBatchSize\">" + maxBatchSize + "</attribute>"+           
      "<attribute name=\"MaxBatchTime\">" + maxBatchTime +"</attribute>";
      if (subName != null)
      {
         config += "<attribute name=\"SubName\">" + subName + "</attribute>";
      }
      if (clientID != null)
      {
         config += "<attribute name=\"ClientID\">" + clientID + "</attribute>";
      }
      config += "<attribute name=\"FailureRetryInterval\">" + failureRetryInterval + "</attribute>";    
      
      config += "<attribute name=\"MaxRetries\">" + maxRetries +"</attribute>";
      
      config += "<attribute name=\"AddMessageIDInHeader\">" + addMessageIDInHeader + "</attribute>";
      config += "</mbean>";
      
      return ServerManagement.getServer(server).deploy(config);            
   }
   
   private void installJMSProviderLoader(int server, Properties props, String factoryRef, String name)
      throws Exception
   {
      ByteArrayOutputStream boa = new ByteArrayOutputStream();
      props.store(boa, "");
      String propsString =  new String(boa.toByteArray());

      String config =
         "<mbean code=\"org.jboss.jms.jndi.JMSProviderLoader\"" + 
         " name=\"jboss.messaging:service=JMSProviderLoader,name=" + name + "\">" +
         "<attribute name=\"ProviderName\">" + name + "</attribute>" +
         "<attribute name=\"ProviderAdapterClass\">org.jboss.jms.jndi.JNDIProviderAdapter</attribute>" +
         "<attribute name=\"FactoryRef\">" + factoryRef + "</attribute>" +
         "<attribute name=\"QueueFactoryRef\">" + factoryRef + "</attribute>" +
         "<attribute name=\"TopicFactoryRef\">" + factoryRef + "</attribute>" +
         "<attribute name=\"Properties\">" + propsString + "</attribute></mbean>";
      
      log.info("Installing bridge: " + config);

      ServerManagement.getServer(0).deploy(config);
   }

}
