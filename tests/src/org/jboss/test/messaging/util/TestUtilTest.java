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


package org.jboss.test.messaging.util;

import org.jboss.test.messaging.MessagingTestCase;

/**
 * A TestUtilTest
 *
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 * 
 * Created Nov 5, 2009 9:48:18 PM
 *
 *
 */
public class TestUtilTest extends MessagingTestCase
{

   public TestUtilTest(String name)
   {
      super(name);
   }

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------
   public void testgetProperSleepTime() throws Exception
   {
      long st = TestUtil.getProperSleepTime("my.sleep", 2000);
      assertEquals(2000, st);
      
      System.setProperty("my.sleep", "5000");
      st = TestUtil.getProperSleepTime("my.sleep", 2000);
      assertEquals(5000, st);
      
      System.setProperty("test.timeFactor", "2");
      st = TestUtil.getProperSleepTime("my.sleep", 2000);
      assertEquals(10000, st);
      
      System.clearProperty("my.sleep");
      st = TestUtil.getProperSleepTime("my.sleep", 2000);
      assertEquals(4000, st);
      
      System.clearProperty("test.timeFactor");
      st = TestUtil.getProperSleepTime("my.sleep", 2000);
      assertEquals(2000, st);
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
