/*  LittleEndianDataInputStream  */

package org.exmosys.streams;

/*
 * LittleEndianDatainputStream: input data in little endian format.
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

import java.io.*;

/**
 *  Implementation of the DataInput interface that read data compatible
 *  with little-endian streams.
 *
 *  The documentation of the DataInput interface says this is strictly
 *  illegal, but on the other hand, it is also strictly useful.
 */

public class LittleEndianDataInputStream extends FilterInputStream
    implements DataInput
{
    public LittleEndianDataInputStream(InputStream is)
    { super(is); }

    public int read(byte[] barray)
	throws IOException
    { return in.read(barray, 0, barray.length); }

    public int read(byte[] barray, int start, int count)
	throws IOException
    { return in.read(barray, start, count); }

    public void readFully(byte[] barray)
	throws IOException
    { readFully(barray, 0, barray.length); }

    public void readFully(byte[] barray, int start, int count)
	throws IOException
    {
	int chunk;
	while (count > 0) {
	    chunk = in.read(barray, start, count);

	    if (chunk < 0) throw new EOFException();

	    count -= chunk;
	    start += chunk;
	}
    }

    public int skipBytes(int count)
	throws IOException
    {
	int chunk = 0;
	int total = 0;
	while (total < count) {
	    chunk = (int) in.skip(count);

	    if (chunk > 0) total += chunk;
	    else break;
	}

	return total;
    }

    public boolean readBoolean()
	throws IOException
    {
	int c = in.read();

	if (c < 0) throw new EOFException();
	return (c != 0);
    }

    public byte readByte()
	throws IOException
    {
	int i = in.read();
	
	if (i < 0) throw new EOFException();
	return (byte) i;
    }

    public int readUnsignedByte()
	throws IOException
    {
	int i = in.read();
	
	if (i < 0) throw new EOFException();
	return i;
    }

    public short readShort()
	throws IOException
    {
	readFully(buf, 0, 2);
	return (short) (buf[1] << 8 + buf[0]);
    }

    public int readUnsignedShort()
	throws IOException
    {
	readFully(buf, 0, 2);
	return (buf[1] << 8 + buf[0]);
    }

    public char readChar()
	throws IOException
    {
	readFully(buf, 0, 2);
	return (char) (buf[1] << 8 + buf[0]);
    }

    public int readInt()
	throws IOException
    {
	readFully(buf, 0, 4);
	return ((buf[3] << 24) +
		((buf[2] & 0xff) << 16) +
		((buf[1] & 0xff) << 8) +
		(buf[0] & 0xff));
    }

    public long readLong()
	throws IOException
    {
	readFully(buf, 0, 8);
	return (((long) buf[7] << 56) +
		((long) (buf[6] & 0xff) << 48) +
		((long) (buf[5] & 0xff) << 40) +
		((long) (buf[4] & 0xff) << 32) +
		((long) (buf[3] & 0xff) << 24) +
		((buf[2] & 0xff) << 16) +
		((buf[1] & 0xff) << 8) +
		(buf[0] & 0xff));
    }

    public float readFloat()
	throws IOException
    { return Float.intBitsToFloat(readInt()); }

    public double readDouble()
	throws IOException
    { return Double.longBitsToDouble(readLong()); }

    /**
     *  readUTF is a java-only format, so read it as all other DataInput
     *  objects do.
     */
    public String readUTF()
	throws IOException
    {
	if (dis == null) dis = new DataInputStream(in);
	return dis.readUTF();
    }

    /**
     *  read a line of chars
     *
     *  @deprecated
     */
    public String readLine()
	throws IOException
    {
	if (dis == null) dis = new DataInputStream(in);
	return dis.readLine();
    }

    private byte[] buf = new byte[8];
    private DataInputStream dis = null;
}
