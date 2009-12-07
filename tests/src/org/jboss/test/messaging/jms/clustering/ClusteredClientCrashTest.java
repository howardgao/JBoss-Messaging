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

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;

import org.jboss.jms.client.delegate.ClientClusteredConnectionFactoryDelegate;
import org.jboss.jms.server.endpoint.ServerConnectionFactoryEndpoint;
import org.jboss.jms.server.endpoint.advised.ConnectionFactoryAdvised;
import org.jboss.jms.wireformat.Dispatcher;
import org.jboss.logging.Logger;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.container.Command;
import org.jboss.test.messaging.tools.container.Server;
import org.jboss.test.messaging.tools.container.ServiceContainer;

/**
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @version <tt>$Revision: 7647 $</tt>
 *          $Id: ClusteredClientCrashTest.java 7647 2009-07-31 05:44:43Z gaohoward $
 */
public class ClusteredClientCrashTest extends ClusteringTestBase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   private static final Logger log = Logger.getLogger(ClusteredClientCrashTest.class);

   // Attributes ----------------------------------------------------

   protected Server localServer;

   // Constructors --------------------------------------------------

   public ClusteredClientCrashTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   public void setUp() throws Exception
   {
      nodeCount = 2;
      super.setUp();

   }

   public void tearDown() throws Exception
   {
      super.tearDown();
   }

   /**
    * Test that when a remote jms client crashes, server side resources for connections are
    * cleaned-up.
    */
   public void testClientCrash() throws Exception
   {    
      ServerManagement.create(2);
      Server remoteServer = ServerManagement.getServer(2);

      // Make sure any previously downloadeded CF is released
      WeakReference ref = new WeakReference(ic[0].lookup("/ClusteredConnectionFactory"));
      int count=0;
      while (ref.get() != null)
      {
         // put some garbage on the memory
         for (int i=0; i<50000 ; i++)
         {
            String str = "Garbage " + i;
         }
         System.gc();
         Thread.sleep(1000);
         if ((count++>10) && ref.get() != null)
         {
            fail("Thre is a leak on ClusteredConnectionFactory");
         }
      }
       
      ClientClusteredConnectionFactoryDelegate cfDelegate =  (ClientClusteredConnectionFactoryDelegate)cf.getDelegate();

      log.info("(Before)Server0 - " + getCountOnServer(0, cfDelegate.getUniqueName()));
      log.info("(Before)Server1 - " + getCountOnServer(1, cfDelegate.getUniqueName()));
      
      assertEquals(1, getCountOnServer(0, cfDelegate.getUniqueName()));
      assertEquals(1, getCountOnServer(1, cfDelegate.getUniqueName()));
      
      cfDelegate.closeCallback(false);
      
      // Validates if closeCallback is working

      assertEquals(0, getCountOnServer(0, cfDelegate.getUniqueName()));
      assertEquals(0, getCountOnServer(1, cfDelegate.getUniqueName()));
      
      ClusterClientCrash command = new ClusterClientCrash(cf);

      assertEquals("OK", remoteServer.executeCommand(command));
      
      

      assertEquals(new Integer(1),ServerManagement.getServer(1).executeCommand(new VerifySizeOfCFClients(cfDelegate.getUniqueName())));

      ServerManagement.kill(2);
      Thread.sleep((long)(60000));

      assertEquals(0, getCountOnServer(1, cfDelegate.getUniqueName()));
      assertEquals(0, getCountOnServer(0, cfDelegate.getUniqueName()));
   }


   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------
   
   private int getCountOnServer(int server, String uniquename) throws Exception
   {
      return (Integer)ServerManagement.getServer(1).executeCommand(new VerifySizeOfCFClients(uniquename));
   }


   // Inner classes -------------------------------------------------

   public static class ClusterClientCrash implements Command
   {

      ConnectionFactory cf = null;
      
      // just to make sure the CF will still be around by the time the VM is killed
      static ArrayList list = new ArrayList();

      public ClusterClientCrash(ConnectionFactory cf)
      {
         this.cf = cf;
      }

      public Object execute(Server server) throws Exception
      {
         list.add(cf);
         Connection conn = cf.createConnection();
         conn.start();

         return "OK";
      }
   }

   public static class VerifySizeOfCFClients implements Command
   {

      private static final long serialVersionUID = 7783882344446813622L;

      String uniqueName;

      public VerifySizeOfCFClients(String uniqueName)
      {
         this.uniqueName = uniqueName;
      }

      public Object execute(Server server) throws Exception
      {

         ConnectionFactoryAdvised advised = (ConnectionFactoryAdvised)Dispatcher.instance.getTarget(uniqueName);
         if (advised == null)
         {
            return -1;
         }
         ServerConnectionFactoryEndpoint endpoint = (ServerConnectionFactoryEndpoint)advised.getEndpoint();
         
         int size = endpoint.getCallbackHandlers().size();
         return new Integer(size);
      }
   }

}
