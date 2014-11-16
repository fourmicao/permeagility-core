/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;

import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;

public class SQL extends Weblet {
	
	public static boolean DEBUG = false;
	
	public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		parms.put("SERVICE", Message.get(con.getLocale(), "SQL_WEBLET"));
		String query = (String)parms.get("SQL");
		String submit = (String)parms.get("SUBMIT");
		if (submit == null) submit = "ExecuteQuery";  // Otherwise, it will think its an update statement
		return 	
			head("Query Weblet:"+query,getSortTableScript()+getAngularControlScript())+
			body( standardLayout(con, parms,  
				getSQLBuilder(con)
				+form("QUERY","#",
					"<textarea name=\"SQL\" rows=6 cols=80 text-build>"+(query==null ? "" : query)+"</textarea>"
					+br()
					+submitButton("ExecuteQuery")+"&nbsp;&nbsp;"
					+submitButton("ExecuteUpdate")
				) 
				+br()
				+paragraph("banner","Results")
				+anchor("TOP","Top")+"     "
				+link("#BOTTOM","Bottom")
				+paragraph(medium("Query="+query))
				+table("sortable", (submit.equals("ExecuteQuery") ? getResult(con, query) : getUpdate(con, query)))
				+anchor("BOTTOM","Bottom")+"     "
				+link("#TOP","Top")		  
			));
	}

	public String getUpdate(DatabaseConnection con, String query) {
		if (query == null || query.equals("")) {
			return "No Update SQL Specified";
		}
		if (query.trim().toUpperCase().startsWith("SELECT")) {
			return "Update cannot occur with a SELECT statement";			
		}
		Object rc = null;
		boolean secRef = false;
		try {
			rc = con.update(query);
			if (rc != null && (query.trim().toUpperCase().startsWith("GRANT") || query.trim().toUpperCase().startsWith("REVOKE"))) {
				Server.refreshSecurity();
				secRef = true;
			}
		} catch (Exception e) {
			System.out.println("Error in SQL Weblet update: "+e.getMessage());
			e.printStackTrace();
			return paragraph("error","Error: "+e.getMessage());
		}
		return rc+" rows updated"+(secRef ? " and security refreshed" : "");
	}

	public String getResult(DatabaseConnection con, String query) {
		if (query == null || query.equals("")) {
			return "No SQL Specified";
		}
		try {
			StringBuffer sb = new StringBuffer();
			int rowCount = 0;
			QueryResult rs = con.query(query);
			ArrayList<String> columns = new ArrayList<String>();
			sb.append(row(getRowHeader(rs,columns)));
			for (int i=0;i<rs.size();i++) {
				sb.append(row("data",getRow(con, rs,i, columns)));
				rowCount++;
			}
			sb.append(paragraph("RowCount="+rowCount));
			return sb.toString();
		} catch (Exception e) {
			System.out.println("Error in SQL Weblet select: "+e.getMessage());
			e.printStackTrace();
			return paragraph("error","Error: "+e.getMessage());
		}
	}

	public String getRowHeader(QueryResult rs, ArrayList<String> cols) throws SQLException {
		StringBuffer sb = new StringBuffer();
		String[] columns = rs.getColumns();
		sb.append(tableHead(medium(bold("rid"))));		
		if (columns != null) {
			for (String colName : columns) {
				sb.append(tableHead(medium(bold(colName))));
				cols.add(colName);
			}
			return sb.toString();
		} else { 
			return "";
		}
	}
	
	@SuppressWarnings("unchecked")
	public String getRow(DatabaseConnection con, QueryResult rs, int r, ArrayList<String> columns) throws SQLException {
		StringBuffer sb = new StringBuffer();
		ODocument row = rs.get(r);
		if (row.getIdentity().toString().contains("-")) {
			sb.append(column(paragraphRight(xSmall(row.getIdentity().toString()))));
		} else {
			sb.append(column(paragraphRight(xSmall(link("permeagility.web.Table?TABLENAME="+row.getClassName()+"&EDIT_ID="+row.getIdentity().toString().substring(1)
					, row.getIdentity().toString()
					, "_blank")))));			
		}
		for (String colName : columns) {
			Object o = row.field(colName);
			if (DEBUG) System.out.println(colName+"="+(o == null ? "null" : o.getClass().getName()));
			if (o instanceof ODocument) {  // OrientDB Link  
				ODocument d = row.field(colName);
				sb.append(column(paragraphRight(xSmall(d == null ? "null" : getDocumentLink(con, d)))));
			} else if (o instanceof Boolean) { // OrientDB boolean
				sb.append(column(paragraphRight(xSmall(""+row.field(colName)))));
			} else if (o instanceof Byte) { // OrientDB boolean
				sb.append(column(paragraphRight(xSmall(""+row.field(colName)))));
			} else if (o instanceof ORecordBytes) { // PermeAgility Image/Blob or other?
				String out;
				ORecordBytes or = (ORecordBytes)o;
				StringBuffer desc = new StringBuffer();
				String blobid = Thumbnail.getThumbnailId(row.getClassName(), row.getIdentity().toString().substring(1), colName, desc);
				if (blobid != null) {
					out = column(Thumbnail.getThumbnailLink(blobid, desc.toString()));
				} else {
					out = column(xSmall("Thumbnail not found for column "+colName+" class="+row.getClassName()+" with rid="+row.getIdentity().toString()));					
				} 
				sb.append(out);
			} else if (o instanceof List) {  // LinkList
				List<ODocument> l = (List)o;
				StringBuffer ll = new StringBuffer();
				if (l != null) {
					for (Object od : l) {
						if (od instanceof ODocument) {
							ODocument d = (ODocument)od;
							ll.append(getDocumentLink(con, d)+br());
						} else {
							ll.append(od+br());							
						}
					}
				}
				sb.append(column(xSmall(ll.toString())));
			} else if (o instanceof Set) {  // LinkSet
				Set<ODocument> l = (Set)o;
				StringBuffer ll = new StringBuffer();
				if (l != null) {
					for (Object od : l) {
						if (od instanceof ODocument) {
							ODocument d = (ODocument)od;
							ll.append(getDocumentLink(con, d)+br());
						} else {
							ll.append(od+br());							
						}
					}
				}
				sb.append(column(xSmall(ll.toString())));
			} else if (o instanceof Map) {    // LinkMap
				Map<String,ODocument> l = (Map)o;
				StringBuffer ll = new StringBuffer();
				if (l != null) {
					for (String k : l.keySet()) {
						Object d = l.get(k);
						if (d != null && d instanceof ODocument) {
							ll.append(k+":"+getDocumentLink(con, (ODocument)d)+br());
						} else {
							ll.append(k+":"+d+br());							
						}
					}
				}
				sb.append(column(xSmall(ll.toString())));
			} else if (o instanceof Number) {
				sb.append(column(paragraphRight(xSmall(""+row.field(colName)))));
			} else {
				sb.append(column(xSmall(""+row.field(colName))));
			}
		}
		return sb.toString();
	}
	
	public String getDocumentLink(DatabaseConnection con, ODocument d) {
		if (d == null || d.getClassName() == null) {
			return ""+d;
		} else {
			return link("permeagility.web.Table?TABLENAME="+d.getClassName()+"&EDIT_ID="+d.getIdentity().toString().substring(1)
				, getDescriptionFromDocument(con, d)
				, "_blank");
		}
	}
	
	String getSQLBuilder(DatabaseConnection con) {
		StringBuffer tableInit = new StringBuffer(); // JSON list of tables and groups
		StringBuffer columnInit = new StringBuffer();  // JSON list of tables and columns
		
		// Add tables in groups (similar code to Schema - should be combined in one place - need one more use
		QueryResult schemas = con.query("SELECT from tableGroup");
		QueryResult tables = con.query("SELECT name, superClass FROM (SELECT expand(classes) FROM metadata:schema) WHERE abstract=false ORDER BY name");
		ArrayList<String> tablesInGroups = new ArrayList<String>(); 
		for (ODocument schema : schemas.get()) {
			StringBuffer tablelist = new StringBuffer();
			String tablesf = schema.field("tables");
			String table[] = {};
			if (tablelist != null) {
				table = tablesf.split(",");
			}
			String groupName = (String)schema.field("name");
			tablelist.append(paragraph("banner", groupName));
			boolean groupHasTable = false;
			for (String tableName : table) {
				tableName = tableName.trim();
				boolean show = true;
				if (tableName.startsWith("-")) {
					show = false;
					tableName = tableName.substring(1);
				}
				tablesInGroups.add(tableName);
				if (show) {
					int privs = Server.getTablePriv(con, tableName);
					//System.out.println("Table privs for table "+tableName+" for user "+con.getUser()+" privs="+privs);
					if (privs > 0) {
						tableName = tableName.trim();
						if (tableInit.length()>0) tableInit.append(", ");
						tableInit.append("{ group:'"+groupName+"', table:'"+tableName+"'}");
						groupHasTable = true;
					}
				}
			}
		}
		
		// Add the non grouped (new) tables
		StringBuffer tablelist = new StringBuffer();
		tablelist.append(paragraph("banner",Message.get(con.getLocale(), "TABLE_NONGROUPED")));
		for (ODocument row : tables.get()) {
			String tablename = row.field("name");
			if (!tablesInGroups.contains(tablename)) {
				if (Server.getTablePriv(con, tablename) > 0) {
					if (tableInit.length()>0) tableInit.append(", ");
					tableInit.append("{ group:'New', table:'"+tablename+"'}");
				}
			}
		}

		// Columns
		for (ODocument t : tables.get()) {
			String tName = t.field("name");
			QueryResult cols = Server.getColumns(tName);
			for (ODocument col : cols.get()) {
				String cName = col.field("name");
				Integer cType = col.field("type");
				String cClass = col.field("linkedClass");
				if (columnInit.length()>0) columnInit.append(", ");
				columnInit.append("{ table:'"+tName+"', column:'"+cName+"', type:'"+Table.getTypeName(cType)+(cClass == null ? "" : " to "+cClass)+"'}");
			}
		}
		
	    return "<div ng-controller=\"TextBuildControl\""
	           +" ng-init=\"tables=["+tableInit+"]; columns=["+columnInit+"];\">\n"
	    +table(0,
	    	row(column("")
	    		+column(
			    	"<select ng-model=\"selGroup\"\n"
			    	+"  ng-options=\"v.group for v in tables | unique:'group'\" >\n"
			    	+"  <option value=\"\">None</option>\n"
			    	+"</select>\n")
			    +column("")
			    +column(
			      "<select ng-model=\"selTable\"\n"
			      +"      ng-options=\"v.table for v in tables | orderBy:'table'\" >\n"
			      +"  <option value=\"\">None</option>\n"
			      +"</select>\n")
			     +column("")
		     )
		     +row(column("<button ng-click=\"add('SELECT FROM ')\">SELECT FROM</button>\n")
		    	+column("<select ng-model=\"selTable\"\n" 
		    			+"  ng-change=\"add(selTable.table+' ')\"\n"
		    			+"  ng-options=\"v.table for v in tables | filter:{group:selGroup.group} | orderBy:'table'\">\n"
		    			+"  <option value=\"\">None</option>\n"
		    			+"</select>\n")
		    	+column("<button ng-click=\"add('WHERE ')\">WHERE</button>\n")
		    	+column("<select ng-model=\"selColumn\"\n" 
		    			+"  ng-options=\"v.column+' -'+v.type for v in columns | filter:{table:selTable.table}\"\n" 
		    			+"  ng-change=\"add(selColumn.column+' ')\">\n"
		    			+"  <option value=\"\">None</option>\n"
		    			+"</select>\n")
		    	+column("<select ng-model=\"selOperator\" ng-change=\"add(selOperator+' ')\">\n"
		    			+"  <option value=\"=\">=</option>\n"
		    			+"  <option value=\"!=\">!=</option>\n"
		    			+"  <option value=\"<\">&lt;</option>\n"
		    			+"  <option value=\">\">&gt;</option>\n"
		    			+"  <option value=\"LIKE\">LIKE</option>\n"
		    			+"  <option value=\"CONTAINS\">CONTAINS</option>\n"
		    			+"  <option value=\"CONTAINSKEY\">CONTAINSKEY</option>\n"
		    			+"  <option value=\"CONTAINSVALUE\">CONTAINSVALUE</option>\n"
		    			+"  <option value=\"\">None</option>\n"
		    			+"</select>\n")
		    	)
		    )
	    +"</div>\n";
	}
}
