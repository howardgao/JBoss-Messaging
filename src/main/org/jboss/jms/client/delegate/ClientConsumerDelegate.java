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
package org.jboss.jms.client.delegate;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import org.jboss.jms.client.state.ConnectionState;
import org.jboss.jms.client.state.HierarchicalState;
import org.jboss.jms.delegate.ConsumerDelegate;
import org.jboss.jms.destination.JBossDestination;
import org.jboss.jms.wireformat.CloseRequest;
import org.jboss.jms.wireformat.ClosingRequest;
import org.jboss.jms.wireformat.ConsumerChangeRateRequest;
import org.jboss.jms.wireformat.RequestSupport;
import org.jboss.logging.Logger;

/**
 * The client-side Consumer delegate class.
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 *
 * @version <tt>$Revision: 3174 $</tt>
 *
 * $Id: ClientConsumerDelegate.java 3174 2007-10-05 15:14:57Z timfox $
 */
public class ClientConsumerDelegate extends DelegateSupport implements ConsumerDelegate
{
   // Constants ------------------------------------------------------------------------------------

	private static final long serialVersionUID = 3253922610778321868L;

	private static final Logger log = Logger.getLogger(ClientConsumerDelegate.class);

   // Attributes -----------------------------------------------------------------------------------

   private int bufferSize;
   private int maxDeliveries;
   private long redeliveryDelay;

   // Static ---------------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   public ClientConsumerDelegate(String objectID, int bufferSize, int maxDeliveries, long redeliveryDelay)
   {
      super(objectID);
      this.bufferSize = bufferSize;
      this.maxDeliveries = maxDeliveries;
      this.redeliveryDelay = redeliveryDelay;
   }

   public ClientConsumerDelegate()
   {
   }

   // DelegateSupport overrides --------------------------------------------------------------------

   public void synchronizeWith(DelegateSupport nd) throws Exception
   {
      log.trace(this + " synchronizing with " + nd);

      super.synchronizeWith(nd);

      ClientConsumerDelegate newDelegate = (ClientConsumerDelegate)nd;

      // The client needs to be set first
      client = ((ConnectionState)state.getParent().getParent()).getRemotingConnection().
         getRemotingClient();

      // synchronize server endpoint state

      // synchronize (recursively) the client-side state

      state.synchronizeWith(newDelegate.getState());

      // synchronize the delegates

      bufferSize = newDelegate.getBufferSize();
      maxDeliveries = newDelegate.getMaxDeliveries();

   }

   public void setState(HierarchicalState state)
   {
      super.setState(state);

      client = ((ConnectionState)state.getParent().getParent()).getRemotingConnection().
                  getRemotingClient();
   }

   // Closeable implementation ---------------------------------------------------------------------

   public void close() throws JMSException
   {
      RequestSupport req = new CloseRequest(id, version);

      doInvoke(client, req);
   }

   public long closing(long sequence) throws JMSException
   {
      RequestSupport req = new ClosingRequest(sequence, id, version);

      return ((Long)doInvoke(client, req)).longValue();
   }

   // ConsumerDelegate implementation --------------------------------------------------------------

   public void changeRate(float newRate) throws JMSException
   {
      RequestSupport req = new ConsumerChangeRateRequest(id, version, newRate);

      doInvoke(client, req);
   }

   /**
    * This invocation should either be handled by the client-side interceptor chain or by the
    * server-side endpoint.
    */
   public MessageListener getMessageListener()
   {
      throw new IllegalStateException("This invocation should not be handled here!");
   }

   /**
    * This invocation should either be handled by the client-side interceptor chain or by the
    * server-side endpoint.
    */
   public Message receive(long timeout) throws JMSException
   {
      throw new IllegalStateException("This invocation should not be handled here!");
   }

   /**
    * This invocation should either be handled by the client-side interceptor chain or by the
    * server-side endpoint.
    */
   public void setMessageListener(MessageListener listener)
   {
      throw new IllegalStateException("This invocation should not be handled here!");
   }

   /**
    * This invocation should either be handled by the client-side interceptor chain or by the
    * server-side endpoint.
    */
   public boolean getNoLocal()
   {
      throw new IllegalStateException("This invocation should not be handled here!");
   }

   /**
    * This invocation should either be handled by the client-side interceptor chain or by the
    * server-side endpoint.
    */
   public JBossDestination getDestination()
   {
      throw new IllegalStateException("This invocation should not be handled here!");
   }

   /**
    * This invocation should either be handled by the client-side interceptor chain or by the
    * server-side endpoint.
    */
   public String getMessageSelector()
   {
      throw new IllegalStateException("This invocation should not be handled here!");
   }

   // Streamable implementation ----------------------------------------------------------

   public void read(DataInputStream in) throws Exception
   {
      super.read(in);

      bufferSize = in.readInt();

      maxDeliveries = in.readInt();
      
      redeliveryDelay = in.readLong();
   }

   public void write(DataOutputStream out) throws Exception
   {
      super.write(out);

      out.writeInt(bufferSize);

      out.writeInt(maxDeliveries);
      
      out.writeLong(redeliveryDelay);
   }

   // Public ---------------------------------------------------------------------------------------

   public String toString()
   {
      return "ConsumerDelegate[" + System.identityHashCode(this) + ", ID=" + id + "]";
   }

   public int getBufferSize()
   {
      return bufferSize;
   }

   public int getMaxDeliveries()
   {
      return maxDeliveries;
   }
   
   public long getRedeliveryDelay()
   {
   	return redeliveryDelay;
   }

   // Protected ------------------------------------------------------------------------------------

   // Package Private ------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner Classes --------------------------------------------------------------------------------
}
