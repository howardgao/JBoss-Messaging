/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.jms.client.container;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import javax.jms.JMSException;

import org.jboss.aop.joinpoint.Invocation;
import org.jboss.aop.joinpoint.MethodInvocation;
import org.jboss.jms.client.FailoverCommandCenter;
import org.jboss.jms.client.delegate.ClientClusteredConnectionFactoryDelegate;
import org.jboss.jms.client.delegate.ClientConnectionDelegate;
import org.jboss.jms.client.delegate.ClientConnectionFactoryDelegate;
import org.jboss.jms.client.delegate.DelegateSupport;
import org.jboss.jms.client.plugin.LoadBalancingPolicy;
import org.jboss.jms.client.state.ConnectionState;
import org.jboss.jms.delegate.CreateConnectionResult;
import org.jboss.jms.exception.MessagingNetworkFailureException;
import org.jboss.logging.Logger;

/**
 * This aspect is part of a clustered ConnectionFactory aspect stack.
 * Some of its responsibilities are:
 *
 * - To choose the next node to create a physical connection to, based on a pluggable load balancing
 *   policy.
 * - To handle physical connection creation (by delegating it to a non-clustered ConnectionFactory
 *   delegate) and install failure listeners.
 * - etc.
 *
 * It's a PER_INSTANCE aspect (one of these per each clustered ConnectionFactory instance)
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 *
 * @version <tt>$Revision: 4021 $</tt>
 *
 * $Id: ClusteringAspect.java 4021 2008-04-08 13:29:24Z clebert.suconic@jboss.com $
 */
public class ClusteringAspect
{
   // Constants ------------------------------------------------------------------------------------

   private static final Logger log = Logger.getLogger(ClusteringAspect.class);
   
   private boolean trace = log.isTraceEnabled();

   public static final int MAX_RECONNECT_HOP_COUNT = 10;

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // This is a PER_INSTANCE aspect, so it has a 1-to-1 relationship with its delegate
   private ClientClusteredConnectionFactoryDelegate clusteredDelegate;

   // Constructors ---------------------------------------------------------------------------------

   public ClusteringAspect()
   {
   }

   // Public ---------------------------------------------------------------------------------------

   public Object handleCreateConnectionDelegate(Invocation invocation)
      throws Throwable
   {
      if (trace)
      {
         log.trace(this + " handleCreateConnectionDelegate");
      }
      
      // initalize this PER_INSTANCE aspect by getting a hold of its corresponding clustered
      // delegate and maintaining a reference to it
      if (clusteredDelegate == null)
      {
         clusteredDelegate = (ClientClusteredConnectionFactoryDelegate)invocation.getTargetObject();
      }
      
      boolean supportsFailover = clusteredDelegate.isSupportsFailover();

      // the method handles both the case of a first connection creation attempt and a reconnect after
      // a client-side failover. The difference is given by the failedNodeID (-1 for first attempt)

      MethodInvocation mi = (MethodInvocation)invocation;
      String username = (String)mi.getArguments()[0];
      String password = (String)mi.getArguments()[1];
      Integer failedNodeID = (Integer)mi.getArguments()[2];

      // We attempt to connect to the next node in a loop, since we might need to go through
      // multiple hops

      int attemptCount = 0;
      ClientConnectionFactoryDelegate delegate = null;

      while (attemptCount < MAX_RECONNECT_HOP_COUNT)
      {
         // since an exception might be captured during an attempt, this has to be the first
         // operation
         attemptCount++;

         int nextHopingServer = -1;
         try
         {
            int failedNodeIDToServer = -1;
            if (delegate == null)
            {
               if (failedNodeID != null && failedNodeID.intValue() >= 0)
               {
               	//It's a reconnect after failover
                  delegate = getFailoverDelegateForNode(failedNodeID);
                  failedNodeIDToServer = failedNodeID.intValue();
                  nextHopingServer = delegate.getServerID();
               }
               else
               {
               	//It's a first time create connection
                  LoadBalancingPolicy loadBalancingPolicy = clusteredDelegate.getLoadBalancingPolicy();                  
                  delegate = (ClientConnectionFactoryDelegate)loadBalancingPolicy.getNext();
               }
            }

            log.trace(this + " has chosen " + delegate + " as target, " +
               (attemptCount == 0 ? "first connection attempt" : attemptCount + " connection attempts"));
  
            CreateConnectionResult res = delegate.
               createConnectionDelegate(username, password, failedNodeIDToServer);
            
            ClientConnectionDelegate cd = (ClientConnectionDelegate)res.getDelegate();

            if (cd != null)
            {
               // valid connection

               log.trace(this + " got local connection delegate " + cd);
               
               if (supportsFailover)
               {
	               ConnectionState state = (ConnectionState)((DelegateSupport)cd).getState();
	
	               state.initializeFailoverCommandCenter();
	
	               FailoverCommandCenter fcc = state.getFailoverCommandCenter();
	
	               // add a connection listener to detect failure; the consolidated remoting connection
	               // listener must be already in place and configured
	               state.getRemotingConnection().getConnectionListener().
	                  setDelegateListener(new ConnectionFailureListener(fcc, state.getRemotingConnection()));
	
	               log.trace(this + " installed failure listener on " + cd);
	
	               // also cache the username and the password into state, useful in case
	               // FailoverCommandCenter needs to create a new connection instead of a failed on
	               state.setUsername(username);
	               state.setPassword(password);
	
	               // also add a reference to the clustered ConnectionFactory delegate, useful in case
	               // FailoverCommandCenter needs to create a new connection instead of a failed on
	               state.setClusteredConnectionFactoryDeleage(clusteredDelegate);
	               
	               log.trace("Successfully initialised new connection");
               }

               return res;
            }
            else
            {
            	// This should never occur if we are not doing failover
            	if (!supportsFailover)
            	{
            		throw new IllegalStateException("Doesn't support failover so must return a connection delegate");
            	}
            	
               // we did not get a valid connection to the node we've just tried

               int actualServerID = res.getActualFailoverNodeID();

               if (actualServerID == -1)
               {
                  // No failover attempt was detected on the server side; this might happen if the
                  // client side network fails temporarily so the client connection breaks but the
                  // server cluster is still up and running - in this case we don't perform failover.

                  // In this case we should try back on the original server

                  log.debug("Client attempted failover, but no failover attempt " +
                            "has been detected on the server side. We will now try again on the original server " +
                            "in case there was a temporary glitch on the client--server network");

                  delegate = getDelegateForNode(failedNodeID.intValue());

                  //Pause a little to avoid hammering the same node in quick succession

                  //Currently hardcoded
                  Thread.sleep(2000);
               }
               else
               {
                  // Server side failover has occurred / is occurring but trying to go to the 'default'
                  // failover node did not succeed. Retry with the node suggested by the cluster.

               	log.trace("Server side failover occurred, but we were non the wrong node! Actual node = " + actualServerID);
               	
                  delegate = getDelegateForNode(actualServerID);
               }

               if (delegate == null)
               {
                  throw new JMSException("Cannot find a cached connection factory delegate for " +
                     "node " + actualServerID);
               }

            }
         }
         catch (MessagingNetworkFailureException e)
         {
            // Setting up the next failover
            failedNodeID = new Integer(nextHopingServer);
            delegate = null;
            log.warn("Exception captured on createConnection... hopping to a new connection factory on server (" + failedNodeID + ")", e);
            // Currently hardcoded
            Thread.sleep(2000);
         }
      }

      throw new JMSException("Maximum number of failover attempts exceeded. " +
                             "Cannot find a server to failover onto.");
   }

   public String toString()
   {
      return "ClusteringAspect[" + clusteredDelegate + "]";
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   private synchronized ClientConnectionFactoryDelegate getFailoverDelegateForNode(Integer nodeID)
   {
   	log.trace("Getting failover delegate for node id " + nodeID);
   	
      ClientConnectionFactoryDelegate[] delegates = clusteredDelegate.getDelegates();

      if (nodeID.intValue() < 0)
      {
         throw new IllegalArgumentException("nodeID must be 0 or positive");
      }
      
      Map failoverMap = clusteredDelegate.getFailoverMap();
      
      if (trace) { dumpFailoverMap(failoverMap); }
      
      Integer failoverNodeID = (Integer)failoverMap.get(nodeID);
      
      log.trace("Found failover node id = " + failoverNodeID);
      
      // FailoverNodeID is not on the map, that means the ConnectionFactory was updated by another
      // connection in another server. So we will have to guess the failoverID by numeric order.
      // In case we guessed the new server wrongly we will have to rely on redirect from failover.
      if (failoverNodeID == null)
      {
      	log.trace("Couldn't find failover node id on map so guessing it");
         failoverNodeID = guessFailoverID(failoverMap, nodeID);
         log.trace("Guess is " + failoverNodeID);
      }

      for (int i = 0; i < delegates.length; i++)
      {
         if (delegates[i].getServerID() == failoverNodeID.intValue())
         {
            return delegates[i];
         }
      }

      return null;
   }
   
   private void dumpFailoverMap(Map failoverMap)
   {
      log.trace("Dumping failover map");
      Iterator iter = failoverMap.entrySet().iterator();
      while (iter.hasNext())
      {
      	Map.Entry entry = (Map.Entry)iter.next();
      	log.trace(entry.getKey() + "-->" + entry.getValue());
      }
   }

   /**
    * FailoverNodeID is not on the map, that means the ConnectionFactory was updated by another
    * connection in another server. So we will have to guess the failoverID by numeric order. In
    * case we guessed the new server wrongly we will have to rely on redirect from failover.
    * (NOTE: There is a testcase that uses reflection to validate this method in
    * org.jboss.test.messaging.jms.clustering.ClusteringAspectInternalTest. Modify that testcase
    * in case you decide to refactor this method).
    */
   private static Integer guessFailoverID(Map failoverMap, Integer nodeID)
   {
   	log.trace("Guessing failover id for node " + nodeID);
      Integer failoverNodeID = null;
      Integer[] nodes = (Integer[]) failoverMap.keySet().toArray(new Integer[failoverMap.size()]);

      // We need to sort the array first
      Arrays.sort(nodes);

      for (int i = 0; i < nodes.length; i++)
      {
         if (nodeID.intValue() < nodes[i].intValue())
         {
            failoverNodeID = nodes[i];
            break;
         }
      }

      // if still null use the first node...
      if (failoverNodeID == null)
      {
         failoverNodeID = nodes[0];
      }
      
      log.trace("Returning guess " + failoverNodeID);
      
      return failoverNodeID;
   }

   private synchronized ClientConnectionFactoryDelegate getDelegateForNode(int nodeID)
   {
   	log.trace("Getting delegate for node id " + nodeID);
   	
      ClientConnectionFactoryDelegate[] delegates = clusteredDelegate.getDelegates();

      for (int i = 0; i < delegates.length; i++)
      {
         if (delegates[i].getServerID() == nodeID)
         {
         	log.trace("Found " + delegates[i]);
            return delegates[i];
         }
      }
      
      log.trace("Didn't find any delegate");
      return null;
   }

   // Inner classes --------------------------------------------------------------------------------

}
