/*
 * JBoss, Home of Professional Open Source
 * Copyright 2007, JBoss Inc., and individual contributors as indicated
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

package org.jboss.test.messaging.jms.server.channelfactory;



import org.jboss.logging.Logger;
import org.jgroups.Channel;

/**
 * Extension to the JGroups JChannelFactory that supports the addition
 * of "additional_data" to the channel config.
 * 
 * @author <a href="mailto://brian.stansberry@jboss.com">Brian Stansberry</a>
 * @author <a href="mailto://clebert.suconic@jboss.com">Clebert Suconic</a> (Only cut unecessary stuff for the MOCK)
 * @version $Revision: 4030 $
 */

public class MockChannelFactory extends org.jgroups.JChannelFactory implements MockChannelFactoryMBean
{
   @Override
   public Channel createMultiplexerChannel(String stack_name, String id, boolean register_for_state_transfer, String substate_id) throws Exception
   {
      // on the mock we aways create a new channel
      return createChannel(stack_name); 
   }
   
   /**
    * Overrides the superclass version by generating a unique node id
    * and passing it down the Channel as additional_data.
    */
   @Override
   public Channel createMultiplexerChannel(String stack_name, String id) throws Exception
   {
      return createMultiplexerChannel(stack_name, id, false, null);
   }
  
}