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


package org.jboss.test.messaging.jms.clustering;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.jboss.test.messaging.tools.ServerManagement;

/**
 * A ServerFailoverTest
 *
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 * 
 * Created Jun 19, 2009 2:02:15 PM
 *
 *
 */
public class ServerFailoverTest extends ClusteringTestBase
{

   private transient boolean running = true;
   
   public ServerFailoverTest(String name)
   {
      super(name);
   }


   public void setUp() throws Exception
   {
      nodeCount = 2;
      super.setUp();
   }

   //https://jira.jboss.org/jira/browse/JBMESSAGING-1657
   public void testMessageHandleOnNodeLeave() throws Exception
   {
      final Connection conn0 = createConnectionOnServer(cf, 0);
      
      for (int i = 0; i < 5; ++i)
      {
         Worker worker = new Worker(conn0);
         worker.start();
         worker.awaitMe();

         ServerManagement.kill(1);

         try
         {
            Thread.sleep(2000);
         }
         catch (InterruptedException ex)
         {
         }

         running = false;

         worker.join();
         
         assertTrue(worker.isOk());

         // start server 1 again.
         startDefaultServer(1, overrides, false);

         try
         {
            Thread.sleep(5000);
         }
         catch (InterruptedException ex)
         {
         }

      }
      conn0.close();
   }
   
   private class Worker extends Thread
   {
      private Connection conn0;
      private boolean ok = true;
      
      public Worker(Connection conn)
      {
         conn0 = conn;
         running = false;
      }
      
      public synchronized void awaitMe()
      {
         try
         {
            while (!running)
            {
               this.wait();
            }
         }
         catch (InterruptedException e)
         {
         }
      }
      
      public void run()
      {
         try
         {
            Session sess0 = conn0.createSession(false, Session.AUTO_ACKNOWLEDGE);
            while (true)
            {
               synchronized(this)
               {
                  if (!running)
                  {
                     running = true;
                     this.notify();
                  }
               }
               MessageConsumer cons0 = sess0.createConsumer(topic[0]);
               try
               {
                  Thread.sleep(10);
               }
               catch (InterruptedException e)
               {
               }
               cons0.close();
               
               if (!running) break;
            }
         }
         catch (JMSException e)
         {
            log.error("failed to run worker", e);
            setOk(false);
         }
      }
      
      public synchronized void setOk(boolean isOK)
      {
         ok = isOK;
      }
      
      public synchronized boolean isOk()
      {
         return ok;
      }
   }
}





