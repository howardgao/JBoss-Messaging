/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.messaging.util;

import java.util.Hashtable;

import javax.naming.InitialContext;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2928 $</tt>
 * $Id: JNDITesterService.java 2928 2007-07-27 00:33:55Z timfox $
 */
public class JNDITesterService implements JNDITesterServiceMBean
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   // JNDITesterServiceMBean implementation --------------------------------------------------------

   public Object installAndUseJNDIEnvironment(Hashtable environment, String thingToLookUp)
      throws Exception
   {

      InitialContext ic = new InitialContext(environment);

      return ic.lookup(thingToLookUp);
   }

   // Public ---------------------------------------------------------------------------------------

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

}
