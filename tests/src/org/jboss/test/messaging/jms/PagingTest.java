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
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.jboss.test.messaging.tools.ServerManagement;


/**
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 * $Id: AcknowledgementTest.java 3173 2007-10-05 12:48:16Z timfox $
 */
public class PagingTest extends JMSTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public PagingTest(String name)
   {
      super(name);
   }

   // TestCase overrides -------------------------------------------

   // Public --------------------------------------------------------

   public void testPaging() throws Exception
   {
   	ServerManagement.deployQueue("pagequeue", null, 10000, 1000, 1000);
   	
   	Queue queue = (Queue)ic.lookup("/queue/pagequeue");
   	
      Connection conn = null;
      
      try
      {
         conn = cf.createConnection();
         
         conn.start();
                  
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageProducer sender = session.createProducer(queue);
         
         sender.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         
         MessageConsumer cons = session.createConsumer(queue);
         
         final int numMessages = 10000;
         
         long start = System.currentTimeMillis();
         
         for (int i = 0; i < numMessages; i++)
         {
         	Message m = session.createMessage();
         	
         	sender.send(m);
         	
         	if (i % 1000 == 0)
         	{
         		log.info("Sent message " + i);
         	}
         }
         
         long end = System.currentTimeMillis();
         
         double rate = 1000 * (double)numMessages / ( end - start);
         
         log.info("Rate " + rate);
         
         start = System.currentTimeMillis();
         
         for (int i = 0; i < numMessages; i++)
         {
         	Message m = cons.receive(2000);
         	
         	if (i % 1000 == 0)
         	{
         		log.info("Got message " + i);
         	}
         }
         
         end = System.currentTimeMillis();
         
         rate = 1000 * (double)numMessages / ( end - start);
         
         log.info("Rate " + rate);
         
         
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
         
         try
         {
         	ServerManagement.undeployQueue("pagequeue");
         }
         catch(Exception ignore)
         {
         	
         }
      }
   }
   
}


