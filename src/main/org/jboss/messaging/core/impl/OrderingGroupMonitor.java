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
import java.util.Iterator;

import javax.jms.JMSException;

import org.jboss.jms.message.JBossMessage;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.contract.Message;
import org.jboss.messaging.core.contract.MessageReference;

/**
*
* This class guards against any delivering of ordering group messages.
*
* @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
*
*/
public class OrderingGroupMonitor
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------
   private HashMap<String, OrderingGroup> orderingGroups = new HashMap<String, OrderingGroup>();

   // Static --------------------------------------------------------
   private static final Logger log = Logger.getLogger(OrderingGroupMonitor.class);

   public static final int OK = 0;
   public static final int NOT_OK_NOT_FIRST = 1;

   public static final int NOT_OK_BEING_SENT = 2;

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   /**
    * Check the message is it is a member of an ordering group, if so,
    * put it in; if not, do nothing.
    * if the message is dropped due to maxSize being reached, it won't 
    * get registered.
    */
   public void registerMessage(MessageReference ref)
   {
      String grpName = extractGroupName(ref);
      if (grpName == null)
      {
         return;
      }
      synchronized (orderingGroups)
      {
         OrderingGroup group = orderingGroups.get(grpName);
         if (group == null)
         {
            group = new OrderingGroup(grpName);
            orderingGroups.put(grpName, group);
         }
         group.register(ref);
      }
   }

   /**
    * If ref is not in our registry, just return OK.
    * If in our registry, check if the ref is the first of the group.
    * return OK if it is at the first place. 
    * otherwise return NOT_OK_BEING_SENT if the ref is being sent or
    * NOT_OK_NOT_FIRST is the ref is not at the first place.
    */
   public int isAvailable(MessageReference ref)
   {
      int result = OK;
      String grpName = extractGroupName(ref);
      if (grpName != null)
      {
         synchronized (orderingGroups)
         {
            OrderingGroup group = orderingGroups.get(grpName);
            if (group != null)
            {
               result = group.isAvailable(ref);
            }
         }
      }
      else
      {
         log.debug("message doesn't have group prop, fine by me");
      }
      return result;
   }

   /**
    * This method indicates a messgae is completed.
    * it is called when a message is acked, commited or rollback
    * once the message is completed, the next one in a ordering 
    * group becomes deliverable.
    * return if there is more messages available after this one.
    */
   public boolean messageCompleted(MessageReference ref)
   {
      String grpName = extractGroupName(ref);
      if (grpName == null)
      {
         //not a ordering group message
         return false;
      }
      synchronized (orderingGroups)
      {
         OrderingGroup group = orderingGroups.get(grpName);
         if (group != null)
         {
            group.unregister(ref);
         }
         return this.hasMessageInQueue();
      }
   }

   /**
    * Check if there is any pending messages in any group.
    */
   private boolean hasMessageInQueue()
   {
      boolean result = false;
      synchronized (orderingGroups)
      {
         Iterator<OrderingGroup> iter = orderingGroups.values().iterator();
         while (iter.hasNext())
         {
            OrderingGroup group = iter.next();
            if (group.hasPendingMessage())
            {
               result = true;
               break;
            }
         }
         
      }
      return result;
   }

   /**
    * reducing the refcount, if zero, remove it.
    */
   public void unmarkSending(MessageReference ref)
   {
      String grpName = extractGroupName(ref);
      if (grpName == null)
      {
         return;
      }
      synchronized (orderingGroups)
      {
         OrderingGroup group = orderingGroups.get(grpName);
         if (group != null)
         {
            group.unmarkSending(ref);
         }
      }
   }

   public void markSending(MessageReference ref)
   {
      String grpName = extractGroupName(ref);
      if (grpName != null)
      {
         synchronized (orderingGroups)
         {
            OrderingGroup group = orderingGroups.get(grpName);
            if (group != null)
            {
               group.markSending(ref);
            }
         }
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------
   private static String extractGroupName(MessageReference ref)
   {
      String name = null;
      try
      {
         Message obj = ref.getMessage();
         if (obj instanceof JBossMessage)
         {
            JBossMessage msg = (JBossMessage)ref.getMessage();
            if (msg != null)
            {
               name = msg.getStringProperty(JBossMessage.JBOSS_MESSAGING_ORDERING_GROUP_ID);
            }
         }
      }
      catch (JMSException e)
      {
      }
      return name;
   }

   /**
    * check if the message has a group name
    */
   public static boolean isOrderingGroupMessage(MessageReference ref)
   {
      return extractGroupName(ref) != null;
   }

   // Inner classes -------------------------------------------------

}
