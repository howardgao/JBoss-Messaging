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
package org.jboss.test.messaging.tools.container;

import org.jboss.util.loading.DelegatingClassLoader;

/**
 * We extend URLClassLoader just to prevent UnifiedLoaderRepository3 to generate spurious warning
 * (in this case): "Tried to add non-URLClassLoader. Ignored". Extending ClassLoader would be fine
 * otherwise.
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 4024 $</tt>
 *
 * $Id: ClassLoaderJMXWrapper.java 4024 2008-04-09 16:05:05Z clebert.suconic@jboss.com $
 */
public class ClassLoaderJMXWrapper extends DelegatingClassLoader implements ClassLoaderJMXWrapperMBean
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public ClassLoaderJMXWrapper(ClassLoader delegate)
   {
      super(delegate);
   }

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
