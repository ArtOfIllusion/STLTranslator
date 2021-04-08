/*  Util.java  */

package org.exmosys.util;

/*
 * Util: Utility methods
 *
 * Copyright (C) 2005 Nik Trevallyn-Jones, Sydney Australia.
 *
 * Author: Nik Trevallyn-Jones, nik777@users.sourceforge.net
 * $Id: Exp $
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of version 2 of the GNU General Public License as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See version 2 of the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * with this program. If not, the license, including version 2, is available
 * from the GNU project, at http://www.gnu.org.
 */


// ----------------------------------------------------------------------------
//
//  CLASS:   Util.java
//
//  PURPOSE: Some handy utility methods.
//
//  OWNER:   Copyright (C) 2000 Designer Objects Pty Ltd, Sydney, Australia.
//
// ----------------------------------------------------------------------------
//  $Log: Util.java,v $
//  Revision 1.1.1.1  2004/03/24 09:09:19  nik
//  import
//
//  Revision 1.2  2004/03/24 08:54:01  nik
//  changes in Util now compile
//  CV:S ----------------------------------------------------------------------
//
//  Revision 1.1.1.1  2004/03/23 11:34:00  nik
//  local checkin
//
//  Revision 1.1.1.1  2002/08/14 03:34:49  nik
//  initial load of V3.1
//  * new API
//  * new packaging
//  * updated Makefile
//  * correct binary file import
//
//  Revision 1.2  2002/06/20 08:32:31  nik
//  minor fixes to Util.java
//
//  Revision 1.2  2002/04/17 10:27:37  nik
//  interim checkin
//
//  Revision 1.1  2002/04/15 04:36:39  nik
//  new files
//
// ----------------------------------------------------------------------------

import java.util.*;

/**
 * Provides useful utility methods.
 */
public class Util
{
    private Util()     // Not to be instantiated
    {}

    /**
     * Does the string s contain only whitespace characters? - as defined by
     * Character.isWhitespace().
     *  @param s the string to test.
     *  @return <i>true</i> if the string contains all whitespace and
     *		 <i>false</i> otherwise.
     */
    public static boolean isWhitespace( String s )
    {
	int len = s.length();
	for ( int i = 0; i < len; i++ )
	    if ( !Character.isWhitespace( s.charAt(i) ) )
		return false;
	return true;
    }

    /**
     * Counts character occurances in a string.
     *  @param s  Any string.
     *  @param x  Any character.
     *  @return The number of times character x occurs in s.
     */
    public static int count( String s, char x )
    {
	int count = 0;

	int len = s.length();
	for (int pos = s.indexOf(x); pos >= 0 && pos < len;
	     pos = s.indexOf(x, pos + 1)) {

	    count++;
	}

	return count;
    }

    /**
     *  count unquoted character occurences in a String.
     *
     *  @param s String in which to count
     *  @param c char to count
     *  @param quote String of open/close quote pairs. Occurences of <i>c</i>
     *		that occur within an open/close pair are not counted.
     *
     *<p>
     *<pre>Eg:
     *   <code>count("abc, a comma (,)", ',', "()")</code>
     *
     *   the call above returns 1, since the second comma is within "()",
     *   which define a quote open/close pair.
     *
     *   <code>count("ab, cd, 'two,quoted,commas'", ',', "()''")</code>
     *
     *   The call above returns 2, since the last two commas are within a quote
     *   open/close pair "''".
     *</pre>
     *
     *  @return the number of occurences, or zero.
     */
    public static int count(String s, char c, String quotes)
    {
	int result = 0;
	
	int maxQuote = (quotes !=  null ? quotes.length() : 0);
	if (maxQuote == 0) return count(s, c);

	int len = s.length();
	int q = 0;
	char x = '\0';
	for (int pos = 0; pos < len && pos >= 0; pos++) {
	    x = s.charAt(pos);

	    if (x == c) result++;		// unquoted match, increment
	    else {				// skip chars between quotes
		q = quotes.indexOf(x);
		if (q >= 0 && q < maxQuote && (q % 2) == 0)
		    pos = s.indexOf(quotes.charAt(q+1), pos+1);
	    }
	}

	return result;
    }

    /**
     *  returns true if this is a quoted value
     */
    public static boolean isQuoted(String value)
    {
	// if first char is a quote, and last char == first char,
	// then it's quoted
	return  (value.length() > 1 && "'\"".indexOf(value.charAt(0)) >= 0 &&
		 value.charAt(value.length() - 1) == value.charAt(0));
    }

    /**
     *  remove quotes from a value
     */
    public static String unQuote(String value)
    {
	String result = value.trim();

	// if it's quoted, remove the quotes
	if (isQuoted(result)) {
	    result = result.substring(1, result.length() - 1);
	}

	return result;
    }

    /**
     *  Translates chars in a string, from 'match' with 'replace'.
     *
     *<br> For each char in str:
     *<ul>
     *<li>If the char is in 'match' then
     *   <ul>
     *   <li>if there is a char in the corresponding position in 'replace', the
     *          it replaces the char (ie, the char is translated).
     *   <li>if there is no corresponding char in 'replace' then nothing is
     *		copied (ie, the char is deleted).
     *   </ul>
     *<br>
     *<li>if the char is <em>not</em> in 'match' then
     *   <ul>
     *   <li>if 'replace' is longer than 'match' and the char matches one of
     *		the extra chars in 'replace', then the char is copied.
     *   <li>Otherwise the char is deleted.
     *   </ul>
     *</ul>
     *
     *  @param string - incoming String
     *
     *  @param match - the list of chars to match in string.
     *
     *  @param out - the list of to replace matched chars with.
     *
     *  @return the translated String
     *
     *  Examples:
     *  match  = "({[+=-"
     *  replace = ")}]"
     *
     *  will convert ( to ); { to }; [ to ]; delete all +=- and copy all else
     *
     *  match  = 'A'
     *  replace = ''
     *  copy all but A
     *
     *  match  = ''
     *  replace = 'A'
     *  delete all but A
     *
     *  match  = 'A'
     *  replace = 'A'
     *  copy all (including A)
     *
     *  match  = ''
     *  replace = ''
     *  delete all.
     *
     *  If this all seems arbitrary, then the following explanation may help:
     *
     *  If 'match' is longer than 'replace', then this specifies selected
     *  deletion, and hence any chars not matched in 'match' are copied
     *  transparently.
     *
     *  So if 'replace' is empty, and 'match' is non empty, then the chars in
     *  'match' are deleted, and everything else is copied.
     *
     *  Conversely, if 'replace' is longer than 'match', then this specifies
     *  copying, and hence any chars not matched in 'match' and not in the
     *  extra chars in 'replace' are deleted.
     *
     *  So if 'match' is empty, and 'replace' is non-empty, then 'replace'
     *  specifies chars to be copied, and everything else is deleted.
     *
     *  If 'match' and 'replace' are idenical non-null strings, then the effect
     *  is to copy everything, without change.
     *  (each char in 'match' is translated to itself, and all others are
     *  copied).
     *  If 'match' and 'replace' are both null strings, then the effect is to
     *  delete all characters.
     *  (null 'match' means no translations, and null 'replace' means no
     *  copying).
     */
    public static String translate(String str, String match, String replace)
    {
	StringBuffer b = new StringBuffer(str.length());

	int pos = 0;
	char c = 0;

	if (match == null) match = "";
	if (replace == null) replace = "";

	boolean copy = (match.length() != 0 &&
			match.length() >= replace.length());

	// loop over the input string
	int max = str.length();
	for (int x = 0; x < max; x++) {
	    c = str.charAt(x);
	    pos = match.indexOf(c);

	    // if found c in 'match'
	    if (pos >= 0) {
		// translate
		if (pos < replace.length()) b.append(replace.charAt(pos));
	    }

	    // copy
	    else if (copy || replace.indexOf(c) >= match.length()) b.append(c);
	}
	
	return b.toString();
    }

}
