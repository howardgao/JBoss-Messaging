/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.jms.JMSException;

import org.jboss.logging.Logger;
import org.jboss.messaging.core.contract.MessageReference;

/**
*
* This class holds the states of messages in an ordering group.
*
* @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
*
*/
public class OrderingGroup
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------
   private List<ReferenceHolder> sortedList = new LinkedList<ReferenceHolder>();

   private HashMap<Long, ReferenceHolder> refMap = new HashMap<Long, ReferenceHolder>();

   private String groupName;

   // Static --------------------------------------------------------
   private static final Logger log = Logger.getLogger(OrderingGroup.class);

   // Constructors --------------------------------------------------
   public OrderingGroup(String name)
   {
      groupName = name;
   }

   private OrderingGroup()
   {
   }

   // Public --------------------------------------------------------
   /**
    * Adding a message to a list. As messages are coming in order, we can just put
    * the message to the end of the list.
    * 
    * @param ref The message reference to be added
    * 
    * @throws JMSException 
    */
   public boolean register(MessageReference ref)
   {
      Long mid = ref.getMessage().getMessageID();
      ReferenceHolder holder = refMap.get(mid);
      if (holder != null)
      {
         return true;
      }
      try
      {
         holder = new ReferenceHolder(mid);
      }
      catch (JMSException e)
      {
         log.error("error creating ReferenceHolder. ", e);
      }
      if (holder == null)
      {
         return false;
      }
      sortedList.add(holder);
      refMap.put(mid, holder);
      return true;
   }

   /**
    * See if the ref be there and be the first
    * Simply comparing the addresses simply doesn't work!
    */
   public int isAvailable(MessageReference ref)
   {
      if (sortedList.size() == 0) {
         return OrderingGroupMonitor.OK;
      }
      
      ReferenceHolder holder = sortedList.get(0);
      return holder.isAvailable(ref);
   }

   /**
    * remove the message reference from the group
    * Note: the ref will be removed if and only if the ref
    * resides the first in the list, otherwise just ignore it.
    */
   public void unregister(MessageReference ref)
   {
      if (sortedList.size() == 0) {
         return;
      }
      ReferenceHolder holder = sortedList.get(0);
      if (holder == null)
      {
         return;
      }
      if (holder.matchMessage(ref))
      {
         long count = holder.releaseRef();
         if (count == 0)
         {
            sortedList.remove(0);
            refMap.remove(ref.getMessage().getMessageID());
         }
      }
   }

   /**
    * check if there are more message available in the list.
    */
   public boolean hasPendingMessage()
   {
      boolean result = false;
      if (sortedList.size() == 0)
      {
         return result;
      }
      result = sortedList.size() > 0;
      return result;
   }

   /**
    * Set the flag that the Message is being delivered.
    * @param ref
    */
   public void markSending(MessageReference ref)
   {
      if (sortedList.size() == 0)
      {
         return;
      }
      ReferenceHolder holder = sortedList.get(0);
      if (holder.matchMessage(ref))
      {
         holder.markSending();
      }
   }

   public String getGroupName()
   {
      return groupName;
   }

   /**
    * @param ref
    */
   public void unmarkSending(MessageReference ref)
   {
      if (sortedList.size() == 0)
      {
         return;
      }
      ReferenceHolder holder = sortedList.get(0);
      if (holder.matchMessage(ref))
      {
         holder.unmarkSending();
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}

class ReferenceHolder
{
   private Long mid;

   private long refCount;

   private long pendingSentCount;

   public ReferenceHolder(Long id) throws JMSException
   {
      mid = id;
      refCount = 1;
      pendingSentCount = 0;
   }

   public void markSending()
   {
      pendingSentCount++;
   }

   public long unmarkSending()
   {
      if (pendingSentCount > 0)
      {
         pendingSentCount--;
      }
      return pendingSentCount;
   }

   public boolean isPending()
   {
      return pendingSentCount > 0;
   }

   public int isAvailable(MessageReference exRef)
   {
      if (matchMessage(exRef))
      {
         if (pendingSentCount < refCount)
         {
            return OrderingGroupMonitor.OK;
         }
         return OrderingGroupMonitor.NOT_OK_BEING_SENT;
      }
      return OrderingGroupMonitor.NOT_OK_NOT_FIRST;
   }

   /**
    * So far only allowed to register once. 
    */
   public void addRef()
   {
      refCount++;
   }

   public long releaseRef()
   {
      if (refCount > 0)
      {
         refCount--;
      }
      return refCount;
   }

   /**
    * decrease the ref count
    * here we don't care about pendingSentCount here.
    */
   public long releaseSendnRef()
   {
      refCount--;
      pendingSentCount--;
      return refCount;
   }

   /**
    * If the holder holds the same message as in the ref.
    */
   public boolean matchMessage(MessageReference newRef)
   {
      Long mid1 = newRef.getMessage().getMessageID();
      return mid1.equals(mid);
   }

   public Long getMessageID()
   {
      return mid;
   }

}
