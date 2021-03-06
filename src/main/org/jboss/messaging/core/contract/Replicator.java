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
package org.jboss.messaging.core.contract;

import java.io.Serializable;
import java.util.Map;


/**
 * A Replicator
 * 
 * This is used for replicating arbitrary data across a cluster.
 * 
 * Data is structured as follows:
 * 
 * There is an arbitrary key to identify the data, e.g. the connection factory name
 * Then, for that key, there is an entry for each node id.
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2868 $</tt>
 *
 * $Id: Replicator.java 2868 2007-07-10 20:22:16Z timfox $
 *
 */
public interface Replicator
{
   public static final String CF_PREFIX = "CF_";
   
   public static final String JVM_ID_KEY = "JVMID";
 
   /**
    * Broadcast data across the cluster, updating replication maps on all nodes, including the local
    * node.
    */
   void put(Serializable key, Serializable data) throws Exception;

   /**
    * Return a node-mapped replicated data.
    *
    * @return a Map<Integer(nodeID)-data>. Returns an empty map if no replicants are found for
    *         'key', but never null.
    */
   Map get(Serializable key) throws Exception;

   /**
    * Updates the replication maps across the cluster by removing the data corresponding to the give
    * key. Only the data corresponding to the current node is removed.
    */
   boolean remove(Serializable key) throws Exception;   
}
