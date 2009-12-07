/*
 * JBoss, Home of Professional Open Source Copyright 2005, JBoss Inc., and
 * individual contributors as indicated by the @authors tag. See the
 * copyright.txt in the distribution for a full listing of individual
 * contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.jboss.test.messaging.jms.clustering;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * 
 * A NullPersistenceClusterTest
 *
 * @author Howard Gao
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * 
 * Created Nov 3, 2008 3:26:20 PM
 *
 *
 */
public class NullPersistenceClusterTest extends ClusteringTestBase
{

   private final String originalPersistence = System.getProperty("test.database", "null");;

   public NullPersistenceClusterTest(final String name)
   {
      super(name);
   }

   @Override
   protected void setUp() throws Exception
   {
      System.setProperty("test.database", "null");
      super.setUp();
   }

   @Override
   protected void tearDown() throws Exception
   {
      super.tearDown();
      System.setProperty("test.database", originalPersistence);
   }

   public void testSimpleMessaging() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = cf.createConnection();

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = sess.createConsumer(queue[0]);

         conn.start();

         MessageProducer prod = sess.createProducer(queue[0]);

         final int NUM_MESSAGES = 100;

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess.createTextMessage("message-" + i);

            prod.send(tm);
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons.receive(1000);

            assertNotNull(tm);

            assertEquals("message-" + i, tm.getText());
         }

         Message m = cons.receive(2000);

         assertNull(m);

         cons.close();
         sess.close();
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

}
