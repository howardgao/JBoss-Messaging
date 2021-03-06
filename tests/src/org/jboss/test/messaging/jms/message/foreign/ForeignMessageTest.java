/*
* JBoss, Home of Professional Open Source
* Copyright 2005, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.test.messaging.jms.message.foreign;

import javax.jms.Message;

import org.jboss.test.messaging.jms.message.MessageTestBase;
import org.jboss.test.messaging.jms.message.SimpleJMSMessage;


/**
 *
 * Tests the delivery/receipt of a foreign message
 *
 *
 * @author <a href="mailto:a.walker@base2group.com>Aaron Walker</a>
 * @version <tt>$Revision: 2830 $</tt>
 *
 * $Id: ForeignMessageTest.java 2830 2007-07-03 17:35:17Z timfox $
 */
public class ForeignMessageTest extends MessageTestBase
{
    public ForeignMessageTest(String name)
    {
        super(name);
    }

    public void setUp() throws Exception
    {
       super.setUp();
       this.message = createForeignMessage();
    }

    public void tearDown() throws Exception
    {
       super.tearDown();
       message = null;
    }

    protected Message createForeignMessage() throws Exception
    {
        SimpleJMSMessage m = new SimpleJMSMessage();
        log.debug("creating JMS Message type " + m.getClass().getName());

        return m;
    }
}
