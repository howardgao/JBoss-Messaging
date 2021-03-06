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
package org.jboss.messaging.core.jmx;

import org.jboss.messaging.core.contract.MessagingComponent;
import org.jboss.messaging.core.contract.PersistenceManager;
import org.jboss.messaging.core.impl.JDBCPersistenceManager;
import org.jboss.messaging.util.ExceptionUtil;

import javax.transaction.TransactionManager;

/**
 * A JDBCPersistenceManagerService
 *
 * MBean wrapper around a JDBCPersistenceManager
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 2684 $</tt>
 *
 * $Id: JDBCPersistenceManagerService.java 2684 2007-05-15 07:31:30Z timfox $
 *
 */
public class JDBCPersistenceManagerService extends JDBCServiceSupport
{
   private PersistenceManager persistenceManager;

   private boolean started;

   private boolean usingBatchUpdates;

   private boolean usingBinaryStream = true;

   private boolean usingTrailingByte;

   private int maxParams = 100;

   private boolean supportsBlobOnSelect = true;
   
   private boolean supportsSetNullOnBlobs = true;

   private boolean detectDuplicates = false;

   private int idCacheSize = 500;

   private boolean useNDBFailoverStrategy = false;

   // Constructors --------------------------------------------------------

   public JDBCPersistenceManagerService()
   {
   }

   // ServerPlugin implementation ------------------------------------------

   public MessagingComponent getInstance()
   {
      return persistenceManager;
   }

   // ServiceMBeanSupport overrides -----------------------------------------

   protected synchronized void startService() throws Exception
   {
      if (started)
      {
         throw new IllegalStateException("Service is already started");
      }

      super.startService();

      try
      {
         TransactionManager tm = getTransactionManagerReference();

         persistenceManager =
            new JDBCPersistenceManager(ds, tm, sqlProperties,
                                       createTablesOnStartup, usingBatchUpdates,
                                       usingBinaryStream, usingTrailingByte, maxParams,
                                       supportsBlobOnSelect, supportsSetNullOnBlobs, detectDuplicates, useNDBFailoverStrategy, idCacheSize);

         persistenceManager.start();

         started = true;
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " startService");
      }
   }

   protected void stopService() throws Exception
   {
      if (!started)
      {
         throw new IllegalStateException("Service is not started");
      }

      try
      {
         persistenceManager.stop();

         persistenceManager = null;

         started = false;
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " startService");
      }

      log.debug(this + " stopped");
   }

   // MBean attributes -------------------------------------------------------

   public boolean isUsingBatchUpdates()
   {
      return usingBatchUpdates;
   }

   public void setUsingBatchUpdates(boolean b)
   {
      usingBatchUpdates = b;
   }

   public int getMaxParams()
   {
      return maxParams;
   }

   public void setMaxParams(int maxParams)
   {
      this.maxParams = maxParams;
   }

   public boolean isUseNDBFailoverStrategy()
   {
      return useNDBFailoverStrategy;
   }

   public void setUseNDBFailoverStrategy(boolean useNDBFailoverStrategy)
   {
      this.useNDBFailoverStrategy = useNDBFailoverStrategy;
   }

   public boolean isUsingBinaryStream()
   {
      return usingBinaryStream;
   }

   public void setUsingBinaryStream(boolean b)
   {
      usingBinaryStream = b;
   }

   public boolean isUsingTrailingByte()
   {
      return usingTrailingByte;
   }

   public void setUsingTrailingByte(boolean b)
   {
      usingTrailingByte = b;
   }

   public boolean isSupportsBlobOnSelect()
   {
   	return supportsBlobOnSelect;
   }

   public void setSupportsBlobOnSelect(boolean b)
   {
   	this.supportsBlobOnSelect = b;
   }

   public boolean isDetectDuplicates()
   {
      return detectDuplicates;
   }

   public void setDetectDuplicates(boolean detectDuplicates)
   {
      this.detectDuplicates = detectDuplicates;
   }
   
   public boolean isSupportsSetNullOnBlobs()
   {
      return supportsSetNullOnBlobs;
   }

   public void setSupportsSetNullOnBlobs(boolean supportsSetNullOnBlobs)
   {
      this.supportsSetNullOnBlobs = supportsSetNullOnBlobs;
   }

   public int getIDCacheSize()
   {
      return idCacheSize;
   }

   public void setIDCacheSize(int idCacheSize)
   {
      this.idCacheSize = idCacheSize;
   }

}
