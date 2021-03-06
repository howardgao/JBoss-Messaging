/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.thirdparty.remoting.util;

import java.io.Serializable;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2868 $</tt>
 *
 * $Id: OnewayCallbackTrigger.java 2868 2007-07-10 20:22:16Z timfox $
 */
public class OnewayCallbackTrigger implements Serializable
{
   // Constants ------------------------------------------------------------------------------------

   private static final long serialVersionUID = 2887545875458754L;

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   private String payload;
   private long[] triggerTimes;

   // Constructors ---------------------------------------------------------------------------------

   public OnewayCallbackTrigger(String payload)
   {
      this(payload, new long[] {0});
   }

   public OnewayCallbackTrigger(String payload, long[] triggerTimes)
   {
      this.payload = payload;
      this.triggerTimes = triggerTimes;
   }

   // Public ---------------------------------------------------------------------------------------

   public String getPayload()
   {
      return payload;
   }

   public long[] getTriggerTimes()
   {
      return triggerTimes;
   }

   public String toString()
   {
      return "OnewayCallbackTrigger[" + payload + "]";
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------
}
