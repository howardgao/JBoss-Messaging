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
package org.jboss.jms.destination;

import javax.jms.JMSException;
import javax.jms.TemporaryQueue;

import org.jboss.jms.delegate.SessionDelegate;
import org.jboss.messaging.util.GUIDGenerator;


/**
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 3352 $</tt>
 *
 * $Id: JBossTemporaryQueue.java 3352 2007-11-21 00:12:06Z clebert.suconic@jboss.com $
 */
public class JBossTemporaryQueue extends JBossQueue implements TemporaryQueue
{
   // Constants -----------------------------------------------------
   
   private static final long serialVersionUID = 4250425221695034957L;
      
   // Attributes ----------------------------------------------------
   
   private transient SessionDelegate delegate;
   private boolean deleted = false;
   
   
   // Static --------------------------------------------------------
   
   // Constructors --------------------------------------------------
   
   public JBossTemporaryQueue(SessionDelegate delegate)
   {
      super(GUIDGenerator.generateGUID());
      this.delegate = delegate;
   }
   
   public JBossTemporaryQueue(String name)
   {
      super(name);
   }
   
   // TemporaryQueue implementation ---------------------------------
   
   public void delete() throws JMSException
   {
      deleted = true;
      if (delegate != null)
      {
         delegate.deleteTemporaryDestination(this);
      }
   }
   
   // JBossDestination overrides ------------------------------------
   
   public boolean isTemporary()
   {
      return true;
   }

   public boolean isDeleted()
   {
      return deleted;
   }

   // Public --------------------------------------------------------

   public String toString()
   {
      return "JBossTemporaryQueue[" + name + "]";
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------
   
  
}
