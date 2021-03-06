/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.thirdparty.remoting;

import org.jboss.test.messaging.MessagingTestCase;

/**
 * This test makes sure that all client-side threads employed by Messaging are well behaved and
 * don't prevent a client from exiting cleanly.
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2868 $</tt>
 *
 * $Id: DaemonThreadTest.java 2868 2007-07-10 20:22:16Z timfox $
 */
public class DaemonThreadTest extends MessagingTestCase
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   public DaemonThreadTest(String name)
   {
      super(name);
   }

   // Public ---------------------------------------------------------------------------------------

   public void testNothingYet() throws Throwable
   {
      // No, we won't forget to implement it, for there is a JIRA issue for it:
      // http://jira.jboss.org/jira/browse/JBMESSAGING-768

      //throw new Exception("Don't forget to implement this test, we need it");
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

}
