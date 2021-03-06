/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.messaging.jms.clustering;

import java.util.Set;

import javax.management.ObjectName;

import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.ServerManagement;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 3874 $</tt>
 *
 * $Id: GroupManagementTest.java 3874 2008-03-13 23:46:12Z clebert.suconic@jboss.com $
 */
public class GroupManagementTest extends MessagingTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public GroupManagementTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   public void testOneNodeCluster() throws Exception
   {
      try
      {
         ServerManagement.start(0, "all", false);

         Set view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(1, view.size());
         assertTrue(view.contains(new Integer(0)));
      }
      finally
      {
         ServerManagement.stop(0);
      }
   }

   public void testJoinNotification() throws Exception
   {
      ClusterEventNotificationListener listener = new ClusterEventNotificationListener();
      ObjectName postOfficeObjectName = new ObjectName("jboss.messaging:service=PostOffice");

      try
      {
         ServerManagement.start(0, "all", false);

         log.info("Started server 0");

         ServerManagement.addNotificationListener(0, postOfficeObjectName, listener);

         ServerManagement.start(1, "all", false);

         log.info("Blocking to receive notification ...");

         if (!listener.viewChanged(30000))
         {
            fail("Did not receive view change!");
         }

         Set view = ServerManagement.getServer(1).getNodeIDView();
         assertEquals(2, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));
      }
      finally
      {
         ServerManagement.removeNotificationListener(0, postOfficeObjectName, listener);
         ServerManagement.stop(1);
         ServerManagement.stop(0);
      }
   }

   public void testTwoNodesCluster() throws Exception
   {
      try
      {
         ServerManagement.start(0, "all", false);

         Set view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(1, view.size());
         assertTrue(view.contains(new Integer(0)));

         ServerManagement.start(1, "all", false);

         view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(2, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));

         view = ServerManagement.getServer(1).getNodeIDView();

         assertEquals(2, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));
      }
      finally
      {
         ServerManagement.stop(1);
         ServerManagement.stop(0);
      }
   }

   public void testThreeNodesCluster() throws Exception
   {
      try
      {
         ServerManagement.start(0, "all", false);

         Set view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(1, view.size());
         assertTrue(view.contains(new Integer(0)));

         ServerManagement.start(1, "all", false);

         view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(2, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));

         view = ServerManagement.getServer(1).getNodeIDView();
         assertEquals(2, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));

         ServerManagement.start(3, "all", false);

         view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(3, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));
         assertTrue(view.contains(new Integer(3)));

         view = ServerManagement.getServer(1).getNodeIDView();
         assertEquals(3, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));
         assertTrue(view.contains(new Integer(3)));

         view = ServerManagement.getServer(3).getNodeIDView();
         assertEquals(3, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));
         assertTrue(view.contains(new Integer(3)));
      }
      finally
      {
         ServerManagement.stop(3);
         ServerManagement.stop(1);
         ServerManagement.stop(0);
      }
   }


   public void testCleanLeave() throws Exception
   {
      try
      {
         ServerManagement.start(0, "all", false);
         ServerManagement.start(1, "all", false);
         ServerManagement.start(2, "all", false);

         Set view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(3, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));
         assertTrue(view.contains(new Integer(2)));

         // Make node 0 to "cleanly" leave the cluster

         ServerManagement.stop(0);

         view = ServerManagement.getServer(1).getNodeIDView();
         assertEquals(2, view.size());
         assertTrue(view.contains(new Integer(1)));
         assertTrue(view.contains(new Integer(2)));

         // Make node 2 to "cleanly" leave the cluster

         ServerManagement.stop(2);

         view = ServerManagement.getServer(1).getNodeIDView();
         assertEquals(1, view.size());
         assertTrue(view.contains(new Integer(1)));

         // Reuse the "hollow" RMI server 0 to start another cluster node

         ServerManagement.start(0, "all", false);

         view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(2, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));


         // Reuse the "hollow" RMI server 2 to start another cluster node

         ServerManagement.start(2, "all", false);

         view = ServerManagement.getServer(2).getNodeIDView();
         assertEquals(3, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));
         assertTrue(view.contains(new Integer(2)));

      }
      finally
      {
         ServerManagement.stop(2);
         ServerManagement.stop(1);
         ServerManagement.stop(0);
      }
   }

   public void testDirtyLeaveOneNode() throws Exception
   {
      ClusterEventNotificationListener clusterEvent = new ClusterEventNotificationListener();
      ObjectName postOfficeObjectName = new ObjectName("jboss.messaging:service=PostOffice");

      try
      {
         // Start with a 2 node cluster

         ServerManagement.start(0, "all", false);
         ServerManagement.start(1, "all", false);

         Set view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(2, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));

         ServerManagement.addNotificationListener(0, postOfficeObjectName, clusterEvent);

         // Make node 1 to "dirty" leave the cluster, by killing the VM running it.

         ServerManagement.kill(1);

         log.info("########");
         log.info("######## KILLED 1");
         log.info("########");

         // Wait for membership change notification

         if (!clusterEvent.viewChanged(30000))
         {
            fail("Did not receive view change after killing server 2!");
         }

         view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(1, view.size());
         assertTrue(view.contains(new Integer(0)));
      }
      finally
      {
         ServerManagement.removeNotificationListener(0, postOfficeObjectName, clusterEvent);
         ServerManagement.stop(1);
         ServerManagement.stop(0);
      }
   }

   public void testDirtyLeaveTwoNodes() throws Exception
   {
      ClusterEventNotificationListener clusterEvent = new ClusterEventNotificationListener();
      ObjectName postOfficeObjectName = new ObjectName("jboss.messaging:service=PostOffice");

      try
      {
         // Start with a 3 node cluster

         ServerManagement.start(0, "all", false);
         ServerManagement.start(1, "all", false);
         ServerManagement.start(2, "all", false);

         Set view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(3, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));
         assertTrue(view.contains(new Integer(2)));

         ServerManagement.addNotificationListener(0, postOfficeObjectName, clusterEvent);

         // Make node 2 to "dirty" leave the cluster, by killing the VM running it.

         ServerManagement.kill(2);

         log.info("########");
         log.info("######## KILLED 2");
         log.info("########");

         // Wait for FAILOVER_COMPLETED notification

         if (!clusterEvent.failoverCompleted(90000))
         {
            fail("Did not receive a FAILOVER_COMPLETED event after killing server 2!");
         }

         log.info("received FAILOVER_COMPLETED");

         view = ServerManagement.getServer(1).getNodeIDView();
         assertEquals(2, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(1)));

         // Make node 1 to "dirty" leave the cluster, by killing the VM running it.

         ServerManagement.kill(1);

         log.info("########");
         log.info("######## KILLED 1");
         log.info("########");

         // Wait for FAILOVER_COMPLETED notification

         if (!clusterEvent.failoverCompleted(30000))
         {
            fail("Did not receive a FAILOVER_COMPLETED event after killing server 1!");
         }

         log.info("received FAILOVER_COMPLETED");

         view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(1, view.size());
         assertTrue(view.contains(new Integer(0)));
      }
      finally
      {
         ServerManagement.removeNotificationListener(0, postOfficeObjectName, clusterEvent);

         ServerManagement.stop(2);
         ServerManagement.stop(1);
         ServerManagement.stop(0);
      }
   }

   public void testSpawnServer() throws Exception
   {

      ObjectName postOfficeObjectName = new ObjectName("jboss.messaging:service=PostOffice");
      ClusterEventNotificationListener clusterEvent = new ClusterEventNotificationListener();

      try
      {
         // Start with a 1 node cluster

         ServerManagement.start(0, "all", false);

         Set view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(1, view.size());
         assertTrue(view.contains(new Integer(0)));

         ServerManagement.addNotificationListener(0, postOfficeObjectName, clusterEvent);

         // start the ninth node, as there is no chance to be started by scripts
         ServerManagement.start(9, "all", false);

         if (!clusterEvent.viewChanged(30000))
         {
            fail("Did not receive a VIEW_CHANGED event after spawning new server!");
         }

         view = ServerManagement.getServer(9).getNodeIDView();

         assertEquals(2, view.size());
         assertTrue(view.contains(new Integer(0)));
         assertTrue(view.contains(new Integer(9)));
      }
      finally
      {
         ServerManagement.removeNotificationListener(0, postOfficeObjectName, clusterEvent);
         ServerManagement.stop(0);
         ServerManagement.kill(9);
      }
   }

   public void testStartServersSimultaneously() throws Exception
   {
      final int numServers = 5;

      try
      {
         class ServerStarter extends Thread
         {
            int nodeID;
            boolean failed;
            ServerStarter(int nodeID)
            {
               this.nodeID = nodeID;
            }

            public void run()
            {
               try
               {
                  log.info("Starting " + nodeID);
                  ServerManagement.start(nodeID, "all", false);

                  ServerManagement.deployQueue("testDistributedQueue1", nodeID);
                  ServerManagement.deployTopic("testDistributedTopic1", nodeID);

                  ServerManagement.deployQueue("testDistributedQueue2", nodeID);
                  ServerManagement.deployTopic("testDistributedTopic2", nodeID);

                  ServerManagement.deployQueue("testDistributedQueue3", nodeID);
                  ServerManagement.deployTopic("testDistributedTopic3", nodeID);
                  log.info("Done start");
               }
               catch (Throwable t)
               {
                  log.error("Failed to start server", t);
                  failed = true;
               }
            }
         }

         ServerStarter[] starters = new ServerStarter[numServers];
         for (int i = 0; i < 5; i++)
         {
            starters[i] = new ServerStarter(i);
            starters[i].start();
         }

         boolean failed = false;
         for (int i = 0; i < 5; i++)
         {
            starters[i].join();
            if (starters[i].failed)
            {
               failed = true;
            }
         }

         assertFalse(failed);

         Set view = ServerManagement.getServer(0).getNodeIDView();
         assertEquals(numServers, view.size());
      }
      finally
      {
         for (int i = numServers - 1; i >=0; i--)
         {
            try
            {
               ServerManagement.stop(i);
            }
            catch (Exception ignore)
            {
            }
         }
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected void setUp() throws Exception
   {
      super.setUp();
   }

   protected void tearDown() throws Exception
   {
      super.tearDown();

      for (int i = ServerManagement.MAX_SERVER_COUNT - 1; i >=0; i--)
      {
         ServerManagement.kill(i);
      }
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
