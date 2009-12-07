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

package org.jboss.jms.client.remoting;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.remoting.transport.socket.OpenConnectionChecker;
import org.jboss.remoting.marshal.Marshaller;
import org.jboss.remoting.marshal.UnMarshaller;

/**
 * @author <a href="mailto:tom.elrod@jboss.com">Tom Elrod</a>
 * @author <a href="mailto:tom.fox@jboss.com">Tim Fox</a>
 *
 * $Id: ClientSocketWrapper.java 7818 2009-09-19 15:05:48Z gaohoward $
 */
public class ClientSocketWrapper extends org.jboss.remoting.transport.socket.ClientSocketWrapper implements OpenConnectionChecker
{
   // Constants ------------------------------------------------------------------------------------
   final static private Logger log = Logger.getLogger(ClientSocketWrapper.class);
   final static protected int CLOSING = 1;
   
   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

//   private DataInputStream in;
//   private DataOutputStream out;
   
//   static
//   {
//      System.out.println("Using new ClientSocketWraper: 9/11/09: 18:20");
//   }

   // Constructors ---------------------------------------------------------------------------------

   public ClientSocketWrapper(Socket socket) throws IOException
   {
      super(socket);
      createStreams(socket, null);
   }

   public ClientSocketWrapper(Socket socket, Map metadata, Integer timeout) throws Exception
   {
      super(socket, metadata, timeout);
      createStreams(socket, metadata);
   }

   // Client SocketWrapper overrides ----------------------------------------------------------------------

   public void checkConnection() throws IOException
   {
      // Test to see if socket is alive by send ACK message
      final byte ACK = 1;

      log.debug(this + ".checkConnection() writing ACK");
      ((DataOutputStream)getOutputStream()).writeByte(ACK);
      ((DataOutputStream)getOutputStream()).flush();
      try
      {
         int b = ((DataInputStream)getInputStream()).readByte();
         log.debug(this + ".checkConnection read " + b);
      }
      catch (IOException e)
      {
         log.debug(this + ".checkConnection(): ", e);
         throw e;
      }
   }
   
   // OpenConnectionChecker implementation ---------------------------------------------------------
   
   public void checkOpenConnection() throws IOException
   {
      if (log.isTraceEnabled()) log.trace("checking open connection");

      if (((DataInputStream)getInputStream()).available() > 1)
      {
         log.trace("remote endpoint has closed");
         throw new IOException("remote endpoint has closed");
      }
   }
   
   // Public ---------------------------------------------------------------------------------------

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected InputStream createInputStream(String serializationType, Socket socket, UnMarshaller unmarshaller)
   throws IOException
   {
      // We make sure the buffer is big (default is 8192)- remember we flush after every request
      // or response so this is ok. We want to avoid flushes at other times.
      //TODO this could be made configurable
      
      BufferedInputStream bin = new BufferedInputStream(socket.getInputStream(), 65536);
      
      return new DataInputStream(bin);
   }

   protected OutputStream createOutputStream(String serializationType, Socket socket, Marshaller marshaller)
   throws IOException
{
      // We make sure the buffer is big (default is 8192)- remember we flush after every request
      // or response so this is ok. We want to avoid flushes at other times.
      //TODO this could be made configurable
      
      OutputStream os = super.createOutputStream(serializationType, socket, marshaller);
      log.debug(this + ": os = " + os);
      BufferedOutputStream bout = new BufferedOutputStream(os, 65536);
      
      return new DataOutputStream(bout);
   }
   
   public String toString()
   {
      Socket socket = getSocket();
      return "NEW ClientSocketWrapper[" + socket + "." +
         Integer.toHexString(System.identityHashCode(socket)) + "]";
   }

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

}
