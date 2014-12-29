/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import permeagility.util.Database;
import permeagility.util.DatabaseConnection;

import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.tool.ODatabaseExport;

public class BackupRestore extends Weblet {

	StringBuffer exportLog = new StringBuffer();

    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
    	
    	Locale locale = con.getLocale();
		String service = Message.get(locale,"BACKUP_AND_RESTORE");
		parms.put("SERVICE",service);
		StringBuffer errors = new StringBuffer();

		if (!Server.isDBA(con)) {
	    	return head(service)+standardLayout(con, parms,paragraph("error",Message.get(locale, "RESTORE_ACCESS")));
		}

		File backupDir = new File("backup");
		if (backupDir == null || !backupDir.exists()) {
			errors.append(paragraph("error",Message.get(locale, "BACKUP_DIRECTORY_CREATED")));
			backupDir.mkdir();
		} else {
			if (!backupDir.isDirectory()) {
				boolean success = backupDir.mkdir();
				if (success) {
					errors.append(paragraph("warning",Message.get(locale, "BACKUP_DIRECTORY_CREATED")));
				} else {
					errors.append(paragraph("error","Backup directory could not be created - cannot perform backups"));					
				}
			}
		}
		String submit = parms.get("SUBMIT");
		
		if (submit != null && submit.equals(Message.get(locale,"BACKUP_NOW"))) {
			String backupName = parms.get("BACKUP_FILENAME");
			System.out.println("Creating backup of database to file "+backupName);
			if (backupName.equals("")) {
				errors.append(paragraph("error",Message.get(locale,"BACKUP_FILENAME_NEEDED")));
			} else {
				try {
					ODatabaseExport exp = new ODatabaseExport(con.getDb(), "backup/"+backupName, new OCommandOutputListener() {
						public void onMessage(String iText) {
							if (exportLog == null) exportLog = new StringBuffer();
							exportLog.append(paragraph("Export message: "+iText));
							System.out.println("Export Message: "+iText);
						}});
					exp.exportDatabase();
					exp.close();
					errors.append(paragraph("success",Message.get(locale,"BACKUP_SUCCESS")+backupName));
				} catch (IOException e1) {
					e1.printStackTrace();
					errors.append(paragraph("error",Message.get(locale,"BACKUP_FAIL")+e1.getLocalizedMessage()));
				}
			}
		}
		
		if (submit != null && submit.equals(Message.get(locale,"RESTORE_NOW"))) {
			if (!Server.DB_NAME.startsWith("plocal")) {
		    	return head(service)+standardLayout(con, parms,paragraph("error",Message.get(locale, "RESTORE_PLOCAL")));
			}

			if (parms.get("CONFIRM") != null && parms.get("CONFIRM").equals("YES") && parms.get("RESTORE") != null) {
				System.out.println("Restoring the database from file "+parms.get("RESTORE"));
				Server.restore_lockout = true;
				Server.restore_file = "backup/"+parms.get("RESTORE");

				Thread restore_thread = new Thread() {
					public void run() {

						// Wait 5 seconds for requests to clear
						try {
							System.out.println("Waiting 5 seconds for requests to clear...");
							sleep(5000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						System.out.println("Shutting down the database connections");
						Server.closeAllConnections();

						// Wait 5 seconds for requests to clear
						try {
							System.out.println("Waiting 5 seconds for connections to clear...");
							sleep(5000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						System.gc();
						
						String dbDirectory = Server.DB_NAME.split(":")[1];

						// Delete the database files in the db_saved directory if they exist from a previous restore
						File dbSaved = new File(dbDirectory+"_saved");
						if (dbSaved.isDirectory()) {
							boolean deletesuccess = true;
							for (File c : dbSaved.listFiles()) {
								try {
									if (!deleteFile(c)) {
										deletesuccess = false;
									}
								} catch (Exception e) {
									e.printStackTrace();
									deletesuccess = false;
								}
							}
							if (deletesuccess) {
								dbSaved.delete();
							} else {
								System.out.println("Error deleting saved database before restore - exiting");
								Server.exit(-4);
							}
						}

						// Rename the database directory
						File dbdata = new File(dbDirectory);
						if (dbdata.isDirectory()) {
							try {
								dbdata.renameTo(new File(dbDirectory+"_saved"));
							} catch (Exception e) {
								e.printStackTrace();
								System.out.println("Error renaming old database - exiting");
								Server.exit(-5);
							}
						} else {
							System.out.println("The data directory "+dbDirectory+" is not a directory - aborting restore - sorry");
							Server.exit(-6);
						}

						// Because the stuff below doesn't work we have to restart and use the settings file to pass the backup file name
						System.out.println("Setting restore localSetting for restore on startup");
						Server.setLocalSetting("restore", Server.restore_file);

						System.out.println("Exit with restart (1)");
						Server.exit(1);
						
						// This doesn't work because the server keeps remembering the database even though the files are gone (well, directory renamed)
/*						Server.initializeServer(Server.restore_file);
						System.out.println("We are back up now - I hope");
*/
					}
				};
				
				restore_thread.start();
				return redirect(con.getLocale(),"/");

			} else {

				return head(service)+
			    standardLayout(con, parms,
			    	errors
		    		+paragraph("banner",Message.get(locale, "CONFIRM_RESTORE",parms.get("RESTORE")))
		    		+form(
		    			hidden("CONFIRM","YES")
		    			+hidden("RESTORE",parms.get("RESTORE"))
		    			+paragraph(Message.get(locale, "RESTORE_CONFIRM"))
		    			+submitButton(Message.get(locale,"RESTORE_NOW"))
		    			+submitButton(Message.get(locale,"CANCEL"))
			        )
			    );
			}
		}
		
		StringBuffer restorePoints = new StringBuffer();
		File[] backupFiles = backupDir.listFiles();
		if (backupFiles != null) {
			for (int i=0; i<backupFiles.length; i++) {
				if (backupFiles[i].getName().endsWith(".gz") || backupFiles[i].getName().endsWith(".json")) {
					String fileSizeString;
					DecimalFormat sizeFormat = new DecimalFormat("#0.0");
					long fileSize = backupFiles[i].length();
					if (fileSize/1024 < 1024) {
						fileSizeString = ""+sizeFormat.format((double)fileSize/1024.0)+"KB";
					} else if (fileSize/1024/1024 < 1024) {
						fileSizeString = ""+sizeFormat.format((double)fileSize/1024.0/1024.0)+"MB";		
					} else {
						fileSizeString = ""+sizeFormat.format((double)fileSize/1024.0/1024.0/1024.0)+"GB";							
					}
					restorePoints.append(row(
								column(backupFiles[i].getName())
								+column(fileSizeString)
								+column(""+(new Date(backupFiles[i].lastModified())))
								+column(form(hidden("RESTORE",backupFiles[i].getName())+submitButton(Message.get(locale, "RESTORE_NOW") ) ) )
					));
				}
			}
		}
		String backupFilename = "Backup_"+formatDate(locale,new Date(),"yyyy-MM-dd_HH-mm");
    	return head(service,getSortTableScript())+
	    standardLayout(con, parms,
	    	errors
    		+paragraph("banner",Message.get(locale, "BACKUP_THE_DATABASE"))
    		+form(
	    		table("layout",
	    			row(column("label",Message.get(locale, "BACKUP_FILENAME"))+column(input("BACKUP_FILENAME",backupFilename,40)))
	    			+row(column("")+column(submitButton(Message.get(locale,"BACKUP_NOW"))))
	        	)
	        )
	    	+paragraph("banner",Message.get(locale, "RESTORE_THE_DATABASE"))
	    	+form(
	    		table("sortable", 
	    			row(columnHeader(Message.get(locale, "BACKUP_FILENAME"))
    					+columnHeader(Message.get(locale, "BACKUP_SIZE"))
    					+columnHeader(Message.get(locale, "BACKUP_DATE"))
    					+columnHeader(Message.get(locale, "RESTORE_NOW")))
	    			+restorePoints.toString())
	    	)
	    	+(exportLog != null ? exportLog.toString() : "")
    	);
    }

    private boolean deleteFile(File f) throws IOException {
    	if (f.isDirectory()) {
    		for (File c : f.listFiles()) {
    			deleteFile(c);
    		}
    		System.out.println("Deleting directory "+f);
    		return f.delete();
    	} else {
        	System.out.println("Deleting file "+f);
    		return f.delete();
    	}
    }
}


