/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.thirdparty.remoting.util;

import org.jboss.remoting.InvocationRequest;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2868 $</tt>
 * $Id: TestableSubsystem.java 2868 2007-07-10 20:22:16Z timfox $
 */
public interface TestableSubsystem
{
   InvocationRequest getNextInvocation(long timeout) throws InterruptedException;

   boolean isFailed();
}
