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
package org.jboss.jms.delegate;

import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;

import org.jboss.jms.client.FailoverListener;
import org.jboss.jms.client.JBossConnectionConsumer;

/**
 * Represents the minimal set of operations to provide connection functionality.
 * Some of the methods may be implemented on the server, others will be handled in the advice stack.
 * 
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 3248 $</tt>
 *
 * $Id: ConnectionDelegate.java 3248 2007-10-26 20:14:06Z timfox $
 */
public interface ConnectionDelegate extends ConnectionEndpoint
{      
   ExceptionListener getExceptionListener() throws JMSException;
   
   void setExceptionListener(ExceptionListener listener) throws JMSException;
  
   ConnectionMetaData getConnectionMetaData() throws JMSException;
   
   JBossConnectionConsumer createConnectionConsumer(Destination dest,
                                                    String subscriptionName,
                                                    String messageSelector,
                                                    ServerSessionPool sessionPool,
                                                    int maxMessages) throws JMSException;

   void registerFailoverListener(FailoverListener failoverListener);
   
   boolean unregisterFailoverListener(FailoverListener failoverListener);
   
   //Special start method that doesn't get intercepted to allow connection to be restarted
   //after failover when the valve is still held
   void startAfterFailover() throws JMSException;
}
