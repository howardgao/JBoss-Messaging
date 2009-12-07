/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.test.messaging.jms.message;

import java.util.Random;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.jboss.jms.client.JBossMessageProducer;
import org.jboss.test.messaging.jms.JMSTestCase;

/**
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 */
public class JMSOrderingGroupPropertyTest extends JMSTestCase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------
   public JMSOrderingGroupPropertyTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------
   /*
    * Note - this test is testing the order of messages under the control of 
    * ordering group message producers. All messages sent through a order group
    * message producer obeys strict ordering rule -- they are delivered in the order
    * they are sent, regardless of priorities.
    */
   public void testMessageOrderWithPriority() throws Exception
   {
      Connection conn = cf.createConnection();

      conn.start();

      Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      JBossMessageProducer prod = (JBossMessageProducer)sessSend.createProducer(queue1);

      prod.enableOrderingGroup(null);

      TextMessage m0 = sessSend.createTextMessage("a");
      TextMessage m1 = sessSend.createTextMessage("b");
      TextMessage m2 = sessSend.createTextMessage("c");
      TextMessage m3 = sessSend.createTextMessage("d");
      TextMessage m4 = sessSend.createTextMessage("e");
      TextMessage m5 = sessSend.createTextMessage("f");
      TextMessage m6 = sessSend.createTextMessage("g");
      TextMessage m7 = sessSend.createTextMessage("h");
      TextMessage m8 = sessSend.createTextMessage("i");
      TextMessage m9 = sessSend.createTextMessage("j");

      Random rdm = new Random();
      int pri = rdm.nextInt(10);
      prod.send(m0, DeliveryMode.NON_PERSISTENT, pri, 0);
      pri = rdm.nextInt(10);
      prod.send(m1, DeliveryMode.NON_PERSISTENT, pri, 0);
      pri = rdm.nextInt(10);
      prod.send(m2, DeliveryMode.NON_PERSISTENT, pri, 0);
      pri = rdm.nextInt(10);
      prod.send(m3, DeliveryMode.NON_PERSISTENT, pri, 0);
      pri = rdm.nextInt(10);
      prod.send(m4, DeliveryMode.NON_PERSISTENT, pri, 0);
      pri = rdm.nextInt(10);
      prod.send(m5, DeliveryMode.NON_PERSISTENT, pri, 0);
      pri = rdm.nextInt(10);
      prod.send(m6, DeliveryMode.NON_PERSISTENT, pri, 0);
      pri = rdm.nextInt(10);
      prod.send(m7, DeliveryMode.NON_PERSISTENT, pri, 0);
      pri = rdm.nextInt(10);
      prod.send(m8, DeliveryMode.NON_PERSISTENT, pri, 0);
      pri = rdm.nextInt(10);
      prod.send(m9, DeliveryMode.NON_PERSISTENT, pri, 0);

      // NP messages are sent async so we need to allow them time to all hit the server
      Thread.sleep(2000);

      Session sessReceive = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = sessReceive.createConsumer(queue1);

      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("a", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("b", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("c", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("d", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("e", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("f", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("g", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("h", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("i", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("j", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(500);
         assertNull(t);
      }

      cons.close();

      conn.close();
   }

   /*
    * If messages are sent to a queue with certain priorities, and a consumer is already open
    * then it is likely that they will be immediately sent to the consumer.
    * even in this case, ordering group should be strictly obeyed.
    */
   public void testMessageOrderWithConsumerBuffering() throws Exception
   {
      Connection conn = cf.createConnection();

      conn.start();

      Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      JBossMessageProducer prod = (JBossMessageProducer)sessSend.createProducer(queue1);
      prod.enableOrderingGroup(null);

      TextMessage m0 = sessSend.createTextMessage("a");
      TextMessage m1 = sessSend.createTextMessage("b");
      TextMessage m2 = sessSend.createTextMessage("c");
      TextMessage m3 = sessSend.createTextMessage("d");
      TextMessage m4 = sessSend.createTextMessage("e");
      TextMessage m5 = sessSend.createTextMessage("f");
      TextMessage m6 = sessSend.createTextMessage("g");
      TextMessage m7 = sessSend.createTextMessage("h");
      TextMessage m8 = sessSend.createTextMessage("i");
      TextMessage m9 = sessSend.createTextMessage("j");

      Session sessReceive = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = sessReceive.createConsumer(queue1);

      prod.send(m0, DeliveryMode.NON_PERSISTENT, 0, 0);
      prod.send(m1, DeliveryMode.NON_PERSISTENT, 1, 0);
      prod.send(m2, DeliveryMode.NON_PERSISTENT, 2, 0);
      prod.send(m3, DeliveryMode.NON_PERSISTENT, 3, 0);
      prod.send(m4, DeliveryMode.NON_PERSISTENT, 4, 0);
      prod.send(m5, DeliveryMode.NON_PERSISTENT, 5, 0);
      prod.send(m6, DeliveryMode.NON_PERSISTENT, 6, 0);
      prod.send(m7, DeliveryMode.NON_PERSISTENT, 7, 0);
      prod.send(m8, DeliveryMode.NON_PERSISTENT, 8, 0);
      prod.send(m9, DeliveryMode.NON_PERSISTENT, 9, 0);

      // Let them all get there

      Thread.sleep(2000);

      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("a", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("b", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("c", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("d", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("e", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("f", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("g", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("h", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("i", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(1000);
         assertNotNull(t);
         assertEquals("j", t.getText());
      }
      {
         TextMessage t = (TextMessage)cons.receive(500);
         assertNull(t);
      }

      cons.close();

      conn.close();
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
