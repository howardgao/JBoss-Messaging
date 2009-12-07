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

import org.jboss.util.threadpool.BasicThreadPool;
import org.jboss.util.threadpool.ThreadPool;
import org.jboss.util.timeout.TimeoutFactory;

/**
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 3365 $</tt>
 *
 * $Id: MessagingTimeoutFactory.java 3365 2007-11-26 15:58:09Z timfox $
 *
 */
public class MessagingTimeoutFactory
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   public static MessagingTimeoutFactory instance = new MessagingTimeoutFactory();

   // Attributes -----------------------------------------------------------------------------------

   private TimeoutFactory factory;

   // Constructors ---------------------------------------------------------------------------------

   private MessagingTimeoutFactory()
   {
      createFactory();
   }

   // Public ---------------------------------------------------------------------------------------

   public TimeoutFactory getFactory()
   {
      return factory;
   }

   public synchronized void reset()
   {
      factory.cancel();
      createFactory();
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   private void createFactory()
   {
      BasicThreadPool threadPool = new BasicThreadPool("Messaging Timeout");
      threadPool.setMaximumQueueSize(Integer.MAX_VALUE);
      factory = new TimeoutFactory(threadPool);
   }

   // Inner classes --------------------------------------------------------------------------------

}
