/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.jms.client.plugin;

import java.util.Random;

import org.jboss.jms.delegate.ConnectionFactoryDelegate;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2868 $</tt>
 *
 * $Id: RoundRobinLoadBalancingPolicy.java 2868 2007-07-10 20:22:16Z timfox $
 */
public class RoundRobinLoadBalancingPolicy implements LoadBalancingPolicy
{
   // Constants ------------------------------------------------------------------------------------

   private static final long serialVersionUID = 5215940403016586462L;
   
   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   private static final Random random = new Random();

   // The index of the next delegate to be used
   private int next;

   private ConnectionFactoryDelegate[] delegates;

   // Constructors ---------------------------------------------------------------------------------

   public RoundRobinLoadBalancingPolicy(ConnectionFactoryDelegate[] delegates)
   {
      next = -1;
      this.delegates = delegates;
   }

   // LoadBalancingPolicy implementation -----------------------------------------------------------

   public synchronized ConnectionFactoryDelegate getNext()
   {
      if (next >= delegates.length)
      {
         next = 0;
      }
      
      if (next < 0)
      {
         next = random.nextInt(delegates.length);
      }

      return delegates[next++];
   }

   public synchronized void updateView(ConnectionFactoryDelegate[] delegates)
   {
      next = -1;
      this.delegates = delegates;
   }

   // Public ---------------------------------------------------------------------------------------

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

}
