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

import javax.jms.Connection;

import org.jboss.jms.client.JBossConnectionFactory;
import org.jboss.jms.client.delegate.ClientClusteredConnectionFactoryDelegate;
import org.jboss.jms.client.delegate.ClientConnectionFactoryDelegate;
import org.jboss.jms.exception.MessagingNetworkFailureException;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.aop.PoisonInterceptor;
import org.jboss.test.messaging.tools.container.ServiceAttributeOverrides;
import org.jboss.test.messaging.tools.container.ServiceContainer;

/**
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @version <tt>$Revision: 7647 $</tt>
 *          $Id: ClusteredConnectionFactoryTest.java 7647 2009-07-31 05:44:43Z gaohoward $
 */
public class ClusteredConnectionFactoryTest extends ClusteringTestBase
{

   // Constants ------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   public ClusteredConnectionFactoryTest(String name)
   {
      super(name);
   }

   // Public ---------------------------------------------------------------------------------------

   
   protected void setUp() throws Exception
   {
   	nodeCount = 2;
   	
   	super.setUp();
   }
   
   public void testGetAOPBroken() throws Exception
   {      
      ServerManagement.kill(1);
      ServerManagement.kill(0);

      try
      {
         assertNotNull(((JBossConnectionFactory)cf).getDelegate().getClientAOPStack());
         fail("This should throw an exception as every server is down");
      }
      catch (MessagingNetworkFailureException e)
      {
         log.trace(e.toString(), e);
      }
   }
   
   public void testLoadAOP() throws Exception
   {
      Connection conn = null;

      try
      {
         ServerManagement.kill(1);
         
         assertNotNull(((JBossConnectionFactory)cf).getDelegate().getClientAOPStack());

         conn = cf.createConnection();
         assertEquals(0, getServerId(conn));
      }
      finally
      {
         if (conn != null)
         {
            try
            {
               conn.close();
            }
            catch (Exception ignored)
            {
            }
         }
      }
   }
      
   public void testCreateConnectionOnBrokenServer() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 0);
         conn.close();
         conn = null;
         
         ServerManagement.kill(1);
         
         conn = cf.createConnection();
         
         assertEquals(0, getServerId(conn));
         
         conn.close();
         
         conn = cf.createConnection();
         
         assertEquals(0, getServerId(conn));
         
         conn.close();
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testPoisonCFs() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = createConnectionOnServer(cf, 0);
         conn.close();
         
         // Poison the server
         ServerManagement.poisonTheServer(1, PoisonInterceptor.CF_CREATE_CONNECTION);

         // this should break on server1
         conn = cf.createConnection();
         
         assertEquals(0, getServerId(conn));
         
         conn.close();
         
         conn = cf.createConnection();
         
         assertEquals(0, getServerId(conn));
         
         conn.close();
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testRestartServer() throws Exception
   {
      JBossConnectionFactory cf2 = (JBossConnectionFactory) ic[1].lookup("/ConnectionFactory");

      ClientClusteredConnectionFactoryDelegate clusterCF = (ClientClusteredConnectionFactoryDelegate)cf.getDelegate();
      ClientConnectionFactoryDelegate delegates[] = clusterCF.getDelegates();
      clusterCF.closeCallback(false);

      ServerManagement.kill(1);

      //Restart the server on the same place
      ServiceAttributeOverrides attr = new ServiceAttributeOverrides();
      attr.put(ServiceContainer.REMOTING_OBJECT_NAME, "LocatorURI",delegates[1].getServerLocatorURI());
      ServerManagement.start(1,config,attr,false);

      // The server back on the same remoting port as before
      startDefaultServer(1, attr, false);

      Connection conn = null;
      try
      {
         conn = cf2.createConnection();
      }
      finally
      {
         try
         {
            if (conn != null)
            {
               conn.close();
            }
         }
         catch (Throwable ignored)
         {
         }

         // The next test will fail If I don't kill the server started here on this test
         ServerManagement.kill(1);
      }
      
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

}
