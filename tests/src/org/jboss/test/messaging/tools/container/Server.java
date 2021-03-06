/*
 * JBoss, Home of Professional Open Source Copyright 2005, JBoss Inc., and individual contributors as indicated by the
 * @authors tag. See the copyright.txt in the distribution for a full listing of individual contributors. This is free
 * software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the License, or (at your option) any later version.
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details. You should have received a copy of the GNU Lesser General Public License along with this software; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.jboss.test.messaging.tools.container;

import java.rmi.Remote;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.transaction.UserTransaction;

import org.jboss.jms.server.DestinationManager;
import org.jboss.jms.server.ServerPeer;
import org.jboss.messaging.core.contract.MessageStore;
import org.jboss.messaging.core.contract.PersistenceManager;
import org.jboss.remoting.ServerInvocationHandler;

/**
 * The remote interface exposed by TestServer.
 * 
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2868 $</tt> $Id: Server.java 2868 2007-07-10 20:22:16Z timfox $
 */
public interface Server extends Remote
{
   int getServerID() throws Exception;

   void start(String containerConfig, boolean clearDatabase) throws Exception;

   /**
    * @param attrOverrides - service attribute overrides that will take precedence over values read from configuration
    *           files.
    */
   void start(String containerConfig,
              ServiceAttributeOverrides attrOverrides,
              boolean clearDatabase,
              boolean startMessgingServer) throws Exception;

   /**
    * @return true if the server was stopped indeed, or false if the server was stopped already when the method was
    *         invoked.
    */
   boolean stop() throws Exception;

   /**
    * For a remote server, it "abruptly" kills the VM running the server. For a local server it just stops the server.
    */
   void kill() throws Exception;

   /**
    * When kill is called you are actually schedulling the server to be killed in few milliseconds. There are certain
    * cases where we need to assure the server was really killed. For that we have this simple ping we can use to verify
    * if the server still alive or not.
    */
   void ping() throws Exception;

   /**
    * Deploys and registers a service based on the MBean service descriptor element, specified as a String. Supports
    * XMBeans. The implementing class and the ObjectName are inferred from the mbean element. If there are configuration
    * attributed specified in the deployment descriptor, they are applied to the service instance.
    */
   ObjectName deploy(String mbeanConfiguration) throws Exception;

   void undeploy(ObjectName on) throws Exception;

   Object getAttribute(ObjectName on, String attribute) throws Exception;

   void setAttribute(ObjectName on, String name, String valueAsString) throws Exception;

   Object invoke(ObjectName on, String operationName, Object[] params, String[] signature) throws Exception;

   void addNotificationListener(ObjectName on, NotificationListener listener) throws Exception;

   void removeNotificationListener(ObjectName on, NotificationListener listener) throws Exception;

   /**
    * Returns a set of ObjectNames corresponding to installed services.
    */
   Set query(ObjectName pattern) throws Exception;

   /**
    * @return one of "socket", "http", ...
    */
   String getRemotingTransport() throws Exception;

   /**
    * Only for remote use!
    */
   void log(int level, String text) throws Exception;

   /**
    * @param serverPeerID - if null, the jboss-service.xml value will be used.
    * @param defaultQueueJNDIContext - if null, the jboss-service.xml value will be used.
    * @param defaultTopicJNDIContext - if null, the jboss-service.xml value will be used.
    */
   void startServerPeer(int serverPeerID,
                        String defaultQueueJNDIContext,
                        String defaultTopicJNDIContext,
                        ServiceAttributeOverrides attrOverrides,
                        boolean clustered) throws Exception;

   void stopServerPeer() throws Exception;

   boolean isServerPeerStarted() throws Exception;

   ObjectName getServerPeerObjectName() throws Exception;

   boolean isStarted() throws Exception;

   /**
    * @return a Set<String> with the subsystems currently registered with the Connector. It is supposed to work locally
    *         as well as remotely.
    */
   Set getConnectorSubsystems() throws Exception;

   /**
    * Add a ServerInvocationHandler to the remoting Connector. This method is supposed to work locally as well as
    * remotely.
    */
   void addServerInvocationHandler(String subsystem, ServerInvocationHandler handler) throws Exception;

   /**
    * Remove a ServerInvocationHandler from the remoting Connector. This method is supposed to work locally as well as
    * remotely.
    */
   void removeServerInvocationHandler(String subsystem) throws Exception;

   /**
    * Only for in-VM use!
    */
   MessageStore getMessageStore() throws Exception;

   /**
    * Only for in-VM use!
    */
   DestinationManager getDestinationManager() throws Exception;

   PersistenceManager getPersistenceManager() throws Exception;

   /**
    * Only for in-VM use
    */
   ServerPeer getServerPeer() throws Exception;

   /**
    * Simulates a topic deployment (copying the topic descriptor in the deploy directory).
    */
   void deployTopic(String name, String jndiName, boolean clustered) throws Exception;

   /**
    * Simulates a topic deployment (copying the topic descriptor in the deploy directory).
    */
   void deployTopic(String name, String jndiName, int fullSize, int pageSize, int downCacheSize, boolean clustered) throws Exception;

   /**
    * Creates a topic programatically.
    */
   void deployTopicProgrammatically(String name, String jndiName) throws Exception;

   /**
    * Simulates a queue deployment (copying the queue descriptor in the deploy directory).
    */
   void deployQueue(String name, String jndiName, boolean clustered) throws Exception;

   /**
    * Simulates a queue deployment (copying the queue descriptor in the deploy directory).
    */
   void deployQueue(String name, String jndiName, int fullSize, int pageSize, int downCacheSize, boolean clustered) throws Exception;

   /**
    * Creates a queue programatically.
    */
   void deployQueueProgrammatically(String name, String jndiName) throws Exception;

   /**
    * Creates a queue deployment with the DLQ and ExpiryQueue attributes. this method doesn't start the mbean service.
    * Rather it returns the ObjectName of the mbean service.
    * 
    * @throws Exception
    */
   ObjectName deployQueueWithDLQnExpiryQ(String q, String dlq, String expq) throws Exception;

   /**
    * Creates a topic deployment with the DLQ and ExpiryQueue attributes. this method doesn't start the mbean service.
    * Rather it returns the ObjectName of the mbean service.
    * 
    * @throws Exception
    */
   ObjectName deployTopicWithDLQnExpiryQ(String topic, String dlq, String expq) throws Exception;

   /**
    * Create a queue deployment without starting it.
    */
   ObjectName deployQueueWithoutStart(String testDLQName, String jndi, boolean b) throws Exception;

   /**
    * Starts the deployed the services.
    */
   void startDestinationService(ObjectName[] objectNames) throws Exception;

   /**
    * Simulates a destination un-deployment (deleting the destination descriptor from the deploy directory).
    */
   void undeployDestination(boolean isQueue, String name) throws Exception;

   /**
    * Destroys a programatically created destination.
    */
   boolean undeployDestinationProgrammatically(boolean isQueue, String name) throws Exception;

   void deployConnectionFactory(String objectName,
                                String[] jndiBindings,
                                int prefetchSize,
                                int defaultTempQueueFullSize,
                                int defaultTempQueuePageSize,
                                int defaultTempQueueDownCacheSize) throws Exception;

   void deployConnectionFactory(String objectName,
                                String[] jndiBindings,
                                boolean supportsFailover,
                                boolean supportsLoadBalancing) throws Exception;

   void deployConnectionFactory(String objectName,
                                String[] jndiBindings,
                                boolean supportsFailover,
                                boolean supportsLoadBalancing,
                                String clientID) throws Exception;

   void deployConnectionFactory(String objectName, String[] jndiBindings, int prefetchSize) throws Exception;

   void deployConnectionFactory(String objectName, String[] jndiBindings) throws Exception;

   void undeployConnectionFactory(ObjectName objectName) throws Exception;

   /**
    * @param config - sending 'config' as a String and not as an org.w3c.dom.Element to avoid NotSerializableExceptions
    *           that show up when running tests on JDK 1.4.
    */
   void configureSecurityForDestination(String destName, String config) throws Exception;

   /**
    * @param config - sending 'config' as a String and not as an org.w3c.dom.Element to avoid NotSerializableExceptions
    *           that show up when running tests on JDK 1.4.
    */
   void setDefaultSecurityConfig(String config) throws Exception;

   /**
    * @return a String that can be converted to an org.w3c.dom.Element using ServerManagement.toElement().
    */
   String getDefaultSecurityConfig() throws Exception;

   /**
    * Executes a command on the server
    * 
    * @param command
    * @return the return value
    * @throws Exception
    */
   Object executeCommand(Command command) throws Exception;

   UserTransaction getUserTransaction() throws Exception;

   /**
    * Returns a Set containing the nodeID (as Integers) of all cluster members at the time of the call. USE IT ONLY FOR
    * CLUSTERING TESTS!
    */
   Set getNodeIDView() throws Exception;

   Map getFailoverMap() throws Exception;

   Map getRecoveryArea(String queueName) throws Exception;

   int getRecoveryMapSize(String queueName) throws Exception;

   /**
    * @return List<Notification>
    */
   List pollNotificationListener(long listenerID) throws Exception;

   void poisonTheServer(int type) throws Exception;

   void flushManagedConnectionPool() throws Exception;

   void resetAllSuckers() throws Exception;

   void deployConnectionFactory(String objectName, String[] jndiBindings, boolean strictTck) throws Exception;

   ObjectName getPostOfficeObjectName() throws Exception;

   void deployQueue(String name, String jndiName, boolean clustered, boolean keepMessage) throws Exception;

   void deployTopic(String name, String jndiName, boolean clustered, boolean keepMessage) throws Exception;

}
