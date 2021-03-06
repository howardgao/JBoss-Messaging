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

package org.jboss.test.messaging.jms.manual;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;

import org.jboss.test.messaging.jms.stress.SeveralClientsStressTest;

/**
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @version <tt>$Revision: 2928 $</tt>
 *          $Id: ManualQueueSoakTest.java 2928 2007-07-27 00:33:55Z timfox $
 */
public class ManualQueueSoakTest extends SeveralClientsStressTest
{

   // Constants ------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------


   // Static ---------------------------------------------------------------------------------------

   protected Context createContext() throws Exception
   {
      Properties props = new Properties();

      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.jnp.interfaces.NamingContextFactory");
      props.put(Context.PROVIDER_URL, "jnp://localhost:1099");
      props.put(Context.URL_PKG_PREFIXES, "org.jnp.interfaces");

      return new InitialContext(props);
   }

   // Constructors ---------------------------------------------------------------------------------

   public ManualQueueSoakTest(String name)
   {
      super(name);
   }

   // Public ---------------------------------------------------------------------------------------

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected void tearDown() throws Exception
   {
      super.tearDown();
   }

   protected void setUp() throws Exception
   {
      startServer = false;
      info=false;
      TEST_ALIVE_FOR = 8 * 60 * 60 * 1000; // 8 hours
      PRODUCER_ALIVE_FOR=5 * 60 * 1000; // 5 minutes
      CONSUMER_ALIVE_FOR=5 * 60 * 1000; // 5 minutes

      NUMBER_OF_PRODUCERS=20;
      NUMBER_OF_CONSUMERS=20;
      LONG_WAIT_ON_PRODUCERS = false;

      super.setUp();

   }

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------


}
