/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.messaging.jms;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.naming.InitialContext;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2925 $</tt>
 *
 * $Id: ConsumerClosedTest.java 2925 2007-07-25 10:43:58Z timfox $
 */
public class ConsumerClosedTest extends JMSTestCase
{
   // Constants -----------------------------------------------------

   public static final int NUMBER_OF_MESSAGES = 10;

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   InitialContext ic;

   // Constructors --------------------------------------------------

   public ConsumerClosedTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------


   public void testMessagesSentDuringClose() throws Exception
   {     
      Connection c = null;
      
      try
      {
	      c = cf.createConnection();
	      c.start();
	
	      Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
	      MessageProducer p = s.createProducer(queue1);
	
	      for(int i = 0; i < NUMBER_OF_MESSAGES; i++)
	      {
	         p.send(s.createTextMessage("message" + i));
	      }
	
	      log.debug("all messages sent");
	
	      MessageConsumer cons = s.createConsumer(queue1);
	      cons.close();
	
	      log.debug("consumer closed");
	      
	      // make sure that all messages are in queue
	      
	      assertRemainingMessages(NUMBER_OF_MESSAGES);
      }
      finally
      {
      	if (c != null)
      	{
      		c.close();
      	}
      	
      	removeAllMessages(queue1.getQueueName(), true, 0);      	
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
