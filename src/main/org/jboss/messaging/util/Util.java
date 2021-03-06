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
package org.jboss.messaging.util;

import java.io.StringReader;
import java.sql.Connection;

import javax.jms.DeliveryMode;
import javax.jms.Session;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2868 $</tt>
 *
 * $Id: Util.java 2868 2007-07-10 20:22:16Z timfox $
 */
public class Util
 {
   // Constants -----------------------------------------------------

    public static Logger log = Logger.getLogger(Util.class);

   // Static --------------------------------------------------------

    public static String guidToString(Object o)
    {
       if (o == null)
       {
          return "null";
       }
       if (!(o instanceof String))
       {
          return o.toString();
       }
       String s = (String)o;
       int idx = s.lastIndexOf('-', s.lastIndexOf('-') - 1);
       if (idx < 0)
       {
          return s;
       }
       return "...-" + s.substring(idx + 1);
    }

    

    public static String transactionIsolationToString(int level)
    {
       return
          level == Connection.TRANSACTION_NONE ? "NONE" :
             level == Connection.TRANSACTION_READ_UNCOMMITTED ? "READ_UNCOMMITTED" :
                level == Connection.TRANSACTION_READ_COMMITTED ? "READ_COMMITTED" :
                   level == Connection.TRANSACTION_REPEATABLE_READ ? "REPEATABLE_READ" :
                      level == Connection.TRANSACTION_SERIALIZABLE ? "SERIALIZABLE" :
                         "UNKNOWN";
    }

    /**
    * TODO this is a duplicate of test.XMLUtil.stringToElement().
    *      Only used by ServerPeer.createDestination(). Get rid of this when I fix that method.
    */
   public static Element stringToElement(String s) throws Exception
   {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder parser = factory.newDocumentBuilder();
      Document doc = parser.parse(new InputSource(new StringReader(s)));
      return doc.getDocumentElement();
   }
   
   public static String deliveryMode(int m)
   {
      if (m == DeliveryMode.NON_PERSISTENT)
      {
         return "NON_PERSISTENT";
      }
      if (m == DeliveryMode.PERSISTENT)
      {
         return "PERSISTENT";
      }
      return "UNKNOWN";
   }

   public static String acknowledgmentMode(int ack)
   {
      if (ack == Session.AUTO_ACKNOWLEDGE)
      {
         return "AUTO_ACKNOWLEDGE";
      }
      if (ack == Session.CLIENT_ACKNOWLEDGE)
      {
         return "CLIENT_ACKNOWLEDGE";
      }
      if (ack == Session.DUPS_OK_ACKNOWLEDGE)
      {
         return "DUPS_OK_ACKNOWLEDGE";
      }
      if (ack == Session.SESSION_TRANSACTED)
      {
         return "SESSION_TRANSACTED";
      }
      return "UNKNOWN";
   }

   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   private Util()
   {
   }

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
