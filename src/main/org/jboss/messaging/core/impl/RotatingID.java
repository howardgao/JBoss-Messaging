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
package org.jboss.messaging.core.impl;

/**
 *
 * A RotatingID
 *
 * 64 bits, made up of:
 *
 * First 10 bits - node id
 * Next 39 bits - 4th - 42nd bits of system time
 * Next 15 bits - rotating counter
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 *
 */
public class RotatingID
{
   private short count;

   private long lastTime = System.currentTimeMillis();

   private final long nodeID;
   
   private final long id1;

   public RotatingID(final int nodeID)
   {
      if (nodeID < 0 || nodeID > 1023)
      {
         throw new IllegalArgumentException("node id must be between 0 to 1023 inclusive, wrong id: " + nodeID);
      }

      this.nodeID = nodeID;
      id1 = this.nodeID << 54;
   }

   public synchronized long getID()
   {
      //https://jira.jboss.org/jira/browse/JBMESSAGING-1682
      long id2 = (System.currentTimeMillis() << 12) & 0x003FFFFFFFFF8000L;

      long id = id1 | id2 | count;

      if (count == Short.MAX_VALUE)
      {
         count = 0;

         long now = System.currentTimeMillis();

         //Safety - not likely to happen

         while (now <= lastTime + 8)
         {
            try
            {
               Thread.sleep(8);
            }
            catch (InterruptedException e)
            {}

            now = System.currentTimeMillis();
         }

         lastTime = now;
      }
      else
      {
         count++;
      }

      return id;
   }
}



