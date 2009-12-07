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

/**
 * 
 * A BrowserHasNextMessageResponse
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 2304 $</tt>
 *
 * $Id: BrowserHasNextMessageResponse.java 2304 2007-02-13 23:43:15Z clebert.suconic@jboss.com $
 *
 */
public class BrowserHasNextMessageResponse extends ResponseSupport
{
   private boolean hasNext;
   
   public BrowserHasNextMessageResponse()
   {      
   }  
   
   public BrowserHasNextMessageResponse(boolean hasNext)
   {
      super(PacketSupport.RESP_BROWSER_HASNEXTMESSAGE);
      
      this.hasNext = hasNext;
   }

   public Object getResponse()
   {
      return Boolean.valueOf(hasNext);
   }
   
   public void write(DataOutputStream os) throws Exception
   {
      super.write(os);
      
      os.writeBoolean(hasNext);
      
      os.flush();
   }
   
   public void read(DataInputStream is) throws Exception
   {
      hasNext = is.readBoolean();
   }

}



