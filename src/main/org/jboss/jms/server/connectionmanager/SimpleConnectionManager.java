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
package org.jboss.jms.server.connectionmanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.aop.AspectManager;
import org.jboss.jms.delegate.ConnectionEndpoint;
import org.jboss.jms.server.ConnectionManager;
import org.jboss.jms.server.endpoint.ServerConnectionEndpoint;
import org.jboss.jms.server.endpoint.ServerConnectionFactoryEndpoint;
import org.jboss.jms.server.endpoint.advised.ConnectionAdvised;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.contract.ClusterNotification;
import org.jboss.messaging.core.contract.ClusterNotificationListener;
import org.jboss.messaging.core.contract.Replicator;
import org.jboss.messaging.util.ConcurrentHashSet;
import org.jboss.messaging.util.Util;
import org.jboss.remoting.Client;
import org.jboss.remoting.ClientDisconnectedException;
import org.jboss.remoting.ConnectionListener;
import org.jboss.remoting.callback.InvokerCallbackHandler;
import org.jboss.remoting.callback.ServerInvokerCallbackHandler;

/**
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 6823 $</tt>
 *
 * $Id: SimpleConnectionManager.java 6823 2009-05-18 07:17:01Z gaohoward $
 */
public class SimpleConnectionManager implements ConnectionManager, ConnectionListener, ClusterNotificationListener
{
   // Constants ------------------------------------------------------------------------------------

   private static final Logger log = Logger.getLogger(SimpleConnectionManager.class);

   // Static ---------------------------------------------------------------------------------------

   private static boolean trace = log.isTraceEnabled();

   // Attributes -----------------------------------------------------------------------------------

   private Map</** VMID */String, Map</** RemoteSessionID */String, ConnectionEndpoint>> jmsClients;
   
   private Map</* VMID */String, Map</* remoting session id */String, InvokerCallbackHandler>> cfHandlers;

   // Map<remotingClientSessionID<String> - jmsClientVMID<String>
   private Map<String, String> remotingSessions;

   // Set<ConnectionEndpoint>
   private Set<ConnectionEndpoint> activeConnectionEndpoints;

   private Replicator replicator;
   
   private Set<ServerConnectionFactoryEndpoint> connectionFactories = new ConcurrentHashSet<ServerConnectionFactoryEndpoint>();
   

   // Constructors ---------------------------------------------------------------------------------

   public SimpleConnectionManager()
   {
      jmsClients = new HashMap<String, Map<String, ConnectionEndpoint>>();
      
      remotingSessions = new HashMap<String, String>();
      
      cfHandlers = new HashMap<String, Map<String, InvokerCallbackHandler>>();
            
      activeConnectionEndpoints = new HashSet<ConnectionEndpoint>();
   }

   // ConnectionManager implementation -------------------------------------------------------------
   
 
   public synchronized void registerConnection(String jmsClientVMID,
                                               String remotingClientSessionID,
                                               ConnectionEndpoint endpoint)
   {    
      Map<String, ConnectionEndpoint> endpoints = jmsClients.get(jmsClientVMID);
      
      if (endpoints == null)
      {
         endpoints = new HashMap<String, ConnectionEndpoint>();
         
         jmsClients.put(jmsClientVMID, endpoints);
      }
      
      endpoints.put(remotingClientSessionID, endpoint);
      
      remotingSessions.put(remotingClientSessionID, jmsClientVMID);

      activeConnectionEndpoints.add(endpoint);
      
      log.debug("registered connection " + endpoint + " as " +
                Util.guidToString(remotingClientSessionID));
   }
   
   public synchronized void registerConnectionFactoryCallback(String JVMID,
                                                               String remotingSessionID,
                                                               InvokerCallbackHandler handler)
   {
      Map<String, InvokerCallbackHandler> handlers = cfHandlers.get(JVMID);
      
      if (handlers == null)
      {
         handlers = new HashMap<String, InvokerCallbackHandler>();
         
         cfHandlers.put(JVMID, handlers);
      }
      
      handlers.put(remotingSessionID, handler);
      
      remotingSessions.put(remotingSessionID, JVMID);

      log.debug("registered cf callback handler " + handler + " as " +
                Util.guidToString(remotingSessionID));
   }

   public synchronized ConnectionEndpoint unregisterConnection(String jmsClientVMId,
                                                               String remotingClientSessionID)
   {
      Map<String, ConnectionEndpoint> endpoints = this.jmsClients.get(jmsClientVMId);
      
      if (endpoints != null)
      {
         ConnectionEndpoint e = endpoints.remove(remotingClientSessionID);

         if (e != null)
         {
            activeConnectionEndpoints.remove(e);
         }

         log.debug("unregistered connection " + e + " with remoting session ID " +
               Util.guidToString(remotingClientSessionID));
         
         if (endpoints.isEmpty())
         {
            jmsClients.remove(jmsClientVMId);
         }
         
         remotingSessions.remove(remotingClientSessionID);
         
         return e;
      }
      
      return null;
   }
   
   public synchronized void unregisterConnectionFactoryCallback(String JVMID, String remotingSessionID)
   {
      Map<String, InvokerCallbackHandler> handlers = cfHandlers.get(JVMID);
      
      if (handlers != null)
      {
         handlers.remove(remotingSessionID);
         
         if (handlers.isEmpty())
         {
            cfHandlers.remove(JVMID);
         }
         
         remotingSessions.remove(remotingSessionID);
      }
   }

   public synchronized List getActiveConnections()
   {
      // I will make a copy to avoid ConcurrentModification
      List<ConnectionEndpoint> list = new ArrayList<ConnectionEndpoint>();
      list.addAll(activeConnectionEndpoints);
      return list;
   }
      
   public synchronized void handleClientFailure(String remotingSessionID)
   {
      cleanupForSessionID(remotingSessionID);
   }
   
   public void registerConnectionFactory(ServerConnectionFactoryEndpoint cf)
   {
      connectionFactories.add(cf);
   }
      
   public void unregisterConnectionFactory(ServerConnectionFactoryEndpoint cf)
   {
      connectionFactories.remove(cf);
   }
   
   // ConnectionListener implementation ------------------------------------------------------------

   /**
    * Be aware that ConnectionNotifier uses to call this method with null Throwables.
    *
    * @param t - plan for it to be null!
    */
   public void handleConnectionException(Throwable t, Client client)
   {  
      if (t instanceof ClientDisconnectedException)
      {
         if (log.isTraceEnabled())
         {
            log.trace("Connection is closed normally: " + client);
         }
      }
      else
      {
         if (log.isTraceEnabled())
         {
            log.trace("Connection is closed on failure event: " + client);
         }
      }
      String remotingSessionID = client.getSessionId();

      if (remotingSessionID != null)
      {
         handleClientFailure(remotingSessionID);
      }
   }
     
   // ClusterNotificationListener implementation ---------------------------------------------------

   /**
    * Closing connections that are coming from a failed node
    * @param notification
    */
   public void notify(ClusterNotification notification)
   {  
      if (notification.type == ClusterNotification.TYPE_NODE_LEAVE)
      {

         log.trace("SimpleConnectionManager was notified about node leaving from node " +
                    notification.nodeID);
         
         try
         {
            //We remove any consumers with the same JVMID as the node that just failed
            //This will remove any message suckers from a failed node
            //This is important to workaround a remoting bug where sending messages down a broken connection
            //can cause a deadlock with the bisocket transport
            
            //Get the jvm id for the failed node
            
            Map ids = replicator.get(Replicator.JVM_ID_KEY);
            
            if (ids == null)
            {
               log.trace("Cannot find jvmid map");
               throw new IllegalStateException("Cannot find jvmid map");
            }
            
            int failedNodeID = notification.nodeID;
            
            String clientVMID = (String)ids.get(new Integer(failedNodeID));
               
            if (clientVMID == null)
            {
               log.error("Cannot find ClientVMID for failed node " + failedNodeID);
               throw new IllegalStateException("Cannot find clientVMID for failed node " + failedNodeID);
            }
            
            //Close the consumers corresponding to that VM

            log.trace("Closing consumers for clientVMID=" + clientVMID);

            if (clientVMID != null)
            {
               cleanupForVMID(clientVMID);
            }
         }
         catch (Exception e)
         {
            log.error("Failed to process failover start", e);
         }
      }     
   }
   
   // MessagingComponent implementation ------------------------------------------------------------
   
   public void start() throws Exception
   {
      //NOOP
   }
   
   public void stop() throws Exception
   {
      //NOOP
   }

   // Public ---------------------------------------------------------------------------------------

   /*
    * Used in testing only
    */
   public synchronized boolean containsRemotingSession(String remotingClientSessionID)
   {
      return remotingSessions.containsKey(remotingClientSessionID);
   }

   /*
    * Used in testing only
    */
   public synchronized Map getClients()
   {
      return Collections.unmodifiableMap(jmsClients);
   }
   
   public void injectReplicator(Replicator replicator)
   {
      this.replicator = replicator;
   }


   public String toString()
   {
      return "ConnectionManager[" + Integer.toHexString(hashCode()) + "]";
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------
  
   private synchronized void cleanupForVMID(String jmsClientID)
   {
      Map<String, ConnectionEndpoint> endpoints = jmsClients.get(jmsClientID);

      if (endpoints != null)
      {
         //Copy to prevent ConcurrentModificationException
         List<ConnectionEndpoint> sces = new ArrayList<ConnectionEndpoint>(endpoints.values());

         // Now close the end points - this will result in a callback into unregisterConnection
         // to remove the data from the jmsClients and sessions maps.

         for(ConnectionEndpoint sce: sces )
         {
            log.debug("clearing up state for connection " + sce);

            // sce could also be a mock test.. so this test is required
            if (sce instanceof ServerConnectionEndpoint)
            {
               //Remoting is dumb and doesn't clean up it's state after itself - so we have to do it.
               ((ServerConnectionEndpoint)sce).closeCallbackClient();
            }
            // wrap the Endpoint in the ConnectionAdvised so that we get a proper call back for the close
            // TODO:  What we really need to do is to wrap the End point in registerConnection, when we
            // get the connection.  But, I'm hesitant, because there are are many of instanceof checks that would
            // fail if I wrapped it during registerConneciton.
            // Need to synchronized to prevent a deadlock
            // See http://jira.jboss.com/jira/browse/JBMESSAGING-797
            synchronized (AspectManager.instance())
            {       
               sce = new ConnectionAdvised(sce);
            }
            try
            {
               sce.closing(-1);
            }
            catch (Throwable ignore)
            {                 
            }
            try
            {
               sce.close();
            }
            catch (Throwable ignore)
            {                 
            }
            log.debug("cleared up state for connection " + sce);      
         }
      }
      
      Map<String, InvokerCallbackHandler> handlers = cfHandlers.remove(jmsClientID);
            
      if (handlers != null)
      {        
         Map<String, InvokerCallbackHandler> handlersClone = new HashMap<String, InvokerCallbackHandler>(handlers);
                  
         for (Map.Entry<String, InvokerCallbackHandler> entry: handlersClone.entrySet())
         {
            try
            {
               ((ServerInvokerCallbackHandler)entry.getValue()).getCallbackClient().disconnect();
            }
            catch (Throwable ignore)
            {
            }

            try
            {
               ((ServerInvokerCallbackHandler)entry.getValue()).shutdown();
            }
            catch (Throwable ignore)
            {
            }           
            
            for (ServerConnectionFactoryEndpoint ep: connectionFactories)
            {
               ep.removeCallbackhandler((ServerInvokerCallbackHandler)entry.getValue());
            }
            
            unregisterConnectionFactoryCallback(jmsClientID, entry.getKey());
         }
      }
   }
         
   private void cleanupForSessionID(String jmsSessionID)
   {      
      String jmsClientID = remotingSessions.get(jmsSessionID);
      
      log.trace("A problem has been detected " +
                    "with the connection to remote client " +
                     jmsSessionID + ", jmsClientID=" + jmsClientID + ". It is possible the client has exited without closing " +
                  "its connection(s) or the network has failed. All associated connection resources will be cleaned up.");
      
      if (jmsClientID != null)
      {        
         Map<String, ConnectionEndpoint> endpoints = jmsClients.get(jmsClientID);
   
         if (endpoints != null)
         {
            ConnectionEndpoint conn = null;
         
            for (Map.Entry<String, ConnectionEndpoint> entry: endpoints.entrySet())
            {
               if (entry.getKey().equals(jmsSessionID))
               {   
                  conn = entry.getValue();
                  
                  break;
               }
            }
            
            if (conn != null)
            {
               
               
               // sce could also be a mock test.. so this test is required
               if (conn instanceof ServerConnectionEndpoint)
               {
                  //Remoting is dumb and doesn't clean up it's state after itself - so we have to do it.
                  ((ServerConnectionEndpoint)conn).closeCallbackClient();
               }
               // wrap the Endpoint in the ConnectionAdvised so that we get a proper call back for the close
               // TODO:  What we really need to do is to wrap the Endpoint in registerConnection, when we
               // get the connection.  Then anyone
               // Need to synchronized to prevent a deadlock
               // See http://jira.jboss.com/jira/browse/JBMESSAGING-797
               synchronized (AspectManager.instance())
               {       
                  conn = new ConnectionAdvised(conn);
               }
               try
               {
                  conn.closing(-1);
               }
               catch (Throwable ignore)
               {              
               }
               try
               {
                  conn.close();
               }
               catch (Throwable ignore)
               {              
               }
               return;
            }
         }
      }

      Map<String, InvokerCallbackHandler> handlers = cfHandlers.get(jmsClientID);
      
      if (handlers != null)
      {        
         boolean found = false;
         
         for (Map.Entry<String, InvokerCallbackHandler> entry: handlers.entrySet())
         {
            if (entry.getKey().equals(jmsSessionID))
            {                             
               try
               {
                  ((ServerInvokerCallbackHandler)entry.getValue()).getCallbackClient().disconnect();
               }
               catch (Throwable ignore)
               {
               }
   
               try
               {
                  ((ServerInvokerCallbackHandler)entry.getValue()).shutdown();
               }
               catch (Throwable ignore)
               {
               }           
               
               for (ServerConnectionFactoryEndpoint ep: connectionFactories)
               {
                  ep.removeCallbackhandler(entry.getValue());
               }
               
               found = true;
               
               break;
            }
         }
         
         if (found)
         {
            unregisterConnectionFactoryCallback(jmsClientID, jmsSessionID);
         }
      }

   }

   // Inner classes --------------------------------------------------------------------------------

}
