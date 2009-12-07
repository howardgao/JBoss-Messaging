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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Map;

import org.jboss.jms.client.delegate.ClientConnectionFactoryDelegate;
import org.jboss.jms.delegate.TopologyResult;

/**
 * This class holds the update cluster view sent by the server to client-side clustered connection
 * factories.
 *
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @author <a href="mailto:tim.fox@jboss.org">Tim Fox</a>
 * @version <tt>$Revision: 3019 $</tt>
 *
 * $Id: ConnectionFactoryUpdate.java 3019 2007-08-21 04:19:48Z clebert.suconic@jboss.com $
 */
public class ConnectionFactoryUpdate extends CallbackSupport
{

   // Constants ------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   TopologyResult topology;

   // Static ---------------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   public ConnectionFactoryUpdate(String uniqueName, ClientConnectionFactoryDelegate[] delegates,
                                  Map failoverMap)
   {
      super(PacketSupport.CONNECTIONFACTORY_UPDATE);

      topology = new TopologyResult(uniqueName, delegates, failoverMap);
   }
   
   public ConnectionFactoryUpdate()
   {      
   }

   // Public ---------------------------------------------------------------------------------------

   public String toString()
   {
      return "ConnectionFactoryUpdateMessage{" + topology + "}";
   }

   public TopologyResult getTopology()
   {
      return topology;
   }

   public void setTopology(TopologyResult topology)
   {
      this.topology = topology;
   }

   // Streamable implementation
   // ---------------------------------------------------------------     

   public void read(DataInputStream is) throws Exception
   {
      topology = new TopologyResult();
      topology.read(is);
   }

   public void write(DataOutputStream os) throws Exception
   {
      super.write(os);

      topology.write(os);

      os.flush();
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

}
