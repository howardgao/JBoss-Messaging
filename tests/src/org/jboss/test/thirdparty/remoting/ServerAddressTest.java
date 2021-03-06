/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.thirdparty.remoting;

import org.jboss.remoting.transport.socket.ServerAddress;
import org.jboss.test.messaging.MessagingTestCase;

/**
 * This test makes sure that Remoting implements ServerAddress.equals() correctly.
 *
 * @author <a href="mailto:ovidiu@svjboss.org">Ovidiu Feodorov</a>
 *
 * @version <tt>$Revision: 3407 $</tt>
 *
 * $Id: ServerAddressTest.java 3407 2007-12-04 11:18:26Z timfox $
 */
public class ServerAddressTest extends MessagingTestCase
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   public ServerAddressTest(String name)
   {
      super(name);
   }

   // Public ---------------------------------------------------------------------------------------

   public void testEquals() throws Throwable
   {
      ServerAddress sa = new ServerAddress("127.0.0.1", 5678, false, 0, 50);
      ServerAddress sa2 = new ServerAddress("127.0.0.1", 5678, false, 1, 50);

      assertFalse(sa.equals(sa2));
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected void setUp() throws Exception
   {
      super.setUp();
   }

   protected void tearDown() throws Exception
   {
      super.tearDown();
   }

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

}
