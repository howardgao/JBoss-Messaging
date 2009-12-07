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
package org.jboss.example.jms.ordering;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Destination;

/**
 *
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 *
 */
public class ConsumerThread extends Thread
{
   private OrderingGroupExample example;
   private Session session;
   private MessageConsumer consumer;
   private Connection connection;

   public ConsumerThread(String name, OrderingGroupExample theExample, ConnectionFactory fact, Destination queue) throws JMSException
   {
      super(name);
      example = theExample;
      
      connection = fact.createConnection();
      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      consumer = session.createConsumer(queue);
      connection.start();
   }

   public void delay(long nt)
   {
      try
      {
         Thread.sleep(nt);
      }
      catch (InterruptedException e)
      {
      }
   }

   //receiving the messages
   public void run()
   {
      int n = example.getNumMessages();
      try
      {
         while (true)
         {
            if (example.allReceived())
            {
               break;
            }
            TextMessage msg = (TextMessage)consumer.receive(2000);
            if (msg != null)
            {
               if (msg.getText().equals(OrderingGroupExample.ORDERING_MSG1))
               {
                  //whoever receives first message, delay for 2 sec.
                  delay(2000);
               }
               example.reportReceive(msg);
               msg.acknowledge();
            }
         }
      }
      catch (JMSException e)
      {
         e.printStackTrace();
      }
      finally
      {
         if (session != null)
         {
            try
            {
               connection.close();
            }
            catch (JMSException e)
            {
            }
         }
      }
   }
   
}
