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
package org.jboss.test.messaging.core;

import org.jboss.messaging.util.JDBCUtil;
import org.jboss.test.messaging.MessagingTestCase;


/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision: 2928 $</tt>
 */
public class JDBCUtilTest extends MessagingTestCase
{
   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public JDBCUtilTest(String name)
   {
      super(name);
   }

   public void testStatementToStringOneArgument()
   {
      String sql = "INSERT INTO A (B) values(?)";
      String statement = JDBCUtil.statementToString(sql, "X");
      assertEquals("INSERT INTO A (B) values(X)", statement);
   }

   public void testStatementToStringTwoArguments()
   {
      String sql = "INSERT INTO A (B, C) values(?, ?)";
      String statement = JDBCUtil.statementToString(sql, "X", "Y");
      assertEquals("INSERT INTO A (B, C) values(X, Y)", statement);
   }

   public void testStatementToStringWitNull()
   {
      String sql = "INSERT INTO A (B, C) values(?, ?)";
      String statement = JDBCUtil.statementToString(sql, null, "Y");
      assertEquals("INSERT INTO A (B, C) values(null, Y)", statement);
   }


   public void testExtraArguments()
   {
      String sql = "INSERT INTO A (B, C) values(?, ?)";
      String statement = JDBCUtil.statementToString(sql, "X", "Y", "Z");
      assertEquals("INSERT INTO A (B, C) values(X, Y)", statement);
   }

   public void testNotEnoughArguments()
   {
      String sql = "INSERT INTO A (B, C, D) values(?, ?, ?)";
      String statement = JDBCUtil.statementToString(sql, "X", "Y");
      assertEquals("INSERT INTO A (B, C, D) values(X, Y, ?)", statement);
   }




}


