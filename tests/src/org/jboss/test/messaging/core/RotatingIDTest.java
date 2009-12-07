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
package org.jboss.test.messaging.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.messaging.core.impl.RotatingID;
import org.jboss.test.messaging.MessagingTestCase;

/**
 *
 * A RotatingIDTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 *
 */
public class RotatingIDTest extends MessagingTestCase
{
   // Constants -----------------------------------------------------

   private String failMsg = null;
   
   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public RotatingIDTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------
   
   public void setUp() throws Exception
   {
      super.setUp();
      failMsg = null;
   }
   

   public void testRotatingID() throws Exception
   {
      final int NUM_ITERS = 1000;

      Set<Long> ids = new HashSet<Long>(NUM_ITERS);

      RotatingID id = new RotatingID(63);

      for (int i = 0; i < NUM_ITERS; i++)
      {
         long l = id.getID();

         if (ids.contains(l))
         {
            fail("Already produced id " + l);
         }

         ids.add(l);

         System.out.println(l);
      }
   }
   
   public void testNodeIDLimits() throws Exception
   {
      //max id 1023, fine
      int nodeID = 0x03FF;
      new RotatingID(nodeID);
      
      //min id 0, fine
      new RotatingID(0);
      
      //wrong id 1024, exceeding max
      nodeID = 1024;
      try
      {
         new RotatingID(nodeID);
         fail("shouldn't allow values greater than 1023!");
      }
      catch (IllegalArgumentException e)
      {
         //ignore
      }
      
      //negative id
      nodeID = -1;
      try
      {
         new RotatingID(nodeID);
         fail("shouldn't allow negative values!");
      }
      catch (IllegalArgumentException e)
      {
         //ignore
      }
   }

   //Because we now use 4th to 42nd bits of time, 
   //the max rate of generating id should never be greater than 4096000 ids/sec.
   public void testIDGenSpeed() throws Exception
   {
      RotatingID id = new RotatingID(555);

      long beginTm = System.currentTimeMillis();
      
      final int NUM_IDS = 4096000;
      
      for (int i = 0; i < NUM_IDS; i++)
      {
         id.getID();
      }

      long endTm = System.currentTimeMillis();
      
      long dur = (endTm - beginTm)/1000;
      
      long rate = NUM_IDS/dur;
      
      log.info("duration: " + dur);
      log.info("sending Rate: " + rate);
      
      assertTrue(rate <= 4096000);
   }

   //https://jira.jboss.org/jira/browse/JBMESSAGING-1682
   //this test new a RotatingID with node id 0200
   //in order to examine that the node id field is not
   //overlapped with the time field.
   public void testRotatingIDStructure() throws Exception
   {
      final int nodeID = 0x0200;
      RotatingID id = new RotatingID(nodeID);
      final int NUM_ID = 65535;
      Set<Long> ids = new HashSet<Long>(NUM_ID);
      List<Long> idlst = new ArrayList<Long>(NUM_ID);

      long beginTm = System.currentTimeMillis();
      
      try
      {
         Thread.sleep(1000);
      }
      catch (InterruptedException e)
      {
         //ignore
      }
      
      for (int i = 0; i < NUM_ID; i++)
      {
         long mid = id.getID();
         if (ids.contains(mid))
         {
            fail("Already produced id " + mid);
         }
         ids.add(mid);
         idlst.add(mid);
      }

      try
      {
         Thread.sleep(1000);
      }
      catch (InterruptedException e)
      {
         //ignore
      }
      
      long endTm = System.currentTimeMillis();
      
      long beginTmVal = (beginTm >> 3) & 0x0000007FFFFFFFFFL;
      long endTmVal = (endTm >> 3) & 0x0000007FFFFFFFFFL;
      
      for (int i = 0; i < NUM_ID; i++)
      {
         long gid = idlst.get(i);
         
         long nodeIdVal = (gid >> 54) & 0x00000000000003FFL;
         assertEquals(nodeID, nodeIdVal);
         
         long tmVal = (gid >> 15) & 0x0000007FFFFFFFFFL;
         assertTrue(tmVal > beginTmVal);
         assertTrue(tmVal < endTmVal);
         
         long countVal = gid & 0x0000000000007FFFL;
         assertEquals(countVal, i%(Short.MAX_VALUE + 1));
      }

   }
   
   //50 generators, each generating 100,000 ids, 
   //check uniqueness of those ids
   public void testIDConflict() throws Exception
   {
      final int numNodes = 50;
      Random rand = new Random();
      final Map<Long, Long> ids = new ConcurrentHashMap<Long, Long>(10000000);
      ExecutorService pool = Executors.newFixedThreadPool(50);

      int nid = rand.nextInt(1024);
      for (int i = 0; i < numNodes; i++)
      {
         int id = (nid+i)%1024;
         log.info("Random node id: " + id);
         pool.execute(new IDGenerator(new RotatingID(id), ids));
      }
      
      pool.shutdown();
      pool.awaitTermination(600, TimeUnit.SECONDS);
      
      assertNull(failMsg, failMsg);
   }

   public synchronized void setFail(String error)
   {
      failMsg = error;
   }
   
   public synchronized boolean notFail()
   {
      return failMsg == null;
   }
   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
   private class IDGenerator implements Runnable
   {
      private RotatingID idGen;
      private Map<Long, Long> holder;
      
      public IDGenerator(RotatingID rid, Map<Long, Long> ids)
      {
         idGen = rid;
         holder = ids;
      }
      
      public void run()
      {
         for (int i = 0; i < 100000 && notFail(); i++)
         {
            long id = idGen.getID();
            if (holder.get(id) != null)
            {
               setFail("Duplicated ID has been generated: " + id);
            }
            holder.put(id, id);
         }
      }
   }
}

