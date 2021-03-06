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
package org.jboss.messaging.core.impl;

import org.jboss.jms.message.JBossMessage;
import org.jboss.jms.tx.MessagingXid;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.contract.Message;
import org.jboss.messaging.core.contract.MessageReference;
import org.jboss.messaging.core.contract.PersistenceManager;
import org.jboss.messaging.core.impl.message.MessageFactory;
import org.jboss.messaging.core.impl.message.MessageSupport;
import org.jboss.messaging.core.impl.tx.PreparedTxInfo;
import org.jboss.messaging.core.impl.tx.Transaction;
import org.jboss.messaging.core.impl.tx.TxCallback;
import org.jboss.messaging.util.JDBCUtil;
import org.jboss.messaging.util.StreamUtils;
import org.jboss.messaging.util.Util;

import javax.sql.DataSource;
import javax.transaction.TransactionManager;
import javax.transaction.xa.Xid;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * JDBC implementation of PersistenceManager
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:adrian@jboss.org">Adrian Brock</a>
 * @author <a href="mailto:juha@jboss.org">Juha Lindfors</a>
 *
 * @version <tt>1.1</tt>
 *
 * JDBCPersistenceManager.java,v 1.1 2006/02/22 17:33:41 timfox Exp
 */
public class JDBCPersistenceManager extends JDBCSupport implements PersistenceManager
{
   // Constants -----------------------------------------------------

   private static final Logger log = Logger
         .getLogger(JDBCPersistenceManager.class);

   // Static --------------------------------------------------------

   private boolean trace = log.isTraceEnabled();

   private boolean usingBinaryStream = true;

   private boolean usingTrailingByte = false;

   private int maxParams;

   private RotatingID orderingID;

   private int nodeID;

   private boolean nodeIDSet;

   // Some versions of the oracle driver don't support binding blobs on select
   // clauses,
   // what would force us to use a two stage insert (insert and if successful,
   // update)
   private final boolean supportsBlobSelect;

   // DB2 doesn't support setNull on Blobs
   private final  boolean supportsSetNullOnBlobs;

   private boolean detectDuplicates;

   private Map<Integer, String> idCache = new ConcurrentHashMap<Integer, String>();

   private Map<Integer, String> failedOverIdCache = new ConcurrentHashMap<Integer, String>();

   //Use semaphore - performs better with high contention than synchronized
   private Semaphore lock = new Semaphore(1);

   private int idCacheCounter = 0;

   private final int idCacheSize;


   // Constructors --------------------------------------------------

   public JDBCPersistenceManager(DataSource ds, TransactionManager tm,
         Properties sqlProperties, boolean createTablesOnStartup,
         boolean usingBatchUpdates, boolean usingBinaryStream,
         boolean usingTrailingByte, int maxParams, boolean supportsBlobSelect, boolean supportsSetNullOnBlobs,
         boolean detectDuplicates, boolean useNDBFailoverStrategy, int idCacheSize)
   {
      super(ds, tm, sqlProperties, createTablesOnStartup, useNDBFailoverStrategy);

      // usingBatchUpdates is currently ignored due to sketchy support from
      // databases

      this.usingBinaryStream = usingBinaryStream;

      this.usingTrailingByte = usingTrailingByte;

      this.maxParams = maxParams;

      this.supportsBlobSelect = supportsBlobSelect;
      
      this.supportsSetNullOnBlobs = supportsSetNullOnBlobs;

      this.detectDuplicates = detectDuplicates;

      this.idCacheSize = idCacheSize;

   }

   public JDBCPersistenceManager(DataSource ds, TransactionManager tm,
         Properties sqlProperties, boolean createTablesOnStartup,
         boolean usingBatchUpdates, boolean usingBinaryStream,
         boolean usingTrailingByte, int maxParams, boolean supportsBlobSelect, boolean supportsSetNullOnBlobs)
   {
      this (ds, tm, sqlProperties, createTablesOnStartup, usingBatchUpdates, usingBinaryStream, usingTrailingByte,
            maxParams, supportsBlobSelect, supportsSetNullOnBlobs, false, false, 0);
   }

   // MessagingComponent overrides ---------------------------------

   public void start() throws Exception
   {
      super.start();

      Connection conn = null;

      PreparedStatement ps = null;

      TransactionWrapper wrap = new TransactionWrapper();

      try
      {
         conn = ds.getConnection();
         // JBossMessaging requires transaction isolation of READ_COMMITTED
         // Any looser isolation level and we cannot maintain consistency for
         // paging (HSQL)
         if (conn.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED)
         {
            int level = conn.getTransactionIsolation();

            String warn = "\n\n"
                  + "JBoss Messaging Warning: DataSource connection transaction isolation should be READ_COMMITTED, but it is currently "
                  + Util.transactionIsolationToString(level)
                  + ".\n"
                  + "                         Using an isolation level less strict than READ_COMMITTED may lead to data consistency problems.\n"
                  + "                         Using an isolation level more strict than READ_COMMITTED may lead to deadlock.\n";
            log.warn(warn);
         }

         log.debug("Adding record on JBM_DUAL");

         // Now we need to insert a row in the DUAL table if it doesn't contain
         // one already
         ps = conn.prepareStatement(this.getSQLStatement("INSERT_DUAL"));

         try
         {
            int rows = ps.executeUpdate();

            if (trace)
            {
               log.trace("Inserted " + rows + " rows into dual");
            }
         }
         catch (SQLException e)
         {
            wrap.exceptionOccurred();
            wrap.end();
            wrap = new TransactionWrapper();

            log.debug("Checking for existance on JBM_DUAL");

            Statement selectCount = null;
            ResultSet rset = null;
            try
            {
               selectCount = conn.createStatement();
               rset = selectCount.executeQuery(this.getSQLStatement("CHECK_DUAL"));

               // if JBM_DUAL is empty, and if an exception happened, we should
               // warn!
               // if JBM_DUAL has a line already, we don't care about the
               // exception...
               if (!rset.next())
               {
                  log
                        .debug(
                              "JBM_DUAL didn't have a record.. throwing exception",
                              e);
                  throw e;
               }

               // if there are two lines or more on JBM_DUAL, that is also a
               // problem
               if (rset.next())
               {
                  log
                        .debug("duplicated record found on JBM_DUAL... throwing exception");
                  throw new IllegalStateException(
                        "JBM_DUAL is missing a primary key as it allowed a duplicate value");
               }
            }
            finally
            {
               try
               {
                  closeResultSet(rset);
               }
               catch (Throwable ignored)
               {
               }
               try
               {
                  closeStatement(selectCount);
               }
               catch (Throwable ignored)
               {
               }
            }
         }
      }
      catch (Exception e)
      {
         wrap.exceptionOccurred();
         throw e;
      }
      finally
      {
         closeStatement(ps);
         closeConnection(conn);
         wrap.end();
      }

      log.debug(this + " started");
   }

   public void stop() throws Exception
   {
      super.stop();

      idCache.clear();
   }

   // Injection -------------------------------------------------

   // This is only known by server peer so we inject it after startup

   public void injectNodeID(int nodeID) throws Exception
   {
      this.nodeID = nodeID;

      this.nodeIDSet = true;

      this.orderingID = new RotatingID(nodeID);
      
      if (detectDuplicates)
      {
         loadIDCache(idCache, nodeID);
      }
   }

   // PersistenceManager implementation -------------------------

   // Related to XA Recovery
   // ======================

   public List getMessageChannelPairRefsForTx(long transactionId)
         throws Exception
   {
      String sql = this.getSQLStatement("SELECT_MESSAGE_ID_FOR_REF");
      return getMessageChannelPair(sql, transactionId);
   }

   public List getMessageChannelPairAcksForTx(long transactionId)
         throws Exception
   {
      String sql = this.getSQLStatement("SELECT_MESSAGE_ID_FOR_ACK");
      return getMessageChannelPair(sql, transactionId);
   }

   public List retrievePreparedTransactions() throws Exception
   {
      if (!this.nodeIDSet)
      {
         // Sanity
         throw new IllegalStateException("Node id has not been set");
      }

      /*
       * Note the API change for 1.0.2 XA Recovery -- List now contains
       * instances of PreparedTxInfo<TxId, Xid> instead of direct Xids [JPL]
       */

      Connection conn = null;
      PreparedStatement st = null;
      ResultSet rs = null;
      PreparedTxInfo txInfo = null;
      TransactionWrapper wrap = new TransactionWrapper();

      try
      {
         List<PreparedTxInfo> transactions = new ArrayList<PreparedTxInfo>();

         conn = ds.getConnection();

         st = conn
               .prepareStatement(getSQLStatement("SELECT_PREPARED_TRANSACTIONS"));

         st.setInt(1, nodeID);

         rs = st.executeQuery();

         while (rs.next())
         {
            // get the existing tx id --MK START
            long txId = rs.getLong(1);

            byte[] branchQual = getVarBinaryColumn(rs, 2);

            int formatId = rs.getInt(3);

            byte[] globalTxId = getVarBinaryColumn(rs, 4);

            Xid xid = new MessagingXid(branchQual, formatId, globalTxId);

            // create a tx info object with the result set detailsdetails
            txInfo = new PreparedTxInfo(txId, xid);

            transactions.add(txInfo);
         }

         return transactions;

      }
      catch (Exception e)
      {
         wrap.exceptionOccurred();
         throw e;
      }
      finally
      {
         closeResultSet(rs);
         closeStatement(st);
         closeConnection(conn);
         wrap.end();
      }
   }

   // Related to counters
   // ===================

   public long reserveIDBlock(final String counterName, final int size)
         throws Exception
   {
      if (trace)
      {
         log.trace("Getting ID block for counter " + counterName + ", size "
               + size);
      }

      if (size <= 0) { throw new IllegalArgumentException(
            "block size must be > 0"); }

      class ReserveIDBlockRunner extends JDBCTxRunner2<Long>
      {
         public Long doTransaction() throws Exception
         {
            // For the clustered case - this MUST use SELECT .. FOR UPDATE or a
            // similar
            // construct the locks the row
            String selectCounterSQL = getSQLStatement("SELECT_COUNTER");

            PreparedStatement ps = null;
            ResultSet rs = null;

            try
            {
               ps = conn.prepareStatement(selectCounterSQL);

               ps.setString(1, counterName);

               rs = ps.executeQuery();

               if (trace)
               {
                  log.trace(JDBCUtil.statementToString(selectCounterSQL,
                        counterName));
               }

               if (!rs.next())
               {
                  rs.close();
                  ps.close();

                  // There is a very small possibility that two threads will
                  // attempt to insert the same counter
                  // at the same time, if so, then the second one will fail
                  // eventually after a few retries by throwing
                  // a primary key violation.

                  String insertCounterSQL = getSQLStatement("INSERT_COUNTER");

                  ps = conn.prepareStatement(insertCounterSQL);

                  ps.setString(1, counterName);
                  ps.setLong(2, size);

                  int rows = ps.executeUpdate();
                  if (trace)
                  {
                     log.trace(JDBCUtil.statementToString(insertCounterSQL,
                           counterName, new Integer(size))
                           + " inserted " + rows + " rows");
                  }

                  return 0L;
               }
               else
               {
                  long nextId = rs.getLong(1);

                  rs.close();
                  ps.close();

                  String updateCounterSQL = getSQLStatement("UPDATE_COUNTER");

                  ps = conn.prepareStatement(updateCounterSQL);

                  ps.setLong(1, nextId + size);
                  ps.setString(2, counterName);

                  int rows = ps.executeUpdate();

                  if (trace)
                  {
                     log.trace(JDBCUtil.statementToString(updateCounterSQL,
                           new Long(nextId + size), counterName)
                           + " updated " + rows + " rows");
                  }

                  return nextId;
               }
            }
            finally
            {
               closeResultSet(rs);
               closeStatement(ps);
            }
         }
      }

      return new ReserveIDBlockRunner().executeWithRetry();
   }

   /*
    * Retrieve a List of messages corresponding to the specified List of message
    * ids. The implementation here for HSQLDB does this by using a
    * PreparedStatment with an IN clause with a maximum of 100 elements. If
    * there are more than maxParams message to retrieve this is repeated a
    * number of times. For "Enterprise" databases (Oracle, DB2, Sybase etc) a
    * more sophisticated technique should be used e.g. Oracle ARRAY types in
    * Oracle which can be submitted as a param to an Oracle prepared statement
    * Although this would all be DB specific.
    */
   public List getMessages(final List messageIds) throws Exception
   {
      if (trace)
      {
         log.trace("Getting batch of messages for " + messageIds);
      }

      class GetMessageListTX extends JDBCTxRunner2<List>
      {

         public List<Message> doTransaction() throws Exception
         {
            PreparedStatement ps = null;
            ResultSet rs = null;

            try
            {
               Iterator iter = messageIds.iterator();

               int size = messageIds.size();

               int count = 0;

               List<Message> msgs = new ArrayList<Message>();

               while (iter.hasNext())
               {
                  if (ps == null)
                  {
                     // PreparedStatements are cached in the JCA layer so we
                     // will never actually have more than
                     // 100 distinct ones
                     int numParams;
                     if (count < (size / maxParams) * maxParams)
                     {
                        numParams = maxParams;
                     }
                     else
                     {
                        numParams = size % maxParams;
                     }
                     StringBuffer buff = new StringBuffer(
                           getSQLStatement("LOAD_MESSAGES"));
                     buff.append(" WHERE ").append(
                           getSQLStatement("MESSAGE_ID_COLUMN"))
                           .append(" IN (");
                     for (int i = 0; i < numParams; i++)
                     {
                        buff.append("?");
                        if (i < numParams - 1)
                        {
                           buff.append(",");
                        }
                     }
                     buff.append(")");
                     ps = conn.prepareStatement(buff.toString());

                     if (trace)
                     {
                        log.trace(buff.toString());
                     }
                  }

                  long msgId = ((Long) iter.next()).longValue();

                  ps.setLong((count % maxParams) + 1, msgId);

                  count++;

                  if (!iter.hasNext() || count % maxParams == 0)
                  {
                     rs = ps.executeQuery();

                     while (rs.next())
                     {
                        long messageId = rs.getLong(1);

                        boolean reliable = rs.getString(2).equals("Y");

                        long expiration = rs.getLong(3);

                        long timestamp = rs.getLong(4);

                        byte priority = rs.getByte(5);

                        byte[] bytes = getBytes(rs, 6);

                        HashMap headers = bytesToMap(bytes);

                        byte[] payload = getBytes(rs, 7);

                        byte type = rs.getByte(8);

                        Message m = MessageFactory.createMessage(messageId,
                              reliable, expiration, timestamp, priority,
                              headers, payload, type);
                        msgs.add(m);
                     }

                     rs.close();
                     ps.close();
                     ps = null;
                  }
               }

               if (trace)
               {
                  log.trace("Loaded " + msgs.size() + " messages in total");
               }

               return msgs;
            }
            catch (Exception e)
            {
               throw e;
            }
            finally
            {
               closeResultSet(rs);
               closeStatement(ps);
            }
         }
      }

      return new GetMessageListTX().executeWithRetry();

   }

   // Related to paging functionality
   // ===============================

   // Used to page NP messages or P messages in a non recoverable queue

   public void pageReferences(final long channelID, final List references,
         final boolean page) throws Exception
   {
      if (trace)
      {
         log.trace("Paging references in channel " + channelID + " refs "
               + references.size());
      }
      
      class PageReferencesRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement psInsertReference = null;
            PreparedStatement psInsertMessage = null;
            PreparedStatement psUpdateMessage = null;

            try
            {
               Iterator iter = references.iterator();

               psInsertReference = conn
                     .prepareStatement(getSQLStatement("INSERT_MESSAGE_REF"));

               if (supportsBlobSelect)
               {
                  psInsertMessage = conn
                        .prepareStatement(getSQLStatement("INSERT_MESSAGE_CONDITIONAL_FULL"));
               }
               else
               {
                  psInsertMessage = conn
                        .prepareStatement(getSQLStatement("INSERT_MESSAGE_CONDITIONAL"));
                  psUpdateMessage = conn
                        .prepareStatement(getSQLStatement("UPDATE_MESSAGE_4CONDITIONAL"));
               }

               while (iter.hasNext())
               {
                  // We may need to persist the message itself
                  MessageReference ref = (MessageReference) iter.next();

                  // For non reliable refs we insert the ref (and maybe the
                  // message) itself

                  // Now store the reference
                    
                  log.trace("Paged ref with page order " + ref.getPagingOrder());

                  addReference(channelID, ref, psInsertReference, page);

                  int rows = psInsertReference.executeUpdate();

                  if (trace)
                  {
                     log.trace("Inserted " + rows + " rows");
                  }
                  
                  // Maybe we need to persist the message itself
                  Message m = ref.getMessage();

                  rows = storeMessage(m, psInsertMessage, psUpdateMessage);

                  if (trace)
                  {
                     log.trace("Inserted " + rows + " rows");
                  }
               }

               return null;
            }
            finally
            {
               closeStatement(psInsertReference);
               closeStatement(psInsertMessage);
               closeStatement(psUpdateMessage);
            }
         }
      }

      new PageReferencesRunner().executeWithRetry();
   }

   // After loading paged refs this is used to remove any NP or P messages in a
   // unrecoverable channel
   public void removeDepagedReferences(final long channelID,
         final List references) throws Exception
   {
      if (trace)
      {
         log.trace(this + " Removing depaged " + references.size()
               + " refs from channel " + channelID);
      }

      class RemoveDepagedReferencesRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement psDeleteReference = null;

            try
            {
               psDeleteReference = conn
                     .prepareStatement(getSQLStatement("DELETE_MESSAGE_REF"));

               Iterator iter = references.iterator();

               while (iter.hasNext())
               {
                  MessageReference ref = (MessageReference) iter.next();

                  removeReference(channelID, ref, psDeleteReference);

                  int rows = psDeleteReference.executeUpdate();

                  if (trace)
                  {
                     log.trace("Deleted " + rows + " references");
                  }

               }

               return null;
            }
            finally
            {
               closeStatement(psDeleteReference);
            }
         }
      }

      new RemoveDepagedReferencesRunner().executeWithRetry();

      deleteMessages(references);
   }

   // After loading paged refs this is used to update P messages to non paged
   public void updateReferencesNotPagedInRange(final long channelID,
         final long orderStart, final long orderEnd, final long num)
         throws Exception
   {
      if (trace)
      {
         log.trace("Updating paged references for channel " + channelID
               + " between " + orderStart + " and " + orderEnd);
      }

      class UpdateReferencesNotPagedInRangeRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement ps = null;

            try
            {
               ps = conn
                     .prepareStatement(getSQLStatement("UPDATE_REFS_NOT_PAGED"));

               ps.setLong(1, orderStart);

               ps.setLong(2, orderEnd);

               ps.setLong(3, channelID);

               int rows = ps.executeUpdate();

               if (trace)
               {
                  log.trace(JDBCUtil.statementToString(
                        getSQLStatement("UPDATE_REFS_NOT_PAGED"), new Long(
                              channelID), new Long(orderStart), new Long(
                              orderEnd))
                        + " updated " + rows + " rows");
               }

               // Sanity check
               if (rows != num) { throw new IllegalStateException(
                     "Did not update correct number of rows"); }

               return null;
            }
            finally
            {
               closeStatement(ps);
            }
         }
      }

      new UpdateReferencesNotPagedInRangeRunner().executeWithRetry();
   }

   public void updatePageOrder(final long channelID, final List references)
         throws Exception
   {
      if (trace)
      {
         log.trace("Updating page order for channel:" + channelID);
      }

      class UpdatePageOrderRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement psUpdateReference = null;
            try
            {
               Iterator iter = references.iterator();

               psUpdateReference = conn
                     .prepareStatement(getSQLStatement("UPDATE_PAGE_ORDER"));

               while (iter.hasNext())
               {
                  MessageReference ref = (MessageReference) iter.next();

                  psUpdateReference.setLong(1, ref.getPagingOrder());

                  psUpdateReference.setLong(2, ref.getMessage().getMessageID());

                  psUpdateReference.setLong(3, channelID);

                  int rows = psUpdateReference.executeUpdate();

                  if (trace)
                  {
                     log.trace("Updated " + rows + " rows");
                  }
               }

               return null;
            }
            finally
            {
               closeStatement(psUpdateReference);
            }
         }
      }

      new UpdatePageOrderRunner().executeWithRetry();
   }

   public List getPagedReferenceInfos(final long channelID,
         final long orderStart, final int number) throws Exception
   {
      if (trace)
      {
         log.trace("loading message reference info for channel " + channelID
               + " from " + orderStart + " number " + number);
      }

      List<ReferenceInfo> refs = new ArrayList<ReferenceInfo>();

      Connection conn = null;
      PreparedStatement ps = null;
      ResultSet rs = null;
      TransactionWrapper wrap = new TransactionWrapper();

      try
      {
         conn = ds.getConnection();

         ps = conn.prepareStatement(getSQLStatement("LOAD_PAGED_REFS"));

         ps.setLong(1, channelID);

         ps.setLong(2, orderStart);

         ps.setLong(3, orderStart + number - 1);

         rs = ps.executeQuery();

         long ord = orderStart;

         while (rs.next())
         {
            long msgId = rs.getLong(1);
            int deliveryCount = rs.getInt(2);
            int pageOrd = rs.getInt(3);
            long sched = rs.getLong(4);

            // Sanity check
            if (pageOrd != ord) { throw new IllegalStateException(
                  "Unexpected pageOrd: " + pageOrd + " expected: " + ord); }

            ReferenceInfo ri = new ReferenceInfo(msgId, deliveryCount, sched);

            refs.add(ri);
            ord++;
         }

         // Sanity check
         if (ord != orderStart + number) { throw new IllegalStateException(
               "Didn't load expected number of references, loaded: "
                     + (ord - orderStart) + " expected: " + number); }

         return refs;
      }
      catch (Exception e)
      {
         wrap.exceptionOccurred();
         throw e;
      }
      finally
      {
         closeResultSet(rs);
         closeStatement(ps);
         closeConnection(conn);
         wrap.end();
      }
   }

   /*
    * Load the initial, non paged refs
    */
   public InitialLoadInfo loadFromStart(final long channelID, final int number)
         throws Exception
   {
      if (trace)
      {
         log.trace("loading initial reference infos for channel " + channelID);
      }

      Connection conn = null;
      PreparedStatement ps = null;
      ResultSet rs = null;
      TransactionWrapper wrap = new TransactionWrapper();

      try
      {
         conn = ds.getConnection();

         // First we get the values for min() and max() page order
         ps = conn.prepareStatement(getSQLStatement("SELECT_MIN_MAX_PAGE_ORD"));

         ps.setLong(1, channelID);

         rs = ps.executeQuery();

         rs.next();

         Long minOrdering = new Long(rs.getLong(1));

         if (rs.wasNull())
         {
            minOrdering = null;
         }

         Long maxOrdering = new Long(rs.getLong(2));

         if (rs.wasNull())
         {
            maxOrdering = null;
         }

         rs.close();
         
         ps.close();

         ps = conn.prepareStatement(getSQLStatement("LOAD_UNPAGED_REFS"));
         
         ps.setFetchSize(number);

         ps.setLong(1, channelID);

         rs = ps.executeQuery();

         List<ReferenceInfo> refs = new ArrayList<ReferenceInfo>();

         List<ReferenceInfo> refsToUpdate = new ArrayList<ReferenceInfo>();

         int count = 0;
         boolean inMemSwitch = false;
         boolean nonPageFlag = true;
         while (rs.next())
         {
            rs.getObject(4);
            inMemSwitch = rs.wasNull();
            if (nonPageFlag && (!inMemSwitch))
            {
               nonPageFlag = false;
            }
            
            long msgId = rs.getLong(1);
            int deliveryCount = rs.getInt(2);
            long sched = rs.getLong(3);

            ReferenceInfo ri = new ReferenceInfo(msgId, deliveryCount, sched);

            if ((count < number) && nonPageFlag)
            {
               refs.add(ri);
            }
            else
            {
               if (inMemSwitch)
               {
                  refsToUpdate.add(ri);
               }
            }
            count++;
         }

         // No refs paged

         if (!refsToUpdate.isEmpty())
         {
            // Take any overflow and convert them to paged refs

            ps.close();
            ps = conn.prepareStatement(getSQLStatement("UPDATE_PAGE_ORDER"));

            Iterator<ReferenceInfo> iter = refsToUpdate.iterator();

            long ordering = 0;

            if (maxOrdering != null)
            {
               ordering = maxOrdering.longValue() + 1;
            }

            while (iter.hasNext())
            {
               ReferenceInfo ri = (ReferenceInfo) iter.next();

               ps.setLong(1, ordering);

               ps.setLong(2, ri.getMessageId());

               ps.setLong(3, channelID);

               int rows = ps.executeUpdate();

               if (trace)
               {
                  log.trace("Updated " + rows + " rows");
               }

               ordering++;
            }

            if (minOrdering == null)
            {
               minOrdering = new Long(0);
            }

            maxOrdering = new Long(ordering - 1);
         }

         return new InitialLoadInfo(minOrdering, maxOrdering, refs);
      }
      catch (Exception e)
      {
         wrap.exceptionOccurred();
         throw e;
      }
      finally
      {
         closeResultSet(rs);
         closeStatement(ps);
         closeConnection(conn);
         wrap.end();
      }
   }

   // Merging functionality
   // --------------------

   public void mergeTransactions(final int fromNodeID,
         final int toNodeID) throws Exception
   {
      if (trace)
      {
         log.trace("Merging transactions from channel " + fromNodeID
               + " to " + toNodeID);
      }

      // Sanity check

      if (fromNodeID == toNodeID) { throw new IllegalArgumentException(
            "Cannot merge transactions - they have the same channel id!!"); }

      class MergeTransactionsRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement statement = null;
            try
            {
               statement = conn.prepareStatement(getSQLStatement("UPDATE_TX"));
               statement.setInt(1, toNodeID);
               statement.setInt(2, fromNodeID);
               int affected = statement.executeUpdate();

               log.debug("Merged " + affected + " transactions from channel "
                     + fromNodeID + " into node " + toNodeID);

               return null;
            }
            finally
            {
               closeStatement(statement);
            }
         }
      }

      new MergeTransactionsRunner().executeWithRetry();

      if (detectDuplicates)
      {
         loadIDCache(failedOverIdCache, fromNodeID);
      }
   }

   /* (non-Javadoc)
    * @see org.jboss.messaging.core.contract.PersistenceManager#dropChannelMessages(long)
    */
   public void dropChannelMessages(final long channelID) throws Exception
   {
      class ChannelDropRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement ps = null;
            PreparedStatement ps2 = null;
            ResultSet rs = null;
            
            try
            {
               ps = conn.prepareStatement(getSQLStatement("LOAD_REFS"));
               ps.setLong(1, channelID);
               rs = ps.executeQuery();
               int rows;
               
               ps2 = conn.prepareStatement(getSQLStatement("DELETE_CHANNEL_MESSAGE"));
               while (rs.next())
               {
                  long mid = rs.getLong(1);
                  ps2.setLong(1, mid);
                  ps2.executeUpdate();
               }
               ps2.close();
               
               ps.close();
               
               ps = conn.prepareStatement(getSQLStatement("DELETE_CHANNEL_MESSAGE_REF"));
               ps.setLong(1, channelID);
               rows = ps.executeUpdate();
               
               if (trace)
               {
                  log.trace("Update page ord updated " + rows + " rows");
               }
            }
            finally
            {
               closeResultSet(rs);
               closeStatement(ps);
               closeStatement(ps2);
            }
            return null;
         }
      }
      
      new ChannelDropRunner().executeWithRetry();
   }

   /* (non-Javadoc)
    * load messages from the channel (fromID) in the DB and
    * add the messages to the channel (toID)
    * @see org.jboss.messaging.core.contract.PersistenceManager#mergeChannelMessage(long, long)
    */
   public void mergeChannelMessage(final long fromID, final long toID) throws Exception
   {

      if (fromID == toID) { throw new IllegalArgumentException(
            "Cannot merge queues - they have the same channel id!!"); }

      class ChannelMergeRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement ps = null;
            ResultSet rs = null;
            PreparedStatement ps2 = null;

            try
            {
               //first get max page order of toID channel
               ps = conn.prepareStatement(getSQLStatement("SELECT_MIN_MAX_PAGE_ORD"));

               ps.setLong(1, toID);

               rs = ps.executeQuery();

               rs.next();

               Long maxOrdering = new Long(rs.getLong(2));

               long pageCount = 0;

               if (rs.wasNull())
               {
                  maxOrdering = null;
               }
               else
               {
                  //If maxOrdering is not null, update the page order
                  pageCount = maxOrdering + 1;
               }

               rs.close();

               ps.close();
               
               if (pageCount > 0)
               {
                  //update paging
                  ps = conn.prepareStatement(getSQLStatement("LOAD_REFS"));
                  
                  ps2 = conn
                     .prepareStatement(getSQLStatement("UPDATE_PAGE_ORDER"));

                  ps.setLong(1, fromID);

                  rs = ps.executeQuery();

                  while (rs.next())
                  {
                     long msgId = rs.getLong(1);

                     ps2.setLong(1, pageCount);

                     ps2.setLong(2, msgId);

                     ps2.setLong(3, fromID);
                     
                     int rows = ps2.executeUpdate();

                     if (trace)
                     {
                        log.trace("Update page ord updated " + rows + " rows");
                     }

                     pageCount++;
                  }
                  ps2.close();
                  ps.close();
               }
               
               //update channel id
               ps = conn.prepareStatement(getSQLStatement("UPDATE_CHANNEL_ID"));

               ps.setLong(1, toID);

               ps.setLong(2, fromID);

               int rows = ps.executeUpdate();

               if (trace)
               {
                  log.trace("Update channel id updated " + rows + " rows");
               }
            }
            finally
            {
               closeResultSet(rs);
               closeStatement(ps);
               closeStatement(ps2);
            }
            return null;
         }
      }
      
      new ChannelMergeRunner().executeWithRetry();
   }

   public InitialLoadInfo mergeAndLoad(final long fromChannelID,
         final long toChannelID, final int numberToLoad,
         final long firstPagingOrder, final long nextPagingOrder)
         throws Exception
   {
      if (trace)
      {
         log.trace("Merging channel from " + fromChannelID + " to "
               + toChannelID + " numberToLoad:" + numberToLoad
               + " firstPagingOrder:" + firstPagingOrder + " nextPagingOrder:"
               + nextPagingOrder);
      }

      // Sanity

      if (fromChannelID == toChannelID) { throw new IllegalArgumentException(
            "Cannot merge queues - they have the same channel id!!"); }

      class MergeAndLoadRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement ps = null;
            ResultSet rs = null;
            PreparedStatement ps2 = null;

            try
            {
               /*
                * If channel is paging and has full size f
                *
                * then we don't need to load any refs but we need to:
                *
                * make sure the page ord is correct across the old paged and new
                * refs
                *
                * we know the max page ord (from the channel) for the old refs
                * so we just need to:
                *
                * 1) Iterate through the failed channel and update page_ord =
                * max + 1, max + 2 etc
                *
                * 2) update channel id
                *
                *
                * If channel is not paging and the total refs before and after
                * <=f
                *
                * 1) Load all refs from failed channel
                *
                * 2) Update channel id
                *
                * return those refs
                *
                *
                * If channel is not paging but total new refs > f
                *
                * 1) Iterate through failed channel refs and take the first x to
                * make the channel full
                *
                * 2) Update the others with page_ord starting at zero
                *
                * 3) Update channel id
                *
                * In general:
                *
                * We have number to load n, max page size p
                *
                * 1) Iterate through failed channel refs in page_ord order
                *
                * 2) Put the first n in a List.
                *
                * 3) Initialise page_ord_count to be p or 0 depending on whether
                * it was specified
                *
                * 4) Update the page_ord of the remaining refs accordiningly
                *
                * 5) Update the channel id
                *
                */

               // First load the refs from the failed channel
               List<ReferenceInfo> refs = new ArrayList<ReferenceInfo>();

               ps = conn.prepareStatement(getSQLStatement("LOAD_REFS"));

               ps.setLong(1, fromChannelID);

               rs = ps.executeQuery();

               int count = 0;

               boolean arePaged = false;

               long pageOrd = nextPagingOrder;

               while (rs.next())
               {
                  long msgId = rs.getLong(1);
                  int deliveryCount = rs.getInt(2);
                  long sched = rs.getLong(3);

                  if (count < numberToLoad)
                  {
                     ReferenceInfo ri = new ReferenceInfo(msgId, deliveryCount,
                           sched);

                     refs.add(ri);
                  }

                  // Set page ord

                  if (ps2 == null)
                  {
                     ps2 = conn
                           .prepareStatement(getSQLStatement("UPDATE_PAGE_ORDER"));
                  }

                  if (count < numberToLoad)
                  {
                     ps2.setNull(1, Types.BIGINT);

                     if (trace)
                     {
                        log.trace("Set page ord to null");
                     }
                  }
                  else
                  {
                     ps2.setLong(1, pageOrd);

                     if (trace)
                     {
                        log.trace("Set page ord to " + pageOrd);
                     }

                     arePaged = true;

                     pageOrd++;
                  }

                  ps2.setLong(2, msgId);

                  ps2.setLong(3, fromChannelID);

                  int rows = ps2.executeUpdate();

                  if (trace)
                  {
                     log.trace("Update page ord updated " + rows + " rows");
                  }

                  count++;
               }

               ps.close();

               // Now swap the channel id

               ps = conn.prepareStatement(getSQLStatement("UPDATE_CHANNEL_ID"));

               ps.setLong(1, toChannelID);

               ps.setLong(2, fromChannelID);

               int rows = ps.executeUpdate();

               if (trace)
               {
                  log.trace("Update channel id updated " + rows + " rows");
               }

               if (arePaged)
               {
                  return new InitialLoadInfo(new Long(firstPagingOrder),
                        new Long(pageOrd - 1), refs, true);
               }
               else
               {
                  return new InitialLoadInfo(null, null, refs, true);
               }
            }
            finally
            {
               closeResultSet(rs);
               closeStatement(ps);
               closeStatement(ps2);
            }
         }
      }
      return (InitialLoadInfo) new MergeAndLoadRunner().executeWithRetry();
   }

   // End of paging functionality
   // ===========================

   public void addReference(final long channelID, final MessageReference ref,
         final Transaction tx) throws Exception
   {
      if (trace) { log.trace("Adding reference " + ref + " in channel " + channelID + " tx " + tx); }

      class AddReferenceRunner extends JDBCTxRunner2
      {
         private Message message;
         private boolean messagePersisted = false;
         public Object doTransaction() throws Exception
         {
            PreparedStatement psReference = null;
            PreparedStatement psInsertMessage = null;

            message = ref.getMessage();

            try
            {
               psReference = conn
                     .prepareStatement(getSQLStatement("INSERT_MESSAGE_REF"));

               // Add the reference
               addReference(channelID, ref, psReference, false);

               int rows = psReference.executeUpdate();

               if (trace)
               {
                  log.trace("Inserted " + rows + " rows");
               }

               if (!message.isPersisted())
               {
                  // First time so persist the message
                  psInsertMessage = conn
                        .prepareStatement(getSQLStatement("INSERT_MESSAGE"));

                  storeMessage(message, psInsertMessage, true);
                  rows = psInsertMessage.executeUpdate();

                  if (trace)
                  {
                     log.trace("Inserted/updated " + rows + " rows");
                  }

                  log.trace("message Inserted/updated " + rows + " rows");



                  if (message instanceof JBossMessage)
                  {
                     cacheID(conn, ((JBossMessage) message).getJMSMessageID());
                  }
                  // Needs to be at the end - in case an exception is thrown in
                  // which case retry will be attempted and we want to insert it
                  // again
                  message.setPersisted(true);
                  messagePersisted = true;
               }

               return null;
            }
            finally
            {
               closeStatement(psReference);
               closeStatement(psInsertMessage);
            }
         }

         public void rollback()
         {
            if(messagePersisted)
            {
               messagePersisted = false;
               message.setPersisted(false);
            }
         }
      }

      if (tx != null)
      {
         // In a tx so we just add the ref in the tx in memory for now
         TransactionCallback callback = getCallback(tx);

         callback.addReferenceToAdd(channelID, ref);
      }
      else
      {
         // No tx so add the ref directly in the db
         new AddReferenceRunner().executeWithRetry();
      }
   }

   public void moveReference(final long sourceChannelID, final long destChannelID, final MessageReference ref)
      throws Exception
   {
      if (trace) { log.trace("Moving reference " + ref + " from " + sourceChannelID + " to " + destChannelID); }

      class MoveReferenceRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement psReference = null;

            try
            {
               psReference = conn.prepareStatement(getSQLStatement("MOVE_REFERENCE"));

               psReference.setLong(1, destChannelID);
               psReference.setLong(2, ref.getMessage().getMessageID());
               psReference.setLong(3, sourceChannelID);

               int rows = psReference.executeUpdate();

               if (trace)
               {
                  log.trace("Updated " + rows + " rows");
               }

               return null;
            }
            finally
            {
               closeStatement(psReference);
            }
         }
      }

      new MoveReferenceRunner().executeWithRetry();
   }

   public void updateDeliveryCount(final long channelID,
         final MessageReference ref) throws Exception
   {
      class UpdateDeliveryCountRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement psReference = null;

            try
            {
               psReference = conn
                     .prepareStatement(getSQLStatement("UPDATE_DELIVERY_COUNT"));

               psReference.setInt(1, ref.getDeliveryCount());

               psReference.setLong(2, ref.getMessage().getMessageID());

               psReference.setLong(3, channelID);

               int rows = psReference.executeUpdate();

               if (trace)
               {
                  log.trace("Updated " + rows + " rows");
               }

               return null;
            }
            finally
            {
               closeStatement(psReference);
            }
         }
      }

      new UpdateDeliveryCountRunner().executeWithRetry();
   }

   public void removeReference(final long channelID,
         final MessageReference ref, final Transaction tx) throws Exception
   {
      if (trace) { log.trace("Removing reference " + ref + " in channel " + channelID + " tx " + tx); }

      class RemoveReferenceRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement psReference = null;

            try
            {
               psReference = conn
                     .prepareStatement(getSQLStatement("DELETE_MESSAGE_REF"));

               // Remove the message reference
               removeReference(channelID, ref, psReference);

               int rows = psReference.executeUpdate();

               if (rows != 1)
               {
                  log.warn("Failed to remove row for: " + ref);
                  return null;
               }

               if (trace)
               {
                  log.trace("Deleted " + rows + " references");
               }

               return null;
            }
            finally
            {
               closeStatement(psReference);
            }
         }
      }

      if (tx != null)
      {
         // In a tx so we just add the ref in the tx in memory for now

         TransactionCallback callback = getCallback(tx);

         callback.addReferenceToRemove(channelID, ref);
      }
      else
      {
         // No tx so we remove the reference directly from the db

         new RemoveReferenceRunner().executeWithRetry();

         deleteMessage(ref.getMessage().getMessageID());
      }
   }



   public boolean idExists(String messageID) throws Exception
   {
      //Does not need to be fast since only called after failover - a scan is sufficient

      for (String id: failedOverIdCache.values())
      {
         if (messageID.equals(id))
         {
            return true;
         }
      }

      return false;
   }

   public void addTransaction(Transaction tx)
   {
      //this forces the tx to be added
      getCallback(tx);
   }


   // Public --------------------------------------------------------

   public String toString()
   {
      return "JDBCPersistenceManager[" + Integer.toHexString(hashCode()) + "]";
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected void cacheID(Connection conn, String id) throws Exception
   {
      if (!detectDuplicates)
      {
         return;
      }

      int pos;

      lock.acquire();

      try
      {
         pos = idCacheCounter;

         idCacheCounter++;

         if (idCacheCounter == idCacheSize)
         {
            idCacheCounter = 0;
         }
      }
      finally
      {
         lock.release();
      }

      boolean exists = idCache.containsKey(pos);

      PreparedStatement ps = null;

      try
      {
         if (exists)
         {
            ps = conn.prepareStatement(getSQLStatement("UPDATE_ID_IN_CACHE"));

            ps.setString(1, id);

            ps.setInt(2, nodeID);

            ps.setInt(3, pos);
         }
         else
         {
            ps = conn.prepareStatement(getSQLStatement("INSERT_ID_IN_CACHE"));

            ps.setInt(1, nodeID);

            ps.setInt(2, pos);

            ps.setString(3, id);
         }

         int rows = ps.executeUpdate();

         if (rows != 1)
         {
            log.warn("Failed to cache id value");
         }
      }
      finally
      {
         closeStatement(ps);
      }

      idCache.put(pos, id);
   }

   protected void loadIDCache(Map<Integer, String> cache, int theNodeID) throws Exception
   {
      Connection conn = null;
      PreparedStatement st = null;
      ResultSet rs = null;
      TransactionWrapper wrap = new TransactionWrapper();

      cache.clear();

      try
      {
         conn = ds.getConnection();

         st = conn.prepareStatement(getSQLStatement("LOAD_ID_CACHE"));

         st.setInt(1, theNodeID);

         rs = st.executeQuery();

         int maxCounter = -1;

         int c = 0;

         while (rs.next() && c < idCacheSize)
         {
            int counter = rs.getInt(1);
            
            String id = rs.getString(2);

            cache.put(counter, id);

            maxCounter = Math.max(maxCounter, counter);

            c++;
         }

         idCacheCounter = maxCounter + 1;
         
         if (idCacheCounter >= idCacheSize)
         {
            idCacheCounter = 0;
         }
      }
      catch (Exception e)
      {
         wrap.exceptionOccurred();
         throw e;
      }
      finally
      {
         closeResultSet(rs);
         closeStatement(st);
         closeConnection(conn);
         wrap.end();
      }
   }

   protected TransactionCallback getCallback(Transaction tx)
   {
      TransactionCallback callback = (TransactionCallback) tx.getCallback(this);

      if (callback == null)
      {
         callback = new TransactionCallback(tx);

         tx.addCallback(callback, this);
      }

      return callback;
   }

   protected void handleBeforeCommit1PC(final List refsToAdd,
         final List refsToRemove, final Transaction tx) throws Exception
   {
      class HandleBeforeCommit1PCRunner extends JDBCTxRunner2
      {
         private List<Message> messagesStored;

         public Object doTransaction() throws Exception
         {
            // For one phase we simply add rows corresponding to the refs and
            // remove rows corresponding to
            // the deliveries in one jdbc tx. We also need to store messages as
            // necessary,
            // depending on whether they've already been stored or still
            // referenced by other channels.

            PreparedStatement psReference = null;
            PreparedStatement psInsertMessage = null;
            PreparedStatement psDeleteReference = null;

            messagesStored = new ArrayList<Message>();

            try
            {
               // First the adds

               boolean first = true;

               for (Iterator i = refsToAdd.iterator(); i.hasNext();)
               {
                  ChannelRefPair pair = (ChannelRefPair) i.next();
                  MessageReference ref = pair.ref;

                  if (psReference == null)
                  {
                     psReference = conn
                           .prepareStatement(getSQLStatement("INSERT_MESSAGE_REF"));
                  }

                  // Now store the reference
                  addReference(pair.channelID, ref, psReference, false);

                  int rows = psReference.executeUpdate();

                  if (trace)
                  {
                     log.trace("Inserted " + rows + " rows");
                  }

                  Message m = ref.getMessage();

                  synchronized (m)
                  {
                     if (!m.isPersisted())
                     {
                        if (psInsertMessage == null)
                        {
                           psInsertMessage = conn
                                 .prepareStatement(getSQLStatement("INSERT_MESSAGE"));
                        }

                        // First time so add message
                        // And in case of clustered queues/topics, the message
                        // could possibly be already persisted on the different
                        // node
                        // so we persist also using the Conditional Update
                        if (trace)
                        {
                           log
                                 .trace("Message does not already exist so inserting it");
                        }
                        storeMessage(m, psInsertMessage, true);
                        rows = psInsertMessage.executeUpdate();
                        if (trace)
                        {
                           log.trace("Inserted " + rows + " rows");
                        }

                        m.setPersisted(true);

                        messagesStored.add(m);

                        //We only cache the first id in a tx - this is enough to determine if the tx succeeded
                        if (first && m instanceof JBossMessage)
                        {
                           cacheID(conn, ((JBossMessage)m).getJMSMessageID());

                           first = false;
                        }
                     }
                  }
               }

               // Now the removes

               for (Iterator i = refsToRemove.iterator(); i.hasNext();)
               {
                  ChannelRefPair pair = (ChannelRefPair) i.next();

                  if (psDeleteReference == null)
                  {
                     psDeleteReference = conn
                           .prepareStatement(getSQLStatement("DELETE_MESSAGE_REF"));
                  }

                  removeReference(pair.channelID, pair.ref, psDeleteReference);

                  int rows = psDeleteReference.executeUpdate();

                  if (trace)
                  {
                     log.trace("Deleted " + rows + " references");
                  }

               }

               return null;
            }
            finally
            {
               closeStatement(psReference);
               closeStatement(psDeleteReference);
               closeStatement(psInsertMessage);
            }
         }

         public void rollback()
         {
            if(messagesStored != null)
            {
               for (Iterator i = messagesStored.iterator(); i.hasNext();)
               {
                  Message msg = (Message) i.next();

                  msg.setPersisted(false);
               }
            }
         }
      }

      new HandleBeforeCommit1PCRunner().executeWithRetry();

      this.deleteMessages(refsToRemove);
   }

   protected void handleBeforeCommit2PC(final List refsToRemove,
         final Transaction tx) throws Exception
   {
      class HandleBeforeCommit2PCRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement ps = null;

            if (trace)
            {
               log.trace(this + " commitPreparedTransaction, tx= " + tx);
            }

            try
            {
               ps = conn
                     .prepareStatement(getSQLStatement("COMMIT_MESSAGE_REF1"));

               ps.setLong(1, tx.getId());

               int rows = ps.executeUpdate();

               if (trace)
               {
                  log.trace(JDBCUtil.statementToString(
                        getSQLStatement("COMMIT_MESSAGE_REF1"), new Long(tx
                              .getId()))
                        + " removed " + rows + " row(s)");
               }

               ps.close();

               ps = conn
                     .prepareStatement(getSQLStatement("COMMIT_MESSAGE_REF2"));
               ps.setLong(1, tx.getId());

               rows = ps.executeUpdate();

               if (trace)
               {
                  log.trace(JDBCUtil.statementToString(
                        getSQLStatement("COMMIT_MESSAGE_REF2"), new Long(tx
                              .getId()))
                        + " updated " + rows + " row(s)");
               }

               removeTXRecord(conn, tx);

               return null;
            }
            finally
            {
               closeStatement(ps);
            }
         }
      }

      new HandleBeforeCommit2PCRunner().executeWithRetry();

      this.deleteMessages(refsToRemove);
   }

   protected void handleBeforePrepare(final List refsToAdd,
         final List refsToRemove, final Transaction tx) throws Exception
   {
      class HandleBeforePrepareRunner extends JDBCTxRunner2
      {
         private List<Message> messagesStored;

         public Object doTransaction() throws Exception
         {
            // We insert a tx record and
            // a row for each ref with +
            // and update the row for each delivery with "-"

            PreparedStatement psReference = null;
            PreparedStatement psInsertMessage = null;
            PreparedStatement psUpdateReference = null;

            messagesStored = new ArrayList<Message>();

            try
            {
               // Insert the tx record even if there are no refs as we need it for recovery
               addTXRecord(conn, tx);

               boolean first = false;

               Iterator iter = refsToAdd.iterator();

               while (iter.hasNext())
               {
                  ChannelRefPair pair = (ChannelRefPair) iter.next();

                  if (psReference == null)
                  {
                     psReference = conn
                           .prepareStatement(getSQLStatement("INSERT_MESSAGE_REF"));
                  }

                  prepareToAddReference(pair.channelID, pair.ref, tx,
                        psReference);

                  int rows = psReference.executeUpdate();

                  if (trace)
                  {
                     log.trace("Inserted " + rows + " rows");
                  }

                  Message m = pair.ref.getMessage();

                  synchronized (m)
                  {
                     if (!m.isPersisted())
                     {
                        if (psInsertMessage == null)
                        {
                           psInsertMessage = conn
                                 .prepareStatement(getSQLStatement("INSERT_MESSAGE"));
                        }

                        storeMessage(m, psInsertMessage, true);
                        rows = psInsertMessage.executeUpdate();

                        if (trace)
                        {
                           log.trace("Inserted " + rows + " rows");
                        }

                        m.setPersisted(true);

                        messagesStored.add(m);

                        //We only cache the first id in a tx - this is enough to determine if the tx succeeded
                        if (first && m instanceof JBossMessage)
                        {
                           cacheID(conn, ((JBossMessage)m).getJMSMessageID());

                           first = false;
                        }
                     }
                  }
               }

               // Now the removes

               iter = refsToRemove.iterator();

               while (iter.hasNext())
               {
                  if (psUpdateReference == null)
                  {
                     psUpdateReference = conn
                           .prepareStatement(getSQLStatement("UPDATE_MESSAGE_REF"));
                  }

                  ChannelRefPair pair = (ChannelRefPair) iter.next();

                  prepareToRemoveReference(pair.channelID, pair.ref, tx,
                        psUpdateReference);

                  int rows = psUpdateReference.executeUpdate();

                  if (trace)
                  {
                     log.trace("updated " + rows + " rows");
                  }
               }

               return null;
            }
            finally
            {
               closeStatement(psReference);
               closeStatement(psInsertMessage);
               closeStatement(psUpdateReference);
            }
         }

         public void rollback()
         {
            if(messagesStored != null)
            {
               for (Iterator i = messagesStored.iterator(); i.hasNext();)
               {
                  Message msg = (Message) i.next();

                  msg.setPersisted(false);
               }
            }
         }
      }

      new HandleBeforePrepareRunner().executeWithRetry();
   }

   protected void handleBeforeRollback(final List refsToAdd,
         final Transaction tx) throws Exception
   {
      class HandleBeforeRollbackRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {
            PreparedStatement ps = null;

            try
            {
               ps = conn
                     .prepareStatement(getSQLStatement("ROLLBACK_MESSAGE_REF1"));

               ps.setLong(1, tx.getId());

               int rows = ps.executeUpdate();

               if (trace)
               {
                  log.trace(JDBCUtil.statementToString(
                        getSQLStatement("ROLLBACK_MESSAGE_REF1"), new Long(tx
                              .getId()))
                        + " removed " + rows + " row(s)");
               }

               ps.close();

               ps = conn
                     .prepareStatement(getSQLStatement("ROLLBACK_MESSAGE_REF2"));
               ps.setLong(1, tx.getId());

               rows = ps.executeUpdate();

               if (trace)
               {
                  log.trace(JDBCUtil.statementToString(
                        getSQLStatement("ROLLBACK_MESSAGE_REF2"), new Long(tx
                              .getId()))
                        + " updated " + rows + " row(s)");
               }

               removeTXRecord(conn, tx);

               return null;
            }
            finally
            {
               closeStatement(ps);
            }
         }
      }

      new HandleBeforeRollbackRunner().executeWithRetry();

      this.deleteMessages(refsToAdd);
   }

   protected void addTXRecord(Connection conn, Transaction tx) throws Exception
   {
      if (trace)
      {
         log.trace("Inserting tx record for " + tx);
      }

      if (!this.nodeIDSet)
      {
         // Sanity
         throw new IllegalStateException("Node id has not been set");
      }

      PreparedStatement ps = null;
      String statement = "UNDEFINED";
      int rows = -1;
      int formatID = -1;
      try
      {
         statement = getSQLStatement("INSERT_TRANSACTION");

         ps = conn.prepareStatement(statement);

         ps.setInt(1, nodeID);

         ps.setLong(2, tx.getId());

         Xid xid = tx.getXid();

         formatID = xid.getFormatId();

         setVarBinaryColumn(3, ps, xid.getBranchQualifier());

         ps.setInt(4, formatID);

         setVarBinaryColumn(5, ps, xid.getGlobalTransactionId());

         rows = ps.executeUpdate();
      }
      finally
      {
         if (trace)
         {
            String s = JDBCUtil.statementToString(statement,
                  new Integer(nodeID), new Long(tx.getId()), "<byte-array>",
                  new Integer(formatID), "<byte-array>");
            log
                  .trace(s
                        + (rows == -1 ? " failed!" : " inserted " + rows
                              + " row(s)"));
         }
         closeStatement(ps);
      }
   }

   protected void removeTXRecord(Connection conn, Transaction tx)
         throws Exception
   {
      if (!this.nodeIDSet)
      {
         // Sanity
         throw new IllegalStateException("Node id has not been set");
      }

      PreparedStatement ps = null;
      try
      {
         ps = conn.prepareStatement(getSQLStatement("DELETE_TRANSACTION"));

         ps.setInt(1, nodeID);

         ps.setLong(2, tx.getId());

         int rows = ps.executeUpdate();

         if (trace)
         {
            log.trace(JDBCUtil.statementToString(
                  getSQLStatement("DELETE_TRANSACTION"), new Integer(nodeID),
                  new Long(tx.getId()))
                  + " removed " + rows + " row(s)");
         }
      }
      finally
      {
         closeStatement(ps);
      }
   }

   protected void addReference(long channelID, MessageReference ref,
         PreparedStatement ps, boolean paged) throws Exception
   {
      if (trace)
      {
         log.trace("adding " + ref + " to channel " + channelID);
      }

      ps.setLong(1, channelID);
      ps.setLong(2, ref.getMessage().getMessageID());
      ps.setNull(3, Types.BIGINT);
      ps.setString(4, "C");
      ps.setLong(5, orderingID.getID());
      if (paged)
      {
         ps.setLong(6, ref.getPagingOrder());
      }
      else
      {
         ps.setNull(6, Types.BIGINT);
      }
      ps.setInt(7, ref.getDeliveryCount());
      ps.setLong(8, ref.getScheduledDeliveryTime());
   }

   protected void removeReference(long channelID, MessageReference ref,
         PreparedStatement ps) throws Exception
   {
      if (trace)
      {
         log.trace("removing " + ref + " from channel " + channelID);
      }

      ps.setLong(1, ref.getMessage().getMessageID());
      ps.setLong(2, channelID);
   }

   protected void prepareToAddReference(long channelID, MessageReference ref,
         Transaction tx, PreparedStatement ps) throws Exception
   {
      if (trace)
      {
         log.trace("adding "
               + ref
               + " to channel "
               + channelID
               + (tx == null ? " non-transactionally" : " on transaction: "
                     + tx));
      }

      ps.setLong(1, channelID);
      ps.setLong(2, ref.getMessage().getMessageID());
      ps.setLong(3, tx.getId());
      ps.setString(4, "+");
      ps.setLong(5, orderingID.getID());
      ps.setNull(6, Types.BIGINT);
      ps.setInt(7, ref.getDeliveryCount());
      ps.setLong(8, ref.getScheduledDeliveryTime());
   }

   protected void prepareToRemoveReference(long channelID,
         MessageReference ref, Transaction tx, PreparedStatement ps)
         throws Exception
   {
      if (trace)
      {
         log.trace("removing "
               + ref
               + " from channel "
               + channelID
               + (tx == null ? " non-transactionally" : " on transaction: "
                     + tx));
      }

      ps.setLong(1, tx.getId());
      ps.setLong(2, ref.getMessage().getMessageID());
      ps.setLong(3, channelID);
   }

   protected byte[] mapToBytes(Map map) throws Exception
   {
      if (map == null || map.isEmpty()) { return null; }

      final int BUFFER_SIZE = 1024;

      ByteArrayOutputStream bos = new ByteArrayOutputStream(BUFFER_SIZE);

      DataOutputStream oos = new DataOutputStream(bos);

      StreamUtils.writeMap(oos, map, true);

      oos.close();

      return bos.toByteArray();
   }

   protected HashMap bytesToMap(byte[] bytes) throws Exception
   {
      if (bytes == null) { return new HashMap(); }

      ByteArrayInputStream bis = new ByteArrayInputStream(bytes);

      DataInputStream dais = new DataInputStream(bis);

      HashMap map = StreamUtils.readMap(dais, true);

      dais.close();

      return map;
   }

   /**
    * Stores the message in the MESSAGE table.
    */
   protected void storeMessage(Message m, PreparedStatement ps, boolean bindBlobs) throws Exception
   {
      // physically insert the row in the database
      // first set the fields from org.jboss.messaging.core.Routable
      ps.setLong(1, m.getMessageID());
      ps.setString(2, m.isReliable() ? "Y" : "N");
      ps.setLong(3, m.getExpiration());
      ps.setLong(4, m.getTimestamp());
      ps.setByte(5, m.getPriority());
      ps.setByte(6, m.getType());

      if (bindBlobs)
      {
         bindBlobs(m, ps, 7, 8);
      }
   }

   protected int storeMessage(Message message, PreparedStatement psInsertMessage, PreparedStatement psUpdateMessage)
                              throws Exception
   {
      int rows;
      if (!supportsBlobSelect)
      {
         // Need to store in two phases
         storeMessage(message, psInsertMessage, false);
         psInsertMessage.setLong(7, message.getMessageID());
         rows = psInsertMessage.executeUpdate();

         if (rows == 1)
         {
            bindBlobs(message, psUpdateMessage, 1, 2);
            psUpdateMessage.setLong(3, message.getMessageID());
            rows = psUpdateMessage.executeUpdate();
            if (rows != 1) { throw new IllegalStateException(
                  "Couldn't update messageId=" + message.getMessageID()
                        + " on paging"); }
         }
      }
      else
      {
         // Can store in one go
         storeMessage(message, psInsertMessage, true);
         psInsertMessage.setLong(9, message.getMessageID());
         rows = psInsertMessage.executeUpdate();
      }
      return rows;
   }
  
   private void bindBlobs(Message m, PreparedStatement ps, int headerPosition,
         int payloadPosition) throws Exception
   {
      // headers
      byte[] bytes = mapToBytes(((MessageSupport) m).getHeaders());
      if (bytes != null)
      {
         setBytes(ps, headerPosition, bytes);
      }
      else
      {
         setNullStream(ps, headerPosition);
      }

      byte[] payload = m.getPayloadAsByteArray();
      if (payload != null)
      {
         setBytes(ps, payloadPosition, payload);
      }
      else
      {
         setNullStream(ps, payloadPosition);
      }
   }

   private void setNullStream(PreparedStatement ps, int position) throws SQLException
   {
      if (supportsSetNullOnBlobs)
      {
         ps.setNull(position, Types.LONGVARBINARY);
      }
      else
      {
         ps.setBinaryStream(position, null, 0);
      }
   }

   protected void setVarBinaryColumn(int column, PreparedStatement ps,
         byte[] bytes) throws Exception
   {
      if (usingTrailingByte)
      {
         // Sybase has the stupid characteristic of truncating all trailing in
         // zeros
         // in varbinary columns
         // So we add an extra byte on the end when we store the varbinary data
         // otherwise we might lose data
         // http://jira.jboss.org/jira/browse/JBMESSAGING-825

         byte[] res = new byte[bytes.length + 1];

         System.arraycopy(bytes, 0, res, 0, bytes.length);

         res[bytes.length] = 127;

         bytes = res;
      }

      ps.setBytes(column, bytes);

      if (trace)
      {
         log.trace("Setting varbinary column of length: " + bytes.length);
      }
   }

   protected byte[] getVarBinaryColumn(ResultSet rs, int columnIndex)
         throws Exception
   {
      byte[] bytes = rs.getBytes(columnIndex);

      if (usingTrailingByte)
      {
         // Get rid of the trailing byte

         // http://jira.jboss.org/jira/browse/JBMESSAGING-825

         byte[] newBytes = new byte[bytes.length - 1];

         System.arraycopy(bytes, 0, newBytes, 0, bytes.length - 1);

         bytes = newBytes;
      }

      return bytes;
   }

   // Used for storing message headers and bodies
   protected void setBytes(PreparedStatement ps, int columnIndex, byte[] bytes)
         throws Exception
   {
      if (usingBinaryStream)
      {
         // Set the bytes using a binary stream - likely to be better for large
         // byte[]

         InputStream is = null;

         try
         {
            is = new ByteArrayInputStream(bytes);

            ps.setBinaryStream(columnIndex, is, bytes.length);
         }
         finally
         {
            if (is != null)
            {
               is.close();
            }
         }
      }
      else
      {
         // Set the bytes using setBytes() - likely to be better for smaller
         // byte[]

         setVarBinaryColumn(columnIndex, ps, bytes);
      }
   }

   protected byte[] getBytes(ResultSet rs, int columnIndex) throws Exception
   {
      if (usingBinaryStream)
      {
         // Get the bytes using a binary stream - likely to be better for large
         // byte[]

         InputStream is = null;
         ByteArrayOutputStream os = null;

         final int BUFFER_SIZE = 4096;

         try
         {
            InputStream i = rs.getBinaryStream(columnIndex);

            if (i == null) { return null; }

            is = new BufferedInputStream(i, BUFFER_SIZE);

            os = new ByteArrayOutputStream(BUFFER_SIZE);

            int b;
            while ((b = is.read()) != -1)
            {
               os.write(b);
            }

            return os.toByteArray();
         }
         finally
         {
            if (is != null)
            {
               is.close();
            }
            if (os != null)
            {
               os.close();
            }
         }
      }
      else
      {
         // Get the bytes using getBytes() - better for smaller byte[]
         return getVarBinaryColumn(rs, columnIndex);
      }
   }

   protected void logBatchUpdate(String name, int[] rows, String action)
   {
      int count = 0;
      for (int i = 0; i < rows.length; i++)
      {
         count += rows[i];
      }
      log.trace("Batch update " + name + ", " + action + " total of " + count
            + " rows");
   }

   // PersistentServiceSupport overrides ----------------------------

   protected Map getDefaultDDLStatements()
   {
      Map<String, String> map = new LinkedHashMap<String, String>();
      map.put("CREATE_DUAL", "CREATE TABLE JBM_DUAL (DUMMY INTEGER, PRIMARY KEY(DUMMY))");
      // Message reference
      map.put("CREATE_MESSAGE_REFERENCE",
              "CREATE TABLE JBM_MSG_REF (MESSAGE_ID BIGINT, "
            + "CHANNEL_ID BIGINT, TRANSACTION_ID BIGINT, STATE CHAR(1), ORD BIGINT, PAGE_ORD BIGINT, "
            + "DELIVERY_COUNT INTEGER, SCHED_DELIVERY BIGINT, PRIMARY KEY(MESSAGE_ID, CHANNEL_ID))");
      map.put("CREATE_IDX_MESSAGE_REF_TX",
              "CREATE INDEX JBM_MSG_REF_TX ON JBM_MSG_REF (TRANSACTION_ID, STATE)");
      // Message
      map.put("CREATE_MESSAGE",
              "CREATE TABLE JBM_MSG (MESSAGE_ID BIGINT, RELIABLE CHAR(1), "
            + "EXPIRATION BIGINT, TIMESTAMP BIGINT, PRIORITY TINYINT, TYPE TINYINT, HEADERS LONGVARBINARY, "
            + "PAYLOAD LONGVARBINARY, "
            + "PRIMARY KEY (MESSAGE_ID))");
      // Transaction
      map.put("CREATE_TRANSACTION",
              "CREATE TABLE JBM_TX ("
            + "NODE_ID INTEGER, TRANSACTION_ID BIGINT, BRANCH_QUAL VARBINARY(254), "
           +  "FORMAT_ID INTEGER, GLOBAL_TXID VARBINARY(254), PRIMARY KEY (TRANSACTION_ID))");
      // Counter
      map.put("CREATE_COUNTER",
              "CREATE TABLE JBM_COUNTER (NAME VARCHAR(255), NEXT_ID BIGINT, PRIMARY KEY(NAME))");
      // Id cache
      map.put("CREATE_ID_CACHE", "CREATE TABLE JBM_ID_CACHE (NODE_ID INTEGER, CNTR INTEGER, JBM_ID VARCHAR(255), PRIMARY KEY(NODE_ID, CNTR))");
      return map;
   }

   protected Map getDefaultDMLStatements()
   {
      Map<String, String> map = new LinkedHashMap<String, String>();
      map.put("INSERT_DUAL", "INSERT INTO JBM_DUAL VALUES (1)");
      map.put("CHECK_DUAL", "SELECT 1 FROM JBM_DUAL");
      // Message reference
      map.put("INSERT_MESSAGE_REF",
              "INSERT INTO JBM_MSG_REF (CHANNEL_ID, MESSAGE_ID, TRANSACTION_ID, STATE, ORD, PAGE_ORD, DELIVERY_COUNT, SCHED_DELIVERY) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
      map.put("DELETE_MESSAGE_REF",
              "DELETE FROM JBM_MSG_REF WHERE MESSAGE_ID=? AND CHANNEL_ID=? AND STATE='C'");
      map.put("UPDATE_MESSAGE_REF",
              "UPDATE JBM_MSG_REF SET TRANSACTION_ID=?, STATE='-' "
            + "WHERE MESSAGE_ID=? AND CHANNEL_ID=? AND STATE='C'");
      map.put("UPDATE_PAGE_ORDER",
              "UPDATE JBM_MSG_REF SET PAGE_ORD = ? WHERE MESSAGE_ID=? AND CHANNEL_ID=?");
      map.put("COMMIT_MESSAGE_REF1",
              "UPDATE JBM_MSG_REF SET STATE='C', TRANSACTION_ID = NULL WHERE TRANSACTION_ID=? AND STATE='+'");
      map.put("COMMIT_MESSAGE_REF2",
              "DELETE FROM JBM_MSG_REF WHERE TRANSACTION_ID=? AND STATE='-'");
      map.put("ROLLBACK_MESSAGE_REF1",
              "DELETE FROM JBM_MSG_REF WHERE TRANSACTION_ID=? AND STATE='+'");
      map.put("ROLLBACK_MESSAGE_REF2",
             "UPDATE JBM_MSG_REF SET STATE='C', TRANSACTION_ID = NULL WHERE TRANSACTION_ID=? AND STATE='-'");
      map.put("LOAD_PAGED_REFS",
              "SELECT MESSAGE_ID, DELIVERY_COUNT, PAGE_ORD, SCHED_DELIVERY FROM JBM_MSG_REF "
            + "WHERE CHANNEL_ID = ? AND PAGE_ORD BETWEEN ? AND ? ORDER BY PAGE_ORD");
      map.put("LOAD_UNPAGED_REFS",
              "SELECT MESSAGE_ID, DELIVERY_COUNT, SCHED_DELIVERY, PAGE_ORD FROM JBM_MSG_REF WHERE STATE = 'C' "
            + "AND CHANNEL_ID = ? ORDER BY ORD");
      map.put("LOAD_REFS",
              "SELECT MESSAGE_ID, DELIVERY_COUNT, SCHED_DELIVERY FROM JBM_MSG_REF WHERE STATE = 'C' "
            + "AND CHANNEL_ID = ? ORDER BY ORD");
      map.put("UPDATE_REFS_NOT_PAGED",
              "UPDATE JBM_MSG_REF SET PAGE_ORD = NULL WHERE PAGE_ORD BETWEEN ? AND ? AND CHANNEL_ID=?");
      map.put("SELECT_MIN_MAX_PAGE_ORD",
              "SELECT MIN(PAGE_ORD), MAX(PAGE_ORD) FROM JBM_MSG_REF WHERE CHANNEL_ID = ?");
      map.put("UPDATE_DELIVERY_COUNT",
              "UPDATE JBM_MSG_REF SET DELIVERY_COUNT = ? WHERE MESSAGE_ID = ? AND CHANNEL_ID = ?");
      map.put("UPDATE_CHANNEL_ID",
              "UPDATE JBM_MSG_REF SET CHANNEL_ID = ? WHERE CHANNEL_ID = ?");
      map.put("MOVE_REFERENCE",
              "UPDATE JBM_MSG_REF SET CHANNEL_ID = ? WHERE MESSAGE_ID = ? AND CHANNEL_ID = ?");
      map.put("LOAD_ID_CACHE", "SELECT CNTR, JBM_ID FROM JBM_ID_CACHE WHERE NODE_ID = ?");
      map.put("UPDATE_ID_IN_CACHE", "UPDATE JBM_ID_CACHE SET JBM_ID = ? WHERE NODE_ID = ? AND CNTR = ?");
      map.put("INSERT_ID_IN_CACHE", "INSERT INTO JBM_ID_CACHE (NODE_ID, CNTR, JBM_ID) VALUES (?, ?, ?)");


      // Message
      map.put("LOAD_MESSAGES",
              "SELECT MESSAGE_ID, RELIABLE, EXPIRATION, TIMESTAMP, "
            + "PRIORITY, HEADERS, PAYLOAD, TYPE " + "FROM JBM_MSG");
      map.put("INSERT_MESSAGE",
              "INSERT INTO JBM_MSG (MESSAGE_ID, RELIABLE, EXPIRATION, "
            + "TIMESTAMP, PRIORITY, TYPE, HEADERS, PAYLOAD) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
      map.put("INSERT_MESSAGE_CONDITIONAL",
              "INSERT INTO JBM_MSG (MESSAGE_ID, RELIABLE, EXPIRATION, "
         +    "TIMESTAMP, PRIORITY, TYPE) "
            + "SELECT ?, ?, ?, ?, ?, ? "
            + "FROM JBM_DUAL WHERE NOT EXISTS (SELECT MESSAGE_ID FROM JBM_MSG WHERE MESSAGE_ID = ?)");
      map.put("INSERT_MESSAGE_CONDITIONAL_FULL",
              "INSERT INTO JBM_MSG (MESSAGE_ID, RELIABLE, EXPIRATION, TIMESTAMP, PRIORITY, TYPE, HEADERS, PAYLOAD) SELECT ?, ?, ?, ?, ?, ?, ?, ? FROM JBM_DUAL WHERE NOT EXISTS (SELECT MESSAGE_ID FROM JBM_MSG WHERE MESSAGE_ID = ?)");
      map.put("UPDATE_MESSAGE_4CONDITIONAL",
              "UPDATE JBM_MSG SET HEADERS=?, PAYLOAD=? WHERE MESSAGE_ID=?");
      map.put("MESSAGE_ID_COLUMN", "MESSAGE_ID");
      map.put("DELETE_MESSAGE",
              "DELETE FROM JBM_MSG WHERE MESSAGE_ID = ? AND NOT EXISTS (SELECT JBM_MSG_REF.MESSAGE_ID FROM JBM_MSG_REF WHERE JBM_MSG_REF.MESSAGE_ID = ?)");
      map.put("DELETE_CHANNEL_MESSAGE_REF", "DELETE FROM JBM_MSG_REF WHERE CHANNEL_ID=?");
      map.put("DELETE_CHANNEL_MESSAGE", "DELETE FROM JBM_MSG WHERE MESSAGE_ID = ?");

      
      // Transaction
      map.put("INSERT_TRANSACTION",
              "INSERT INTO JBM_TX (NODE_ID, TRANSACTION_ID, BRANCH_QUAL, FORMAT_ID, GLOBAL_TXID) "
          +   "VALUES(?, ?, ?, ?, ?)");
      map.put("DELETE_TRANSACTION",
              "DELETE FROM JBM_TX WHERE NODE_ID = ? AND TRANSACTION_ID = ?");
      map.put("SELECT_PREPARED_TRANSACTIONS",
              "SELECT TRANSACTION_ID, BRANCH_QUAL, FORMAT_ID, GLOBAL_TXID FROM JBM_TX WHERE NODE_ID = ?");
      map.put("SELECT_MESSAGE_ID_FOR_REF",
              "SELECT MESSAGE_ID, CHANNEL_ID FROM JBM_MSG_REF WHERE TRANSACTION_ID = ? AND STATE = '+' ORDER BY ORD");
      map.put("SELECT_MESSAGE_ID_FOR_ACK",
              "SELECT MESSAGE_ID, CHANNEL_ID FROM JBM_MSG_REF WHERE TRANSACTION_ID = ? AND STATE = '-' ORDER BY ORD");
      map.put("UPDATE_TX", "UPDATE JBM_TX SET NODE_ID=? WHERE NODE_ID=?");

      // Counter
      map.put("UPDATE_COUNTER",
             "UPDATE JBM_COUNTER SET NEXT_ID = ? WHERE NAME=?");
      map.put("SELECT_COUNTER", "SELECT NEXT_ID FROM JBM_COUNTER WHERE NAME=?");
      map.put("INSERT_COUNTER",
              "INSERT INTO JBM_COUNTER (NAME, NEXT_ID) VALUES (?, ?)");
      // Other
      map.put("SELECT_ALL_CHANNELS",
              "SELECT DISTINCT(CHANNEL_ID) FROM JBM_MSG_REF");

      return map;
   }

   // Private -------------------------------------------------------

   private void deleteMessages(final List references) throws Exception
   {
      class DeleteMessagesRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {

            PreparedStatement psMessage = null;

            try
            {
               psMessage = conn
                     .prepareStatement(getSQLStatement("DELETE_MESSAGE"));

               Iterator iter = references.iterator();

               while (iter.hasNext())
               {
                  Object obj = iter.next();

                  MessageReference ref;
                  if (obj instanceof MessageReference)
                  {
                     ref = (MessageReference) obj;
                  }
                  else
                  {
                     ref = ((ChannelRefPair) obj).ref;
                  }

                  psMessage.setLong(1, ref.getMessage().getMessageID());
                  psMessage.setLong(2, ref.getMessage().getMessageID());

                  int rows = psMessage.executeUpdate();

                  if (trace)
                  {
                     log.trace("Deleted " + rows + " messages");
                  }

               }

               return null;
            }
            finally
            {
               closeStatement(psMessage);
            }
         }
      }

      // Order to avoid deadlock
      orderReferences(references);

      new DeleteMessagesRunner().executeWithRetry();
   }

   private void deleteMessage(final long messageID) throws Exception
   {
      class DeleteMessageRunner extends JDBCTxRunner2
      {
         public Object doTransaction() throws Exception
         {

            PreparedStatement psMessage = null;

            try
            {
               psMessage = conn
                     .prepareStatement(getSQLStatement("DELETE_MESSAGE"));

               psMessage.setLong(1, messageID);
               psMessage.setLong(2, messageID);

               int rows = psMessage.executeUpdate();

               if (trace)
               {
                  log.trace("Deleted " + rows + " messages");
               }

               return null;
            }
            finally
            {
               closeStatement(psMessage);
            }
         }
      }

      new DeleteMessageRunner().executeWithRetry();
   }

   private List getMessageChannelPair(String sqlQuery, long transactionId)
         throws Exception
   {
      if (trace) log.trace("loading message and channel ids for tx ["
            + transactionId + "]");

      if (!this.nodeIDSet)
      {
         // Sanity
         throw new IllegalStateException("Node id has not been set");
      }

      Connection conn = null;
      PreparedStatement ps = null;
      ResultSet rs = null;
      TransactionWrapper wrap = new TransactionWrapper();

      try
      {
         conn = ds.getConnection();

         ps = conn.prepareStatement(sqlQuery);

         ps.setLong(1, transactionId);

         rs = ps.executeQuery();

         // Don't use a Map. A message could be in multiple channels in a tx, so
         // if you use a map
         // when you put the same message again it's going to overwrite the
         // previous put!!

         class Holder
         {
            long messageId;
            long channelId;

            Holder(long messageId, long channelId)
            {
               this.messageId = messageId;
               this.channelId = channelId;
            }
         }

         List<Holder> holders = new ArrayList<Holder>();

         // Unique set of messages
         Set<Long> msgIds = new HashSet<Long>();

         // TODO it would probably have been simpler just to have done all this
         // in a SQL JOIN rather
         // than do the join in memory.....

         while (rs.next())
         {
            long messageId = rs.getLong(1);
            long channelId = rs.getLong(2);

            Holder holder = new Holder(messageId, channelId);

            holders.add(holder);

            msgIds.add(messageId);

            if (trace) log.trace("Loaded MsgID: " + messageId
                  + " and ChannelID: " + channelId);
         }

         Map messageMap = new HashMap();

         List messages = getMessages(new ArrayList(msgIds));

         for (Iterator iter = messages.iterator(); iter.hasNext();)
         {
            Message msg = (Message) iter.next();

            messageMap.put(new Long(msg.getMessageID()), msg);
         }

         List returnList = new ArrayList();

         for (Iterator iter = holders.iterator(); iter.hasNext();)
         {
            Holder holder = (Holder) iter.next();

            Message msg = (Message) messageMap.get(new Long(holder.messageId));

            if (msg == null) { throw new IllegalStateException(
                  "Cannot find message " + holder.messageId); }

            MessageChannelPair pair = new MessageChannelPair(msg,
                  holder.channelId);

            returnList.add(pair);
         }

         return returnList;
      }
      catch (Exception e)
      {
         wrap.exceptionOccurred();
         throw e;
      }
      finally
      {
         closeResultSet(rs);
         closeStatement(ps);
         closeConnection(conn);
         wrap.end();
      }
   }

   // Inner classes -------------------------------------------------


   // We generate the ordering for the message reference by taking the lowest
   // 48 bits of the current time and
   // concatenating with a 15 bit rotating counter to form a string of 63
   // bits which we then place
   // in the right most bits of a long, giving a positive signed 63 bit
   // integer.

   // Having a time element in the ordering means we don't have to maintain a
   // counter in the database
   // It also helps with failover since if two queues merge after failover
   // then, the ordering will mean
   // their orderings interleave nicely and they still get consumed in pretty
   // much time order

   // We only have to guarantee ordering per session, so having slight
   // differences of time on different nodes is
   // not a problem

   // The time element is good for about 8919 years - if you're still running
   // JBoss Messaging then, I suggest you need an
   // upgrade!

   private static class ChannelRefPair
   {
      private long channelID;
      private MessageReference ref;

      private ChannelRefPair(long channelID, MessageReference ref)
      {
         this.channelID = channelID;
         this.ref = ref;
      }
   }

   private class TransactionCallback implements TxCallback
   {
      private Transaction tx;

      private List refsToAdd;

      private List refsToRemove;

      private TransactionCallback(Transaction tx)
      {
         this.tx = tx;

         refsToAdd = new ArrayList();

         refsToRemove = new ArrayList();
      }

      private void addReferenceToAdd(long channelId, MessageReference ref)
      {
         refsToAdd.add(new ChannelRefPair(channelId, ref));
      }

      private void addReferenceToRemove(long channelId, MessageReference ref)
      {
         refsToRemove.add(new ChannelRefPair(channelId, ref));
      }

      public void afterCommit(boolean onePhase)
      {
         // NOOP
      }

      public void afterPrepare()
      {
         // NOOP
      }

      public void afterRollback(boolean onePhase)
      {
         // NOOP
      }

      public void beforeCommit(boolean onePhase) throws Exception
      {
         if (onePhase)
         {
            handleBeforeCommit1PC(refsToAdd, refsToRemove, tx);
         }
         else
         {
            handleBeforeCommit2PC(refsToRemove, tx);
         }
      }

      public void beforePrepare() throws Exception
      {
         handleBeforePrepare(refsToAdd, refsToRemove, tx);
      }

      public void beforeRollback(boolean onePhase) throws Exception
      {
         if (onePhase)
         {
            // NOOP - nothing in db
         }
         else
         {
            handleBeforeRollback(refsToAdd, tx);
         }
      }
   }

   private void orderReferences(List references)
   {
      Collections.sort(references, MessageOrderComparator.instance);
   }

   private static class MessageOrderComparator implements Comparator
   {
      static MessageOrderComparator instance = new MessageOrderComparator();

      public int compare(Object o1, Object o2)
      {
         MessageReference ref1;
         MessageReference ref2;

         if (o1 instanceof MessageReference)
         {
            ref1 = (MessageReference) o1;
            ref2 = (MessageReference) o2;
         }
         else
         {
            ref1 = ((ChannelRefPair) o1).ref;
            ref2 = ((ChannelRefPair) o2).ref;
         }

         long id1 = ref1.getMessage().getMessageID();
         long id2 = ref2.getMessage().getMessageID();

         return (id1 < id2 ? -1 : (id1 == id2 ? 0 : 1));
      }
   }

}
