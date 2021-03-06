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
package org.jboss.jms.server.endpoint.advised;

import javax.jms.JMSException;

import org.jboss.jms.delegate.ConnectionEndpoint;
import org.jboss.jms.delegate.IDBlock;
import org.jboss.jms.delegate.SessionDelegate;
import org.jboss.jms.tx.MessagingXid;
import org.jboss.jms.tx.TransactionRequest;

/**
 * The server-side advised instance corresponding to a Connection. It is bound to the AOP
 * Dispatcher's map.
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 3174 $</tt>
 *
 * $Id: ConnectionAdvised.java 3174 2007-10-05 15:14:57Z timfox $
 */
public class ConnectionAdvised extends AdvisedSupport implements ConnectionEndpoint
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   protected ConnectionEndpoint endpoint;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   public ConnectionAdvised(ConnectionEndpoint endpoint)
   {
      this.endpoint = endpoint;
   }

   // ConnectionEndpoint implementation -----------------------------

   public void close() throws JMSException
   {
      endpoint.close();
   }

   public long closing(long sequence) throws JMSException
   {
      return endpoint.closing(sequence);
   }

   public SessionDelegate createSessionDelegate(boolean transacted,
                                                int acknowledgmentMode,
                                                boolean isXA) throws JMSException
   {
      return endpoint.createSessionDelegate(transacted, acknowledgmentMode, isXA);
   }

   public String getClientID() throws JMSException
   {
      return endpoint.getClientID();
   }

   public void setClientID(String id) throws JMSException
   {
      endpoint.setClientID(id);
   }

   public void start() throws JMSException
   {
      endpoint.start();
   }

   public void stop() throws JMSException
   {
      endpoint.stop();
   }

   public void sendTransaction(TransactionRequest request,
                               boolean checkForDuplicates) throws JMSException
   {
      endpoint.sendTransaction(request, checkForDuplicates);
   }

   public MessagingXid[] getPreparedTransactions() throws JMSException
   {
      return endpoint.getPreparedTransactions();
   }
   
   public IDBlock getIdBlock(int size) throws JMSException
   {
      return endpoint.getIdBlock(size);
   }
   
   // Public --------------------------------------------------------

   public Object getEndpoint()
   {
      return endpoint;
   }

   public String toString()
   {
      return "ConnectionAdvised->" + endpoint;
   }

   // Protected -----------------------------------------------------

   // Package Private -----------------------------------------------

   // Private -------------------------------------------------------

   // Inner Classes -------------------------------------------------
}
