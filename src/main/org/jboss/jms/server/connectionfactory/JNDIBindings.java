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
package org.jboss.jms.server.connectionfactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jboss.messaging.util.XMLUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2868 $</tt>
 *
 * $Id: JNDIBindings.java 2868 2007-07-10 20:22:16Z timfox $
 */
public class JNDIBindings
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   private Element delegate;
   private List names;

   // Constructors --------------------------------------------------

   public JNDIBindings(Element delegate)
   {
      parse(delegate);
      this.delegate = delegate;
   }

   // Public --------------------------------------------------------

   public Element getDelegate()
   {
      return delegate;
   }

   /**
    * @return List<String>
    */
   public List getNames()
   {
      return names;
   }

   public String toString()
   {
      if (names == null)
      {
         return "";
      }

      StringBuffer sb = new StringBuffer();

      for(Iterator i = names.iterator(); i.hasNext(); )
      {
         sb.append(i.next());
         if (i.hasNext())
         {
            sb.append(", ");
         }
      }
      return sb.toString();
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   private void parse(Element e)
   {
      if (!"bindings".equals(e.getNodeName()))
      {
         throw new IllegalArgumentException("The element is not a <bindings> element");
      }

      if (!e.hasChildNodes())
      {
         names = Collections.EMPTY_LIST;
         return;
      }

      NodeList nl= e.getChildNodes();
      for(int i = 0; i < nl.getLength(); i++)
      {
         Node n = nl.item(i);
         if ("binding".equals(n.getNodeName()))
         {
            String text = XMLUtil.getTextContent(n).trim();
            if (names == null)
            {
               names = new ArrayList();
            }
            names.add(text);
         }
      }

      if (names == null)
      {
         names = Collections.EMPTY_LIST;
      }
   }

   // Inner classes -------------------------------------------------
}
