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
package org.jboss.test.messaging.jms.message;

import java.io.Serializable;

import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TopicConnection;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import org.jboss.test.messaging.jms.JMSTestCase;


/**
 * 
 * A ObjectMessageDeliveryTest
 * @author <a href="mailto:jhowell@redhat.com">Jay Howell</a>
 * @version <tt>$Revision: 2925 $</tt>
 *
 * $Id: 
 *
 */
public class ObjectSerializationMessageDeliveryTest extends JMSTestCase
{
   // Constants -----------------------------------------------------
   
   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------
   
   public ObjectSerializationMessageDeliveryTest(String name)
   {
      super(name);
   }
   
   // Public --------------------------------------------------------
   
   static class TestObject implements Serializable
   {
      private static final long serialVersionUID = -340663970717491155L;
      String text;
   }
   
   /**
    * 
    */
   public void testTopic() throws Exception
   {
      TopicConnection conn = cf.createTopicConnection();

      try
      {
         TopicSession s1 = conn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
         TopicSession s2 = conn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
         TopicSession s3 = conn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
         TopicPublisher publisher = s1.createPublisher(topic1);
         TopicSubscriber sub = s1.createSubscriber(topic1);
         TopicSubscriber sub2 = s2.createSubscriber(topic1);
         TopicSubscriber sub3 = s3.createSubscriber(topic1);
         conn.start();
                  
         //Create 3 object messages with different bodies
         
         TestObject to1 = new TestObject();
         to1.text = "hello1";
         
         ObjectMessage om1 = s1.createObjectMessage();
         om1.setObject(to1);
         
     
         
         //send to topic
         publisher.send(om1);
         
     
         
         ObjectMessage rm1 = (ObjectMessage)sub.receive(MAX_TIMEOUT);
         
         ObjectMessage rm2 = (ObjectMessage)sub2.receive(MAX_TIMEOUT);
         
         ObjectMessage rm3 = (ObjectMessage)sub3.receive(MAX_TIMEOUT);
         
         
         assertNotNull(rm1);
         assertNotNull(rm2);
         assertNotNull(rm3);
         
         TestObject ro1 = (TestObject)rm1.getObject();
         TestObject ro2 = (TestObject)rm2.getObject();
         TestObject ro3 = (TestObject)rm3.getObject();
         assertNotSame("Objects in two different sessions got the same reference to the same object",ro1,ro2);
         assertNotSame("Objects in two different sessions got the same reference to the same object",ro1,ro3);
         assertNotSame("Objects in two different sessions got the same reference to the same object",ro2,ro3);
         
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


