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

/**
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @version <tt>$Revision: 3261 $</tt>
 *          $Id: MessagingShutdownException.java 3261 2007-10-30 04:25:11Z clebert.suconic@jboss.com $
 */
public class MessagingShutdownException extends MessagingJMSException
{
   private static final long serialVersionUID = -2234413113067993577L;

   // Constructors ---------------------------------------------------------------------------------

   public MessagingShutdownException(String reason)
   {
      super(reason);
   }

   public MessagingShutdownException(Throwable cause)
   {
      super(cause);
   }

   public MessagingShutdownException(String reason, String errorCode)
   {
      super(reason, errorCode);
   }

   public MessagingShutdownException(String reason, Throwable cause)
   {
      super(reason, cause);
   }

   public MessagingShutdownException(String reason, String errorCode, Throwable cause)
   {
      super(reason, errorCode, cause);
   }
}
