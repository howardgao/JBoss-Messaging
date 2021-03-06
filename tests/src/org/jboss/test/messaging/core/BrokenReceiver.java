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
import java.util.List;

import org.jboss.messaging.core.contract.Delivery;
import org.jboss.messaging.core.contract.DeliveryObserver;
import org.jboss.messaging.core.contract.MessageReference;
import org.jboss.messaging.core.contract.Receiver;
import org.jboss.messaging.core.impl.SimpleDelivery;
import org.jboss.messaging.core.impl.tx.Transaction;


/**
 * A simple Receiver that "breaks" after the
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 2925 $</tt>
 *
 * $Id: BrokenReceiver.java 2925 2007-07-25 10:43:58Z timfox $
 */
public class BrokenReceiver implements Receiver
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   private int counter;
   private int failurePoint;
   private List refs;


   // Constructors --------------------------------------------------

   public BrokenReceiver(int failurePoint)
   {
      refs = new ArrayList();
      this.failurePoint = failurePoint;
   }

   // Receiver implementation ---------------------------------------

   public Delivery handle(DeliveryObserver observer, MessageReference ref, Transaction tx)
   {
      counter++;

      if (counter == failurePoint)
      {
         throw new RuntimeException("I AM BROKEN!");
      }

      refs.add(ref);
      return new SimpleDelivery(observer, ref, true, false);
   }

   // Public --------------------------------------------------------

   public List getMessages()
   {
      return refs;
   }

   public String toString()
   {
      return "BrokenReceiver";
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
