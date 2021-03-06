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
package org.jboss.test.messaging.core.paging;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;

import org.jboss.jms.tx.MessagingXid;
import org.jboss.messaging.core.contract.Delivery;
import org.jboss.messaging.core.contract.DeliveryObserver;
import org.jboss.messaging.core.contract.MessageReference;
import org.jboss.messaging.core.contract.PersistenceManager;
import org.jboss.messaging.core.contract.Queue;
import org.jboss.messaging.core.contract.Receiver;
import org.jboss.messaging.core.impl.IDManager;
import org.jboss.messaging.core.impl.JDBCPersistenceManager;
import org.jboss.messaging.core.impl.SimpleDelivery;
import org.jboss.messaging.core.impl.message.SimpleMessageStore;
import org.jboss.messaging.core.impl.tx.Transaction;
import org.jboss.messaging.core.impl.tx.TransactionRepository;
import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.container.ServiceContainer;
import org.jboss.tm.TransactionManagerService;
import org.jboss.util.id.GUID;

/**
 *
 * A PagingStateTestBase.
 *
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 5086 $</tt>
 *
 * $Id: PagingStateTestBase.java 5086 2008-10-08 14:42:35Z clebert.suconic@jboss.com $
 */
public class PagingStateTestBase extends MessagingTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   protected ServiceContainer sc;
   protected PersistenceManager pm;
   protected SimpleMessageStore ms;
   protected TransactionRepository tr;

   // Constructors --------------------------------------------------

   public PagingStateTestBase(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------


   public void setUp() throws Exception
   {
      super.setUp();

      sc = new ServiceContainer("all,-remoting,-security");
      sc.start();

      pm =
         new JDBCPersistenceManager(sc.getDataSource(), sc.getTransactionManager(),
                  sc.getPersistenceManagerSQLProperties(),
                  true, true, true, false, 100, !sc.getDatabaseName().equals("oracle") && !sc.getDatabaseName().equals("db2"), !sc.getDatabaseName().equals("db2"));
      ((JDBCPersistenceManager)pm).injectNodeID(1);
      pm.start();

      ms = new SimpleMessageStore();
      ms.start();

      tr = new TransactionRepository(pm, ms, 0);
      tr.start();

      ms.clear();
   }


   public void tearDown() throws Exception
   {
   	;
   	if (checkNoMessageData())
   	{
   		fail("Message data still exists");
   	}
      pm.stop();
      tr.stop();
      ms.stop();
      sc.stop();
      super.tearDown();
   }

   protected Transaction createXATx() throws Exception
   {
      MessagingXid xid =
         new MessagingXid(new GUID().toString().getBytes(), 345, new GUID().toString().getBytes());

      return tr.createTransaction(xid);
   }

   protected void assertSameIds(List ids, MessageReference[] refs, int start, int end)
   {
      assertNotNull(ids);
      assertEquals(ids.size(), end - start + 1);
      Iterator iter = ids.iterator();
      int i = start;
      while (iter.hasNext())
      {
         Long id = (Long)iter.next();
         assertEquals(refs[i].getMessage().getMessageID(), id.longValue());
         i++;
      }
   }

   class ConsumingReceiver implements Receiver
   {
      int numToConsume;

      int count;

      MessageReference[] refs;

      int consumeCount;

      boolean xa;

      boolean tx;

      SimpleDelivery[] dels;

      ConsumingReceiver(int numToConsume, MessageReference[] refs,
                        int consumeCount, boolean tx, boolean xa) throws Exception
      {
         this.numToConsume = numToConsume;

         this.refs = refs;

         this.consumeCount = consumeCount;

         this.xa = xa;

         this.tx = tx;

         this.dels = new SimpleDelivery[numToConsume];
      }

      public synchronized Delivery handle(DeliveryObserver observer,
                                          MessageReference ref, Transaction tx)
      {
         if (count >= numToConsume)
         {
            return null;
         }

         assertEquals(refs[consumeCount + count].getMessage().getMessageID(), ref.getMessage().getMessageID());

         SimpleDelivery del = new SimpleDelivery(observer, ref);

         dels[count] = del;

         count++;

         if (count == numToConsume)
         {
            notify();
         }

         return del;
      }

      void acknowledge() throws Throwable
      {
         //Wait for them all to arrive first

         synchronized (this)
         {
            while (count < numToConsume)
            {
               wait(10000);

               if (count < numToConsume)
               {
                  PagingStateTestBase.fail();
                  return;
               }
            }
         }

         Transaction theTx = null;

         if (tx)
         {
            if (xa)
            {
               theTx = createXATx();
            }
            else
            {
               theTx = tr.createTransaction();
            }
         }

         for (int i = 0; i < numToConsume; i++)
         {
            dels[i].acknowledge(theTx);
         }

         if (tx)
         {
            if (xa)
            {
               theTx.prepare();
               theTx.commit();
            }
            else
            {
               theTx.commit();
            }
         }
      }
   }

   class CancellingReceiver implements Receiver
   {
      int numToCancel;

      int count;

      SimpleDelivery[] toCancel;

      CancellingReceiver(int numToConsume)
         throws Exception
      {
         this.numToCancel = numToConsume;

         this.toCancel = new SimpleDelivery[numToCancel];

      }

      public synchronized Delivery handle(DeliveryObserver observer,
                                          MessageReference ref, Transaction tx)
      {
         if (count == numToCancel)
         {
            return null;
         }

         SimpleDelivery del = new SimpleDelivery(observer, ref);

         toCancel[count] = del;

         count++;

         if (count == numToCancel)
         {
            notify();
         }

         return del;

      }

      public synchronized SimpleDelivery[] getToCancel() throws Exception
      {
         // Wait for them all to arrive first

         while (count < numToCancel)
         {
            wait(1000);

            if (count < numToCancel)
            {
               PagingStateTestBase.fail();
               return null;
            }
         }

         return toCancel;

      }

      void cancel() throws Exception
      {
         //Wait for them all to arrive first

         synchronized (this)
         {

            while (count < numToCancel)
            {
               wait(1000);

               if (count < numToCancel)
               {
                  PagingStateTestBase.fail();
                  return;
               }
            }
         }

         for (int i = numToCancel - 1; i >=0; i--)
         {
            try
            {
               toCancel[i].cancel();
            }
            catch (Throwable t)
            {
               log.error("Failed to cancel", t);
               PagingStateTestBase.fail();
            }
         }
      }
   }

   protected void consume(Queue queue, int consumeCount,
         MessageReference[] refs, int num)
      throws Throwable
   {
      ConsumingReceiver r = new ConsumingReceiver(num, refs, consumeCount, false, false);
      queue.getLocalDistributor().add(r);
      queue.deliver();
      r.acknowledge();
      queue.getLocalDistributor().remove(r);
   }

   protected void consumeInTx(Queue queue, int consumeCount,
         MessageReference[] refs, int num)
      throws Throwable
   {
      ConsumingReceiver r = new ConsumingReceiver(num, refs, consumeCount, true, false);
      queue.getLocalDistributor().add(r);
      queue.deliver();
      r.acknowledge();
      queue.getLocalDistributor().remove(r);
   }

   protected void consumeIn2PCTx(Queue queue, int consumeCount,
         MessageReference[] refs, int num)
      throws Throwable
   {
      ConsumingReceiver r = new ConsumingReceiver(num, refs, consumeCount, true, true);
      queue.getLocalDistributor().add(r);
      queue.deliver();
      r.acknowledge();
      queue.getLocalDistributor().remove(r);
      //Need to give enough time for the call to handle to complete and return
      //thus removing the ref
    //  Thread.sleep(500);
   }

   protected SimpleDelivery[] getDeliveries(Queue queue, int number) throws Exception
   {
      CancellingReceiver r1 = new CancellingReceiver(number);
      queue.getLocalDistributor().add(r1);
      queue.deliver();
      SimpleDelivery[] dels = r1.getToCancel();
      queue.getLocalDistributor().remove(r1);
      //Need to give enough time for the call to handle to complete and return
      //thus removing the ref
    //  Thread.sleep(500);

      return dels;
   }

   protected void cancelDeliveries(Queue queue, int number) throws Exception
   {
      CancellingReceiver r1 = new CancellingReceiver(number);
      queue.getLocalDistributor().add(r1);
      queue.deliver();
      r1.cancel();
      queue.getLocalDistributor().remove(r1);
      //Need to give enough time for the call to handle to complete and return
      //thus removing the ref
     // Thread.sleep(500);
   }


   protected List getReferenceIdsOrderedByOrd(long queueId) throws Exception
   {
      InitialContext ctx = new InitialContext();

      TransactionManager mgr = (TransactionManager)ctx.lookup(TransactionManagerService.JNDI_NAME);
      DataSource ds = (DataSource)ctx.lookup("java:/DefaultDS");

      javax.transaction.Transaction txOld = mgr.suspend();
      mgr.begin();

      Connection conn = ds.getConnection();

      List msgIds = new ArrayList();

      String sql =
         "SELECT MESSAGE_ID, ORD, PAGE_ORD FROM JBM_MSG_REF WHERE CHANNEL_ID=? ORDER BY ORD";
      PreparedStatement ps = conn.prepareStatement(sql);
      ps.setLong(1, queueId);

      ResultSet rs = ps.executeQuery();

      while (rs.next())
      {
         long msgId = rs.getLong(1);

         msgIds.add(new Long(msgId));
      }
      rs.close();
      ps.close();

      conn.close();

      mgr.commit();

      if (txOld != null)
      {
         mgr.resume(txOld);
      }

      return msgIds;
   }

   protected List getReferenceIdsOrderedByPageOrd(long queueId) throws Exception
   {
      InitialContext ctx = new InitialContext();

      TransactionManager mgr = (TransactionManager)ctx.lookup(TransactionManagerService.JNDI_NAME);
      DataSource ds = (DataSource)ctx.lookup("java:/DefaultDS");

      javax.transaction.Transaction txOld = mgr.suspend();
      mgr.begin();

      Connection conn = ds.getConnection();

      List msgIds = new ArrayList();

      String sql =
         "SELECT MESSAGE_ID, ORD, PAGE_ORD FROM JBM_MSG_REF WHERE CHANNEL_ID=? ORDER BY PAGE_ORD";
      PreparedStatement ps = conn.prepareStatement(sql);
      ps.setLong(1, queueId);

      ResultSet rs = ps.executeQuery();

      while (rs.next())
      {
         long msgId = rs.getLong(1);

         msgIds.add(new Long(msgId));
      }
      rs.close();
      ps.close();

      conn.close();

      mgr.commit();

      if (txOld != null)
      {
         mgr.resume(txOld);
      }

      return msgIds;
   }

   protected List getPagedReferenceIds(long queueId) throws Exception
   {
      InitialContext ctx = new InitialContext();

      TransactionManager mgr = (TransactionManager)ctx.lookup(TransactionManagerService.JNDI_NAME);
      DataSource ds = (DataSource)ctx.lookup("java:/DefaultDS");

      javax.transaction.Transaction txOld = mgr.suspend();
      mgr.begin();

      Connection conn = ds.getConnection();
      String sql =
         "SELECT MESSAGE_ID FROM JBM_MSG_REF WHERE " +
         "CHANNEL_ID=? AND PAGE_ORD IS NOT NULL ORDER BY PAGE_ORD";

      PreparedStatement ps = conn.prepareStatement(sql);
      ps.setLong(1, queueId);

      ResultSet rs = ps.executeQuery();

      List msgIds = new ArrayList();

      while (rs.next())
      {
         long msgId = rs.getLong(1);
         msgIds.add(new Long(msgId));
      }
      rs.close();
      ps.close();
      conn.close();

      mgr.commit();

      if (txOld != null)
      {
         mgr.resume(txOld);
      }

      return msgIds;
   }

   protected List getMessageIds() throws Exception
   {
      InitialContext ctx = new InitialContext();

      TransactionManager mgr = (TransactionManager)ctx.lookup(TransactionManagerService.JNDI_NAME);
      DataSource ds = (DataSource)ctx.lookup("java:/DefaultDS");

      javax.transaction.Transaction txOld = mgr.suspend();
      mgr.begin();

      Connection conn = ds.getConnection();
      String sql = "SELECT MESSAGE_ID FROM JBM_MSG ORDER BY MESSAGE_ID";
      PreparedStatement ps = conn.prepareStatement(sql);

      ResultSet rs = ps.executeQuery();

      List msgIds = new ArrayList();

      while (rs.next())
      {
         long msgId = rs.getLong(1);
         msgIds.add(new Long(msgId));
      }
      rs.close();
      ps.close();
      conn.close();

      mgr.commit();

      if (txOld != null)
      {
         mgr.resume(txOld);
      }

      return msgIds;
   }
}
