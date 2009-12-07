/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.jms.wireformat;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import org.jboss.jms.delegate.BrowserEndpoint;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2868 $</tt>
 * $Id: BrowserResetRequest.java 2868 2007-07-10 20:22:16Z timfox $
 */
public class BrowserResetRequest extends RequestSupport
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   public BrowserResetRequest()
   {
   }

   public BrowserResetRequest(String objectId, byte version)
   {
      super(objectId, PacketSupport.REQ_BROWSER_RESET, version);
   }

   // RequestSupport overrides ---------------------------------------------------------------------

   public ResponseSupport serverInvoke() throws Exception
   {
      BrowserEndpoint endpoint = (BrowserEndpoint)Dispatcher.instance.getTarget(objectId);

      if (endpoint == null)
      {
         throw new IllegalStateException("Cannot find object in dispatcher with ID " + objectId);
      }

      endpoint.reset();
      return null;
   }

   public void read(DataInputStream is) throws Exception
   {
      super.read(is);
   }

   public void write(DataOutputStream os) throws Exception
   {
      super.write(os);
      os.flush();
   }

   // Public ---------------------------------------------------------------------------------------

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

}
