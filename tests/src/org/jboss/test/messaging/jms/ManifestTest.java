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
package org.jboss.test.messaging.jms;

import java.io.File;
import java.util.Iterator;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.jms.Connection;
import javax.jms.ConnectionMetaData;

/**
 * Tests MANIFEST.MF content of the output jar.
 *
 * @author <a href="mailto:afu@novell.com">Alex Fu</a>
 * @version <tt>$Revision: 2925 $</tt>
 *
 * $Id: ManifestTest.java 2925 2007-07-25 10:43:58Z timfox $
 */
public class ManifestTest extends JMSTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public ManifestTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   public void testManifestEntries() throws Exception
   {
      Properties props = System.getProperties();
      String userDir = props.getProperty("build.lib");
      
      log.trace("userDir is " + userDir);

      // The jar must be there
      File file = new File(userDir, "jboss-messaging.jar");
      assertTrue(file.exists());

      // Open the jar and load MANIFEST.MF
      JarFile jar = new JarFile(file);
      Manifest manifest = jar.getManifest();
      
      // Open a connection and get ConnectionMetaData
      Connection conn = null;
      
      try
      {	      
	      conn = cf.createConnection();
	      assertNotNull(conn);
	      ConnectionMetaData meta = conn.getMetaData();
	      
	      // Compare the value from ConnectionMetaData and MANIFEST.MF
	      Attributes attrs = manifest.getMainAttributes();
	      
	      log.info("META--> " + meta.getJMSMajorVersion());
	      log.info("META--> " + meta.getJMSMinorVersion());
	      log.info("META--> " + meta.getJMSProviderName());
	      log.info("META--> " + meta.getJMSVersion());
	      log.info("META--> " + meta.getProviderMajorVersion());
	      log.info("META--> " + meta.getProviderMinorVersion());
	      log.info("META--> " + meta.getProviderVersion());
	      
	      Iterator itr = attrs.entrySet().iterator();
	      while (itr.hasNext()) {
	         Object item = itr.next();
	         log.trace("MANIFEST--> " + item + " : " + attrs.get(item));
	      }
	      
	      assertEquals(attrs.getValue("Implementation-Title"), meta.getJMSProviderName());
	      String ver = attrs.getValue("Implementation-Version");
	      assertTrue(-1 != ver.indexOf(meta.getProviderVersion()));
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      }
   }
   
   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------

}