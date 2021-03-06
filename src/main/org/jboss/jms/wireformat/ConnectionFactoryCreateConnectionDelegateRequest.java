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

import org.jboss.jms.delegate.CreateConnectionResult;
import org.jboss.jms.server.endpoint.advised.ConnectionFactoryAdvised;
import org.jboss.remoting.callback.ServerInvokerCallbackHandler;

/**
 * A ConnectionFactoryCreateConnectionDelegateRequest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 3019 $</tt>
 *
 * $Id: ConnectionFactoryCreateConnectionDelegateRequest.java 3019 2007-08-21 04:19:48Z clebert.suconic@jboss.com $
 *
 */
public class ConnectionFactoryCreateConnectionDelegateRequest extends CallbackRequestSupport
{
   private String username;
   
   private String password;
   
   private int failedNodeId;
   
   private transient ServerInvokerCallbackHandler callbackHandler;
   
   public ConnectionFactoryCreateConnectionDelegateRequest()
   {      
   }

   public ConnectionFactoryCreateConnectionDelegateRequest(String objectId,
                                                           byte version,
                                                           String remotingSessionId,
                                                           String clientVMId,
                                                           String username, String password,
                                                           int failedNodeId)
   {
      super(clientVMId, remotingSessionId, objectId, PacketSupport.REQ_CONNECTIONFACTORY_CREATECONNECTIONDELEGATE, version);
      
      this.username = username;
      
      this.password = password;
      
      this.failedNodeId = failedNodeId;
   }

   public void read(DataInputStream is) throws Exception
   {
      super.read(is);
      
      username = readNullableString(is);
      
      password = readNullableString(is);
      
      failedNodeId = is.readInt();
   }

   public ResponseSupport serverInvoke() throws Exception
   {         
      ConnectionFactoryAdvised advised =
         (ConnectionFactoryAdvised)Dispatcher.instance.getTarget(objectId);
      
      if (advised == null)
      {
         throw new IllegalStateException("Cannot find object in dispatcher with id " + objectId);
      }
      
      CreateConnectionResult del = 
         advised.createConnectionDelegate(username, password, failedNodeId,
                                           getRemotingSessionID(), getClientVMID(), version,
                                           callbackHandler);
      
      return new ConnectionFactoryCreateConnectionDelegateResponse(del);
   }

   public void write(DataOutputStream os) throws Exception
   {
      super.write(os);
      
      //Write the args
           
      writeNullableString(username, os);
      
      writeNullableString(password, os);
      
      os.writeInt(failedNodeId); 
      
      os.flush();
   }
   
   public ServerInvokerCallbackHandler getCallbackHandler()
   {
      return this.callbackHandler;
   }
   
   public void setCallbackHandler(ServerInvokerCallbackHandler handler)
   {
      this.callbackHandler = handler;
   }

}
