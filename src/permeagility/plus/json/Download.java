/* 
 * Copyright 2015 PermeAgility Incorporated.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package permeagility.plus.json;

import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.web.Weblet;

public class Download extends permeagility.web.Download {

    @Override
    public String getContentType() { return "application/json"; }

    @Override
    public String getContentDisposition() { return "inline; filename=\"data.json\""; }

    @Override
    public byte[] getBytes(DatabaseConnection con, HashMap<String, String> parms) {
        StringBuilder sb = new StringBuilder();
        String fromTable = parms.get("FROMTABLE");
        String fromSQL = parms.get("SQL");
        String callback = parms.get("CALLBACK");
        if (callback != null && callback.isEmpty()) { callback = null; }
        
        int depth = -1;  // unlimited is the default
        try { depth = Integer.parseInt(parms.get("DEPTH")); } catch (Exception e) {}  // It will either parse or not

        if ((fromTable == null || fromTable.isEmpty()) && (fromSQL == null || fromSQL.isEmpty())) {
            return null;
        } else {
            try {
                if (fromSQL == null || fromSQL.isEmpty()) {
                    fromSQL = "SELECT FROM "+fromTable;
                }
                QueryResult qr = con.query(fromSQL);
                if (callback != null) { sb.append(callback+"("); }
                sb.append("{ \""+(fromTable==null ? "data" : fromTable)+"\":[ ");
                String comma = "";
                for (ODocument d : qr.get()) {
                    sb.append(comma+exportDocument(con, d, depth, 0));
                    comma = ", \n";
                }
                sb.append(" ] }\n");
                if (callback != null) { sb.append(")"); }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sb.toString().getBytes(Weblet.charset);
    }
    
    public static String exportDocument(DatabaseConnection con, ODocument d, int maxLevel, int level) {
        StringBuilder sb = new StringBuilder();
        String comma = "";
        sb.append("{");
        String[] columns = d.fieldNames();
        for (String p : columns) {
            OType t = d.fieldType(p);
            if (t != OType.CUSTOM) {
                sb.append(comma);
                if (t == OType.LINK) {
                    ODocument ld = (ODocument)d.field(p);
                    if (ld == null) {
                        sb.append("\""+p+"\":null");                    
                    } else if (level < maxLevel) {
                        sb.append("\""+p+"\":"+exportDocument(con, ld , maxLevel, level+1));
                    } else {
                        sb.append("\""+p+"\":\""+ld.getIdentity().toString().substring(1)+"\"");                    
                    }
                } else if (t == OType.LINKSET) {
                    Set<ODocument> set = d.field(p);
                    String lcomma = "";
                    sb.append("\""+p+"\": [");
                    if (set != null) {
                        for (ODocument sd : set) {
                            if (sd != null) {
                                if (level < maxLevel) {
                                    sb.append(lcomma+exportDocument(con, sd, maxLevel, level+1));
                                } else {
                                    sb.append(lcomma+"\""+sd.getIdentity().toString().substring(1)+"\"");                    
                                }
                                lcomma = ", \n";
                            }
                        }
                    }
                    sb.append("] ");
                } else if (t == OType.LINKLIST) {
                    List<ODocument> set = d.field(p);
                    String lcomma = "";
                    sb.append("\""+p+"\": [");
                    if (set != null) {
                        for (ODocument sd : set) {
                            if (sd != null) {
                                if (level < maxLevel) {
                                    sb.append(lcomma+exportDocument(con, sd, maxLevel, level+1));
                                } else {
                                    sb.append(lcomma+"\""+sd.getIdentity().toString().substring(1)+"\"");                    
                                }
                                lcomma = ", \n";
                            }
                        }
                    }
                    sb.append("] ");
                } else if (t == OType.DATE || t == OType.DATETIME) {
                    sb.append("\""+p+"\":\""+d.field(p)+"\"");
                } else if (t == OType.EMBEDDED || t == OType.EMBEDDEDLIST || t == OType.EMBEDDEDMAP || t == OType.EMBEDDEDSET) {
                    sb.append("\""+p+"\":\""+d.field(p)+"\"");                
                } else if (t == OType.STRING) {
                    String content = d.field(p);
                    if (content != null && !content.isEmpty()) {
                        content = content.replace("\"","\\\"").replace("\\s","\\u005cs").replace("\t","\\t").replace("\r","\\r").replace("\n","\\n");
                    }
                    sb.append("\""+p+"\":\""+(content == null ? "" : content)+"\"");                
                } else {
                    sb.append("\""+p+"\":"+d.field(p));
                }
                comma = ", \n";
            }
        }
        sb.append("}");
        return sb.toString();
    }

}
