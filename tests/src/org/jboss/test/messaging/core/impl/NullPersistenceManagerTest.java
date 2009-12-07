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
package org.jboss.test.messaging.core.impl;

import java.util.Hashtable;
import java.util.Random;

import org.jboss.messaging.core.impl.NullPersistenceManager;
import org.jboss.test.messaging.MessagingTestCase;

public class NullPersistenceManagerTest extends MessagingTestCase
{

   public NullPersistenceManagerTest(String name)
   {
      super(name);
   }

   /**
    * To make sure that
    * 1. the server peer ID must be between 0 and 255
    * 2. the size should not exceed Short.MAX_VALUE
    * 3. the block generated should be unique even
    *    after the manager was reset due to failures.
    */
   public void testReserveIDBlock()
   {
      NullPersistenceManager manager = new NullPersistenceManager();
      manager.setPeerID(65536);
      manager.setTimeMark(System.currentTimeMillis());

      try
      {
         manager.reserveIDBlock("testCounter", 255);
         fail("Failed to throw exception when server peer ID exceeding 255.");
      }
      catch (Exception e)
      {
         log.debug(e.getMessage());
      }

      manager.setPeerID(-1);
      try
      {
         manager.reserveIDBlock("testCounter", 255);
         fail("Failed to throw exception when server peer ID negative.");
      }
      catch (Exception e)
      {
         log.debug(e.getMessage());
      }

      manager.setPeerID(255);
      try
      {
         manager.reserveIDBlock("testCounter", Short.MAX_VALUE + 1);
         fail("Failed to throw exception when block size exceeds Short.MAX_VALUE");
      }
      catch (Exception e)
      {
         log.debug(e.getMessage());
      }

      long nextID = 0;
      int ftimes = 0;
      Hashtable<String, Integer> data = new Hashtable<String, Integer>();
      try
      {
         for (int i = 0; i < 500; ++i)
         {
            int szblock = getRandomBlocksize();
            nextID = manager.reserveIDBlock("testCounter", szblock);
            // simulate failure
            if (getFailure(i))
            {
               manager = new NullPersistenceManager();
               manager.setPeerID(255);
               manager.setTimeMark(System.currentTimeMillis());
               ftimes++;
            }
            checkData(data, nextID, szblock);
         }
      }
      catch (Exception e)
      {
         fail("Exception calling reserveIDBlock()");
         log.error(e);
      }
      log.debug("failure times: " + ftimes);
      System.out.println("data in set: " + data.size());
      System.out.println("failure times: " + ftimes);
      System.gc();

   }

   private void checkData(Hashtable<String, Integer> data, long nID, int szblock)
   {
      long id = nID;
      for (int i = 0; i < szblock; ++i)
      {
         // every ID should be unique
         String key = Long.toHexString(id);
         assertNull(data.get(key));
         data.put(key, szblock);
         id++;
      }

   }

   // generate a number big enough to cause a wrap to happen.
   private int getRandomBlocksize()
   {
      int base = 1024;
      Random var = new Random();
      base += var.nextInt(50);
      return base;
   }

   // check if i can be divided by 53 plus a random
   private boolean getFailure(int i)
   {
      Random var = new Random();
      int num = 43 + var.nextInt(9);
      boolean failure = ((i + 1) % num == 0);
      if (var.nextBoolean())
      {
         try
         {
            Thread.sleep(var.nextInt(9) * 5);
         }
         catch (InterruptedException e)
         {
         }
      }
      return failure;
   }

}
