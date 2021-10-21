/*
 * Copyright (C) 2002-2004 by Nik Trevallyn-Jones
 * Changes copyright (C) 2021 by Lucas Stanek
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

/*
 *  This is a derivative work.
 *  This code was based very strongly on the OBJTranslator code (written by,
 *  and copyright of Peter Eastman), and the original STL output script
 *  (written by, and copyright of Francois Guillet).
 */

package artofillusion.translators;

import artofillusion.*;
import artofillusion.ui.*;
import artofillusion.math.*;
import artofillusion.object.*;
import artofillusion.animation.*;

import buoy.event.*;
import buoy.widget.*;
import java.text.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

import org.exmosys.streams.*;
import org.exmosys.util.Util;

/**
 * STLTranslator is a Translator which imports and exports STL files.
 */

public class STLTranslator implements Plugin, Translator
{
    public static final int EXPORT = 0;
    public static final int IMPORT = 1;

    /* public so external code (eg scripts) can have access */
    public double surfError = 0.05, tolerance = 0.0;
    public int decimal = 12;
    public boolean ignoreError=false, centered=true, frame=true;

    protected Object ed;

    protected BFrame parent;
    protected Scene theScene;
    protected Plugin solidPlugin;
    protected ArrayList theList;
    protected Mat4 move;
    protected String path=null, name=null;
    protected boolean errs[];
    protected int action, faces=0, rendermode;

    protected Thread thread;
    protected OutputStream out;
    protected InputStream in;
    protected CharArrayWriter message;
    protected Exception error;

    /* internal machinations of the GUI - protected */
    protected BDialog dlg;
    protected BTextField pathField = new BTextField("Untitled", 15);
    protected BComboBox typeChoice = new BComboBox(new String[] {
	Translate.text("Ascii"),
	Translate.text("Binary")
    });

    protected BCheckBox centerBox = new
	BCheckBox(Translate.text("center"), true);
    protected BCheckBox frameBox = new
	BCheckBox(Translate.text("frame"), true);
    protected BCheckBox ignoreBox = new
	BCheckBox(Translate.text("ignoreErrors"), false);
    
    protected ValueField errorField = new
	ValueField(tolerance, ValueField.NONNEGATIVE);

    protected ValueField surfErrorField = new
	ValueField(surfError, ValueField.NONNEGATIVE);

    protected ValueField decimalField = new
	ValueField((double) decimal, ValueField.INTEGER | ValueField.POSITIVE);

    protected BCheckBox compressBox = new
	BCheckBox(Translate.text("fileCompression"), false);

    protected BProgressBar prog = new BProgressBar();
    protected BButton browseButton = Translate.button("browse", this,
						      "showBrowseWindow");

    protected BButton ok = Translate.button("Ok", this, "ok");
    protected BButton can = Translate.button("Cancel", this, "cancel");
    protected BButton fix = Translate.button("Fix", this, "fix");

    protected BFileChooser browser = new BFileChooser(BFileChooser.SAVE_FILE,
					    Translate.text("path"));

    protected BTextArea messageArea = new BTextArea(10, 45);
    protected BScrollPane scroll = new
	BScrollPane(BScrollPane.SCROLLBAR_AS_NEEDED,
		    BScrollPane.SCROLLBAR_AS_NEEDED);

    protected NumberFormat nf = NumberFormat.getInstance(Locale.US);

    protected BComboBox meshChoice = new BComboBox();

    /* internal logic - private */
    private static final int WORD = StreamTokenizer.TT_WORD;
    private static final int NUM = StreamTokenizer.TT_NUMBER;
    private static final int EOL = StreamTokenizer.TT_EOL;
    private static final int CR = '\r';
    private static final int EOF = StreamTokenizer.TT_EOF;

    private static final int ASCII	= 0;
    private static final int BINARY	= 1;
    private static final int AUTO	= 2;
    private static final int COMPRESSED	= 128;
    
    private static final String QUOTES = "\"'";

    private static final String yesno[] = new String [] {
	Translate.text("Yes"), Translate.text("No")
    };

    private static final String gostop[] = new String [] {
	Translate.text("Continue"), Translate.text("Cancel")
    };

    /**
     *  process messages sent to our Plugin interface
     */
    public void processMessage(int msg, Object[] args)
    {
	switch (msg) {
	case Plugin.APPLICATION_STARTING:
	    rendermode = ModellingApp.getPreferences().getDefaultDisplayMode();
	    break;

	case Plugin.SCENE_WINDOW_CREATED:
	    // handle the newly created window
	    LayoutWindow lw = (LayoutWindow) args[0];

	    if (theScene == null || lw.getScene() != theScene
		|| theScene.getNumObjects() < 3)
		break;

	    //lw.setModified();	// mark the scene as modified
	    ModellingApp.getPreferences().setDefaultDisplayMode(rendermode);

	    if (frameBox.getState() == false) break;

	    // now adjust the scene camera to frame the object
	    ObjectInfo cam = theScene.getObject(0);
	    ObjectInfo obj = theScene.getObject(2);

	    BoundingBox bb = obj.getBounds()
		.transformAndOutset(obj.coords.fromLocal());

	    Vec3 size = bb.getSize();
	    //bb.outset(Math.max(size.x, size.y)*0.05);	// grow by 5%

	    double fov = ((SceneCamera) cam.object).getFieldOfView();
	    Vec3 pos = cam.coords.getOrigin();

	    //System.out.println("fov=" + fov + "; size=" + size + "; bb=" + bb +
	    //   "; pos=" + pos);

	    // set the camera's distance so the entire object is visible
	    pos.z = Math.max(size.x, size.y)/Math.tan(fov*Math.PI/180.0);
	    cam.coords.setOrigin(pos);
	
	    //System.out.println("set cam to: " + cam.coords.getOrigin());

	    theScene.setSelection(2);
	    lw.frameWithCameraCommand(true);	// frame the scene
	    theScene.clearSelection();

	    theScene = null;

	    break;
	}
    }

    /**
     *  get the translator name (used as description)
     */
    public String getName()
    { return "STL (.stl)"; }

    /**
     *  return true if this translator can import
     */
    public boolean canImport()
    { return true; }
  
    /**
     *  return true if this translaotor can export
     */
    public boolean canExport()
    { return true; }

    /**
     *  perform an import from a file
     */  
    public void importFile(BFrame frame)
    {
	parent = frame;

	// create a new scene for this import
	theScene = createScene();

	runTask(IMPORT);

	// change the default render mode
	ApplicationPreferences prefs = ModellingApp.getPreferences();
	rendermode = prefs.getDefaultDisplayMode();

	// change the mode - reset later because newWindow() is asynchronous
	prefs.setDefaultDisplayMode(ViewerCanvas.RENDER_FLAT);

	ModellingApp.newWindow(theScene);
    }

    /**
     *  perform an export to a file
     */  
    public void exportFile(BFrame frame, Scene scene)
    {
	//System.out.println("export to file");
	parent = frame;
	theScene = scene;

	if (theScene.getNumObjects() == 0) {
	    new BStandardDialog("", new String [] {
		Translate.text("errorExportingScene"),
		Translate.text("The scene is empty")
	    }, BStandardDialog.ERROR).showMessageDialog(parent);

	    return;
	}

	if (theList == null)
	    theList = new ArrayList(theScene.getNumObjects()*2);
	else theList.clear();

	int[] sel = theScene.getSelection();
	
	if (sel != null && sel.length > 0) {
	    for (int x = 0; x < sel.length; x++)
		addObject(theScene.getObject(sel[x]), theList, theScene);
	}
	else {
	    int max = theScene.getNumObjects();
	    for (int x = 0; x < max; x++)
		addObject(theScene.getObject(x), theList, theScene);
	}

	if (message == null) message = new CharArrayWriter(1024*16);
	else message.reset();

	ObjectInfo info = null;
	TriangleMesh mesh = null;

	int error = 0;

	// validate the mesh(es) before we start
	int max = theList.size();
	errs = new boolean[max];
	for (int x = 0; x < max; x++) {
	    info = (ObjectInfo) theList.get(x);
	    mesh = info.object.convertToTriangleMesh(surfError);

	    if (mesh == null) continue;

	    try {
		message.write("\n" + info.name + ": ");
		if (!validate(mesh, message)) {
		    error++;
		    errs[x] = true;
		}
		else {
		    message.write(Translate.text("ok"));
		    errs[x] = false;
		}
	    } catch (IOException e) {}
	}

	if (error == 0) message.reset();

	/*
	 *  NTJ: consolidate error handling into GUI
	if (error > 0) {
	    int choice = new
		BStandardDialog(Translate.text("validationError"),
				message.toString(),
				BStandardDialog.QUESTION).
		showOptionDialog(parent, gostop, gostop[1]);

	    if (choice==1) return;
	}
	*/

	runTask(EXPORT);
    }

    /**
     *  attempt to import from the specified file
     */
    public Scene importSTL(File f)
	throws IOException
    {
	boolean compressed = false;

	int type = findFileType(f);
	if (type >= COMPRESSED) {
	    compressed = true;
	    type -= COMPRESSED;
	}
	else compressed = false;

	InputStream in = new FileInputStream(f);
	if (compressed) in = new GZIPInputStream(in);

	Scene scene = createScene();

	if (type == BINARY)
	    importStream(scene, in);
	else
	    importStream(scene, new InputStreamReader(in));

	return scene;
    } 

    /**
     *  export the Scene (in ASCII STL) to the specified stream.
     */
    public void exportStream(List list, PrintWriter out)
	throws IOException, InterruptedException
    {
	System.out.println("export to stream");

	// find the bounds, and set the transform
	findBounds(list);

	ObjectInfo info = null;
	TriangleMesh mesh = null;
	MeshVertex vert[] = null;
	TriangleMesh.Face face[] = null;
	Mat4 trans = null;
	Vec3 v;
	double length;
	String name = null;

	nf.setMaximumFractionDigits(decimal);
	nf.setGroupingUsed(false);

	// Write the objects in the scene.
	int max = list.size();
	for (int x = 0; x < max; x++) {
	    info = (ObjectInfo) list.get(x);
	    mesh = info.object.convertToTriangleMesh(surfError);

	    if (mesh == null) continue;

	    if (name == null) {
		// Write the header information.
		name = Util.translate(info.name, " ", "_");
		out.print("solid \"");
		out.print(name);
		out.print("\"; Produced by Art of Illusion ");
		out.print(ModellingApp.VERSION);
		out.print(", ");
		out.print(new Date().toString());
	    }

	    vert = mesh.getVertices();
	    face = mesh.getFaces();

	    trans = info.coords.fromLocal().times(move);

	    // print all faces to file
	    for (int i = 0; i < face.length; i++) {
		v = vert[face[i].v2].r.minus(vert[face[i].v1].r)
		    .cross(vert[face[i].v3].r.minus(vert[face[i].v1].r));

		length = v.length();
		if (length > 0.0) v.scale(1.0/length);

		//System.out.println("STL; norm before trans=" + v);
		v = info.coords.fromLocal().timesDirection(v);
		
		writeVec(out, "\nfacet normal ", v, null);
		out.print("\n  outer loop");
		writeVec(out, "\n    vertex ", vert[face[i].v1].r, trans);
		writeVec(out, "\n    vertex ", vert[face[i].v2].r, trans);
		writeVec(out, "\n    vertex ", vert[face[i].v3].r, trans);
		
		out.print("\n  endloop\nendfacet");
	    }
	}

	out.println("\nendsolid");
	out.flush();
	
	System.out.println("stream complete");
    }

    /**
     *  export the Scene (in BINARY STL) to the specified stream.
     */
    public void exportStream(List list, OutputStream os)
	throws IOException, InterruptedException
    {
	System.out.println("export to stream");

	// find the bounds, and set the transform
	findBounds(list);

	// binary STL is always little-endian
	DataOutput out = new LittleEndianDataOutputStream(os);

	nf.setMaximumFractionDigits(decimal);
	nf.setGroupingUsed(false);

	ObjectInfo info = null;
	TriangleMesh mesh = null;
	MeshVertex vert[] = null;
	TriangleMesh.Face face[] = null;
	Vec3 v;
	Mat4 trans = null;
	double length;
	String hdr = null;

	for (int x = list.size()-1; x >= 0; x--) {
	    info = (ObjectInfo) list.get(x);
	    mesh = info.object.convertToTriangleMesh(surfError);

	    if (mesh == null) continue;

	    if (hdr == null) {
		// generate 80 bytes of header text
		hdr = "\"" + Util.translate(info.name, " ", "_") +
		    "\"; Produced by Art of Illusion " + ModellingApp.VERSION +
		    ", " + new Date().toString() +
		"                                                            ";

		out.writeBytes(hdr.substring(0, 80));
		out.writeInt(faces);
	    }

	    vert = mesh.getVertices();
	    face = mesh.getFaces();

	    trans = info.coords.fromLocal().times(move);

	    // print all faces to file
	    for (int i = 0; i < face.length; i++) {
		v = vert[face[i].v2].r.minus(vert[face[i].v1].r).cross(vert[face[i].v3].r.minus(vert[face[i].v1].r));

		length = v.length();
		if (length > 0.0) v.scale(1.0/length);

		//System.out.println("STL; norm before trans=" + v);
		v = info.coords.fromLocal().timesDirection(v);

		writeVec(out, v, null);
		writeVec(out, vert[face[i].v1].r, trans);
		writeVec(out, vert[face[i].v2].r, trans);
		writeVec(out, vert[face[i].v3].r, trans);

		// two byte padding (ho-hum...)
		out.writeBytes("  ");
	    }
	}

	System.out.println("stream complete");
    }

    /**
     *  Import a new Scene object from an (ASCII STL) stream.
     */
    public Scene importStream(Scene scene, Reader in)
	throws IOException
    {
	ObjectInfo info = null;
	CoordinateSystem coords;

	int x, vertno=0, face[], faceArray[][] = new int[0][0];
	String s, name;
	Vec3 centre, vert, norm, calcNorm, vertArray[], v1, v2, v3;
	Integer index;
	ArrayList vlist = new ArrayList(1024*128);
	ArrayList flist = new ArrayList(1024*64);
	HashMap vmap = new HashMap(1024*128);

	name = null;
	norm = new Vec3();
	vert = new Vec3();
	vertArray = new Vec3[0];
	faceArray = new int[0][0];
	face = new int[3];
	BoundingBox bounds = new BoundingBox(vert, vert);

	// ensure we have a scene to import into
	if (scene == null) {
	    scene = createScene();

	    // only copy to theScene if we created the scene
	    theScene = scene;
	}

	rendermode = ModellingApp.getPreferences().getDefaultDisplayMode();

	if (message == null) message = new CharArrayWriter(1024*16);
	else message.reset();

	int lineno = 1;

	try {
	    StreamTokenizer token = new StreamTokenizer(in);

	    // set up the tokenizer
	    token.resetSyntax();
	    token.eolIsSignificant(true);

	    token.wordChars('!', '~'); //Classic 7-bit ASCII printable range, not including space
	    token.wordChars(0x00A1, 0x00FF); // ISO-8259-1 extended ASCII/US-ASCII...
            token.ordinaryChar(0x00AD); //Except soft-hyphen (for dynamically-broken lines)
            token.commentChar(';'); //From semicolon to EOL, comment metadata is ignored.

	    token.whitespaceChars(' ', ' '); //space
            token.whitespaceChars(0x00A0, 0x00A0); //non-breaking space
	    token.whitespaceChars('\t', '\t');

	    int count = 0;
	    boolean more = true;
	    while (more) {

		// process the next token
		switch(token.nextToken()) {
		case WORD:
		    // keyword
		    s = token.sval;

		    //System.out.println("STL: token=" + s);

		    if (s.equals("solid")) {
			if (count > 0 && info == null)
			    throw new Ex("unmatched \"solid\" keyword ");

			info = null;
			vlist.clear();
			flist.clear();
			vmap.clear();

			if (token.nextToken() == WORD) {
			    s = token.sval;

			    if (QUOTES.indexOf(s.charAt(0)) >= 0
				&& s.charAt(s.length()-1) == s.charAt(0)) {
				
				name = s.substring(1, s.length()-1);
			    }
			}
			
			if (name == null || name.length() == 0)
			    name = "Object-" + count;

			System.out.println("STL: name=" + name);

			while (token.ttype != EOL && token.nextToken() != CR) {
			    if (token.ttype == EOF) more = false;
			    //System.out.print(".");
			}

			//System.out.println();
		    }

		    else if (s.equals("endsolid")) {
			if (info != null)
			    throw new Ex("missing \"solid\" keyword");

			count++;

			faceArray = (int[][]) flist.toArray(faceArray);
			vertArray = (Vec3[]) vlist.toArray(vertArray);

			centre = bounds.getCenter();
			coords = new
			    CoordinateSystem(centre, Vec3.vz(), Vec3.vy());

			if (centered) {
			    double dx = (centre.x > 0.0 ? -centre.x : 0.0);
			    double dy = (centre.y > 0.0 ? -centre.y : 0.0);
			    double dz = (centre.z > 0.0 ? -centre.z : 0.0);
			    coords.setOrigin(new Vec3(dx, dy, dz));
			}

			TriangleMesh mesh = new
			    TriangleMesh(vertArray, faceArray);

			validate(mesh, message);

			info = new ObjectInfo(mesh, coords, name);

			info.addTrack(new PositionTrack(info), 0);
			info.addTrack(new RotationTrack(info), 1);

			scene.addObject(info, null);
		    }

		    else if (s.equals("facet")) {
			if (info != null)
			    throw new Ex("missing \"solid\" keyword");

			if (token.nextToken() == WORD
			    && token.sval.equals("normal")) {

			    readVec(token, norm);
			}
			else 
			    throw new Ex("missing \"normal\" keyword");
		    }

		    else if (s.equals("endfacet")) {
			if (info != null)
			    throw new Ex("missing \"solid\" keyword");

			if (vertno != 3)
			    throw new Ex("incorrect number of vertices: " +
					 vertno);
		    }

		    else if (s.equals("outer")) {
			if (info != null)
			    throw new Ex("missing \"solid\" keyword");

			if (token.nextToken() != WORD
			    || !token.sval.equals("loop"))
			    throw new Ex("missing \"loop\" keyword");

			vertno = 0;
			face = new int[3];
		    }

		    else if (s.equals("endloop")) {
			if (info != null)
			    throw new Ex("missing \"solid\" keyword");

			v1 = (Vec3) vlist.get(face[0]);
			v2 = (Vec3) vlist.get(face[1]);
			v3 = (Vec3) vlist.get(face[2]);

			calcNorm = v2.minus(v1).cross(v3.minus(v1));
			double length = calcNorm.length();
			if (length > 0.0) calcNorm.scale(1.0/length);

			if (!calcNorm.equals(norm)
			    && Math.abs(calcNorm.minus(norm).length())
			    > tolerance)

			    message.write("\nWarning (line " +
				      lineno +
					"): facet normal mismatch. read: " +
					norm + "; calculated: " + calcNorm);
			    
			flist.add(face);
		    }

		    else if (s.equals("vertex")) {
			if (info != null)
			    throw new Ex("missing \"solid\" keyword");

			if (vertno >= 3)
			    throw new Ex("too many vertices: " + vertno);

			readVec(token, vert);
			index = (Integer) vmap.get(vert.toString());

			if (index != null) {
			    face[vertno++] = index.intValue();
			}
			else {
			    if (vert.x < bounds.minx) bounds.minx = vert.x;
			    if (vert.x > bounds.maxx) bounds.maxx = vert.x;
			    if (vert.y < bounds.miny) bounds.miny = vert.y;
			    if (vert.y > bounds.maxy) bounds.maxy = vert.y;
			    if (vert.z < bounds.minz) bounds.minz = vert.z;
			    if (vert.z > bounds.maxz) bounds.maxz = vert.z;

			    x = vlist.size();
			    face[vertno++] = x;
			    vmap.put(vert.toString(), new Integer(x));
			    vlist.add(vert);

			    vert = new Vec3();	// new Vec3
			}
		    }
		    break;

		case EOL:
		case CR:
		    //System.out.println("STL: next line...");
		    lineno++;

		    // check for eol pairs (\r\n)
		    if (token.nextToken() != EOL && token.ttype != CR)
			token.pushBack();
		    break;

		case EOF:
		    more = false;
		    break;

		default:
		    throw new Ex("invalid token (type): " + token.ttype);
		}
	    }

	    if (count == 0)
		message.write("\nNo object created");
	}
	catch (Exception e) {
	    new BStandardDialog("", new String [] {
		Translate.text("errorLoadingFile"),
		"(at line " + lineno + ")\n" + e.getMessage()
	    }, BStandardDialog.ERROR).showMessageDialog(parent);
	    return null;
	}

	return scene;
    }

    /**
     *  Import a new Scene object from a (BINARY STL) stream
     */
    public Scene importStream(Scene scene, InputStream is)
	throws IOException
    {
	ObjectInfo info = null;
	CoordinateSystem coords;
	int x, vertno=0, face[], faceArray[][] = new int[0][0], len;
	String name;
	Vec3 centre, vert, norm, calcNorm, vertArray[], v1, v2, v3;
	Integer index;
	ArrayList vlist = new ArrayList(1024*128);
	ArrayList flist = new ArrayList(1024*64);
	HashMap vmap = new HashMap(1024*128);
	byte[] buff = new byte[80];

	name = null;
	norm = new Vec3();
	vert = new Vec3();
	vertArray = new Vec3[0];
	faceArray = new int[0][0];
	face = new int[3];
	BoundingBox bounds = new BoundingBox(vert, vert);

	// ensure we have a Scene to import into
	if (scene == null) {
	    scene = createScene();

	    // only copy to theScene if we created the scene
	    theScene = scene;
	}

	rendermode = ModellingApp.getPreferences().getDefaultDisplayMode();

	if (message == null) message = new CharArrayWriter(1024*16);
	else message.reset();

	// binary STL is always little-endian
	DataInput in = new LittleEndianDataInputStream(is);

	try {

	    int count = 0;
	    boolean more = true;
	    //while (more) {

	    //System.out.println("STL: count=" + count);

		try {
		    in.readFully(buff);
		} catch (EOFException e) {
		    System.out.println("STL: " + e);
		    return scene;
		}

		int pos = 0;
		while (pos < 80 && buff[pos] == ' ') pos++;

		if (QUOTES.indexOf(buff[pos]) >= 0) {
		    int epos = pos;
		    while (epos < 80 && buff[epos] != buff[pos]) epos++;

		    if (pos < 80 && epos > pos && epos < 80)
			name = new String(buff, pos+1, epos-pos-2);
		}
		if (name == null || name.length() == 0)
		    name = "Object-" + count;

		System.out.println("STL: name=" + name);

		info = null;
		vlist.clear();
		flist.clear();
		vmap.clear();

		// get the number of faces
		len = in.readInt();

		if (len <= 0) {
		    message.write("No faces defined");
		    //break;
		    return scene;
		}

		System.out.println("STL; faces=" + len);

		count++;
			
		// read every face
		for (int faceno = 0; faceno < len; faceno++) {

		    //System.out.println("STL: face#" + faceno);

		    face = new int[3];

		    // read the normal
		    readVec(in, norm);

		    // read the 3 vertices
		    for (vertno = 0; vertno < 3; vertno++) {
			readVec(in, vert);
			index = (Integer) vmap.get(vert.toString());

			if (index != null) {
			    face[vertno] = index.intValue();
			}
			else {
			    if (vert.x < bounds.minx) bounds.minx = vert.x;
			    if (vert.x > bounds.maxx) bounds.maxx = vert.x;
			    if (vert.y < bounds.miny) bounds.miny = vert.y;
			    if (vert.y > bounds.maxy) bounds.maxy = vert.y;
			    if (vert.z < bounds.minz) bounds.minz = vert.z;
			    if (vert.z > bounds.maxz) bounds.maxz = vert.z;

			    x = vlist.size();
			    face[vertno] = x;
			    vmap.put(vert.toString(), new Integer(x));
			    vlist.add(vert);

			    vert = new Vec3();	// new Vec3
			}
		    }

		    // read padding
		    in.skipBytes(2);

		    // calculate the normal, and compare
		    v1 = (Vec3) vlist.get(face[0]);
		    v2 = (Vec3) vlist.get(face[1]);
		    v3 = (Vec3) vlist.get(face[2]);

		    calcNorm = v2.minus(v1).cross(v3.minus(v1));
		    double length = calcNorm.length();
		    if (length > 0.0) calcNorm.scale(1.0/length);

		    if (!calcNorm.equals(norm)
			&& Math.abs(calcNorm.minus(norm).length())
			> tolerance)

			message.write("\nWarning: facet normal mismatch." +
				      "read: " + norm + 
				      "; calculated: " + calcNorm);
			    
		    flist.add(face);
		}

		//System.out.println("STL: building mesh");

		// build the mesh
		faceArray = (int[][]) flist.toArray(faceArray);
		vertArray = (Vec3[]) vlist.toArray(vertArray);

		centre = bounds.getCenter();
		coords = new CoordinateSystem(centre, Vec3.vz(), Vec3.vy());

		if (centered) {
		    double dx = (centre.x > 0.0 ? -centre.x : 0.0);
		    double dy = (centre.y > 0.0 ? -centre.y : 0.0);
		    double dz = (centre.z > 0.0 ? -centre.z : 0.0);
		    coords.setOrigin(new Vec3(dx, dy, dz));
		}

		TriangleMesh mesh = new TriangleMesh(vertArray, faceArray);

		validate(mesh, message);

		info = new ObjectInfo(mesh, coords, name);

		info.addTrack(new PositionTrack(info), 0);
		info.addTrack(new RotationTrack(info), 1);

		scene.addObject(info, null);

		//System.out.println("STL: new object added");
		//}

	    if (count == 0)
		message.write("\nNo object created");
	}
	catch (Exception e) {
	    new BStandardDialog("", new String [] {
		Translate.text("errorLoadingFile"), e.toString()
	    }, BStandardDialog.ERROR).showMessageDialog(parent);

	    return null;
	}

	return scene;
    }

    /**
     *  add an object to a list.
     *
     *  Unwraps ObjectCollection objects, so each contained element is added
     *  separately.
     */
    public static void addObject(ObjectInfo obj, List list, Scene scene)
    {
	Object3D obj3d = obj.object;
	if (obj3d instanceof ObjectCollection) {
	    Enumeration iter = ((ObjectCollection) obj3d)
		.getObjects(obj, false, scene);

	    while (iter.hasMoreElements())
		addObject((ObjectInfo) iter.nextElement(), list, scene);
	}
	else list.add(obj);
    }

    /**
     *  runs the specified task in a GUI.
     */
    public void runTask(int action)
    {
	name = "Untitled.stl";
	pathField.setText(name);
	if (path == null) path = ModellingApp.currentDirectory;
	prog.setShowProgressText(true);

	this.action = action;
	error = null;

	// copy existing values into the GUI
	ignoreBox.setState(ignoreError);
	centerBox.setState(centered);
	frameBox.setState(frame);
	errorField.setValue(tolerance);
	surfErrorField.setValue(surfError);
	decimalField.setValue(decimal);

	can.setText(Translate.text("cancel"));
	ok.setEnabled(true);
	ok.setVisible(true);

	pathField.setEnabled(true);
	typeChoice.setEnabled(true);
	browseButton.setEnabled(true);
	errorField.setEnabled(true);
	surfErrorField.setEnabled(true);
	decimalField.setEnabled(true);
	centerBox.setEnabled(true);
	frameBox.setEnabled(true);
	ignoreBox.setEnabled(true);
	compressBox.setEnabled(true);
	prog.setEnabled(false);

	// Show the window.
	RowContainer path = new RowContainer();
	path.add(new BLabel(Translate.text("path")));
	path.add(pathField);
	path.add(browseButton);

	RowContainer type = new RowContainer();
	type.add(new BLabel(Translate.text("fileType")));
	type.add(typeChoice);

	RowContainer decRow = new RowContainer();
	decRow.add(Translate.label("maxDecimalDigits"));
	decRow.add(decimalField);

	RowContainer butts = new RowContainer();
	butts.add(ok);
	butts.add(can);

	messageArea.setEditable(false);
	scroll.remove(messageArea);
	//scroll.setVisible(false);

	ColumnContainer col = new ColumnContainer();
	col.add(path);
	col.add(type);
	col.add(decRow);

	RowContainer errRow = new RowContainer();
	if (action == EXPORT) {
	    browser.setMode(BFileChooser.SAVE_FILE);

	    if (typeChoice.getItemCount() > AUTO) typeChoice.remove(AUTO);
	    typeChoice.setSelectedIndex(ASCII);

	    errRow.add(Translate.label("maxSurfaceError"));
	    errRow.add(surfErrorField);
	    col.add(errRow);

	    // display any initial warning messages
	    if (message.size() > 0) {
		messageArea.setText(message.toString());
		scroll.setContent(messageArea);

		if (solidPlugin == null) {
		    Plugin[] plugin = ModellingApp.getPlugins();

		    for (int x = 0; x < plugin.length; x++) {
			if (plugin[x].getClass().getName()
			    .equals("artofillusion.plugin.SolidTool")) {

			    solidPlugin = plugin[x];
			    break;
			}
		    }
		}

		if (solidPlugin != null) butts.add(fix);
	    }
	}
	if (action == IMPORT) {
	    browser.setMode(BFileChooser.OPEN_FILE);

	    if (typeChoice.getItemCount() <= AUTO)
		typeChoice.add(Translate.text("Auto"));

	    typeChoice.setSelectedIndex(AUTO);

	    errRow.add(Translate.label("errorTolerance"));
	    errRow.add(errorField);

	    col.add(errRow);

	    RowContainer viewRow = new RowContainer();
	    viewRow.add(centerBox);
	    viewRow.add(frameBox);
	    col.add(viewRow);
	}

	col.add(ignoreBox);
	col.add(compressBox);
	col.add(prog);
	col.add(scroll);
	col.add(butts);

	dlg = new BDialog(parent, actionName[action], true);
	dlg.setContent(col);
	dlg.pack();
	UIUtilities.centerDialog(dlg, parent);

	// returns when dialog is exited
	dlg.setVisible(true);
			  
	prog.setIndeterminate(false);

	if (dlg != null) dlg.dispose();
	dlg = null;

	if (error !=  null) {
	    System.out.println("\n\nException: " + error + "\n\n");
	    error.printStackTrace(System.out);
	}
    }

    /**
     *  cancel the current task (import or export)
     */
    public void cancel()
    {
	if (thread != null) {
	    System.out.println("Cancelled: " + thread.getName());
	    thread.interrupt();
	}
	thread = null;

	if (dlg != null) {
	    dlg.setVisible(false);
	    dlg.dispose();
	}
	dlg = null;
    }

    /**
     *  fix the object
     */
    public void fix()
    {
	int x, y;

	// select the mesh to fix
	meshChoice.removeAll();
	int max = theList.size();
	String item;
	for (x = 0; x < max; x++) {
	    if (errs[x]) {
		meshChoice.add(((ObjectInfo) theList.get(x)).name);
	    }
	}

	ComponentsDialog choose = new
	    ComponentsDialog(parent, Translate.text("Select Object"),
			    new Widget[] { meshChoice },
			    new String[] { "Object" });

	if (choose.clickedOk()) {
	    int idx = meshChoice.getSelectedIndex();

	    Runnable onClose = new Runnable() {
		    public void run()
		    {}
		};

	    Object[] args = new Object[] {
		parent, (ObjectInfo) theList.get(idx), onClose, message
	    };

	    dlg.setVisible(false);
	    dlg.dispose();

	    solidPlugin.processMessage(-1, args);
	}
    }

    /**
     *  display a window to browse for a a file
     */
    private void showBrowseWindow(WidgetEvent ev)
    {
	WindowWidget parent = UIUtilities.findWindow(ev.getWidget());

	name = pathField.getText();
	File file = (path != null ? new File(path, name) : new File(name));
	browser.setDirectory(file.getParentFile());
	browser.setSelectedFile(file);

	boolean ok = browser.showDialog(parent);
	if (ok) {
	    file = browser.getSelectedFile();

	    path = file.getParentFile().getAbsolutePath();
	    name = file.getName();
	    pathField.setText(name);
	}
    }

    /**
     *  user clicked "ok" in the GUI - run the task in a thread.
     */
    public void ok()
    {
	name = pathField.getText();

	// separate out the pathname
	int pos = name.lastIndexOf(slash);
	if (pos > 0) {
	    path = name.substring(0, pos);
	    name = name.substring(pos+1);
	}
	else if (path == null || path.length() == 0)
	    path = ModellingApp.currentDirectory;

	File file = new File(path, name);

	ignoreError = ignoreBox.getState();
	centered = centerBox.getState();
	frame = frameBox.getState();
	tolerance = errorField.getValue();
	surfError = surfErrorField.getValue();
	decimal = (int) decimalField.getValue();

	if (action == EXPORT && file.exists()) {
	    int choice = new
		BStandardDialog(Translate.text("fileExists"),
				Translate.text("overwriteFile",
					       file.getName()),
				BStandardDialog.QUESTION).
		showOptionDialog(parent, yesno, yesno[1]);

	    if (choice==1) return;
	}

	else if (action == IMPORT && !file.exists()) {
	    new BStandardDialog("", new String [] {
		Translate.text("fileNotFound"),
		Translate.text("file " + file.getName() + " does not exist")
	    }, BStandardDialog.ERROR).showMessageDialog(parent);

	    return;
	}

	boolean compress = compressBox.getState();
	int type = typeChoice.getSelectedIndex();

	// determine type automatically
	if (type == AUTO) {
	    try {
		type = findFileType(file);

		if (type >= COMPRESSED) {
		    compress = true;
		    type -= COMPRESSED;
		}
	    } catch (IOException e) {
		new BStandardDialog("", new String [] {
		    Translate.text("fileError"),
		    Translate.text("Error while determining type of: " +
				   file.getName())
		}, BStandardDialog.ERROR).showMessageDialog(parent);

		return;
	    }
	}

	ok.setEnabled(false);
	pathField.setEnabled(false);
	browseButton.setEnabled(false);
	errorField.setEnabled(false);
	surfErrorField.setEnabled(false);
	decimalField.setEnabled(false);
	centerBox.setEnabled(false);
	frameBox.setEnabled(false);
	ignoreBox.setEnabled(false);
	compressBox.setEnabled(false);
	typeChoice.setEnabled(false);
	typeChoice.setSelectedIndex(type);

	prog.setEnabled(true);

	prog.setIndeterminate(true);
	prog.setProgressText(Translate.text(actionName[action]));

	if (message != null) message.reset();

	//System.out.println("creating task thread...");
	try {
	    switch (action) {
	    case EXPORT:
		out = new FileOutputStream(file);
		if (compress) out = new GZIPOutputStream(out);
		out = new BufferedOutputStream(out);

		thread = new Thread(new Runnable() {
			public void run()
			{
			    try {
				if (typeChoice.getSelectedIndex() == BINARY)
				    exportStream(theList, out);
				else
				    exportStream(theList,
						 new PrintWriter(out));

				if (thread == null) return;

				// display any warning messages
				if (message.size() > 0) {
				    messageArea.setText(message.toString());
				    scroll.setContent(messageArea);

				    ok.setVisible(false);
				    can.setText(Translate.text("Done"));
				    prog.setIndeterminate(false);

				    dlg.pack();
				}
				else dlg.dispose();
			    } catch (Exception e) { error = e; }
			    finally {
				try {
				    if (out != null) {
					out.flush();
					out.close();
				    }
				} catch (Exception e) {}
			    }
			}
		    });

		thread.start();
		break;

	    case IMPORT:
		in = new FileInputStream(file);
		if (compress) in = new GZIPInputStream(in);
		in = new BufferedInputStream(in);
		
		thread = new Thread(new Runnable() {
			public void run()
			{
			    try {
				if (typeChoice.getSelectedIndex() == BINARY)
				    importStream(theScene, in);
				else
				    importStream(theScene, new
						 InputStreamReader(in));

				if (thread == null) return;

				// display any warning messages
				if (message.size() > 0) {
				    messageArea.setText(message.toString());
				    scroll.setContent(messageArea);

				    ok.setVisible(false);
				    can.setText(Translate.text("Done"));
				    prog.setIndeterminate(false);

				    dlg.pack();
				}
				else dlg.dispose();
			    } catch (Exception e) { error = e; }
			    finally {
				try {
				    if (in != null) {
					in.close();
				    }
				} catch (Exception e) {}
			    }
			}
		    });

		thread.start();
		break;
	    }
	}
	catch (Exception e) {
	    System.out.println("\n\nException: " + e + "\n\n");
	    e.printStackTrace(System.out);

	    new BStandardDialog("", new String [] {
		Translate.text("errorTranslating"),
		Translate.text(e.toString())
	    }, BStandardDialog.ERROR).showMessageDialog(parent);

	    dlg.dispose();
	    dlg = null;
	}

	//System.out.println("task launched");
    }

    /**
     *  find the bounds of the list of meshes
     */
    protected void findBounds(List list)
    {
	BoundingBox bb = null;
	double dx=0, dy=0, dz=0;
	ObjectInfo info;
	TriangleMesh mesh = null;

	faces = 0;

	int max = list.size();
	for (int x = 0; x < max; x++) {
	    info = (ObjectInfo) list.get(x);

	    bb = info.getBounds().transformAndOutset(info.coords.fromLocal());

	    //System.out.println("export: bounds=" + bb);

	    if (bb.minx < dx) dx = bb.minx;
	    if (bb.miny < dy) dy = bb.miny;
	    if (bb.minz < dz) dz = bb.minz;

	    mesh = info.object.convertToTriangleMesh(surfError);
	    if (mesh != null) faces += mesh.getFaces().length;
	}

	// calculate the transform to ensure no negative numbers...
	dx = (dx < 0 ? -dx : 0.0);
	dy = (dy < 0 ? -dy : 0.0);
	dz = (dz < 0 ? -dz : 0.0);
	move = Mat4.translation(dx, dy, dz);
    }

    /**
     *  create a new (empty) scene
     */
    public Scene createScene()
    {
	// Create a scene to add objects to.
	Scene result = new Scene();
	Vec3 camPos = new Vec3(0.0, 0.0, ModellingApp.DIST_TO_SCREEN);
	CoordinateSystem coords = new
	    CoordinateSystem(camPos, new Vec3(0.0, 0.0, -1.0), Vec3.vy());

	ObjectInfo cam = new ObjectInfo(new SceneCamera(), coords, "Camera 1");

	cam.addTrack(new PositionTrack(cam), 0);
	cam.addTrack(new RotationTrack(cam), 1);
	result.addObject(cam, null);

	ObjectInfo info = new
	    ObjectInfo(new DirectionalLight(new RGBColor(1.0f, 1.0f, 1.0f),
					    0.8f), coords.duplicate(),
		       "Light 1");

	info.addTrack(new PositionTrack(info), 0);
	info.addTrack(new RotationTrack(info), 1);
	result.addObject(info, null);

	return result;
    }

    /**
     *  validate that a trianglemesh is consistent with STL
     */
    public static boolean validate(TriangleMesh mesh, Writer err)
    {
	boolean valid = true;

	try {
	    // number of faces must be even
	    int fc = mesh.getFaces().length;
	    if (((fc/2) * 2) != fc) {
		valid = false;
		err.write("\nvalidate: number of faces (" + fc +
			  ") is not even");
	    }

	    // number of edges must be a multiple of 3
	    int ec = mesh.getEdges().length;
	    if (((ec/3) * 3) != ec) {
		valid = false;
		err.write("\nvalidate: number of edges (" + ec +
			  ") is not a multiple of 3");
	    }

	    // number of edges must be 2/3 number of faces
	    if (2*ec != 3*fc) {
		valid = false;
		err.write("\nvalidate: number of faces (" + fc +
			  ") is not 2/3 the number of edges (" + ec + ")");
	    }

	    // calculate the number of holes we've found...
	    int vc = mesh.getVertices().length;
	    int holes = -((fc - ec + vc - 2) / 2);
	    if (holes > 0)
		err.write("\nvalidate: calculated holes (using Euler's) = " +
			  holes);

	    System.out.println("validate: fc=" +fc+ "; ec=" +ec+ "; vc=" +vc);
	} catch (IOException e) {
	    System.out.println("Exception in validate: " + e);
	}

	return valid;
    }

    /**
     *  try to guess the file type
     */
    public int findFileType(File file)
	throws IOException
    {
	int result = 0;
	InputStream in = null;
	LittleEndianDataInputStream preread = null;

	boolean compressed = true;

	try {
	    in = new FileInputStream(file);
	    in = new GZIPInputStream(in);

	    in.read();
	} catch (IOException e) {
	    compressed = false;
	}

	try {
	    result = (compressed ? COMPRESSED : 0);

	    in = new FileInputStream(file);
	    if (compressed) in = new GZIPInputStream(in);

	    preread = new LittleEndianDataInputStream(in);

	    preread.skip(80);	// skip header
	    int len = preread.readInt();	// get number of faces

	    System.out.println("preread: faces=" + len + "; length=" +
			       file.length() + "; calc len=" +
			       (84 + (len*50)));

	    return result +(file.length() == 84 + (len * 50) ? BINARY : ASCII);
	}	
	finally {
	    try {
		if (preread != null) preread.close();
	    } catch (Exception e) {}
	}
    }

    /**
     *  write a 3D vector to an (ASCII) STL stream.
     *
     *  @param out the printwriter to write to
     *  @param prefix the String prefix to write before the vector
     *  @param p the Vec3 point to print
     *  @param trans the Mat4 transformation to apply to p before printing
     *		(may be <i>null</i>).
     */
    protected void writeVec(PrintWriter out, String prefix, Vec3 p,
				   Mat4 trans)
    {
	if (trans != null) p = trans.times(p);

	out.print(prefix);
	out.print(" ");

	out.print(nf.format(p.x));
	out.print(" ");
	out.print(nf.format(p.y));
	out.print(" ");
	out.print(nf.format(p.z));
    }

    /**
     *  write a 3D vector to a (binary) STL stream
     */
    protected void writeVec(DataOutput out, Vec3 p, Mat4 trans)
	throws IOException
    {
	if (trans != null) p = trans.times(p);

	out.writeFloat((float) p.x);
	out.writeFloat((float) p.y);
	out.writeFloat((float) p.z);
    }

    /**
     *  read a 3D vector from an (ASCII) STL stream
     *
     *  @param token the StreamTokenizer to parse from
     *  @param vec the Vec3 object to put the parsed values into
     *		(may be <i>null</i>)
     *
     *  @return the result of the parsing, in a Vec3 object
     *  @throws IOException from the IO
     *  @throws RuntimeException of a token is not a number
     */
    protected static Vec3 readVec(StreamTokenizer token, Vec3 vec)
	throws IOException
    {
	if (vec == null) vec = new Vec3();

	if (token.nextToken() == NUM) vec.x = token.nval;
	else if (token.ttype == WORD) vec.x = Double.parseDouble(token.sval);
	else throw new RuntimeException("Invalid number");

	if (token.nextToken() == NUM) vec.y = token.nval;
	else if (token.ttype == WORD) vec.y = Double.parseDouble(token.sval);
	else throw new RuntimeException("Invalid number");

	if (token.nextToken() == NUM) vec.z = token.nval;
	else if (token.ttype == WORD) vec.z = Double.parseDouble(token.sval);
	else throw new RuntimeException("Invalid number");

	
	return vec;
    }

    /**
     *  read a 3D vector from a (binary) STL stream.
     */
    protected static Vec3 readVec(DataInput in, Vec3 vec)
	throws IOException
    {
	if (vec == null) vec = new Vec3();

	vec.x = in.readFloat();
	vec.y = in.readFloat();
	vec.z = in.readFloat();

	return vec;
    }

    /**
     *  an internal Exception with a shorter name
     */
    protected static class Ex extends RuntimeException {
	public Ex(String msg)
	{ super(msg); }
    }

    private static String slash = System.getProperty("file.separator");
    private static final String[] actionName = { "STLExport", "STLImport" };
}
