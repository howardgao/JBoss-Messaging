
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

import org.jboss.jms.delegate.BrowserEndpoint;

/**
 * 
 * A BrowserNextMessageBlockRequest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 2857 $</tt>
 *
 * $Id: BrowserNextMessageBlockRequest.java 2857 2007-07-08 13:21:39Z timfox $
 *
 */
public class BrowserNextMessageBlockRequest extends RequestSupport
{
   private int maxMessages;
   
   public BrowserNextMessageBlockRequest()
   {      
   }
   
   public BrowserNextMessageBlockRequest(String objectId,
                                         byte version,
                                         int maxMessages)
   {
      super(objectId, PacketSupport.REQ_BROWSER_NEXTMESSAGEBLOCK, version);
      
      this.maxMessages = maxMessages;
   }

   public void read(DataInputStream is) throws Exception
   {
      super.read(is);
      
      maxMessages = is.readInt();
   }

   public ResponseSupport serverInvoke() throws Exception
   {
      BrowserEndpoint endpoint = 
         (BrowserEndpoint)Dispatcher.instance.getTarget(objectId);
      
      if (endpoint == null)
      {
         throw new IllegalStateException("Cannot find object in dispatcher with id " + objectId);
      }
      
      return new BrowserNextMessageBlockResponse(endpoint.nextMessageBlock(maxMessages));
   }

   public void write(DataOutputStream os) throws Exception
   {
      super.write(os);
      
      os.writeInt(maxMessages);
      
      os.flush();
   }
}




