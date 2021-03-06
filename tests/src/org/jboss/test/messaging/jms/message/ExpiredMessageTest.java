/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.messaging.jms.message;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.jboss.logging.Logger;
import org.jboss.test.messaging.jms.JMSTestCase;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 3429 $</tt>
 * $Id: ExpiredMessageTest.java 3429 2007-12-06 15:41:49Z timfox $
 */
public class ExpiredMessageTest extends JMSTestCase
{
   // Constants ------------------------------------------------------------------------------------

   private static final Logger log = Logger.getLogger(ExpiredMessageTest.class);

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   public ExpiredMessageTest(String name)
   {
      super(name);
   }

   // Public ---------------------------------------------------------------------------------------

   public void testSimpleExpiration() throws Exception
   {
      Connection conn = cf.createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = session.createProducer(queue1);
      prod.setTimeToLive(1);

      Message m = session.createTextMessage("This message will die");

      prod.send(m);

      // wait for the message to die

      Thread.sleep(250);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      assertNull(cons.receive(2000));
      
      conn.close();
   }
   
   public void testManyExpiredMessagesAtOnce() throws Exception
   {
      Connection conn = cf.createConnection();
      
      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      
      MessageProducer prod = session.createProducer(queue1);
      
      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      
      prod.setTimeToLive(1);
      
      Message m = session.createTextMessage("This message will die");
      
      final int MESSAGE_COUNT = 100;
   
      for (int i = 0; i < MESSAGE_COUNT; i++)
      {
         prod.send(m);
      }
      
      MessageConsumer cons = session.createConsumer(queue1);
      conn.start();
      
      assertNull(cons.receive(10000));
      
      conn.close();
   }

  

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------
 
   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------
}
