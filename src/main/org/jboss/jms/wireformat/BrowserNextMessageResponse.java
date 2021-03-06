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

import org.jboss.jms.message.JBossMessage;
import org.jboss.messaging.core.impl.message.MessageFactory;

/**
 * 
 * A BrowserNextMessageResponse
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 2796 $</tt>
 *
 * $Id: BrowserNextMessageResponse.java 2796 2007-06-25 22:24:41Z timfox $
 *
 */
public class BrowserNextMessageResponse extends ResponseSupport
{
   private JBossMessage msg;
   
   public BrowserNextMessageResponse()
   {      
   }
   
   public BrowserNextMessageResponse(JBossMessage msg)
   {
      super(PacketSupport.RESP_BROWSER_NEXTMESSAGE);
      
      this.msg = msg;
   }

   public Object getResponse()
   {
      return msg;
   }
   
   public void write(DataOutputStream os) throws Exception
   {
      super.write(os);
      
      os.writeByte(msg.getType());
      
      msg.write(os);
      
      os.flush();
   }
   
   public void read(DataInputStream is) throws Exception
   {
      byte type = is.readByte();
      
      msg = (JBossMessage)MessageFactory.createMessage(type);
      
      msg.read(is);
   }

}



