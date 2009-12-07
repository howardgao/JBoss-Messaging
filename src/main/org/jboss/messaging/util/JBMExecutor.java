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

package org.jboss.messaging.util;

/** Any Executor being used on client side has to clean its contextClassLoader, as that could cause leaks.
 *  This class encapsulates the necessary cleanup to avoid that leak.
 *
 * This class also extends the QueuedExector with a method to clear all but the currently
 * executing task without shutting it down.
 *
 * We need this functionality when failing over a session.
 *
 * In that case we need to clear all tasks apart from the currently executing one.
 *
 * We can't just shutdownAfterProcessingCurrentTask then use another instance
 * after failover since when failover resumes the current task and the next delivery
 * will be executed on different threads and smack into each other.
 *
 * http://jira.jboss.org/jira/browse/JBMESSAGING-904
 * http://jira.jboss.com/jira/browse/JBMESSAGING-1200
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 **/
public class JBMExecutor extends NamedThreadQueuedExecutor
{

   private boolean needToSetClassLoader = true;

   public JBMExecutor(final String name)
   {
      super(name);
   }

   class TCLRunnable implements Runnable
   {

      private final Runnable realRunnable;

      private ClassLoader tcl;

      public TCLRunnable(final Runnable realRunnable)
      {
         if (needToSetClassLoader)
         {
            tcl = SecurityActions.getTCL();
         }

         this.realRunnable = realRunnable;
      }

      @SuppressWarnings("unchecked")
      public void run()
      {
         if (needToSetClassLoader)
         {
            needToSetClassLoader = false;
            SecurityActions.setTCL(tcl);
         }
         realRunnable.run();
      }
   }

   @Override
   public void execute(final Runnable runnable) throws InterruptedException
   {
      super.execute(new TCLRunnable(runnable));
   }

   public void clearClassLoader() throws InterruptedException
   {
      super.execute(new Runnable()
      {
         @SuppressWarnings("unchecked")
         public void run()
         {
            needToSetClassLoader = true;
         }
      });

   }

   public void clearAllExceptCurrentTask()
   {
      try
      {
         while (queue_.poll(0) != null)
         {
            ;
         }
      }
      catch (InterruptedException ex)
      {
         Thread.currentThread().interrupt();
      }
   }

}
