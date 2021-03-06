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
package org.jboss.jms.client.remoting;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

import org.jboss.jms.server.ServerPeer;
import org.jboss.jms.wireformat.JMSWireFormat;
import org.jboss.logging.Logger;
import org.jboss.messaging.util.GUIDGenerator;
import org.jboss.remoting.Client;
import org.jboss.remoting.ConnectionListener;
import org.jboss.remoting.InvokerLocator;
import org.jboss.remoting.ServerInvoker;
import org.jboss.remoting.callback.CallbackPoller;
import org.jboss.remoting.callback.InvokerCallbackHandler;
import org.jboss.remoting.transport.bisocket.Bisocket;
import org.jboss.remoting.transport.socket.MicroSocketClientInvoker;
import org.jboss.remoting.transport.socket.SocketServerInvoker;
import org.jboss.util.id.GUID;



/**
 * Encapsulates the state and behaviour from jboss remoting needed for a JMS connection.
 * 
 * Each JMS connection maintains a single Client instance for invoking on the server.
 *
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 7597 $</tt>
 * $Id: JMSRemotingConnection.java 7597 2009-07-21 14:49:14Z gaohoward $
 */
public class JMSRemotingConnection
{
   // Constants ------------------------------------------------------------------------------------

   private static final Logger log = Logger.getLogger(JMSRemotingConnection.class);
   
   // Static ---------------------------------------------------------------------------------------

   private static String getPropertySafely(String propName)
   {
      String prop = null;

      try
      {
         prop = System.getProperty(propName);
      }
      catch (Exception ignore)
      {
         // May get a security exception depending on security permissions, in which case we
         // return null
      }

      return prop;
   }

   /**
    * Build the configuration we need to use to make sure a callback server works the way we want.
    *
    * @param doPushCallbacks - whether the callback should be push or pull. For socket transport
    *        allow true push callbacks, with callback Connector. For http transport, simulate push
    *        callbacks.
    * @param metadata - metadata that should be added to the metadata map being created. Can be
    *        null.
    *
    * @return a Map to be used when adding a listener to a client, thus enabling a callback server.
    */
   public static Map createCallbackMetadata(boolean doPushCallbacks, Map metadata,
                                            InvokerLocator serverLocator)
   {
      if (metadata == null)
      {
         metadata = new HashMap();
      }

      // Use our own direct thread pool that basically does nothing.
      // Note! This needs to be done irrespective of the transport and is used even for INVM
      //       invocations.
      metadata.put(ServerInvoker.ONEWAY_THREAD_POOL_CLASS_KEY,
                   "org.jboss.jms.server.remoting.DirectThreadPool");

      if (doPushCallbacks)
      {
         metadata.put(MicroSocketClientInvoker.CLIENT_SOCKET_CLASS_FLAG,
                      "org.jboss.jms.client.remoting.ClientSocketWrapper");
         metadata.put(SocketServerInvoker.SERVER_SOCKET_CLASS_FLAG,
                      "org.jboss.jms.server.remoting.ServerSocketWrapper");

         String bindAddress = getPropertySafely("jboss.messaging.callback.bind.address");
                  
         if (bindAddress != null)
         {
            metadata.put(Client.CALLBACK_SERVER_HOST, bindAddress);
         }

         String propertyPort = getPropertySafely("jboss.messaging.callback.bind.port");
                  
         if (propertyPort != null)
         {
            metadata.put(Client.CALLBACK_SERVER_PORT, propertyPort);
         }
         
         //Callback client pool MUST be size 1 so one way messages don't get out of order
         
         metadata.put(MicroSocketClientInvoker.MAX_POOL_SIZE_FLAG, "1");
         
         String protocol = serverLocator.getProtocol();
         if ("bisocket".equals(protocol) || "sslbisocket".equals(protocol))
         {
            metadata.put(Bisocket.IS_CALLBACK_SERVER, "true");

            // Setting the port prevents the Remoting Client from using PortUtil.findPort(), which
            // creates ServerSockets. The actual value of the port shouldn't matter. To "guarantee"
            // that each InvokerLocator is unique, a GUID is appended to the InvokerLocator.
            if (propertyPort == null)
            {
               String guid = new GUID().toString();
               int hash = guid.hashCode();
               
               // Make sure the hash code is > 0.
               // See http://jira.jboss.org/jira/browse/JBMESSAGING-863.
               while(hash <= 0)
               {
                  if (hash == 0)
                  {
                     guid = GUIDGenerator.generateGUID();
                     hash = guid.hashCode();
                  }
                  if (hash < 0)
                  {
                     if (hash == Integer.MIN_VALUE)
                     {
                        hash = Integer.MAX_VALUE;
                     }
                     else
                     {
                        hash = -hash;
                     }
                  }
               }
               
               metadata.put(Client.CALLBACK_SERVER_PORT, Integer.toString(hash));
               metadata.put("guid", guid);
            }
         }
      }
      else
      {
         String blockingMode = getPropertySafely("jboss.messaging.callback.blockingMode");
         
         if (blockingMode == null)
         {
            blockingMode = (String)serverLocator.getParameters().get(ServerInvoker.BLOCKING_MODE);
         }
         
         if (blockingMode == null)
         {
            //Default to blocking
            
            blockingMode = ServerInvoker.BLOCKING;            
         }
         
         metadata.put(ServerInvoker.BLOCKING_MODE, blockingMode);
         
         if (blockingMode.equals(ServerInvoker.BLOCKING))
         {         
            String blockingTimeout = getPropertySafely("jboss.messaging.callback.blockingTimeout");
            
            if (blockingTimeout == null)
            {
                blockingTimeout = (String)serverLocator.getParameters().get(ServerInvoker.BLOCKING_TIMEOUT);
            }
            
            if (blockingTimeout != null)
            {
               metadata.put(ServerInvoker.BLOCKING_TIMEOUT, blockingTimeout);
            } 
         }
         else
         {
            // "jboss.messaging.callback.pollPeriod" system property, if set, has the
            // highest priority ...
            String callbackPollPeriod = getPropertySafely("jboss.messaging.callback.pollPeriod");

            if (callbackPollPeriod == null)
            {
               // followed by the value configured on the HTTP connector ("callbackPollPeriod") ...
               callbackPollPeriod = (String)serverLocator.getParameters().get(CallbackPoller.CALLBACK_POLL_PERIOD);               
            }

            if (callbackPollPeriod != null)
            {
               metadata.put(CallbackPoller.CALLBACK_POLL_PERIOD, callbackPollPeriod);
            }
         }
         
         String reportPollingStatistics =
            getPropertySafely("jboss.messaging.callback.reportPollingStatistics");

         if (reportPollingStatistics != null)
         {
            metadata.put(CallbackPoller.REPORT_STATISTICS, reportPollingStatistics);
         }
      }

      return metadata;
   }

   /**
    * Configures and add the invokerCallbackHandler the right way (push or pull).
    *
    * @param configurer - passed for logging purposes only.
    * @param initialMetadata - some initial metadata that we might want to pass along when
    *        registering invoker callback handler.
    */
   public static void addInvokerCallbackHandler(Object configurer,
                                                Client client,
                                                Map initialMetadata,
                                                InvokerLocator serverLocator,
                                                InvokerCallbackHandler invokerCallbackHandler)
      throws Throwable
   {

      // For transports derived from the socket transport, allow true push callbacks,
      // with callback Connector. For http transport, simulate push callbacks.
      String protocol = serverLocator.getProtocol();
      boolean isBisocket = "bisocket".equals(protocol) || "sslbisocket".equals(protocol);
      boolean isSocket   = "socket".equals(protocol)   || "sslsocket".equals(protocol);
      boolean doPushCallbacks = isBisocket || isSocket;
      Map metadata = createCallbackMetadata(doPushCallbacks, initialMetadata, serverLocator);

      if (doPushCallbacks)
      {
         log.trace(configurer + " is doing push callbacks");
         client.addListener(invokerCallbackHandler, metadata, null, true);
      }
      else
      {
         log.trace(configurer + " is simulating push callbacks");
         client.addListener(invokerCallbackHandler, metadata);
      }
   }

   // Attributes -----------------------------------------------------------------------------------

   private Client client;
   private Client onewayClient;
   private boolean clientPing;
   private InvokerLocator serverLocator;
   private CallbackManager callbackManager;
   private boolean strictTck;
   private boolean sendAcksAsync;

   // When a failover is performed, this flag is set to true
   protected boolean failed = false;

   // Maintaining a reference to the remoting connection listener for cases when we need to
   // explicitly remove it from the remoting client
   private ConsolidatedRemotingConnectionListener remotingConnectionListener;

   // Constructors ---------------------------------------------------------------------------------

   public JMSRemotingConnection(String serverLocatorURI, boolean clientPing, boolean strictTck,
                                boolean sendAcksAsync) throws Exception
   {
      this(serverLocatorURI, clientPing, strictTck, null, sendAcksAsync);
   }
      
   public JMSRemotingConnection(String serverLocatorURI, boolean clientPing, boolean strictTck, ConsolidatedRemotingConnectionListener listener, boolean sendAcksAsync) throws Exception
   {
      serverLocator = new InvokerLocator(serverLocatorURI);
      this.clientPing = clientPing;
      this.strictTck = strictTck;
      this.sendAcksAsync = sendAcksAsync;
      this.remotingConnectionListener = listener;
      
      log.trace(this + " created");
   }

   // Public ---------------------------------------------------------------------------------------

   private static final String JBM_MAX_POOL_SIZE_KEY = "JBM_clientMaxPoolSize";
   
   public void start() throws Throwable
   {
      // Enable client pinging. Server leasing is enabled separately on the server side.

      Map config = new HashMap();
      Map config2 = new HashMap();
      
      String trustStoreLoc = System.getProperty("org.jboss.remoting.trustStore");
      if (trustStoreLoc != null)
      {
         config.put("org.jboss.remoting.trustStore", trustStoreLoc);
         config2.put("org.jboss.remoting.trustStore", trustStoreLoc);
         String trustStorePassword = System.getProperty("org.jboss.remoting.trustStorePassword");
         if (trustStorePassword != null)
         {
            config.put("org.jboss.remoting.trustStorePassword", trustStorePassword);
            config2.put("org.jboss.remoting.trustStorePassword", trustStorePassword);
         }
      }

      config.put(Client.ENABLE_LEASE, String.valueOf(clientPing));
      
      if (serverLocator.getParameters().containsKey(MicroSocketClientInvoker.MAX_POOL_SIZE_FLAG))
      {
         throw new IllegalArgumentException("Invalid remoting configuration - do not specify clientMaxPoolSize" +
                                            " use " + JBM_MAX_POOL_SIZE_KEY +" instead");
      }         
      
      if (!serverLocator.getProtocol().equals("http") & !serverLocator.getProtocol().equals("https"))
      {
         String val = (String)serverLocator.getParameters().get(JBM_MAX_POOL_SIZE_KEY);
         
         if (val == null)
         {
            log.warn(JBM_MAX_POOL_SIZE_KEY + " not specified - defaulting to 200");
            
            val = "200";
         }
         
         config.put(MicroSocketClientInvoker.MAX_POOL_SIZE_FLAG, val);
      }

      client = new Client(serverLocator, config);

      client.setSubsystem(ServerPeer.REMOTING_JMS_SUBSYSTEM);
      
      config2.put(Client.ENABLE_LEASE, "false");
      config2.put(MicroSocketClientInvoker.MAX_POOL_SIZE_FLAG, "1");
      
      //Note we *must* use same serverLocator or invm optimisation won't work even if one param on the URI
      //is different
      onewayClient = new Client(serverLocator, config2);
      
      onewayClient.setSubsystem(ServerPeer.REMOTING_JMS_SUBSYSTEM);
                       
      if (log.isTraceEnabled()) { log.trace(this + " created client"); }

      callbackManager = new CallbackManager();

      //Do a privileged Action to connect
      AccessController.doPrivileged( new PrivilegedExceptionAction()
      {
         public Object run() throws Exception
         {
            if (remotingConnectionListener != null)
            {
               client.connect(remotingConnectionListener, serverLocator.getParameters());
            }
            else
            {
               client.connect();
            }
            onewayClient.connect();
            return null;
         }
      });

      // We explicitly set the Marshaller since otherwise remoting tries to resolve the marshaller
      // every time which is very slow - see org.jboss.remoting.transport.socket.ProcessInvocation
      // This can make a massive difference on performance. We also do this in
      // ServerConnectionEndpoint.setCallbackClient.

      client.setMarshaller(new JMSWireFormat());
      client.setUnMarshaller(new JMSWireFormat());
      
      onewayClient.setMarshaller(new JMSWireFormat());
      onewayClient.setUnMarshaller(new JMSWireFormat());
      
      Map metadata = new HashMap();
      
      metadata.put(InvokerLocator.DATATYPE, "jms");

      addInvokerCallbackHandler(this, client, metadata, serverLocator, callbackManager);
      
      log.trace(this + " started");
   }

   public void stop()
   {
      log.trace(this + " stop");
      
      // explicitly remove the callback listener, to avoid race conditions on server
      // (http://jira.jboss.org/jira/browse/JBMESSAGING-535)

      try
      {
         client.removeListener(callbackManager);
      }
      catch(Throwable ignore)
      {
         // very unlikely to get an exception on a local remove (I suspect badly designed API),
         // but we're failed anyway, so we don't care too much
         
         // Actually an exception will always be thrown here if the failure was detected by the connection
         // validator since the validator will disconnect the client before calling the connection
         // listener.

         log.trace(this + " failed to cleanly remove callback manager from the client", ignore);
      }

      try
      {
      	client.disconnect();
      }
      catch (Throwable ignore)
      {      	
      	log.trace(this + " failed to disconnect the client", ignore);
      }
      
      try
      {
         onewayClient.disconnect();
      }
      catch (Throwable ignore)
      {        
         log.trace(this + " failed to disconnect the client", ignore);
      }
      
      client = null;
      onewayClient = null;

      log.trace(this + " closed");
   }

   public Client getRemotingClient()
   {
      return client;
   }
   
   public Client getOnewayClient()
   {
      return onewayClient;
   }
   
   public CallbackManager getCallbackManager()
   {
      return callbackManager;
   }

   public boolean isStrictTck()
   {
       return strictTck;
   }
   
   public boolean isSendAcksAsync()
   {
      return sendAcksAsync;
   }

   public synchronized boolean isFailed()
   {
      return failed;
   }

   /**
    * Used by the FailoverCommandCenter to mark this remoting connection as "condemned", following
    * a failure detected by either a failed invocation, or the ConnectionListener.
    */
   public synchronized void setFailed()
   {
      failed = true;
      
      if (client == null) 
      {
         return;
      }

      // Remoting has the bad habit of letting the job of cleaning after a failed connection up to
      // the application. Here, we take care of that, by disconnecting the remoting client, and
      // thus silencing both the connection validator and the lease pinger, and also locally
      // cleaning up the callback listener

      try
      {
         client.setDisconnectTimeout(0);
      }
      catch (Throwable ignore)
      {      	
      	log.trace(this + " failed to set disconnect timeout", ignore);
      }
      
      try
      {
         onewayClient.setDisconnectTimeout(0);
      }
      catch (Throwable ignore)
      {        
         log.trace(this + " failed to set disconnect timeout", ignore);
      }
       
      stop();
   }

   /**
    * @return true if the listener was correctly installed, or false if the add attempt was ignored
    *         because there is already another listener installed.
    */
   public synchronized boolean addConnectionListener(ConsolidatedRemotingConnectionListener listener)
   {
      if (remotingConnectionListener != null)
      {
         return false;
      }

      client.addConnectionListener(listener, serverLocator.getParameters());
      remotingConnectionListener = listener;

      return true;
   }

   public synchronized void addPlainConnectionListener(final ConnectionListener listener)
   {
      client.addConnectionListener(listener, serverLocator.getParameters());
   }

   public synchronized void removePlainConnectionListener(ConnectionListener listener)
   {
      client.removeConnectionListener(listener);
   }

   public synchronized ConsolidatedRemotingConnectionListener getConnectionListener()
   {
      return remotingConnectionListener;
   }

   /**
    * May return null, if no connection listener was previously installed.
    */
   public synchronized ConsolidatedRemotingConnectionListener removeConnectionListener()
   {
      if (remotingConnectionListener == null)
      {
         return null;
      }

      client.removeConnectionListener(remotingConnectionListener);

      log.trace(this + " removed consolidated connection listener from " + client);
      ConsolidatedRemotingConnectionListener toReturn = remotingConnectionListener;
      remotingConnectionListener = null;
      return toReturn;
   }

   public String toString()
   {
      return "JMSRemotingConnection[" + serverLocator.getLocatorURI() + "]";
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------
   
   // Inner classes --------------------------------------------------------------------------------

}
