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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.management.ObjectName;
import javax.naming.NamingException;

import org.jboss.jms.server.destination.SubscriptionInfo;
import org.jboss.test.messaging.tools.ServerManagement;


/**
 * Tests focused on durable subscription behavior. More durable subscription tests can be found in
 * MessageConsumerTest.
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 * $Id: DurableSubscriberTest.java 1319 2006-09-19 17:17:53Z ovidiu.feodorov@jboss.com $
 */
public class DurableSubscriptionTest extends JMSTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public DurableSubscriptionTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   public void testSimplestDurableSubscription() throws Exception
   {
      Connection conn = null;
      
      try
      {      
	      conn = cf.createConnection();
	
	      conn.setClientID("brookeburke");
	
	      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	      MessageProducer prod = s.createProducer(topic1);
	      prod.setDeliveryMode(DeliveryMode.PERSISTENT);
	
	      s.createDurableSubscriber(topic1, "monicabelucci");
	
	      ObjectName destObjectName =
	         new ObjectName("jboss.messaging.destination:service=Topic,name=Topic1");
	      List subs = (List)ServerManagement.invoke(destObjectName, "listAllSubscriptions", null, null);
	      
	      assertNotNull(subs);
	      
	      assertEquals(1, subs.size());
	      
	      SubscriptionInfo info = (SubscriptionInfo)subs.get(0);
	
	      assertEquals("monicabelucci", info.getName());
	
	      prod.send(s.createTextMessage("k"));
	
	      conn.close();
	
	      subs = (List)ServerManagement.invoke(destObjectName, "listAllSubscriptions", null, null);
	
	      assertEquals(1, subs.size());
	      
	      info = (SubscriptionInfo)subs.get(0);
	
	      assertEquals("monicabelucci", info.getName());
	
	      conn = cf.createConnection();
	      conn.setClientID("brookeburke");
	
	      s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      MessageConsumer durable = s.createDurableSubscriber(topic1, "monicabelucci");
	
	      conn.start();
	
	      TextMessage tm = (TextMessage)durable.receive(1000);
	      assertEquals("k", tm.getText());
	
	      log.info("*** consuming");
	      
	      Message m = durable.receive(1000);
	      assertNull(m);
	      
	      durable.close();
	      
	      s.unsubscribe("monicabelucci");
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      }
   }
      

   /**
    * JMS 1.1 6.11.1: A client can change an existing durable subscription by creating a durable
    * TopicSubscriber with the same name and a new topic and/or message selector, or NoLocal
    * attribute. Changing a durable subscription is equivalent to deleting and recreating it.
    *
    * Test with a different topic (a redeployed topic is a different topic).
    */
   public void testDurableSubscriptionOnNewTopic() throws Exception
   {      
      Connection conn = null;
      
      try
      {	      
	      conn = cf.createConnection();
	
	      conn.setClientID("brookeburke");
	
	      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	      MessageProducer prod = s.createProducer(topic1);
	      prod.setDeliveryMode(DeliveryMode.PERSISTENT);
	
	      s.createDurableSubscriber(topic1, "monicabelucci");
	
	      prod.send(s.createTextMessage("one"));
	
	      conn.close();
	
	      conn = cf.createConnection();
	
	      conn.setClientID("brookeburke");
	
	      s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	      MessageConsumer durable = s.createDurableSubscriber(topic2, "monicabelucci");
	
	      conn.start();
	
	      Message m = durable.receive(1000);
	      assertNull(m);
	      
	      durable.close();
	      
	      s.unsubscribe("monicabelucci");
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      }
   }

   /**
    * JMS 1.1 6.11.1: A client can change an existing durable subscription by creating a durable
    * TopicSubscriber with the same name and a new topic and/or message selector, or NoLocal
    * attribute. Changing a durable subscription is equivalent to deleting and recreating it.
    *
    * Test with a different selector.
    */
   public void testDurableSubscriptionDifferentSelector() throws Exception
   {
      Connection conn = null;
      
      try
      {      
	      conn = cf.createConnection();
	
	      conn.setClientID("brookeburke");
	
	      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	      MessageProducer prod = s.createProducer(topic1);
	      prod.setDeliveryMode(DeliveryMode.PERSISTENT);
	      
	      MessageConsumer durable =
	         s.createDurableSubscriber(topic1,
	                                   "monicabelucci",
	                                   "color = 'red' AND shape = 'square'",
	                                   false);
	
	      TextMessage tm = s.createTextMessage("A red square message");
	      tm.setStringProperty("color", "red");
	      tm.setStringProperty("shape", "square");
	      
	      prod.send(tm);
	
	      conn.start();
	
	      TextMessage rm = (TextMessage)durable.receive(5000);
	      assertEquals("A red square message", rm.getText());
	
	      tm = s.createTextMessage("Another red square message");
	      tm.setStringProperty("color", "red");
	      tm.setStringProperty("shape", "square");
	      prod.send(tm);
	
	      // TODO: when subscriptions/durable subscription will be registered as MBean, use the JMX
	      //       interface to make sure the 'another red square message' is maintained by the
	      //       durable subascription
	      //       http://jira.jboss.org/jira/browse/JBMESSAGING-217
	
	      conn.close();
	
	      conn = cf.createConnection();
	
	      conn.setClientID("brookeburke");
	
	      s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      // modify the selector
	      durable = s.createDurableSubscriber(topic1,
	                                          "monicabelucci",
	                                          "color = 'red'",
	                                          false);
	
	      conn.start();
	
	      Message m = durable.receive(1000);
	
	      // the durable subscription is destroyed and re-created. The red square message stored by
	      // the previous durable subscription is lost and (hopefully) garbage collected.
	      assertNull(m);
	      
	      durable.close();
	      
	      s.unsubscribe("monicabelucci");
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      }
   }

   public void testDurableSubscriptionOnTemporaryTopic() throws Exception
   {      
      Connection conn = null;
      
      conn = cf.createConnection();
      
      try
      {
	      conn.setClientID("doesn't actually matter");
	      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	      Topic temporaryTopic = s.createTemporaryTopic();
	
	      try
	      {
	         s.createDurableSubscriber(temporaryTopic, "mySubscription");
	         fail("this should throw exception");
	      }
	      catch(InvalidDestinationException e)
	      {
	         // OK
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

   /**
    * Topic undeployment/redeployment has an activation/deactivation semantic, so undeploying a
    * topic for which there are durable subscriptions preserves the content of those durable
    * subscriptions, which can be then access upon topic redeployment.
    * @throws Exception
    */
   public void testDurableSubscriptionOnTopicRedeployment() throws Exception
   {
      try
      {
         ic.lookup("/topic/TopicToBeRedeployed");
         fail("should throw exception, topic shouldn't be deployed on the server");
      }
      catch(NamingException e)
      {
         // OK
      }

      ServerManagement.deployTopic("TopicToBeRedeployed");

      Topic topic = (Topic)ic.lookup("/topic/TopicToBeRedeployed");

      Connection conn = null;
      
      try
      {
	      
	      conn = cf.createConnection();
	      conn.setClientID("brookeburke");
	
	      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	      MessageProducer prod = s.createProducer(topic);
	      prod.setDeliveryMode(DeliveryMode.PERSISTENT);
	      MessageConsumer ds = s.createDurableSubscriber(topic, "monicabelucci");
	      conn.start();
	
	      prod.send(s.createTextMessage("one"));
	      prod.send(s.createTextMessage("two"));
	      
	      TextMessage tm = (TextMessage)ds.receive();
	      assertEquals("one", tm.getText());
	      conn.close();
	
	      ServerManagement.undeployTopic("TopicToBeRedeployed");
	      log.debug("topic undeployed");
	
	      try
	      {
	         topic = (Topic)ic.lookup("/topic/TopicToBeRedeployed");
	         fail("should throw exception");
	      }
	      catch(NamingException e)
	      {
	         // OK
	      }
	
	      conn = cf.createConnection();
	      conn.setClientID("brookeburke");
	
	      s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      try
	      {
	         s.createDurableSubscriber(topic, "monicabelucci");
	         fail("should throw exception");
	      }
	      catch(JMSException e)
	      {
	         // OK
	      }
	
	      ServerManagement.deployTopic("TopicToBeRedeployed");
	      log.debug("topic redeployed");
	
	      // since redeployment has an activation semantic, I expect to find the messages there
	
	      topic = (Topic)ic.lookup("/topic/TopicToBeRedeployed");
	      ds =  s.createDurableSubscriber(topic, "monicabelucci");
	      conn.start();
	
	      tm = (TextMessage)ds.receive(1000);
	      assertEquals("two", tm.getText());
	      
	      ds.close();
	      
	      s.unsubscribe("monicabelucci");
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      	ServerManagement.undeployTopic("TopicToBeRedeployed");
      }
   }

   public void testUnsubscribeDurableSubscription() throws Exception
   {
      Connection conn = null;
      
      try
      {	      
	      conn = cf.createConnection();
	      conn.setClientID("ak47");
	
	      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	      MessageConsumer cons = s.createDurableSubscriber(topic1, "uzzi");
	      MessageProducer prod = s.createProducer(topic1);
	      prod.setDeliveryMode(DeliveryMode.PERSISTENT);
	
	      prod.send(s.createTextMessage("one"));
	
	      cons.close();
	      s.unsubscribe("uzzi");	

	      MessageConsumer ds = s.createDurableSubscriber(topic1, "uzzi");
	      conn.start();
	
	      assertNull(ds.receive(1000));
	      
	      ds.close();
	      
	      s.unsubscribe("uzzi");
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      }
   }

   public void testInvalidSelectorException() throws Exception
   {
      Connection c = null;
      
      try
      {
	      
	      c = cf.createConnection();
	      c.setClientID("sofiavergara");
	      Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      try
	      {
	         s.createDurableSubscriber(topic1, "mysubscribption", "=TEST 'test'", true);
	         fail("this should fail");
	      }
	      catch(InvalidSelectorException e)
	      {
	         // OK
	      }
      }
      finally
      {
      	if (c != null)
      	{
      		c.close();
      	}
      }
   }


   //See JMS 1.1. spec sec 6.11
   public void testUnsubscribeWithActiveConsumer() throws Exception
   {
      Connection conn = null;
      
      try
      {
	      
	      conn = cf.createConnection();
	      conn.setClientID("zeke");
	
	      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      TopicSubscriber dursub = s.createDurableSubscriber(topic1, "dursub0");
	
	      try
	      {
	         s.unsubscribe("dursub0");
	         fail();
	      }
	      catch (IllegalStateException e)
	      {
	         //Ok - it is illegal to ubscribe a subscription if it has active consumers
	      }
	         
	      dursub.close();
	      
	      s.unsubscribe("dursub0");
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      }
   }
   
   public void testSubscribeWithActiveSubscription() throws Exception
   {
      Connection conn = null;
      
      try
      {
	      
	      conn = cf.createConnection();
	      conn.setClientID("zeke");
	
	      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      TopicSubscriber dursub1 = s.createDurableSubscriber(topic1, "dursub1");
	
	      try
	      {      
	      	s.createDurableSubscriber(topic1, "dursub1");
	         fail();
	      }
	      catch (IllegalStateException e)
	      {
	         //Ok - it is illegal to have more than one active subscriber on a subscrtiption at any one time
	      }
	         
	      dursub1.close();
	      
	      s.unsubscribe("dursub1");
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      }
   }

   public void testDurableSubscriptionWithPeriodsInName() throws Exception
   {
      Connection conn = null;
      
      try
      {	      
	      conn = cf.createConnection();
	      conn.setClientID(".client.id.with.periods.");
	
	      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      TopicSubscriber subscriber = s.createDurableSubscriber(topic1, ".subscription.name.with.periods.");
	      
	      ServerManagement.undeployTopic("Topic1");
	      ServerManagement.deployTopic("Topic1");
	      
	      topic1 = (Topic)ic.lookup("/topic/Topic1");
	      s.createProducer(topic1).send(s.createTextMessage("Subscription test"));
	      
	      conn.start();
	
	      Message m = subscriber.receive(1000L);
	      
	      assertNotNull(m);
	      assertTrue(m instanceof TextMessage);
	
	      subscriber.close();
	      
	      s.unsubscribe(".subscription.name.with.periods.");
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      }
   }
   
   public void testSameTransaction() throws Exception
   {
      Connection conn = null;
      
      try
      {        
         conn = cf.createConnection();
         conn.setClientID(".testSameTransaction");
         conn.start();
         
         
         // Several Iterations as it can pass eventually
         for (int i=0; i<50; i++)
         {
            log.info("*************************************************************");
            log.info("Iteration " + i);
            log.info("*************************************************************");

            Session s = conn.createSession(true, Session.AUTO_ACKNOWLEDGE);
            
            MessageProducer prod = s.createProducer(topic1);

            final CountDownLatch latchReceive = new CountDownLatch(1);
            TopicSubscriber subscriber = s.createDurableSubscriber(topic1, "topic" + i);
            subscriber.setMessageListener(new MessageListener(){
               public void onMessage(Message arg0)
               {
                  latchReceive.countDown();
               }
            });
            
            prod.send(s.createTextMessage("msg1"));
            s.commit(); // to the message send
            
            latchReceive.await(10, TimeUnit.SECONDS);
            assertEquals(0, latchReceive.getCount());
            
            s.commit(); // to the message receive
            
            prod.close();
            
            subscriber.close();
            
            s.unsubscribe("topic" + i);
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

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
