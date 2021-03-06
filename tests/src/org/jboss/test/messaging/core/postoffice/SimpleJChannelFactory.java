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

package org.jboss.test.messaging.core.postoffice;

import org.jboss.messaging.core.contract.ChannelFactory;
import org.jgroups.JChannel;

/**
 * A JChannelFactory that will use String JChannel configurations to create JChannel instances.
 *
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @version <tt>$Revision: 3124 $</tt>
 *
 * $Id: SimpleJChannelFactory.java 3124 2007-09-20 12:23:32Z timfox $
 */
public class SimpleJChannelFactory implements ChannelFactory
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   String asyncConfig;
   String syncConfig;

   // Constructors ---------------------------------------------------------------------------------

   public SimpleJChannelFactory(String syncConfig, String asyncConfig)
   {
      this.syncConfig = syncConfig;
      this.asyncConfig = asyncConfig;
   }

   // JChannelFactory ------------------------------------------------------------------------------

   public JChannel createControlChannel() throws Exception
   {
      return new JChannel(syncConfig);
   }

   public JChannel createDataChannel() throws Exception
   {
      return new JChannel(asyncConfig);
   }

   // Public ---------------------------------------------------------------------------------------

   public String getAsyncConfig()
   {
      return asyncConfig;
   }

   public void setAsyncConfig(String asyncConfig)
   {
      this.asyncConfig = asyncConfig;
   }

   public String getSyncConfig()
   {
      return syncConfig;
   }

   public void setSyncConfig(String syncConfig)
   {
      this.syncConfig = syncConfig;
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

}
