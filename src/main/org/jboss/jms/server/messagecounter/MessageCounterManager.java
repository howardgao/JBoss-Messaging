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
package org.jboss.jms.server.messagecounter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.jboss.logging.Logger;
import org.jboss.messaging.core.contract.MessagingComponent;

/**
 * 
 * A MessageCounterManager
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 3307 $</tt>
 *
 * $Id: MessageCounterManager.java 3307 2007-11-09 20:43:00Z timfox $
 *
 */
public class MessageCounterManager implements MessagingComponent
{
   private static final Logger log = Logger.getLogger(MessageCounterManager.class);
   
   private Map messageCounters;
   
   private boolean started;
   
   private Timer timer;
   
   private long period;
   
   private PingMessageCountersTask task;
          
   public MessageCounterManager(long period)
   {
      messageCounters = new HashMap();
      
      this.period = period;
   }

   public synchronized void start()
   {
      if (started)
      {  
         return;
      }
      
      // Needs to be daemon
      timer = new Timer(true);
      
      task = new PingMessageCountersTask();
            
      timer.schedule(task, 0, period);      
      
      started = true;      
   }

   public synchronized void stop()
   { 
      if (!started)
      {
         return;
      }
      
      //Wait for timer task to stop
      
      task.stop();
      
      timer.cancel();
      
      timer = null;
      
      started = false;
   }
   
   public synchronized void reschedule(long newPeriod)
   {
      boolean wasStarted = this.started;
      
      if (wasStarted)
      {
         stop();
      }
      
      period = newPeriod;
      
      if (wasStarted)
      {
         start();
      }
   }
   
   public void registerMessageCounter(String name, MessageCounter counter)
   {
      synchronized (messageCounters)
      {
         messageCounters.put(name, counter);
      }
   }
   
   public MessageCounter unregisterMessageCounter(String name)
   {
      synchronized (messageCounters)
      {
         return (MessageCounter)messageCounters.remove(name);
      }
   }
   
   public Set getMessageCounters()
   {
      synchronized (messageCounters)
      {
         return new HashSet(messageCounters.values());
      }
   }
   
   public MessageCounter getMessageCounter(String name)
   {
      synchronized (messageCounters)
      {
         return (MessageCounter)messageCounters.get(name);
      }
   }
   
   public void resetAllCounters()
   {
      synchronized (messageCounters)
      {
         Iterator iter = messageCounters.values().iterator();
         
         while (iter.hasNext())
         {
            MessageCounter counter = (MessageCounter)iter.next();
            
            counter.resetCounter();
         }
      }
   }
   
   public void resetAllCounterHistories()
   {
      synchronized (messageCounters)
      {
         Iterator iter = messageCounters.values().iterator();
         
         while (iter.hasNext())
         {
            MessageCounter counter = (MessageCounter)iter.next();
            
            counter.resetHistory();
         }
      }
   }
   
   class PingMessageCountersTask extends TimerTask
   {
      public synchronized void run()
      {
         synchronized (messageCounters)
         {
            Iterator iter = messageCounters.values().iterator();
            
            while (iter.hasNext())
            {
               MessageCounter counter = (MessageCounter)iter.next();
               
               counter.onTimer();
            }
         }
      }  
                        
      synchronized void stop()
      {
         cancel();
      }
   }
}
