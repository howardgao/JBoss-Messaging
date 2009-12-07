/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.jms.client;

import org.jboss.jms.client.container.JmsClientAspectXMLLoader;
import org.jboss.jms.delegate.ConnectionFactoryEndpoint;

/**
 * A static singleton that insures the client-side AOP stack is loaded.
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 5366 $</tt>
 *
 * $Id: ClientAOPStackLoader.java 5366 2008-11-14 21:46:28Z clebert.suconic@jboss.com $
 */
public class ClientAOPStackLoader
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   public static ClientAOPStackLoader getInstance()
   {
      synchronized(ClientAOPStackLoader.class)
      {
         if (instance == null)
         {
            instance = new ClientAOPStackLoader();
         }
         return instance;
      }
   }

   // Attributes -----------------------------------------------------------------------------------

   private static ClientAOPStackLoader instance;

   private boolean loaded;

   // Constructors ---------------------------------------------------------------------------------

   private ClientAOPStackLoader()
   {
      loaded = false;
   }

   // Public ---------------------------------------------------------------------------------------

   /**
    * @param delegate - either an instance of ClientClusteredConnectionFactoryDelegate or
    *        ClientConnectionFactoryDelegate.
    *
    * @throws Exception - if something goes wrong with downloading the AOP configuration from the
    *         server and installing it.
    */
   public synchronized void load(ConnectionFactoryEndpoint delegate) throws Exception
   {
      if (loaded)
      {
         return;
      }

      ClassLoader savedLoader = SecurityActions.getTCL();

      try
      {
         // This was done because of some weird behavior of AOP & classLoading
         // http://jira.jboss.org/jira/browse/JBMESSAGING-980
         SecurityActions.setTCL(this.getClass().getClassLoader());

         byte[] clientAOPStack = delegate.getClientAOPStack();

         new JmsClientAspectXMLLoader().deployXML(clientAOPStack);

         loaded = true;
      }
      finally
      {
         SecurityActions.setTCL(savedLoader);
      }
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------
}
