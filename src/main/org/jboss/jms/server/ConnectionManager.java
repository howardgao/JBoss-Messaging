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
package org.jboss.jms.server;

import java.util.List;

import org.jboss.jms.delegate.ConnectionEndpoint;
import org.jboss.jms.server.endpoint.ServerConnectionFactoryEndpoint;
import org.jboss.messaging.core.contract.MessagingComponent;
import org.jboss.remoting.callback.InvokerCallbackHandler;


/**
 * An interface that allows management of ConnectionEnpoints and their association with remoting
 * clients.
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 4986 $</tt>
 *
 * $Id: ConnectionManager.java 4986 2008-09-18 22:11:43Z clebert.suconic@jboss.com $
 */
public interface ConnectionManager extends MessagingComponent
{
   void registerConnection(String jmsClientVMId,
                           String remotingClientSessionID,
                           ConnectionEndpoint endpoint);

   /**
    * @return null if there is no such connection.
    */
   ConnectionEndpoint unregisterConnection(String jmsClientVMID, String remotingClientSessionID);
   
   boolean containsRemotingSession(String remotingClientSessionID);

   /**
    * Returns a list of active connection endpoints currently maintained by an instance of this
    * manager. The implementation should make a copy of the list to avoid
    * ConcurrentModificationException. The list could be empty, but never null.
    *
    * @return List<ConnectionEndpoint>
    */
   List getActiveConnections();
   
   void registerConnectionFactoryCallback(String JVMID, String remotingSessionID, InvokerCallbackHandler handler);

   void unregisterConnectionFactoryCallback(String JVMID, String remotingSessionID);
   

   void handleClientFailure(String remotingSessionID);
   
   void registerConnectionFactory(ServerConnectionFactoryEndpoint cf);
   
   void unregisterConnectionFactory(ServerConnectionFactoryEndpoint cf);
}
