/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import permeagility.util.Database;
import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;

import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;

public class Thumbnail {

	public static boolean DEBUG = false;
	
	public static int THUMBNAIL_SMALL_WIDTH = 50;
	public static int THUMBNAIL_SMALL_HEIGHT = 50;
	public static int THUMBNAIL_MEDIUM_WIDTH = 640;
	public static int THUMBNAIL_MEDIUM_HEIGHT = 480;
	
	static Database database;

	public static void setDatabase(Database d) {
		database = d;
	}

	public static String getThumbnailLink(String tid, String description) {
		if (description.startsWith("image")) {
			return "<A CLASS=\"thumbnail\" href=\"#thumb\">\n"
				+"<IMG SRC=\"../thumbnail?ID=" + tid + "\" >\n"
				+"<SPAN>"
				+description
				+"<button onClick=\"window.open('/thumbnail?SIZE=FULL&ID="+tid+"')\">Download full size</button><br>"
				+ "<IMG SRC=\"../thumbnail?ID="+tid+"&SIZE=MEDIUM\"/>\n"
				+"</SPAN></A>\n";
		} else {
			return "<A href=\"/thumbnail?SIZE=FULL&ID="+tid+"\" title=\""+description+"\">Download</A>";			
		}
	}

	public static String getThumbnailId(String table, String rid, String column, StringBuffer desc) {
		if (rid.startsWith("#")) {
			rid = rid.substring(1);
		}		
		if (table.equals("thumbnail")) {
			return rid;
		}
		DatabaseConnection con = database.getConnection();
		if (con != null) {
			try {
				String query = "SELECT FROM thumbnail\n"
						+"WHERE table = '"+table+"'\n"
						+"AND id = '"+rid+"'\n"
						+"AND column = '"+column+"'";
				QueryResult qr = con.query(query);
				if (qr.size()>0) {
					ODocument doc = qr.get(0);
					String type = doc.field("type");
					if (type.startsWith("image")) {
						desc.append(type +" "+ doc.field("name")+" "+doc.field("size")+" bytes "+doc.field("width")+"x"+doc.field("height"));
					} else {
						desc.append(type +" "+ doc.field("name")+" "+doc.field("size")+" bytes");
					}
					return doc.getIdentity().toString().substring(1);
				} else {
					if (DEBUG) System.out.println("No thumbnail found for table="+table+" rid="+rid+" column="+column+" query="+query);
					return createThumbnail(table,con.get("#"+rid),column);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				database.freeConnection(con);
			}
		}
		return null;
	}

	public static byte[] getThumbnail(String rid, String size, StringBuffer type, StringBuffer file) {
		
		DatabaseConnection con = database.getConnection();
		if (con != null) {
			try {
				ODocument thumbnail = null;
				QueryResult qr = con.query("SELECT FROM #"+rid);
				if (qr != null && qr.size() > 0) {
					thumbnail = qr.get(0);
				}
				if (thumbnail == null) {
					return null;
				}
				String column = null;
				if (size != null && size.equalsIgnoreCase("full")) {
					ODocument full = con.get("#"+thumbnail.field("id"));
					if (full != null) {
						if (DEBUG) System.out.println("Going full size! - "+rid);
						column = thumbnail.field("column");
						thumbnail = full;
					}
				}
				if (column == null) {
					if (size != null && size.equalsIgnoreCase("medium")) {
						column = "medium";
					} else {
						column = "small";
					}
				}
				if (DEBUG) System.out.println("Thumbnail: reading from column "+column);
				ORecordBytes bytes = thumbnail.field(column);
				if (bytes != null) {
					ByteArrayInputStream bis = new ByteArrayInputStream(bytes.toStream());
					if (size != null && size.equalsIgnoreCase("full")) {
						StringBuffer content_type = new StringBuffer();
						if (bis.available() > 0) {
							int binc = bis.read();
							do {
								content_type.append((char)binc);
								binc = bis.read();
							} while (binc != 0x00 && bis.available() > 0);
						}
						StringBuffer content_filename = new StringBuffer();
						if (bis.available() > 0) {
							int binc = bis.read();
							do {
								content_filename.append((char)binc);
								binc = bis.read();
							} while (binc != 0x00 && bis.available() > 0);
						}
						type.append(content_type);
						file.append(content_filename);
					} else {
						String t = thumbnail.field("type");
						if (!t.contains("svg")) {
							t = "image/jpeg";
						}
						type.append(t);
						file.append(thumbnail.field("name"));
					}
					ByteArrayOutputStream content = new ByteArrayOutputStream();
					if (DEBUG) System.out.print("Reading blob content: available="+bis.available());
					int avail;
					int binc;
					while ((binc = bis.read()) != -1 && (avail = bis.available()) > 0) {
						content.write(binc);
						byte[] buf = new byte[avail];
						int br = bis.read(buf);
						if (br > 0) {
							content.write(buf,0,br);
						}
					}
					if (content.size() > 0) {
						return content.toByteArray();
					}
				} else {
					System.out.println("Thumbnail: Image is empty");
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				database.freeConnection(con);
			}
		}
		return null;
	}

	
	public static String createThumbnail(String table, ODocument doc, String column) {

		if (DEBUG) System.out.println("Thumbnail: creating thumbnail for "+table+" column="+column+" id="+doc.getIdentity().toString());
		DatabaseConnection con = database.getConnection();
		if (con == null) {
			return null;
		}
		try {
			// Delete any and all that may already exist
			String query = "SELECT FROM thumbnail\n"
					+"WHERE table = '"+table+"'\n"
					+"AND id = '"+doc.getIdentity().toString().substring(1)+"'\n"
					+"AND column = '"+column+"'";
			QueryResult qr = con.query(query);
			if (qr.size()>0) {
				for (ODocument d : qr.get()) {
					d.delete();
				}
			}
			ORecordBytes image = (ORecordBytes)doc.field(column);
			
			StringBuffer content_type = new StringBuffer();
			StringBuffer content_filename = new StringBuffer();
			
			byte[] bytes = image.toStream();
			if (DEBUG) System.out.println("bytes length="+bytes.length);
			ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
			if (bis.available() > 0) {
				int binc = bis.read();
				do {
					content_type.append((char)binc);
					binc = bis.read();
				} while (binc != 0x00 && bis.available() > 0);
			}
			if (bis.available() > 0) {
				int binc = bis.read();
				do {
					content_filename.append((char)binc);
					binc = bis.read();
				} while (binc != 0x00 && bis.available() > 0);
			}
			
			ByteArrayOutputStream content = null;
			content = new ByteArrayOutputStream();
			if (DEBUG) System.out.println("Reading blob content: available="+bis.available()+" type="+content_type);
			int avail;
			int binc;
			while ((binc = bis.read()) != -1 && (avail = bis.available()) > 0) {
				content.write(binc);
				byte[] buf = new byte[avail];
				int br = bis.read(buf);
				if (br > 0) {
					content.write(buf,0,br);
				}
			} 

			ODocument thumbnail = con.create("thumbnail");

			if (content != null) {
				if (content_type.toString().equals("image/svg+xml")) {  // SVG scales to any size and doesn't take up much space
					System.out.println("encoding svg straight into thumbnails");
					thumbnail.field("small",new ORecordBytes(content.toByteArray()));
					thumbnail.field("medium",new ORecordBytes(content.toByteArray()));					
				} else if (content_type.toString().startsWith("image")) {
					if (DEBUG) System.out.println(" -> Encoding "+content.size()+" bytes of image");
					// Now scale it down into two sizes
					ImageIcon imagefull = new ImageIcon(content.toByteArray());
					int width = imagefull.getIconWidth();
					int height = imagefull.getIconHeight();
					int gheight = (int)((double)THUMBNAIL_SMALL_WIDTH/(double)width*(double)height);
					int g2height = (int)((double)THUMBNAIL_MEDIUM_WIDTH/(double)width*(double)height);
					BufferedImage imagesmall = new BufferedImage(THUMBNAIL_SMALL_WIDTH,gheight,BufferedImage.TYPE_INT_RGB);
					BufferedImage imagemedium = new BufferedImage(THUMBNAIL_MEDIUM_WIDTH,g2height,BufferedImage.TYPE_INT_RGB);
					Graphics2D g = (Graphics2D)imagesmall.getGraphics();
					Graphics2D g2 = (Graphics2D)imagemedium.getGraphics();
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g.drawImage(imagefull.getImage(), 0, 0, THUMBNAIL_SMALL_WIDTH, gheight, null);
					g2.drawImage(imagefull.getImage(), 0, 0, THUMBNAIL_MEDIUM_WIDTH, g2height, null);
					ByteArrayOutputStream tos = new ByteArrayOutputStream();
					ByteArrayOutputStream tos2 = new ByteArrayOutputStream();
					ImageIO.write(imagesmall, "jpg", tos);
					ImageIO.write(imagemedium, "jpg", tos2);
					g.dispose();
					g2.dispose();
					thumbnail.field("width",width);
					thumbnail.field("height",height);
					thumbnail.field("small",new ORecordBytes(tos.toByteArray()));
					thumbnail.field("medium",new ORecordBytes(tos2.toByteArray()));
					if (DEBUG) System.out.println(" -> Encoded "+content.size()+" bytes into "+tos.size()+" and "+tos2.size());
				} else {
					if (DEBUG) System.out.println("Thumbnail: not an image - no encoding");
				}
				thumbnail.field("size",content.size());
			}			
			thumbnail.field("table",table);
			thumbnail.field("id",doc.getIdentity().toString().substring(1));
			thumbnail.field("column",column);
			thumbnail.field("type",content_type);
			thumbnail.field("name",content_filename);
			thumbnail.save();
			return thumbnail.getIdentity().toString().substring(1);
		
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			database.freeConnection(con);			
		}

		return null;
	}

	public static String deleteThumbnail(String table, String id) {

		if (DEBUG) System.out.println("Thumbnail: deleting thumbnail for "+table+" id="+id);
		DatabaseConnection con = database.getConnection();
		if (con == null) {
			return null;
		}
		try {
			// Delete any and all that may already exist
			String query = "SELECT FROM thumbnail\n"
					+"WHERE table = '"+table+"'\n"
					+"AND id = '"+id+"'";
			QueryResult qr = con.query(query);
			if (qr.size()>0) {
				for (ODocument d : qr.get()) {
					d.delete();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			database.freeConnection(con);			
		}

		return null;
	}

	
}
