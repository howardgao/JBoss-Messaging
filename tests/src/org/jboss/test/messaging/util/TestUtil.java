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

/**
 * A TestUtil
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 */
public class TestUtil
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------
   
   /**
    * Get the sleep time used by the test. It is used to adapt timing sensitive
    * tests in different test environments. 
    * 
    * @param key The key used to retrieve the value from system property.
    * @param originalVal if the property are not defined in system property by the key, originalVal 
    * will be used.
    * 
    * @return the return value will be the effective value times the test.timeFactor system property.
    * 
    */   
   public static long getProperSleepTime(String key, long originVal)
   {
      long eVal = originVal;
      String value = System.getProperty(key);
      if (value != null)
      {
         eVal = Long.valueOf(value);
      }
      
      long factor = 1;
      
      value = System.getProperty("test.timeFactor");
      
      if (value != null)
      {
         factor = Long.valueOf(value);
      }
      
      return factor * eVal;
   }
   
   //see getProperSleepTime(key, originVal)
   public static long getProperSleepTime(long originVal)
   {
      long eVal = originVal;
      
      long factor = 1;
      
      String value = System.getProperty("test.timeFactor");
      
      if (value != null)
      {
         factor = Long.valueOf(value);
      }
      
      return factor * eVal;
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
