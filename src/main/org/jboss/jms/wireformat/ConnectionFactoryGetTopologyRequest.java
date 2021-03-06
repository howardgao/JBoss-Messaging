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

package org.jboss.jms.wireformat;

import java.io.DataOutputStream;
import java.io.DataInputStream;
import org.jboss.messaging.util.Version;
import org.jboss.jms.delegate.ConnectionFactoryEndpoint;

/**
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @version <tt>$Revision: 3019 $</tt>
 *          $Id: ConnectionFactoryGetTopologyRequest.java 3019 2007-08-21 04:19:48Z clebert.suconic@jboss.com $
 */
public class ConnectionFactoryGetTopologyRequest extends RequestSupport
{

   // Constants ------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   public ConnectionFactoryGetTopologyRequest()
   {
   }

   public ConnectionFactoryGetTopologyRequest(String objectId)
   {
      super(objectId, REQ_CONNECTIONFACTORY_GETTOPOLOGY, Version.instance().getProviderIncrementingVersion());
   }

   // Public ---------------------------------------------------------------------------------------


   public void write(DataOutputStream os) throws Exception
   {
      super.write(os);
      os.flush();
   }

   public void read(DataInputStream is) throws Exception
   {
      super.read(is);
   }

   public ResponseSupport serverInvoke() throws Exception
   {
      ConnectionFactoryEndpoint endpoint =
         (ConnectionFactoryEndpoint)Dispatcher.instance.getTarget(objectId);

      if (endpoint == null)
      {
         throw new IllegalStateException("Cannot find object with ID " + objectId + " in dispatcher");
      }

      return new ConnectionFactoryGetTopologyResponse(endpoint.getTopology());
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

}
