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

import java.util.Map;
import EDU.oswego.cs.dl.util.concurrent.ConcurrentReaderHashMap;

/**
 * 
 * A ConcurrentReaderHashSet.
 * 
 * Offers same concurrency as ConcurrentHashMap but for a Set
 * 
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="clebert.suconic@jboss.com">Clebert Suconic</a>
 * @version <tt>$Revision: 3092 $</tt>
 *
 * $Id: ConcurrentReaderHashSet.java 3092 2007-09-10 21:42:30Z clebert.suconic@jboss.com $
 */
public class ConcurrentReaderHashSet<Key> extends AbstractHashSet<Key>
{
   public ConcurrentReaderHashSet()
   {
      super();
   }

   protected Map buildInternalHashMap()
   {
      return new ConcurrentReaderHashMap();
   }

}
