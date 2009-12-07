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
package org.jboss.test.messaging.jms.server.recovery;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.transaction.xa.XAResource;

import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.ServerManagement;

/**
 * 
 * ASRecoverytest
 *
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 *
 */
public class ASRecoveryTest extends MessagingTestCase
{   
   protected static ConnectionFactory cf0;
   
   protected static Properties targetProps;
   
   public ASRecoveryTest(String name)
   {
      super(name);
   }

   protected void setUp() throws Exception
   {
      super.setUp();

      //Start the servers
      	
      ServerManagement.start(0, "all", true);
      	
      setUpAdministeredObjects();
   }
   
   protected void tearDown() throws Exception
   {       
      super.tearDown(); 
   }
   
   protected void setUpAdministeredObjects() throws Exception
   {
      Hashtable props1 = ServerManagement.getJNDIEnvironment(0);
      targetProps = new Properties();
      Enumeration keys = props1.keys();
      while (keys.hasMoreElements())
      {
         Object key = keys.nextElement();
         targetProps.put(key, props1.get(key));
      }
   }
   
   public void testRemoteXAResourceReconnect() throws Exception
   {
      MockMessagingXAResourceWrapper wrapper = new MockMessagingXAResourceWrapper(targetProps);
      
      XAResource res = wrapper.getDelegate();
      
      assertNotNull(res);
      
      res.recover(XAResource.TMENDRSCAN);
      
      //get again
      res = wrapper.getDelegate();
      
      res.recover(XAResource.TMENDRSCAN);
      
      //kill the server
      ServerManagement.kill(0);
      
      try
      {
         res = wrapper.getDelegate();
         fail("should have thrown an exception due to lose of server!");
      }
      catch (Exception e)
      {
         //fine.
      }
      
      //Start the server again.
      
      ServerManagement.start(0, "all", true);
         
      setUpAdministeredObjects();
      
      //get again.
      res = wrapper.getDelegate();
      
      assertNotNull(res);
      
      //should work again.
      res.recover(XAResource.TMENDRSCAN);
      
      wrapper.close();
   }
   
   
   // Inner classes -------------------------------------------------------------------
   
}

