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
package org.jboss.test.messaging.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.management.ObjectName;

import org.jboss.jms.client.JBossConnectionFactory;
import org.jboss.jms.client.JBossMessageProducer;
import org.jboss.jms.client.delegate.ClientConnectionFactoryDelegate;
import org.jboss.jms.message.JBossMessage;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.container.ServiceContainer;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 * @version <tt>$Revision: 6821 $</tt>
 *
 * $Id: ConnectionFactoryTest.java 6821 2009-05-16 13:23:49Z gaohoward $
 */
public class ConnectionFactoryTest extends JMSTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public ConnectionFactoryTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   /**
    * Test that ConnectionFactory can be cast to QueueConnectionFactory and QueueConnection can be
    * created.
    */
   public void testQueueConnectionFactory() throws Exception
   {
      QueueConnectionFactory qcf = (QueueConnectionFactory)ic.lookup("/ConnectionFactory");
      QueueConnection qc = qcf.createQueueConnection();
      qc.close();
   }

   /**
    * Test that ConnectionFactory can be cast to TopicConnectionFactory and TopicConnection can be
    * created.
    */
   public void testTopicConnectionFactory() throws Exception
   {
      TopicConnectionFactory qcf = (TopicConnectionFactory)ic.lookup("/ConnectionFactory");
      TopicConnection tc = qcf.createTopicConnection();
      tc.close();
   }

   public void testAdministrativelyConfiguredClientID() throws Exception
   {
      // deploy a connection factory that has an administatively configured clientID

      String mbeanConfig = "<mbean code=\"org.jboss.jms.server.connectionfactory.ConnectionFactory\"\n" + "       name=\"jboss.messaging.connectionfactory:service=TestConnectionFactory\"\n"
                           + "       xmbean-dd=\"xmdesc/ConnectionFactory-xmbean.xml\">\n"
                           + "       <constructor>\n"
                           + "           <arg type=\"java.lang.String\" value=\"sofiavergara\"/>\n"
                           + "       </constructor>\n"
                           + "       <depends optional-attribute-name=\"ServerPeer\">jboss.messaging:service=ServerPeer</depends>\n"
                           + "       <depends optional-attribute-name=\"Connector\">jboss.messaging:service=Connector,transport=bisocket</depends>\n"
                           + "       <attribute name=\"JNDIBindings\">\n"
                           + "          <bindings>\n"
                           + "            <binding>/TestConnectionFactory</binding>\n"
                           + "          </bindings>\n"
                           + "       </attribute>\n"
                           + " </mbean>";

      ObjectName on = ServerManagement.deploy(mbeanConfig);
      ServerManagement.invoke(on, "create", new Object[0], new String[0]);
      ServerManagement.invoke(on, "start", new Object[0], new String[0]);

      ConnectionFactory cf = (ConnectionFactory)ic.lookup("/TestConnectionFactory");
      Connection c = cf.createConnection();

      assertEquals("sofiavergara", c.getClientID());

      try
      {
         c.setClientID("somethingelse");
         fail("should throw exception");

      }
      catch (javax.jms.IllegalStateException e)
      {
         // OK
      }

      // Now try and deploy another one with the same client id

      mbeanConfig = "<mbean code=\"org.jboss.jms.server.connectionfactory.ConnectionFactory\"\n" + "       name=\"jboss.messaging.connectionfactory:service=TestConnectionFactory2\"\n"
                    + "       xmbean-dd=\"xmdesc/ConnectionFactory-xmbean.xml\">\n"
                    + "       <constructor>\n"
                    + "           <arg type=\"java.lang.String\" value=\"sofiavergara\"/>\n"
                    + "       </constructor>\n"
                    + "       <depends optional-attribute-name=\"ServerPeer\">jboss.messaging:service=ServerPeer</depends>\n"
                    + "       <depends optional-attribute-name=\"Connector\">jboss.messaging:service=Connector,transport=bisocket</depends>\n"
                    + "       <attribute name=\"JNDIBindings\">\n"
                    + "          <bindings>\n"
                    + "            <binding>/TestConnectionFactory2</binding>\n"
                    + "          </bindings>\n"
                    + "       </attribute>\n"
                    + " </mbean>";

      ObjectName on2 = ServerManagement.deploy(mbeanConfig);
      ServerManagement.invoke(on2, "create", new Object[0], new String[0]);
      ServerManagement.invoke(on2, "start", new Object[0], new String[0]);

      ServerManagement.invoke(on2, "stop", new Object[0], new String[0]);
      ServerManagement.invoke(on2, "destroy", new Object[0], new String[0]);
      ServerManagement.undeploy(on2);

      cf = (ConnectionFactory)ic.lookup("/TestConnectionFactory");
      Connection c2 = null;
      try
      {
         c2 = cf.createConnection();
      }
      catch (JMSException e)
      {
         // Ok
      }

      if (c2 != null)
         c2.close();

      c.close();

      ServerManagement.invoke(on, "stop", new Object[0], new String[0]);
      ServerManagement.invoke(on, "destroy", new Object[0], new String[0]);
      ServerManagement.undeploy(on);
   }

   public void testAdministrativelyConfiguredConnectors() throws Exception
   {
      // Deploy a few connectors
      String name1 = "jboss.messaging:service=Connector1,transport=bisocket";

      String name2 = "jboss.messaging:service=Connector2,transport=bisocket";

      String name3 = "jboss.messaging:service=Connector3,transport=bisocket";

      ObjectName c1 = deployConnector(1234, name1);
      ObjectName c2 = deployConnector(1235, name2);
      ObjectName c3 = deployConnector(1236, name3);

      ObjectName cf1 = deployConnectionFactory("jboss.messaging.destination:service=TestConnectionFactory1",
                                               name1,
                                               "/TestConnectionFactory1",
                                               "clientid1",
                                               false);
      ObjectName cf2 = deployConnectionFactory("jboss.messaging.destination:service=TestConnectionFactory2",
                                               name2,
                                               "/TestConnectionFactory2",
                                               "clientid2",
                                               false);
      ObjectName cf3 = deployConnectionFactory("jboss.messaging.destination:service=TestConnectionFactory3",
                                               name3,
                                               "/TestConnectionFactory3",
                                               "clientid3",
                                               false);
      // Last one shares the same connector
      ObjectName cf4 = deployConnectionFactory("jboss.messaging.destination:service=TestConnectionFactory4",
                                               name3,
                                               "/TestConnectionFactory4",
                                               "clientid4",
                                               false);

      JBossConnectionFactory f1 = (JBossConnectionFactory)ic.lookup("/TestConnectionFactory1");
      ClientConnectionFactoryDelegate del1 = (ClientConnectionFactoryDelegate)f1.getDelegate();

      assertTrue(del1.getServerLocatorURI().startsWith("bisocket://localhost:1234"));

      JBossConnectionFactory f2 = (JBossConnectionFactory)ic.lookup("/TestConnectionFactory2");
      ClientConnectionFactoryDelegate del2 = (ClientConnectionFactoryDelegate)f2.getDelegate();
      assertTrue(del2.getServerLocatorURI().startsWith("bisocket://localhost:1235"));

      JBossConnectionFactory f3 = (JBossConnectionFactory)ic.lookup("/TestConnectionFactory3");
      ClientConnectionFactoryDelegate del3 = (ClientConnectionFactoryDelegate)f3.getDelegate();
      assertTrue(del3.getServerLocatorURI().startsWith("bisocket://localhost:1236"));

      JBossConnectionFactory f4 = (JBossConnectionFactory)ic.lookup("/TestConnectionFactory4");
      ClientConnectionFactoryDelegate del4 = (ClientConnectionFactoryDelegate)f4.getDelegate();
      assertTrue(del4.getServerLocatorURI().startsWith("bisocket://localhost:1236"));

      Connection con1 = f1.createConnection();
      Connection con2 = f2.createConnection();
      Connection con3 = f3.createConnection();
      Connection con4 = f4.createConnection();
      con1.close();
      con2.close();
      con3.close();
      con4.close();

      stopService(cf1);
      stopService(cf2);
      stopService(cf3);

      // Check f4 is still ok
      Connection conn5 = f4.createConnection();
      conn5.close();

      stopService(cf4);

      stopService(c1);
      stopService(c2);
      stopService(c3);
   }

   public void testNoClientIDConfigured_1() throws Exception
   {
      // the ConnectionFactories that ship with Messaging do not have their clientID
      // administratively configured.

      ConnectionFactory cf = (ConnectionFactory)ic.lookup("/ConnectionFactory");
      Connection c = cf.createConnection();

      assertNull(c.getClientID());

      c.close();
   }

   public void testNoClientIDConfigured_2() throws Exception
   {
      // the ConnectionFactories that ship with Messaging do not have their clientID
      // administratively configured.

      ConnectionFactory cf = (ConnectionFactory)ic.lookup("/ConnectionFactory");
      Connection c = cf.createConnection();

      // set the client id immediately after the connection is created

      c.setClientID("sofiavergara2");
      assertEquals("sofiavergara2", c.getClientID());

      c.close();
   }

   // Added for http://jira.jboss.org/jira/browse/JBMESSAGING-939
   public void testDurableSubscriptionOnPreConfiguredConnectionFactory() throws Exception
   {
      ObjectName cf1 = deployConnectionFactory("jboss.messaging.destination:service=TestConnectionFactory1",
                                               ServiceContainer.REMOTING_OBJECT_NAME.getCanonicalName(),
                                               "/TestDurableCF",
                                               "cfTest",
                                               false);

      ServerManagement.deployTopic("TestSubscriber");

      Connection conn = null;

      try
      {
         Topic topic = (Topic)ic.lookup("/topic/TestSubscriber");
         ConnectionFactory cf = (ConnectionFactory)ic.lookup("/TestDurableCF");
         conn = cf.createConnection();

         // I have to remove this asertion, as the test would work if doing this assertion
         // as getClientID performed some operation that cleared the bug condition during
         // the creation of this testcase
         // assertEquals("cfTest", conn.getClientID());

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         session.createDurableSubscriber(topic, "durableSubscriberChangeSelectorTest", "TEST = 'test'", false);
      }
      finally
      {
         try
         {
            if (conn != null)
            {
               conn.close();
            }
         }
         catch (Exception e)
         {
            log.warn(e.toString(), e);
         }

         try
         {
            stopService(cf1);
         }
         catch (Exception e)
         {
            log.warn(e.toString(), e);
         }

         try
         {
            ServerManagement.destroyTopic("TestSubscriber");
         }
         catch (Exception e)
         {
            log.warn(e.toString(), e);
         }

      }

   }

   public void testSlowConsumers() throws Exception
   {
      ObjectName cf1 = deployConnectionFactory("jboss.messaging.destination:service=TestConnectionFactorySlowConsumers",
                                               ServiceContainer.REMOTING_OBJECT_NAME.getCanonicalName(),
                                               "/TestSlowConsumersCF",
                                               null,
                                               true);

      Connection conn = null;

      try
      {
         ConnectionFactory cf = (ConnectionFactory)ic.lookup("/TestSlowConsumersCF");

         conn = cf.createConnection();

         Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final Object waitLock = new Object();

         final int numMessages = 500;

         class FastListener implements MessageListener
         {
            volatile int processed;

            public void onMessage(Message msg)
            {
               processed++;

               TextMessage tm = (TextMessage)msg;

               try
               {
                  log.info("Fast listener got message " + tm.getText());
               }
               catch (JMSException e)
               {
               }

               if (processed == numMessages - 2)
               {
                  synchronized (waitLock)
                  {
                     log.info("Notifying");
                     waitLock.notifyAll();
                  }
               }
            }
         }

         final FastListener fast = new FastListener();

         class SlowListener implements MessageListener
         {
            volatile int processed;

            public void onMessage(Message msg)
            {
               TextMessage tm = (TextMessage)msg;

               processed++;

               try
               {
                  log.info("Slow listener got message " + tm.getText());
               }
               catch (JMSException e)
               {
               }

               synchronized (waitLock)
               {
                  // Should really cope with spurious wakeups
                  while (fast.processed != numMessages - 2)
                  {
                     log.info("Waiting");
                     try
                     {
                        waitLock.wait(20000);
                     }
                     catch (InterruptedException e)
                     {
                     }
                     log.info("Waited");
                  }

                  waitLock.notify();
               }
            }
         }

         final SlowListener slow = new SlowListener();

         MessageConsumer cons1 = session1.createConsumer(queue1);

         cons1.setMessageListener(slow);

         MessageConsumer cons2 = session2.createConsumer(queue1);

         cons2.setMessageListener(fast);

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSend.createProducer(queue1);

         conn.start();

         for (int i = 0; i < numMessages; i++)
         {
            TextMessage tm = sessSend.createTextMessage("message" + i);

            prod.send(tm);
         }

         // All the messages bar one should be consumed by the fast listener - since the slow listener shouldn't buffer
         // any.

         synchronized (waitLock)
         {
            // Should really cope with spurious wakeups
            while (fast.processed != numMessages - 2)
            {
               log.info("Waiting");
               waitLock.wait(20000);
               log.info("Waited");
            }

            while (slow.processed != 2)
            {
               log.info("Waiting");
               waitLock.wait(20000);
               log.info("Waited");
            }
         }

         assertTrue(fast.processed == numMessages - 2);

         // Thread.sleep(10000);

      }
      finally
      {
         try
         {
            if (conn != null)
            {
               log.info("Closing connection");
               conn.close();
               log.info("Closed connection");
            }
         }
         catch (Exception e)
         {
            log.warn(e.toString(), e);
         }

         try
         {
            stopService(cf1);
         }
         catch (Exception e)
         {
            log.warn(e.toString(), e);
         }

      }

   }

   public void testTwoSerialSlowConsumers() throws Exception
   {
      ObjectName cf1 = deployConnectionFactory("jboss.messaging.destination:service=TestConnectionFactorySlowConsumers",
                                               ServiceContainer.REMOTING_OBJECT_NAME.getCanonicalName(),
                                               "/TestSlowConsumersCF",
                                               null,
                                               true);

      Connection conn = null;

      Session session1 = null;
      MessageConsumer cons1 = null;
      
      Session session2 = null;
      MessageConsumer cons2 = null;
      
      try
      {
         ConnectionFactory cf = (ConnectionFactory)ic.lookup("/TestSlowConsumersCF");

         conn = cf.createConnection();

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = sessSend.createProducer(queue1);

         for (int i = 1; i <= 2; i++)
         {
            TextMessage tm = sessSend.createTextMessage("message " + i);
            prod.send(tm);
         }

         conn.start();

         session1 = conn.createSession(true, Session.AUTO_ACKNOWLEDGE);
         cons1 = session1.createConsumer(queue1);

         
         TextMessage tm = (TextMessage)cons1.receive(1000);
         assertNotNull(tm);
         assertEquals("message 1", tm.getText());
         
         // a long running process should close its consumer to avoid message caching on the client
         // This close can be removed when we change the distribution policy on slow consumers
         cons1.close();
         cons1 = null;

         session2 = conn.createSession(true, Session.AUTO_ACKNOWLEDGE);
         cons2 = session2.createConsumer(queue1);

         tm = (TextMessage)cons2.receive(1000);
         assertNotNull(tm);
         assertEquals("message 2", tm.getText());


      }
      finally
      {
         if (session1 != null && session2 != null)
         {
            session1.commit();
            session2.commit();
            
            if (checkNoMessageData())
            {
               if (cons1 != null)
               {
                  while (cons1.receive(100) != null);
               }
               if (cons2 != null)
               {
                  while (cons2.receive(100) != null);
               }

               session1.commit();
               session2.commit();
            }
            
         }

         try
         {
            if (conn != null)
            {
               log.info("Closing connection");
               conn.close();
               log.info("Closed connection");
            }
         }
         catch (Exception e)
         {
            log.warn(e.toString(), e);
         }

         try
         {
            stopService(cf1);
         }
         catch (Exception e)
         {
            log.warn(e.toString(), e);
         }

      }

   }

   /**
    * This is test the configuration of ordering group on connection factories
    * First it deployed a connection factory with ordering group disabled, then use the factory to 
    * send messages. then check the message at the receiving end to make sure the 
    * configuration really take effect.
    * 
    * Also it tests the connection factory with the ordering group related parameters absent.
    * in which case the effect should be same as the above.
    */
   public void testAdministrativelyConfiguredNoOrderingGroup() throws Exception
   {
      Connection c = null;
      Connection c2 = null;

      try
      {
         String mbeanConfig = "<mbean code=\"org.jboss.jms.server.connectionfactory.ConnectionFactory\"\n" + "       name=\"jboss.messaging.connectionfactory:service=TestNoOrderingGroupConnectionFactory\"\n"
                              + "       xmbean-dd=\"xmdesc/ConnectionFactory-xmbean.xml\">\n"
                              + "       <depends optional-attribute-name=\"ServerPeer\">jboss.messaging:service=ServerPeer</depends>\n"
                              + "       <depends optional-attribute-name=\"Connector\">jboss.messaging:service=Connector,transport=bisocket</depends>\n"
                              + "       <attribute name=\"JNDIBindings\">\n"
                              + "          <bindings>\n"
                              + "            <binding>/TestNoOrderingGroupConnectionFactory</binding>\n"
                              + "          </bindings>\n"
                              + "       </attribute>\n"
                              + "       <attribute name=\"EnableOrderingGroup\">false</attribute>\n"
                              + "       <attribute name=\"DefaultOrderingGroupName\">MyOrderingGroup</attribute>\n"
                              + " </mbean>";

         ObjectName on = ServerManagement.deploy(mbeanConfig);
         ServerManagement.invoke(on, "create", new Object[0], new String[0]);
         ServerManagement.invoke(on, "start", new Object[0], new String[0]);

         ConnectionFactory cf = (ConnectionFactory)ic.lookup("/TestNoOrderingGroupConnectionFactory");
         c = cf.createConnection();

         Session session = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = session.createProducer(queue1);

         final int NUM_MSG = 1000;

         for (int i = 1; i < NUM_MSG; i++)
         {
            TextMessage tm = session.createTextMessage("message " + i);
            prod.send(tm);
         }

         c.start();

         MessageConsumer consmr = session.createConsumer(queue1);

         for (int i = 1; i < NUM_MSG; i++)
         {
            TextMessage rm = (TextMessage)consmr.receive(500);
            assertNotNull(rm);
            // each message should not have the header.
            String orderingGroupName = rm.getStringProperty(JBossMessage.JBOSS_MESSAGING_ORDERING_GROUP_ID);
            assertNull(orderingGroupName);
         }

         session.close();

         String mbeanConfig2 = "<mbean code=\"org.jboss.jms.server.connectionfactory.ConnectionFactory\"\n" + "       name=\"jboss.messaging.connectionfactory:service=TestNoOrderingGroupConnectionFactory2\"\n"
                               + "       xmbean-dd=\"xmdesc/ConnectionFactory-xmbean.xml\">\n"
                               + "       <depends optional-attribute-name=\"ServerPeer\">jboss.messaging:service=ServerPeer</depends>\n"
                               + "       <depends optional-attribute-name=\"Connector\">jboss.messaging:service=Connector,transport=bisocket</depends>\n"
                               + "       <attribute name=\"JNDIBindings\">\n"
                               + "          <bindings>\n"
                               + "            <binding>/TestNoOrderingGroupConnectionFactory2</binding>\n"
                               + "          </bindings>\n"
                               + "       </attribute>\n"
                               + " </mbean>";

         ObjectName on2 = ServerManagement.deploy(mbeanConfig2);
         ServerManagement.invoke(on2, "create", new Object[0], new String[0]);
         ServerManagement.invoke(on2, "start", new Object[0], new String[0]);

         ConnectionFactory cf2 = (ConnectionFactory)ic.lookup("/TestNoOrderingGroupConnectionFactory2");
         c2 = cf2.createConnection();

         Session session2 = c2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod2 = session2.createProducer(queue1);

         for (int i = 1; i < NUM_MSG; i++)
         {
            TextMessage tm = session2.createTextMessage("message " + i);
            prod2.send(tm);
         }

         c2.start();

         MessageConsumer consmr2 = session2.createConsumer(queue1);

         for (int i = 1; i < NUM_MSG; i++)
         {
            TextMessage rm = (TextMessage)consmr2.receive(500);
            assertNotNull(rm);
            // each message should not have the header.
            String orderingGroupName = rm.getStringProperty(JBossMessage.JBOSS_MESSAGING_ORDERING_GROUP_ID);
            assertNull(orderingGroupName);
         }

         ServerManagement.invoke(on, "stop", new Object[0], new String[0]);
         ServerManagement.invoke(on, "destroy", new Object[0], new String[0]);
         ServerManagement.undeploy(on);

         ServerManagement.invoke(on2, "stop", new Object[0], new String[0]);
         ServerManagement.invoke(on2, "destroy", new Object[0], new String[0]);
         ServerManagement.undeploy(on2);
      }
      finally
      {
         try
         {
            if (c != null)
            {
               log.info("Closing connection");
               c.close();
               log.info("Closed connection");
            }
            if (c2 != null)
            {
               log.info("Closing connection");
               c2.close();
               log.info("Closed connection");
            }
         }
         catch (Exception e)
         {
            log.warn(e.toString(), e);
         }
      }
   }

   
   /**
    * This is test the configuration of ordering group on connection factories
    * First it deployed a ordering-group configured factory, then use the factory to 
    * send messages. then check the message at the receiving end to make sure the 
    * configuration really take effect.
    */
   public void testAdministrativelyConfiguredOrderingGroup() throws Exception
   {
      Connection c = null;
      
      try
      {
         String mbeanConfig = "<mbean code=\"org.jboss.jms.server.connectionfactory.ConnectionFactory\"\n" + "       name=\"jboss.messaging.connectionfactory:service=TestOrderingGroupConnectionFactory\"\n"
         + "       xmbean-dd=\"xmdesc/ConnectionFactory-xmbean.xml\">\n"
         + "       <depends optional-attribute-name=\"ServerPeer\">jboss.messaging:service=ServerPeer</depends>\n"
         + "       <depends optional-attribute-name=\"Connector\">jboss.messaging:service=Connector,transport=bisocket</depends>\n"
         + "       <attribute name=\"JNDIBindings\">\n"
         + "          <bindings>\n"
         + "            <binding>/TestOrderingGroupConnectionFactory</binding>\n"
         + "          </bindings>\n"
         + "       </attribute>\n"
         + "       <attribute name=\"EnableOrderingGroup\">true</attribute>\n"
         + "       <attribute name=\"DefaultOrderingGroupName\">MyOrderingGroup2</attribute>\n"
         + " </mbean>";

         ObjectName on = ServerManagement.deploy(mbeanConfig);
         ServerManagement.invoke(on, "create", new Object[0], new String[0]);
         ServerManagement.invoke(on, "start", new Object[0], new String[0]);

         ConnectionFactory cf = (ConnectionFactory)ic.lookup("/TestOrderingGroupConnectionFactory");
         c = cf.createConnection();

         Session session = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = session.createProducer(queue1);

         final int NUM_MSG = 1000;

         for (int i = 1; i < NUM_MSG; i++)
         {
            TextMessage tm = session.createTextMessage("message " + i);
            prod.send(tm);
         }

         c.start();

         MessageConsumer consmr = session.createConsumer(queue1);

         for (int i = 1; i < NUM_MSG; i++)
         {
            TextMessage rm = (TextMessage)consmr.receive(500);
            assertNotNull(rm);
            // each message should have the header.
            String orderingGroupName = rm.getStringProperty(JBossMessage.JBOSS_MESSAGING_ORDERING_GROUP_ID);
            assertNotNull(orderingGroupName);
            assertEquals("MyOrderingGroup2", orderingGroupName);
         }
         
         //this tests that factory defaults can be overridden.
         JBossMessageProducer jProd = (JBossMessageProducer)prod;
         jProd.enableOrderingGroup("MyAnotherOrderingGroup");

         for (int i = 1; i < NUM_MSG; i++)
         {
            TextMessage tm = session.createTextMessage("message " + i);
            jProd.send(tm);
         }

         for (int i = 1; i < NUM_MSG; i++)
         {
            TextMessage rm = (TextMessage)consmr.receive(500);
            assertNotNull(rm);
            // each message should have the header.
            String orderingGroupName = rm.getStringProperty(JBossMessage.JBOSS_MESSAGING_ORDERING_GROUP_ID);
            assertNotNull(orderingGroupName);
            assertEquals("MyAnotherOrderingGroup", orderingGroupName);
         }

         session.close();
         ServerManagement.invoke(on, "stop", new Object[0], new String[0]);
         ServerManagement.invoke(on, "destroy", new Object[0], new String[0]);
         ServerManagement.undeploy(on);
      }
      finally
      {
         try
         {
            if (c != null)
            {
               log.info("Closing connection");
               c.close();
               log.info("Closed connection");
            }
         }
         catch (Exception e)
         {
            log.warn(e.toString(), e);
         }
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   private ObjectName deployConnector(int port, String name) throws Exception
   {
      String mbeanConfig = "<mbean code=\"org.jboss.remoting.transport.Connector\"\n" + " name=\"" +
                           name +
                           "\"\n" +
                           " display-name=\"BiSocket transport Connector\">\n" +
                           "</mbean>";

      String config = "<attribute name=\"Configuration\">\n" + "<config>" +
                      "<invoker transport=\"bisocket\">" +

                      "<attribute name=\"marshaller\" isParam=\"true\">org.jboss.jms.wireformat.JMSWireFormat</attribute>" +
                      "<attribute name=\"unmarshaller\" isParam=\"true\">org.jboss.jms.wireformat.JMSWireFormat</attribute>" +
                      "<attribute name=\"dataType\" isParam=\"true\">jms</attribute>" +
                      "<attribute name=\"socket.check_connection\" isParam=\"true\">false</attribute>" +
                      "<attribute name=\"timeout\" isParam=\"true\">0</attribute>" +
                      "<attribute name=\"serverBindAddress\">localhost</attribute>" +
                      "<attribute name=\"serverBindPort\">" +
                      port +
                      "</attribute>" +
                      "<attribute name=\"clientSocketClass\" isParam=\"true\">org.jboss.jms.client.remoting.ClientSocketWrapper</attribute>" +
                      "<attribute name=\"serverSocketClass\" isParam=\"true\">org.jboss.jms.server.remoting.ServerSocketWrapper</attribute>" +
                      "<attribute name=\"numberOfCallRetries\" isParam=\"true\">1</attribute>" +
                      "<attribute name=\"pingFrequency\" isParam=\"true\">214748364</attribute>" +
                      "<attribute name=\"pingWindowFactor\" isParam=\"true\">10</attribute>" +
                      "<attribute name=\"onewayThreadPool\">org.jboss.jms.server.remoting.DirectThreadPool</attribute>" +

                      "<attribute name=\"clientLeasePeriod\" isParam=\"true\">10000</attribute>" +

                      "<attribute name=\"numberOfRetries\" isParam=\"true\">10</attribute>" +
                      "<attribute name=\"JBM_clientMaxPoolSize\" isParam=\"true\">200</attribute>" +

                      "</invoker>" +
                      "<handlers>" +
                      "<handler subsystem=\"JMS\">org.jboss.jms.server.remoting.JMSServerInvocationHandler</handler>" +
                      "</handlers>" +
                      "</config>" +
                      "</attribute>\n";

      ObjectName on = ServerManagement.deploy(mbeanConfig);

      ServerManagement.setAttribute(on, "Configuration", config);

      ServerManagement.invoke(on, "create", new Object[0], new String[0]);

      ServerManagement.invoke(on, "start", new Object[0], new String[0]);

      return on;
   }

   private ObjectName deployConnectionFactory(String name,
                                              String connectorName,
                                              String binding,
                                              String clientID,
                                              boolean slowConsumers) throws Exception
   {
      String mbeanConfig = "<mbean code=\"org.jboss.jms.server.connectionfactory.ConnectionFactory\"\n" + "       name=\"" + name +
                           "\"\n" +
                           "       xmbean-dd=\"xmdesc/ConnectionFactory-xmbean.xml\">\n" +
                           "       <constructor>\n" +
                           "           <arg type=\"java.lang.String\" value=\"" +
                           clientID +
                           "\"/>\n" +
                           "       </constructor>\n" +
                           "       <depends optional-attribute-name=\"ServerPeer\">jboss.messaging:service=ServerPeer</depends>\n" +
                           "       <depends optional-attribute-name=\"Connector\">" +
                           connectorName +
                           "</depends>\n" +
                           "       <attribute name=\"JNDIBindings\">\n" +
                           "          <bindings>\n" +
                           "            <binding>" +
                           binding +
                           " </binding>\n" +
                           "          </bindings>\n" +
                           "       </attribute>\n" +
                           "       <attribute name=\"SlowConsumers\">" +
                           slowConsumers +
                           "</attribute>\n" +
                           " </mbean>";

      ObjectName on = ServerManagement.deploy(mbeanConfig);
      ServerManagement.invoke(on, "create", new Object[0], new String[0]);
      ServerManagement.invoke(on, "start", new Object[0], new String[0]);

      return on;
   }

   private void stopService(ObjectName on) throws Exception
   {
      ServerManagement.invoke(on, "stop", new Object[0], new String[0]);
      ServerManagement.invoke(on, "destroy", new Object[0], new String[0]);
      ServerManagement.undeploy(on);
   }

   // Inner classes -------------------------------------------------

}
