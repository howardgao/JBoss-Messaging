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
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import EDU.oswego.cs.dl.util.concurrent.Slot;

/**
 * The most comprehensive, yet simple, unit test.
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Foxv</a>
 * @version <tt>$Revision: 3390 $</tt>
 *
 * $Id: JMSTest.java 3390 2007-11-30 17:55:48Z timfox $
 */
public class JMSTest extends JMSTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   InitialContext ic;

   // Constructors --------------------------------------------------

   public JMSTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------
   
   public void testNoop() throws Exception
   {
      log.info("noop");
   }

   public void test_NonPersistent_NonTransactional() throws Exception
   {
      Connection conn = null;
      
      try
      {	      
	      conn = cf.createConnection();
	
	      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      MessageProducer prod = session.createProducer(queue1);
	      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
	
	      TextMessage m = session.createTextMessage("message one");
	
	      prod.send(m);
	      
	      // It's np so give it some time to hit the server before closing
         
         Thread.sleep(2000);   
	
	      conn.close();
	
	      conn = cf.createConnection();
	
	      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      MessageConsumer cons = session.createConsumer(queue1);
	
	      conn.start();
	
	      TextMessage rm = (TextMessage)cons.receive();
	
	      assertEquals("message one", rm.getText());
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      }
   }

   public void test_CreateTextMessageNull() throws Exception
   {      
      Connection conn = null;
      
      try
      {      
	      conn = cf.createConnection();
	
	      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      MessageProducer prod = session.createProducer(queue1);
	      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
	
	      TextMessage m = session.createTextMessage();
	
	      m.setText("message one");
	
	      prod.send(m);
	      
	      //It's np so give it some time to hit the server before closing
	      
	      Thread.sleep(2000);
	
	      conn.close();
	
	      conn = cf.createConnection();
	
	      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      MessageConsumer cons = session.createConsumer(queue1);
	
	      conn.start();
	
	      TextMessage rm = (TextMessage)cons.receive();
	
	      assertEquals("message one", rm.getText());
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      }
   }

   public void test_Persistent_NonTransactional() throws Exception
   {
      Connection conn = null;
      
      try
      {      
	      conn = cf.createConnection();
	
	      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      MessageProducer prod = session.createProducer(queue1);
	      prod.setDeliveryMode(DeliveryMode.PERSISTENT);
	
	      TextMessage m = session.createTextMessage("message one");
	
	      prod.send(m);
	      
	      // It's np so give it some time to hit the server before closing
         
         Thread.sleep(2000);
   	      
	
	      conn.close();
	
	      conn = cf.createConnection();
	
	      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      MessageConsumer cons = session.createConsumer(queue1);
	
	      conn.start();
	
	      TextMessage rm = (TextMessage)cons.receive();
	
	      assertEquals("message one", rm.getText());	
	   }
	   finally
	   {
	   	if (conn != null)
	   	{
	   		conn.close();
	   	}
	   }
   }

   public void test_NonPersistent_Transactional_Send() throws Exception
   {
      Connection conn = null;
      
      try
      {      
	      conn = cf.createConnection();
	
	      Session session = conn.createSession(true, Session.SESSION_TRANSACTED);
	
	      MessageProducer prod = session.createProducer(queue1);
	      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
	
	      TextMessage m = session.createTextMessage("message one");
	      prod.send(m);
	      m = session.createTextMessage("message two");
	      prod.send(m);
	
	      session.commit();
	
	      conn.close();
	
	      conn = cf.createConnection();
	
	      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      MessageConsumer cons = session.createConsumer(queue1);
	
	      conn.start();
	
	      TextMessage rm = (TextMessage)cons.receive();
	      assertEquals("message one", rm.getText());
	      rm = (TextMessage)cons.receive();
	      assertEquals("message two", rm.getText());
      }
	   finally
	   {
	   	if (conn != null)
	   	{
	   		conn.close();
	   	}
	   }
   }

   public void test_Persistent_Transactional_Send() throws Exception
   {      
      Connection conn = null;
      
      try
      {      
	      conn = cf.createConnection();
	
	      Session session = conn.createSession(true, Session.SESSION_TRANSACTED);
	
	      MessageProducer prod = session.createProducer(queue1);
	      prod.setDeliveryMode(DeliveryMode.PERSISTENT);
	
	      TextMessage m = session.createTextMessage("message one");
	      prod.send(m);
	      m = session.createTextMessage("message two");
	      prod.send(m);
	
	      session.commit();
	
	      conn.close();
	
	      conn = cf.createConnection();
	
	      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      MessageConsumer cons = session.createConsumer(queue1);
	
	      conn.start();
	
	      TextMessage rm = (TextMessage)cons.receive();
	      assertEquals("message one", rm.getText());
	      rm = (TextMessage)cons.receive();
         assertEquals("message two", rm.getText());
      }
	   finally
	   {
	   	if (conn != null)
	   	{
	   		conn.close();
	   	}
	   }
   }


   public void test_NonPersistent_Transactional_Acknowledgment() throws Exception
   {      
      Connection conn = null;
      
      try
      {      
	      conn = cf.createConnection();
	
	      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      MessageProducer prod = session.createProducer(queue1);
	      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
	      TextMessage m = session.createTextMessage("one");
	      prod.send(m);
	      
	      //It's np so give it some time to hit the server before closing
         
         Thread.sleep(2000);
   
	
	      conn.close();
	
	      conn = cf.createConnection();
	
	      session = conn.createSession(true, Session.SESSION_TRANSACTED);
	
	      MessageConsumer cons = session.createConsumer(queue1);
	
	      conn.start();
	
	      TextMessage rm = (TextMessage)cons.receive();
	      assertEquals("one", rm.getText());
	
	      session.commit();
      }
	   finally
	   {
	   	if (conn != null)
	   	{
	   		conn.close();
	   	}
	   }
   }

   public void test_Asynchronous_to_Client() throws Exception
   {
      Connection conn = null;
      
      try
      {      
	      conn = cf.createConnection();
	
	      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      final MessageConsumer cons = session.createConsumer(queue1);
	
	      conn.start();
	
	      final Slot slot = new Slot();
	
	      new Thread(new Runnable()
	      {
	         public void run()
	         {
	            try
	            {
	               Message m = cons.receive(5000);
	               if (m != null)
	               {
	                  slot.put(m);
	               }
	            }
	            catch(Exception e)
	            {
	               log.error("receive failed", e);
	            }
	
	         }
	      }, "Receiving Thread").start();
	
	      MessageProducer prod = session.createProducer(queue1);
	      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
	
	      TextMessage m = session.createTextMessage("message one");
	
	      prod.send(m);
	
	      TextMessage rm = (TextMessage)slot.poll(5000);
	
	      assertEquals("message one", rm.getText());
      }
	   finally
	   {
	   	if (conn != null)
	   	{
	   		conn.close();
	   	}
	   }
   }

   public void test_MessageListener() throws Exception
   {      
      Connection conn = null;
      
      try
      {      
	      conn = cf.createConnection();
	
	      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	
	      MessageConsumer cons = session.createConsumer(queue1);
	
	      final Slot slot = new Slot();
	
	      cons.setMessageListener(new MessageListener()
	      {
	         public void onMessage(Message m)
	         {
	            try
	            {
	               slot.put(m);
	            }
	            catch(InterruptedException e)
	            {
	               log.warn("got InterruptedException", e);
	            }
	         }
	      });
	
	      conn.start();
	
	      MessageProducer prod = session.createProducer(queue1);
	      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
	      TextMessage m = session.createTextMessage("one");
	      prod.send(m);
	
	      TextMessage rm = (TextMessage)slot.poll(5000);
	
	      assertEquals("one", rm.getText());
      }
	   finally
	   {
	   	if (conn != null)
	   	{
	   		conn.close();
	   	}
	   }
   }

   public void test_ClientAcknowledge() throws Exception
   {
      Connection conn = null;
      
      try
      {      
	      conn = cf.createConnection();
	
	      Session session = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
	      MessageProducer p = session.createProducer(queue1);
	      p.send(session.createTextMessage("CLACK"));
	
	      MessageConsumer cons = session.createConsumer(queue1);
	
	      conn.start();
	
	      TextMessage m = (TextMessage)cons.receive(1000);
	
	      assertEquals("CLACK", m.getText());
	
	      // make sure the message is still in "delivering" state
	      assertRemainingMessages(1);
	
	      m.acknowledge();
	
	      assertRemainingMessages(0);
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
