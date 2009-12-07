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

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * 
 * A ZeroByteBytesMessageTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * Created 22 Sep 2008 11:46:57
 *
 *
 */
public class ZeroByteBytesMessageTest extends JMSTestCase
{   
   //  Constants -----------------------------------------------------------------------------------
   
   // Static ---------------------------------------------------------------------------------------
   
   // Attributes -----------------------------------------------------------------------------------
   
   // Constructors ---------------------------------------------------------------------------------
   
   public ZeroByteBytesMessageTest(String name)
   {
      super(name);
   }
   
   // Public ---------------------------------------------------------------------------------------
      
   // https://jira.jboss.org/jira/browse/JBMESSAGING-1281
   public void testZeroByteBytesMessage() throws Exception
   {
      Connection conn = null;
      
      try
      {      
         conn = cf.createConnection();
         
         conn.start();
         
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
   
         MessageProducer prod = session.createProducer(queue1);
         
         MessageConsumer cons = session.createConsumer(queue1);
         
         BytesMessage bm = session.createBytesMessage();
         
         prod.send(bm);
         
         BytesMessage bm2 = (BytesMessage)cons.receive(10000);
         
         assertNotNull(bm2);
         
         byte[] bytes2 = new byte[] {};
         
         bm2.readBytes(bytes2);
                  
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   
   // Package protected ----------------------------------------------------------------------------
   
   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------
   
   // Inner classes --------------------------------------------------------------------------------
}

