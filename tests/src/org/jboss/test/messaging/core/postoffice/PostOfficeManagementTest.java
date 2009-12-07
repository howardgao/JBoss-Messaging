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


package org.jboss.test.messaging.core.postoffice;

import javax.management.ObjectName;

import org.jboss.messaging.core.impl.postoffice.MessagingPostOffice;
import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.ServerManagement;

/**
 * A PostOfficeManagementTest
 *
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 *
 *
 */
public class PostOfficeManagementTest extends MessagingTestCase
{

   /**
    * @param name
    */
   public PostOfficeManagementTest(String name)
   {
      super(name);
   }
   
   public void setUp() throws Exception
   {
      super.setUp();
      
      ServerManagement.stop();
      
      ServerManagement.start("all");
            
   }

   public void tearDown() throws Exception
   {
      super.tearDown();
      
      ServerManagement.stop();
   }
   
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------
   public void testDeployAndPropertyChange() throws Exception
   {
      ObjectName poName = ServerManagement.getServer(0).getPostOfficeObjectName();
      boolean failoverOnNodeLeave = ((Boolean)ServerManagement.getAttribute(poName, "FailoverOnNodeLeave")).booleanValue();
      assertFalse(failoverOnNodeLeave);
      ServerManagement.setAttribute(poName, "FailoverOnNodeLeave", "true");
      failoverOnNodeLeave = ((Boolean)ServerManagement.getAttribute(poName, "FailoverOnNodeLeave")).booleanValue();
      assertTrue(failoverOnNodeLeave);
   }


   //https://jira.jboss.org/jira/browse/JBMESSAGING-1562
   public void testGroupNameOverride() throws Exception
   {
      ServerManagement.stop();
      System.setProperty("test.clustered", "true");
      ServerManagement.start(0, "all");
      
      ObjectName poName = ServerManagement.getServer(0).getPostOfficeObjectName();
      MessagingPostOffice office = (MessagingPostOffice)ServerManagement.getAttribute(poName, "Instance");
      
      String partitionName = (String)ServerManagement.getAttribute(poName, "ChannelPartitionName");
      
      String groupName = office.getGroupMember().getGroupName();
      
      //if the channelPartitionName is defined, the group name should be set with it.
      if (partitionName != null)
      {
         assertEquals(partitionName, groupName);
      }
      else
      {
         //Note: some hudson test scripts define jboss.messaging.groupname, so we 
         //need to check this.
         log.info("groupName is: " + groupName);
         String sysGroupName = System.getProperty("jboss.messaging.groupname");
         log.info("systemgroupName is: " + sysGroupName);
         if (sysGroupName != null)
         {
            assertEquals(sysGroupName, groupName);
         }
         else
         {
            assertEquals("MessagingPostOffice", groupName);
         }
      }

      ServerManagement.stop(0);
      System.setProperty("test.clustered", "false");
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}

