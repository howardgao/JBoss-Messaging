/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2009, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.test.messaging.jms.clustering;

import java.util.List;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.jboss.jms.destination.JBossQueue;
import org.jboss.messaging.core.contract.Queue;
import org.jboss.messaging.core.impl.clusterconnection.MessageSucker;

/**
 * A FakeMessageSucker
 *
 * @author howard
 * 
 * Created Sep 14, 2009 2:31:27 PM
 *
 *
 */
public class FakeMessageSucker extends MessageSucker
{
   private List<TextMessage> buffer;

   private Object queueUpdateLock = new Object();

   private boolean queueNotUpdated = true;

   FakeMessageSucker(Queue localQueue, Session sourceSession, long sourceChannelID, List<TextMessage> buffer)
   {
      super(localQueue, sourceSession, sourceSession, true, sourceChannelID);
      this.buffer = buffer;
   }

   // Public --------------------------------------------------------
   public void start() throws Exception
   {
      super.start();
   }

   public void stop()
   {
      super.stop();
   }

   public void updateQueue(JBossQueue q)
   {
      synchronized (queueUpdateLock)
      {
         this.jbq = q;
         queueNotUpdated = false;
         queueUpdateLock.notify();
      }
   }

   public void resume(Session session) throws JMSException
   {
      synchronized (queueUpdateLock)
      {
         if (queueNotUpdated)
         {
            try
            {
               queueUpdateLock.wait(20000);
            }
            catch (InterruptedException e)
            {
            }
         }
      }
      super.resume(session);
   }

   public void onMessage(Message msg)
   {

      try
      {
         buffer.add((TextMessage)msg);
         msg.acknowledge();
      }
      catch (JMSException e)
      {
      }
   }

   public void resetQueue(JBossQueue newQueue)
   {
      jbq = newQueue;
   }

}
