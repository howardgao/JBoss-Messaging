/*
 * JBoss, Home of Professional Open Source Copyright 2005, JBoss Inc., and
 * individual contributors as indicated by the @authors tag. See the
 * copyright.txt in the distribution for a full listing of individual
 * contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.jboss.messaging.core.impl;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.messaging.core.contract.MessageReference;
import org.jboss.messaging.core.contract.PersistenceManager;
import org.jboss.messaging.core.impl.tx.Transaction;

/*
 * 
 * A NullPersistenceManager
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 */
public class NullPersistenceManager implements PersistenceManager
{
   private static final int MAX_PEER_ID = 1023;

   private ConcurrentMap<String, IDCounter> counters = new ConcurrentHashMap<String, IDCounter>();

   private int peerID; // 0 - 1023

   private long timeMark;

   public void addReference(long channelID, MessageReference ref, Transaction tx) throws Exception
   {
      // NOOP
   }

   public List getMessageChannelPairAcksForTx(long transactionId) throws Exception
   {
      return Collections.EMPTY_LIST;
   }

   public List getMessageChannelPairRefsForTx(long transactionId) throws Exception
   {
      return Collections.EMPTY_LIST;
   }

   public List getMessages(List messageIds) throws Exception
   {
      return Collections.EMPTY_LIST;
   }

   public List getPagedReferenceInfos(long channelID, long orderStart, int number) throws Exception
   {
      return Collections.EMPTY_LIST;
   }

   public boolean idExists(String messageID) throws Exception
   {
      return false;
   }

   public InitialLoadInfo loadFromStart(long channelID, int fullSize) throws Exception
   {
      return new InitialLoadInfo(null, null, Collections.EMPTY_LIST);
   }

   public InitialLoadInfo mergeAndLoad(long fromChannelID,
                                       long toChannelID,
                                       int numberToLoad,
                                       long firstPagingOrder,
                                       long nextPagingOrder) throws Exception
   {
      return new InitialLoadInfo(null, null, Collections.EMPTY_LIST);
   }

   public void mergeTransactions(int fromNodeID, int toNodeID) throws Exception
   {
      // NOOP
   }

   public void moveReference(long sourceChannelID, long destChannelID, MessageReference ref) throws Exception
   {
      // NOOP
   }

   public void pageReferences(long channelID, List references, boolean paged) throws Exception
   {
      // NOOP
   }

   public void removeDepagedReferences(long channelID, List refs) throws Exception
   {
      // NOOP
   }

   public void removeReference(long channelID, MessageReference ref, Transaction tx) throws Exception
   {
      // NOOP
   }

   public void addTransaction(Transaction tx)
   {
      //To change body of implemented methods use File | Settings | File Templates.
   }

   public long reserveIDBlock(String counterName, int size) throws Exception
   {
      checkServerID();
      IDCounter counter = counters.get(counterName);

      if (counter == null)
      {
         synchronized (counters)
         {
            counter = counters.get(counterName);
            if (counter == null)
            {
               counter = new IDCounter(this);
               counters.put(counterName, counter);
            }
         }
      }
      long idStart = counter.reserveAndGetNextId(size);

      return idStart;
   }

   private void checkServerID() throws Exception
   {
      if (peerID > MAX_PEER_ID)
      {
         throw new Exception("ServerPeerID " + peerID + " exceeding " + MAX_PEER_ID);
      }
      if (peerID < 0)
      {
         throw new Exception("ServerPeerID cannot have negative values");
      }
   }

   public List retrievePreparedTransactions() throws Exception
   {
      return Collections.EMPTY_LIST;
   }

   public void updateDeliveryCount(long channelID, MessageReference ref) throws Exception
   {
      // NOOP
   }

   public void updatePageOrder(long channelID, List references) throws Exception
   {
      // /NOOP
   }

   public void updateReferencesNotPagedInRange(long channelID, long orderStart, long orderEnd, long num) throws Exception
   {
      // NOOP
   }

   public void start() throws Exception
   {
      // NOOP
   }

   public void stop() throws Exception
   {
      // NOOP
   }

   public void initCounter(int serverPeerID, long serverStartTime)
   {
      setPeerID(serverPeerID);
      setTimeMark(serverStartTime);
   }

   public void setPeerID(int peerID)
   {
      this.peerID = peerID;
   }

   public int getPeerID()
   {
      return peerID;
   }

   public void setTimeMark(long timeMark)
   {
      this.timeMark = timeMark;
   }

   public long getTimeMark()
   {
      return timeMark;
   }

   public void dropChannelMessages(long channelID) throws Exception
   {
   }

   public void mergeChannelMessage(long fromID, long toID) throws Exception
   {
   }

}

class IDCounter
{

   private NullPersistenceManager manager;

   private short counter;

   private long tmMark;

   private long peerIDBit;

   public IDCounter(NullPersistenceManager pManager)
   {
      manager = pManager;
      counter = 0;
      tmMark = manager.getTimeMark() & MASK_TIME;
      peerIDBit = (((long)manager.getPeerID()) & MASK_SERVER_PEER_ID) << 48;
      recalculate();// avoid quick restart conflict
   }

   /**
    * for each named counter, we generate it using the following algorithm:
    * <16-bit ServerPeerID> + <32-bit time bit> + <16-bit counter>
    * the 16-bit counter starts from zero and increases by 1. If the counter
    * wraps to zero, we re-calculate the time using current time. Check will be
    * performed when the calculated current time value is the same as the old
    * value. If so, sleep for a while and get the current time value again.
    * That will make sure the generated ID will always be unique even
    * if server peer gets restarted from a previous shutting down.
    * 
    * Note: the block size is limited by the counter (which is a short).
    * if the block size is greater than Short.MAX_VALUE - counter, we
    * will discard the counter and do a recalculate op because we cannot 
    * return a consecutive block of long values.
    * 
    * @param size : size of the block to be reserved. 
    * @return
    */
   private static final long MASK_SERVER_PEER_ID = 0x000000000000FFFFL;

   private static final long MASK_TIME = 0x0000000FFFFFFFF0L;

   public synchronized long reserveAndGetNextId(int size) throws Exception
   {
      if (size > Short.MAX_VALUE)
      {
         throw new Exception("The block size exceeds " + Short.MAX_VALUE);
      }
      if (size > (Short.MAX_VALUE - counter))
      {
         recalculate();
      }
      long nextID = assembleID();
      counter += size;

      return nextID;
   }

   private long assembleID()
   {
      long id = peerIDBit;
      id += tmMark << 12;
      id += counter;
      return id;
   }

   private void recalculate()
   {
      counter = 0;
      long newTm = System.currentTimeMillis() & MASK_TIME;

      while (newTm == tmMark)
      {
         try
         {
            Thread.sleep(20);
         }
         catch (InterruptedException e)
         {
         }
         newTm = System.currentTimeMillis() & MASK_TIME;
      }
      tmMark = newTm;
   }
}
