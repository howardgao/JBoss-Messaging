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

package org.jboss.test.messaging.jms.clustering;

import java.util.Iterator;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.InitialContext;

import org.jboss.jms.client.FailoverEvent;
import org.jboss.jms.client.FailoverListener;
import org.jboss.jms.client.JBossConnection;
import org.jboss.jms.client.JBossConnectionFactory;
import org.jboss.jms.client.delegate.ClientClusteredConnectionFactoryDelegate;
import org.jboss.jms.client.delegate.DelegateSupport;
import org.jboss.jms.client.state.ConnectionState;
import org.jboss.jms.tx.ResourceManagerFactory;
import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.container.ServiceAttributeOverrides;

import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;

/**
 * @author <a href="mailto:tim.fox@jboss.org">Tim Fox</a>
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2928 $</tt>
 * $Id: ClusteringTestBase.java 2928 2007-07-27 00:33:55Z timfox $
 */
public class ClusteringTestBase extends MessagingTestCase
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   protected String config = "all+http";

   protected static InitialContext[] ic;
   protected static Queue queue[];
   protected static Topic topic[];

   // No need to have multiple connection factories since a clustered connection factory will create
   // connections in a round robin fashion on different servers.

   protected static JBossConnectionFactory cf;

   protected int nodeCount = 4;
   protected ServiceAttributeOverrides overrides;

   protected static ServiceAttributeOverrides currentOverrides;

   // Constructors ---------------------------------------------------------------------------------

   public ClusteringTestBase(String name)
   {
      super(name);
   }

   // Public ---------------------------------------------------------------------------------------

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected int getFailoverNodeForNode(JBossConnectionFactory factory, int nodeID)
   {
   	Integer l = (Integer)((ClientClusteredConnectionFactoryDelegate)(factory.getDelegate())).getFailoverMap().get(new Integer(nodeID));

      return l.intValue();
   }

   protected int getNodeThatFailsOverOnto(JBossConnectionFactory factory, int nodeID)
   {
   	Map map = ((ClientClusteredConnectionFactoryDelegate)(factory.getDelegate())).getFailoverMap();

   	Iterator iter = map.entrySet().iterator();

   	while (iter.hasNext())
   	{
   		Map.Entry entry = (Map.Entry)iter.next();

   		int val = ((Integer)entry.getValue()).intValue();
   		int key = ((Integer)entry.getKey()).intValue();

   		if (val == nodeID)
   		{
   			return key;
   		}
   	}

   	throw new IllegalStateException("Cannot find node that fails over onto " + nodeID);
   }

   protected void setUp() throws Exception
   {
      super.setUp();

      ic = new InitialContext[nodeCount];

      queue = new Queue[nodeCount];

      topic = new Topic[nodeCount];

      for (int i = 0; i < nodeCount; i++)
      {
         startDefaultServer(i, overrides, i == 0);

         ic[i] = new InitialContext(ServerManagement.getJNDIEnvironment(i));

         queue[i] = (Queue)ic[i].lookup("queue/testDistributedQueue");

         topic[i] = (Topic)ic[i].lookup("topic/testDistributedTopic");
      }

      cf = (JBossConnectionFactory)ic[0].lookup("/ClusteredConnectionFactory");
   }

   protected void tearDown() throws Exception
   {
      super.tearDown();

      log.info("************* TEARING DOWN");

      String failString = null;

      try
      {

         if (ResourceManagerFactory.instance.size() != 0)
         {
            ResourceManagerFactory.instance.dump();

            failString  = "Connection(s) have been left open";
         }

         for (int i = 0; i < nodeCount; i++)
         {
            if (ServerManagement.isStarted(i))
            {
               if (!isEmpty(queue[i], i))
               {
                  failString = "Queue " + queue[i].getQueueName() + " is not empty on node" + i;

                  break;
               }

               // Check no subscriptions left lying around

               Integer messageCount = getNoSubscriptions(topic[i], i);

               if (messageCount.intValue() != 0)
               {
                  failString = "Topic " + topic[i].getTopicName() + " has subscriptions";

                  break;
               }
            }
         }
      }
      catch (Throwable ignore)
      {
      }

      for (int i = 9 ; i >= 0; i--)
      {
         try
         {
            ic[i].close();
         }
         catch (Throwable ignore)
         {
         }

         log.info("********** TRYING TO KILL SERVER " + i);
         try
         {
            ServerManagement.kill(i);
         }
         catch (Throwable ignore)
         {
         }
      }

      ic = null;

      queue = null;

      topic = null;

      if (failString != null)
      {
         fail(failString);
      }
   }

   protected void startDefaultServer(int serverNumber, ServiceAttributeOverrides attributes, boolean cleanDatabase)
      throws Exception
   {
      ServerManagement.start(serverNumber, config, attributes, cleanDatabase);

      log.info("deploying queue on node " + serverNumber);
      ServerManagement.deployQueue("testDistributedQueue", serverNumber);
      ServerManagement.deployTopic("testDistributedTopic", serverNumber);
   }

   protected String getLocatorURL(Connection conn)
   {
      return getConnectionState(conn).getRemotingConnection().
         getRemotingClient().getInvoker().getLocator().getLocatorURI();
   }

   protected String getObjectId(Connection conn)
   {
      return ((DelegateSupport) ((JBossConnection) conn).
         getDelegate()).getID();
   }

   protected ConnectionState getConnectionState(Connection conn)
   {
      return (ConnectionState) (((DelegateSupport) ((JBossConnection) conn).
         getDelegate()).getState());
   }

   protected void waitForFailoverComplete(int serverID, Connection conn1)
      throws Exception
   {

      assertEquals(serverID, ((JBossConnection)conn1).getServerID());

      // register a failover listener
      SimpleFailoverListener failoverListener = new SimpleFailoverListener();
      ((JBossConnection)conn1).registerFailoverListener(failoverListener);

      log.debug("killing node " + serverID + " ....");

      ServerManagement.kill(serverID);

      log.info("########");
      log.info("######## KILLED NODE " + serverID);
      log.info("########");

      // wait for the client-side failover to complete

      while (true)
      {
      	FailoverEvent event = failoverListener.getEvent(30000);
      	if (event != null && FailoverEvent.FAILOVER_COMPLETED == event.getType())
      	{
      		break;
      	}
      	if (event == null)
      	{
      		fail("Did not get expected FAILOVER_COMPLETED event");
      	}
      }

      // failover complete
      log.info("failover completed");
   }



   /**
    * Lookup for the connection with the right serverID. I'm using this method to find the proper
    * serverId so I won't relay on loadBalancing policies on testcases.
    */
   protected Connection getConnection(Connection[] conn, int serverId) throws Exception
   {
      for(int i = 0; i < conn.length; i++)
      {
         ConnectionState state = (ConnectionState)(((DelegateSupport)((JBossConnection)conn[i]).
            getDelegate()).getState());

         if (state.getServerID() == serverId)
         {
            return conn[i];
         }
      }

      return null;
   }

   protected void checkConnectionsDifferentServers(Connection[] conn) throws Exception
   {
      int[] serverID = new int[conn.length];
      for(int i = 0; i < conn.length; i++)
      {
         ConnectionState state = (ConnectionState)(((DelegateSupport)((JBossConnection)conn[i]).
            getDelegate()).getState());
         serverID[i] = state.getServerID();
      }

      for(int i = 0; i < nodeCount; i++)
      {
         for(int j = 0; j < nodeCount; j++)
         {
            if (i == j)
            {
               continue;
            }

            if (serverID[i] == serverID[j])
            {
               fail("Connections " + i + " and " + j +
                  " are pointing to the same physical node (" + serverID[i] + ")");
            }
         }
      }
   }

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

   protected class SimpleFailoverListener implements FailoverListener
   {
      private LinkedQueue buffer;

      public SimpleFailoverListener()
      {
         buffer = new LinkedQueue();
      }

      public void failoverEventOccured(FailoverEvent event)
      {
         try
         {
            buffer.put(event);
         }
         catch(InterruptedException e)
         {
            throw new RuntimeException("Putting thread interrupted while trying to add event " +
               "to buffer", e);
         }
      }

      /**
       * Blocks until a FailoverEvent is available or timeout occurs, in which case returns null.
       */
      public FailoverEvent getEvent(long timeout) throws InterruptedException
      {
         return (FailoverEvent)buffer.poll(timeout);
      }
   }

}

