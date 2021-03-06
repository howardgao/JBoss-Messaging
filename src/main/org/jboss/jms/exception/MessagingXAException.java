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
package org.jboss.jms.exception;

import javax.transaction.xa.XAException;

/**
 * 
 * A MessagingXAException

 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 2685 $</tt>
 *
 * $Id: MessagingXAException.java 2685 2007-05-15 07:56:12Z timfox $
 *
 */
public class MessagingXAException extends XAException
{
   private static final long serialVersionUID = 1144870736311098699L;
   
   private Throwable cause;
   
   private String msg;
   
   public MessagingXAException(int code)
   {
      super(code);
   }
   
   public MessagingXAException(int code, Throwable cause)
   {
      super(code);
      
      this.cause = cause;
   }
   
   public MessagingXAException(int code, String msg)
   {
      super(code);
      
      this.msg = msg;
   }
   
   public MessagingXAException(int code, String msg, Throwable cause)
   {
      super(code);
      
      this.msg = msg;
      
      this.cause = cause;
   }
   
   public Throwable getCause()
   {
      return cause;
   }
   
   public String getMessage()
   {
      return msg;
   }
}
