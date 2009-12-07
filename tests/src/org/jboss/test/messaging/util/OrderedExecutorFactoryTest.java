/*
 * JBoss, Home of Professional Open Source Copyright 2005, JBoss Inc., and
 * individual contributors as indicated by the @authors tag. See the
 * copyright.txt in the distribution for a full listing of individual
 * contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.jboss.test.messaging.util;

import java.util.ArrayList;
import java.util.concurrent.Executors;

import org.jboss.messaging.util.CompatibleExecutor;
import org.jboss.messaging.util.JBMThreadFactory;
import org.jboss.messaging.util.OrderedExecutorFactory;
import org.jboss.test.messaging.MessagingTestCase;

public class OrderedExecutorFactoryTest extends MessagingTestCase
{

   private ArrayList<CounterRunnable> exeQueue = new ArrayList<CounterRunnable>();

   public OrderedExecutorFactoryTest(String name)
   {
      super(name);
   }

   public void testExecutionOrder()
   {
      OrderedExecutorFactory factory = new OrderedExecutorFactory(Executors.newCachedThreadPool(new JBMThreadFactory("test-thread-factory")));
      CompatibleExecutor executor = factory.getExecutor("test executor");

      final int numTasks = 200000;

      CounterRunnable[] tasks = new CounterRunnable[numTasks];

      for (int i = 0; i < numTasks; ++i)
      {
         tasks[i] = new CounterRunnable(this);
      }
      exeQueue.clear();
      for (int i = 0; i < numTasks; ++i)
      {
         executor.execute(tasks[i]);
      }

      executor.shutdownAfterProcessingCurrentlyQueuedTasks();
      assertTrue(exeQueue.size() == numTasks);

      for (int i = 0; i < numTasks; ++i)
      {
         CounterRunnable finTask = exeQueue.get(i);
         assertTrue(finTask == tasks[i]);
      }

      exeQueue.clear();
   }

   class CounterRunnable implements Runnable
   {
      private OrderedExecutorFactoryTest myUser;

      public CounterRunnable(OrderedExecutorFactoryTest user)
      {
         myUser = user;
      }

      public void run()
      {
         myUser.registerOrder(this);
      }

   }

   public void registerOrder(CounterRunnable task)
   {
      exeQueue.add(task);
   }
}
