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
package org.jboss.jms.client.state;

import java.util.Collections;
import java.util.UUID;

import javax.jms.DeliveryMode;
import javax.jms.Destination;

import org.jboss.jms.client.delegate.DelegateSupport;
import org.jboss.jms.delegate.ProducerDelegate;
import org.jboss.messaging.util.Version;

/**
 * State corresponding to a producer. This state is acessible inside aspects/interceptors.
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodoorv</a>
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 *
 * @version <tt>$Revision: 6821 $</tt>
 *
 * $Id: ProducerState.java 6821 2009-05-16 13:23:49Z gaohoward $
 */
public class ProducerState extends HierarchicalStateSupport
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   private Destination destination;

   private boolean disableMessageID = false;
   private boolean disableMessageTimestamp = false;
   private int priority = 4;
   private long timeToLive = 0;
   private int deliveryMode = DeliveryMode.PERSISTENT;
   private int strictTCK; // cache here

   private SessionState parent;
   private ProducerDelegate delegate;

   private boolean isOrderingGroupEnabled = false;
   private String orderingGroupName = null;
   // Constructors ---------------------------------------------------------------------------------

   public ProducerState(SessionState parent, ProducerDelegate delegate, Destination dest)
   {
      super(parent, (DelegateSupport)delegate);
      children = Collections.EMPTY_SET;
      this.destination = dest;
   }

   // HierarchicalState implementation -------------------------------------------------------------

   public DelegateSupport getDelegate()
   {
      return (DelegateSupport)delegate;
   }

   public void setDelegate(DelegateSupport delegate)
   {
      this.delegate=(ProducerDelegate)delegate;
   }

   public void setParent(HierarchicalState parent)
   {
      this.parent = (SessionState)parent;
   }

   public HierarchicalState getParent()
   {
      return parent;
   }

   public Version getVersionToUse()
   {
      return parent.getVersionToUse();
   }

   // HierarchicalStateSupport overrides -----------------------------------------------------------

   public void synchronizeWith(HierarchicalState newState) throws Exception
   {
      // nothing to do here, ProducerState is a modest state
   }

   // Public ---------------------------------------------------------------------------------------

   public Destination getDestination()
   {
      return destination;
   }

   public void setDestination(Destination dest)
   {
      this.destination = dest;

   }
   public boolean isDisableMessageID()
   {
      return disableMessageID;
   }

   public void setDisableMessageID(boolean disableMessageID)
   {
      this.disableMessageID = disableMessageID;
   }

   public boolean isDisableMessageTimestamp()
   {
      return disableMessageTimestamp;
   }

   public void setDisableMessageTimestamp(boolean disableMessageTimestamp)
   {
      this.disableMessageTimestamp = disableMessageTimestamp;
   }

   public int getPriority()
   {
      return priority;
   }

   public void setPriority(int priority)
   {
      this.priority = priority;
   }

   public long getTimeToLive()
   {
      return timeToLive;
   }

   public void setTimeToLive(long timeToLive)
   {
      this.timeToLive = timeToLive;
   }

   public int getDeliveryMode()
   {
      return deliveryMode;
   }

   public void setDeliveryMode(int deliveryMode)
   {
      this.deliveryMode = deliveryMode;
   }

   public void setOrderingGroupEnabled(boolean isOrderingGroupEnabled)
   {
      this.isOrderingGroupEnabled = isOrderingGroupEnabled;
   }

   public boolean isOrderingGroupEnabled()
   {
      return isOrderingGroupEnabled;
   }

   public void setOrderingGroupName(String ordGroupName)
   {
      if (ordGroupName == null)
      {
         ordGroupName = "JBM-ORD-GRP:" + UUID.randomUUID().toString();
      }
      this.orderingGroupName = ordGroupName;
   }

   public String getOrderingGroupName()
   {
      return orderingGroupName;
   }

   //let the session reset the ordering group
   public void disableOrderingGroup()
   {
      if (isOrderingGroupEnabled)
      {
         setOrderingGroupEnabled(false);
         parent.removeOrderingGroup(orderingGroupName);
      }
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------


}



