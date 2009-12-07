/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.example.jms.topic;

import javax.jms.Message;
import javax.jms.MessageListener;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2868 $</tt>
 
 * $Id: ExampleListener.java 2868 2007-07-10 20:22:16Z timfox $
 */
public class ExampleListener implements MessageListener
{   
   private Message message;
      
   public synchronized void onMessage(Message message)
   {
      this.message = message;
      notifyAll();
   }
   
   public synchronized Message getMessage()
   {
      return message;
   }
      
   protected synchronized void waitForMessage()
   {
      if (message != null)
      {
         return;
      }
      
      try
      {
         wait(5000);
      }
      catch(InterruptedException e)
      {
         // OK
      }
   }
}
