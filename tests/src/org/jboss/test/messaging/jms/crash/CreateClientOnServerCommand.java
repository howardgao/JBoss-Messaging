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
package org.jboss.test.messaging.jms.crash;

import java.util.ArrayList;
import java.util.List;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.jboss.jms.client.JBossConnection;
import org.jboss.test.messaging.tools.container.Command;
import org.jboss.test.messaging.tools.container.Server;

/**
 * 
 * A CreateClientOnServerCommand.
 * 
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 3094 $</tt>
 *
 * $Id: CreateClientOnServerCommand.java 3094 2007-09-11 02:16:32Z clebert.suconic@jboss.com $
 */
public class CreateClientOnServerCommand implements Command
{
   private static final long serialVersionUID = -997724797145152821L;
   
   private ConnectionFactory cf;
   
   private Queue queue;
   
   private boolean retainReference;
   
   private static List commands = new ArrayList();
   
   public CreateClientOnServerCommand(ConnectionFactory cf, Queue queue, boolean retainReference)
   {
      this.cf = cf;
      
      this.queue = queue;
      
      this.retainReference = retainReference;
   }
   
   /*
    * Just create a connection, send and receive a message and leave the connection open.
    */
   public Object execute(Server server) throws Exception
   {
      if (retainReference)
      {
         commands.add(this);
      }
      
      Connection conn = cf.createConnection();
        
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
          
      MessageProducer prod = sess.createProducer(queue);
      
      prod.send(sess.createMessage());
         
      MessageConsumer cons = sess.createConsumer(queue);
      
      conn.start();
      
      cons.receive();
      
      //Leave the connection unclosed
      
      //Return the remoting client session id for the connection
      return ((JBossConnection)conn).getRemotingClientSessionID();
   }

}
