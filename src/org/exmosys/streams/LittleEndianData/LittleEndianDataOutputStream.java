/*  LittleEndianDataOutputStream  */

package org.exmosys.streams;

/*
 * LittleEndianDataOutputStream: output data in little endian format
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
 *  implementation of the DataOutput interface that writes data compatible
 *  with little-endian streams.
 *
 *  The documentation of the DataOutput interface says this is strictly
 *  illegal, but on the other hand, it is also strictly useful.
 */

public class LittleEndianDataOutputStream extends FilterOutputStream
    implements DataOutput
{
    public LittleEndianDataOutputStream(OutputStream os)
    { super(os); }

    public void write(int i)
	throws IOException
    { out.write(i); }

    public void write(byte[] barray)
	throws IOException
    { out.write(barray, 0, barray.length); }

    public void write(byte[] barray, int start, int count)
	throws IOException
    { out.write(barray, start, count); }

    public void flush()
	throws IOException
    { out.flush(); }

    public void writeByte(int i)
	throws IOException
    { out.write(i); }

    public void writeBoolean(boolean b)
	throws IOException
    { out.write(b ? 1 : 0); }

    public void writeShort(int i)
	throws IOException
    { writeChar(i); }

    public void writeChar(int i)
	throws IOException
    { 
	buf[0] = (byte) i;
	buf[1] = (byte) (i >>> 8);

	out.write(buf, 0, 2);
    }

    public void writeInt(int i)
	throws IOException
    {
        buf[0] = (byte) i;
        buf[1] = (byte) (i >>>  8);
        buf[2] = (byte) (i >>> 16);
        buf[3] = (byte) (i >>> 24);
	
	out.write(buf, 0, 4);
    }

    public void writeLong(long l)
	throws IOException
    {

        buf[0] = (byte) (l & 0xff);
        buf[1] = (byte) (l >>>  8);
        buf[2] = (byte) (l >>> 16);
        buf[3] = (byte) (l >>> 24);
        buf[4] = (byte) (l >>> 32);
        buf[5] = (byte) (l >>> 40);
        buf[6] = (byte) (l >>> 48);
        buf[7] = (byte) (l >>> 56);
	
	out.write(buf, 0, 8);
    }

    public void writeFloat(float f)
	throws IOException
    { writeInt(Float.floatToIntBits(f)); }

    public void writeDouble(double d)
	throws IOException
    { writeLong(Double.doubleToLongBits(d)); }

    public void writeBytes(String s)
	throws IOException
    {
	int len = s.length();
	for (int x = 0; x < len; x++) {
	    //System.out.println("x=" + x);
	    out.write((byte) s.charAt(x));
	}
    }

    public void writeChars(String s)
	throws IOException
    {
	int len = s.length();
	for (int x = 0; x < len; x++) writeChar(s.charAt(x));
    }

    /**
     *  writeUTF is a java-only format, so write it as all other DataOutput
     *  objects do.
     */
    public void writeUTF(String s)
	throws IOException
    {
	if (dos == null) dos = new DataOutputStream(out);
	dos.writeUTF(s);
    }

    private byte[] buf = new byte[8];
    protected DataOutputStream dos = null;
}
