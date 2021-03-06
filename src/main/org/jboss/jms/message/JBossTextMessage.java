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
package org.jboss.jms.message;

import java.util.Map;

import javax.jms.JMSException;
import javax.jms.TextMessage;

/**
 * This class implements javax.jms.TextMessage ported from SpyTextMessage in JBossMQ.
 * 
 * @author Norbert Lataille (Norbert.Lataille@m4x.org)
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @author <a href="mailto:adrian@jboss.org">Adrian Brock</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * 
 * @version $Revision: 3190 $
 *
 * $Id: JBossTextMessage.java 3190 2007-10-15 13:03:24Z timfox $
 */
public class JBossTextMessage extends JBossMessage implements TextMessage
{
   // Constants -----------------------------------------------------

   private static final long serialVersionUID = -5661567664746852006L;
   
   public static final byte TYPE = 3;

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------
   
   /**
    * Only deserialization should use this constructor directory
    */
   public JBossTextMessage()
   {     
   }
   
   /*
    * This constructor is used to construct messages prior to sending
    */
   public JBossTextMessage(long messageID)
   {
      super(messageID);
   }
   
   /*
    * This constructor is used to construct messages when retrieved from persistence storage
    */
   public JBossTextMessage(long messageID, boolean reliable, long expiration, long timestamp,
                           byte priority, Map coreHeaders, byte[] payloadAsByteArray)
   {
      super(messageID, reliable, expiration, timestamp, priority, coreHeaders, payloadAsByteArray);
   }

   /**
    * 
    * Make a shallow copy of another JBossTextMessage
    * 
    * @param other
    */
   public JBossTextMessage(JBossTextMessage other)
   {
      super(other);
   }

   /**
    * A copy constructor for non-JBoss Messaging JMS TextMessages.
    */
   public JBossTextMessage(TextMessage foreign, long id) throws JMSException
   {
      super(foreign, id);
      String text = foreign.getText();
      if (text != null)
      {
         setText(text);
      }
 
   }

   // Public --------------------------------------------------------

   public byte getType()
   {
      return JBossTextMessage.TYPE;
   }

   // TextMessage implementation ------------------------------------

   public void setText(String string) throws JMSException
   {
      payload = string;
      payloadAsByteArray = null;
   }

   public String getText() throws JMSException
   {
      return (String)getPayload();
   }

   // JBossMessage override -----------------------------------------
   
   public JBossMessage doCopy()
   {
      return new JBossTextMessage(this);
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   // Public --------------------------------------------------------
}