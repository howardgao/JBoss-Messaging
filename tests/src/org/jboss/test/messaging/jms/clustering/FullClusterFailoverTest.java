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
package org.jboss.test.messaging.jms.clustering;

import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;
import org.jboss.jms.tx.ResourceManagerFactory;
import org.jboss.test.messaging.tools.ServerManagement;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;

/**
 * @author <a href="ataylor@redhat.com">Andy Taylor</a>
 */
public class FullClusterFailoverTest extends ClusteringTestBase
{
   public FullClusterFailoverTest(String name)
   {
      super(name);
   }

   public void testAllNodesFailoverTest() throws Exception
   {
      ServerManagement.getServer(0).deployConnectionFactory("jboss.messaging.connectionfactory:service=FailoverClusteredConnectionFactory", new String[]{"/FailoverClusteredConnectionFactory"}, true, false);
      ServerManagement.getServer(1).deployConnectionFactory("jboss.messaging.connectionfactory:service=FailoverClusteredConnectionFactory", new String[]{"/FailoverClusteredConnectionFactory"}, true, false);
      ServerManagement.getServer(2).deployConnectionFactory("jboss.messaging.connectionfactory:service=FailoverClusteredConnectionFactory", new String[]{"/FailoverClusteredConnectionFactory"}, true, false);
      ServerManagement.getServer(3).deployConnectionFactory("jboss.messaging.connectionfactory:service=FailoverClusteredConnectionFactory", new String[]{"/FailoverClusteredConnectionFactory"}, true, false);


      ConnectionFactory theCF = (ConnectionFactory)ic[1].lookup("/FailoverClusteredConnectionFactory");

      Connection conn = null;

      try
      {
         final LinkedQueue buffer = new LinkedQueue();
         conn = createConnectionOnServer(theCF, 1);
         conn.start();

         conn.setExceptionListener(new ExceptionListener()
         {
            public void onException(JMSException e)
            {
               try
               {
                  buffer.put(e);
               }
               catch (InterruptedException e1)
               {
                  e1.printStackTrace();
               }
            }
         });
         waitForFailoverComplete(1, conn);
         assertNull(buffer.peek());
         waitForFailoverComplete(2, conn);
         assertNull(buffer.peek());
         waitForFailoverComplete(3, conn);
         assertNull(buffer.peek());
         ServerManagement.kill(0);
         Exception e = (Exception) buffer.poll(120000);
         System.out.println("e = " + e);
         assertNotNull(e);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
         ResourceManagerFactory.instance.clear();
      }
   }

}
