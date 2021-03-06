/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.jms.client;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import org.jboss.logging.Logger;

import EDU.oswego.cs.dl.util.concurrent.ConcurrentHashMap;
import EDU.oswego.cs.dl.util.concurrent.ReadWriteLock;
import EDU.oswego.cs.dl.util.concurrent.ReentrantWriterPreferenceReadWriteLock;

/**
 * The valve will block any call as long as it is closed.
 *
 * Usage: call enter() when performing a regular call and leave() in a finally block. Call close()
 * when performing a failover, and open() in a finally block.
 *
 * The class contains logic to avoid dead locks between multiple threads closing the valve at the
 * same time, which uses referencing counting on a threadLocal variable. That's why it's very
 * important to aways leave the valve in a finally block.
 *
 * This class also generate tracing information, to help debug situations like the case the valve
 * can't be closed, but only if trace is enabled on log4j.
 *
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @version <tt>$Revision: 3262 $</tt>
 *
 * $Id: FailoverValve.java 3262 2007-10-30 09:09:55Z timfox $
 */
public class FailoverValve
{
   // Constants ------------------------------------------------------------------------------------

   private static final Logger log = Logger.getLogger(FailoverValve.class);

   public static final long DEFAULT_ATTEMPT_TIMEOUT = 5000;

   // Static ---------------------------------------------------------------------------------------

   private static boolean trace = log.isTraceEnabled();

   // Attributes -----------------------------------------------------------------------------------

   // we keep a ThreadLocal counter to help avoid deadlocks when multiple threads are closing
   // the valve
   private ThreadLocal counterLocal = new ThreadLocal();

   private ReadWriteLock lock;

   private int activeCloses = 0;

   // these are only initialized if tracing is enabled
   private ThreadLocal stackCloses;
   private ThreadLocal stackEnters;

   private Map debugCloses;
   private Map debugEnters;

   private FailoverCommandCenter fcc;

   private long writeLockAttemptTimeout;

   // Constructors ---------------------------------------------------------------------------------

   public FailoverValve()
   {
      this(null, DEFAULT_ATTEMPT_TIMEOUT);
   }

   public FailoverValve(long attemptTiemout)
   {
      this(null, attemptTiemout);
   }

   public FailoverValve(FailoverCommandCenter fcc)
   {
      this(fcc, DEFAULT_ATTEMPT_TIMEOUT);
   }

   /**
    * @param fcc - can be null, for an uninitialized valve.
    */
   public FailoverValve(FailoverCommandCenter fcc, long attemptTiemout)
   {
      this.fcc = fcc;

      // We're using reentrant locks because we will need to to acquire read locks after write locks
      // have been already acquired. There is also a case when a readLock will be promoted to
      // writeLock when a failover occurs; using reentrant locks will make this usage transparent
      // for the API, we just close the valve and the read lock is promoted to write lock.
      lock = new ReentrantWriterPreferenceReadWriteLock();

      this.writeLockAttemptTimeout = attemptTiemout;

      if (trace)
      {
         stackCloses = new ThreadLocal();
         stackEnters = new ThreadLocal();
         debugCloses = new ConcurrentHashMap();
         debugEnters = new ConcurrentHashMap();
      }
   }

   // Public ---------------------------------------------------------------------------------------

   public void enter() throws InterruptedException
   {
      lock.readLock().acquire();

      getCounter().counter++;

      if (trace)
      {
         Exception ex = new Exception();
         getStackEnters().push(ex);
         debugEnters.put(ex, Thread.currentThread());
      }
   }

   public void leave() throws InterruptedException
   {
      lock.readLock().release();

      // sanity check
      if (getCounter().counter-- < 0)
      {
         throw new IllegalStateException("leave() was called without a prior enter() call");
      }

      if (trace)
      {
         Exception ex = (Exception) getStackEnters().pop();
         debugEnters.remove(ex);
      }
   }

   public void close() throws InterruptedException
   {
      log.debug(this + " close ...");

      // Before assuming a write lock, we need to release reentrant read locks.
      // When simultaneous threads are closing a valve (as simultaneous threads are capturing a
      // failure) we won't be able to close the valve until all the readLocks are released. This
      // release routine will be able to resolve the deadlock while we still guarantee the unicity
      // of the lock. The useCase for this is when a failure is captured when a thread is already
      // holding a read-lock. For example if a failure happens when sending ACKs, the valve will be
      // already hold on receiveMessage, while the sendACK will be trying to close the Valve. This
      // wouldn't be a problem if we had only single threads but the problem is we will be waiting
      // on a readLock on another thread that might also be waiting to close the valve as fail event
      // will be captured by multiple threads. So, in summary we need to completely leave the valve
      // before closing it or a dead lock will happen if multiple threads are closing the valve at
      // same time waiting on each others readLocks before acquiring a writeLock.
      int counter = getCounter().counter;

      for (int i = 0; i < counter; i++)
      {
         lock.readLock().release();
      }

      boolean acquired = false;

      do
      {
         acquired = lock.writeLock().attempt(writeLockAttemptTimeout);

         if (!acquired)
         {
            log.debug(this + " could not close, trying again ...", new Exception());
            if (trace) { log.trace(debugValve()); }
         }
      }
      while (!acquired);

      log.debug(this + " closed");

      activeCloses++;

      // Sanity check only...
      if (activeCloses > 1)
      {
         lock.writeLock().release();
         throw new IllegalStateException("Valve closed twice");
      }

      if (trace)
      {
         Exception ex = new Exception();
         getStackCloses().push(ex);
         debugCloses.put(ex, Thread.currentThread());
      }
   }

   public void open() throws InterruptedException
   {
      if (activeCloses <= 0)
      {
         throw new IllegalStateException("Valve not closed");
      }

      log.debug(this + " opening ...");

      activeCloses--;

      lock.writeLock().release();

      // re-apply the locks as we had before closing the valve
      int counter = getCounter().counter;
      for (int i = 0; i < counter; i++)
      {
         lock.readLock().acquire();
      }

      if (trace)
      {
         Exception ex = (Exception) getStackCloses().pop();
         debugCloses.remove(ex);
      }

      log.debug(this + " opened");
   }

   public long getWriteLockAttemptTimeout()
   {
      return writeLockAttemptTimeout;
   }

   public String toString()
   {
      return "FailoverValve[" +
         (fcc == null ?
            "UNINITIALIZED" :
            "connectionID=" + fcc.getConnectionState().getDelegate().getID()) +
         "]";
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   /**
    * Counter of times this thread entered the valve.
    */
   private Counter getCounter()
   {
      Counter localCounter = (Counter)counterLocal.get();

      if (localCounter == null)
      {
         localCounter = new Counter();
         counterLocal.set(localCounter);
      }

      return localCounter;
   }


   private Stack getStackCloses()
   {
      if (stackCloses.get() == null)
      {
         stackCloses.set(new Stack());
      }

      return (Stack) stackCloses.get();
   }

   private Stack getStackEnters()
   {
      if (stackEnters.get() == null)
      {
         stackEnters.set(new Stack());
      }
      return (Stack) stackEnters.get();
   }

   /**
    * This method will show the threads that are currently holding locks (enters or closes).
    * */
   private synchronized String debugValve()
   {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);

      writer.println("********************** Debug Valve Information *************************");
      writer.println("Close owners");

      // Close should never have more than 1 thread owning, but as this is a debug report we will
      // consider that as a possibility just to show eventual bugs (just in case this class is ever
      // changed)
      for (Iterator iter = debugCloses.entrySet().iterator(); iter.hasNext();)
      {
         Map.Entry entry = (Map.Entry) iter.next();
         writer.println("Thread that owns a close =" + entry.getValue());
         writer.println("StackTrace:");
         Exception e = (Exception) entry.getKey();
         e.printStackTrace(writer);
      }

      writer.println("Valve owners");
      for (Iterator iter = debugEnters.entrySet().iterator(); iter.hasNext();)
      {
         Map.Entry entry = (Map.Entry) iter.next();
         writer.println("Thread that owns valve =" + entry.getValue());
         writer.println("StackTrace:");
         Exception e = (Exception) entry.getKey();
         e.printStackTrace(writer);
      }

      return buffer.toString();
   }

   // Inner classes --------------------------------------------------------------------------------

   /**
    * Used to count the number of read locks (or enters) owned by this thread
    */
   private static class Counter
   {
      int counter;
   }

}
