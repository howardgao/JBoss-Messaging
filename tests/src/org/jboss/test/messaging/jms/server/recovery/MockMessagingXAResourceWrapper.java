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


package org.jboss.test.messaging.jms.server.recovery;

import java.util.Hashtable;

import javax.jms.ConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.jboss.jms.jndi.JMSProviderAdapter;
import org.jboss.jms.server.recovery.MessagingXAResourceWrapper;
import org.jboss.util.naming.Util;

/**
 * A MockMessagingXAResourceWrapper
 *
 * @author howard
 * 
 * Created May 12, 2009 11:34:08 AM
 *
 *
 */
public class MockMessagingXAResourceWrapper extends MessagingXAResourceWrapper
{

   private Hashtable icProp;
   
   public MockMessagingXAResourceWrapper(Hashtable prop)
   {
      super("fakeProvider", null, null);
      icProp = prop;
   }

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------
   protected XAConnectionFactory getConnectionFactory() throws Exception
   {
      InitialContext ic = null;
      try
      {
         ic = new InitialContext(icProp);
      
         XAConnectionFactory xaFact = (XAConnectionFactory)ic.lookup("/XAConnectionFactory");
      
         return xaFact;
      }
      finally
      {
         if (ic != null)
         {
           ic.close();
         }
      }
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
