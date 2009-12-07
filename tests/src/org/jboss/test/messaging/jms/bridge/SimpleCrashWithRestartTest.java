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
package org.jboss.test.messaging.jms.bridge;

import java.util.Properties;

import org.jboss.jms.jndi.JMSProviderAdapter;
import org.jboss.jms.server.bridge.Bridge;
import org.jboss.logging.Logger;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.TestJMSProviderAdaptor;

/**
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 * @version <tt>$Revision: 4112 $</tt>
 *
 * $Id: SimpleCrashWithRestartTest.java 4112 2008-04-24 15:37:10Z clebert.suconic@jboss.com $
 *
 */
public class SimpleCrashWithRestartTest extends BridgeTestBase
{
   private static final Logger log = Logger.getLogger(ReconnectTest.class);

   public SimpleCrashWithRestartTest(String name)
   {
      super(name);
   }

   protected void setUp() throws Exception
   {   
      super.setUp();         
      
      //Now install local JMSProviderAdaptor classes
      
      Properties props1 = new Properties();
      props1.putAll(ServerManagement.getJNDIEnvironment(1));
        
      JMSProviderAdapter targetAdaptor =
         new TestJMSProviderAdaptor(props1, "/XAConnectionFactory", "adaptor1");
      
      sc.installJMSProviderAdaptor("adaptor1", targetAdaptor);
      
      sc.startRecoveryManager();      
   }

   protected void tearDown() throws Exception
   {  
      super.tearDown();

      sc.stopRecoveryManager();
      
      sc.uninstallJMSProviderAdaptor("adaptor1");

      log.debug(this + " torn down");
   }
      
   /*
    * Send some messages   
    * Crash the server after prepare but on commit
    * Bring up the destination server
    * Send some more messages
    * Verify all messages are received
    */
   public void testCrashAndRestart() throws Exception
   {
         Bridge bridge = null;
          
         try
         {
            final int NUM_MESSAGES = 10;         
            
            bridge = new Bridge(false, sourceProps, targetProps, "/queue/sourceQueue", "/queue/targetQueue",
                                "/ConnectionFactory", "/ConnectionFactory", 
                                null, null, null, null,
                                null, 1000, -1, Bridge.QOS_AT_MOST_ONCE, 
                                NUM_MESSAGES, -1, null, null, false);
            
            bridge.start();
            
            sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES, true);
                 
            checkMessagesReceived(cf1, targetQueue, Bridge.QOS_ONCE_AND_ONLY_ONCE, NUM_MESSAGES, true);
            
            ServerManagement.kill(1);
            
            
            // >30 sec (timeout on remoting)
            Thread.sleep(35000);
            
            ServerManagement.start(1, "all", false);
            ServerManagement.deployQueue("targetQueue", 1);     
            
            setUpAdministeredObjects();
            
            sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES, true);
             
            checkMessagesReceived(cf1, targetQueue, Bridge.QOS_AT_MOST_ONCE, NUM_MESSAGES, true);
         }
         finally
         {      
            if (bridge != null)
            {
               try
               {
                  bridge.stop();
               }
               catch (Exception e)
               {
                  log.error("Failed to stop bridge", e);
               }
            }
         }                  
   }
   
   // Inner classes -------------------------------------------------------------------   
}
