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
package permeagility.web;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.Date;

public class Settings extends Weblet {
	
	public static boolean DEBUG = false;

    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
		String service = Message.get(con.getLocale(),"SERVER_SETTINGS");
		parms.put("SERVICE",service);
		StringBuilder errors = new StringBuilder();
		
		// Get current style
		QueryResult currentStyleResult = con.query("SELECT FROM "+Setup.TABLE_CONSTANT+" WHERE classname='permeagility.web.Context' AND field='DEFAULT_STYLE'");
		ODocument styleConstant = null;
		String currentStyleName = null;
		if (currentStyleResult != null && currentStyleResult.size()>0) {
			styleConstant = currentStyleResult.get(0);
			currentStyleName = styleConstant.field("value");
			if (DEBUG) System.out.println("Settings: CurrentStyle="+styleConstant.field("value"));
		} else {
			System.out.println("Settings: Could not determine current style setting");
		}

		// Get current table row limit
		QueryResult currentRowCountResult = con.query("SELECT value FROM "+Setup.TABLE_CONSTANT+" WHERE classname='permeagility.web.Table' AND field='ROW_COUNT_LIMIT'");
		String currentRowCount = "";
		if (currentRowCountResult != null && currentRowCountResult.size()>0) {
			currentRowCount = currentRowCountResult.getStringValue(0, "value");
		}

		String submit = (String)parms.get("SUBMIT");
		if (submit != null) {
			
			// Update style if changed
			String setStyle = parms.get("SET_STYLE");
			if (setStyle != null && !setStyle.equals(styleConstant.getIdentity().toString())) {
				try {
					ODocument style = con.get(setStyle);
					styleConstant.field("value", style.field("name").toString());
					currentStyleName = style.field("name");
					String theme = style.field("editorTheme");
					styleConstant.save();
					
					Boolean horiz = style.field("horizontal");
					if (horiz == null) horiz = new Boolean(false);
					setCreateConstant(con,"permeagility.web.Menu","HORIZONTAL_LAYOUT",""+horiz);
					if (theme != null && !theme.equals("")) {
						setCreateConstant(con,"permeagility.web.Context","EDITOR_THEME",""+style.field("editorTheme"));
					}
					setCreateConstant(con,"permeagility.web.Header","LOGO_FILE",(String)style.field("logo"));
					Menu.clearCache();
					Server.tableUpdated("constant");
					errors.append(paragraph("success",Message.get(con.getLocale(),"SYSTEM_STYLE_UPDATED")));
				} catch (Exception e) {
					errors.append(paragraph("error","Error setting style: "+e.getLocalizedMessage()));
					e.printStackTrace();
				}
			}

			// Update rowcount if changed
			String setRowCount = parms.get("SET_ROWCOUNT");
			if (setRowCount != null && !setRowCount.equals(currentRowCount)) {
                            int newRowCount = -1;
                            try {
                                newRowCount = Integer.parseInt(setRowCount);
                            } catch (Exception e) {
                                errors.append(Message.get(con.getLocale(),"ERROR_PARSING_NUMBER",setRowCount));
                            }
                            if (newRowCount > 0) {
                            try {
                                con.update("UPDATE "+Setup.TABLE_CONSTANT+" SET value = '"+newRowCount+"' WHERE classname='permeagility.web.Table' AND field='ROW_COUNT_LIMIT'");
                                currentRowCount = setRowCount;
                                Server.tableUpdated("constant");
                                errors.append(paragraph("success",Message.get(con.getLocale(),"ROW_COUNT_LIMIT_UPDATED",setRowCount)));
                            } catch (Exception e) {
                                errors.append(paragraph("error","Error setting table rowcount limit: "+e.getLocalizedMessage()));
                            }
                        }
                    }
		
		}
		
		String selectedStyleID = "";

		// Need to retrieve the style to get its RID to set the default for the pick list
		try {
                    QueryResult selectedStyle = con.query("SELECT from "+Setup.TABLE_STYLE+" WHERE name='"+currentStyleName+"'");
                    selectedStyleID = (selectedStyle == null || selectedStyle.size() == 0 ? "" : selectedStyle.get(0).getIdentity().toString().substring(1));
		} catch (Exception e) {
                    errors.append(paragraph("error","Selected style does not exist"));
		}
		return head(service)+
	    standardLayout(con, parms,errors
	    	+form(
                    table("layout",
                        row(column("label",Message.get(con.getLocale(), "SET_STYLE"))+column(createListFromTable("SET_STYLE", selectedStyleID, con, "style", null, false, null, true)))
                        +row(column("label",Message.get(con.getLocale(), "SET_ROWCOUNT"))+column(input("SET_ROWCOUNT", currentRowCount)))
                        +row(column("")+column(submitButton(con.getLocale(), "SUBMIT_BUTTON")))
                    )
	    	)
    	);
    }

    public static boolean setCreateConstant(DatabaseConnection con, String classname, String field, String value) {
    	try {
            ODocument currentSetting = con.queryDocument("SELECT FROM "+Setup.TABLE_CONSTANT+" WHERE classname='"+classname+"' AND field='"+field+"'");
            if (currentSetting == null) {
                currentSetting = con.create(Setup.TABLE_CONSTANT);
                currentSetting.field("classname",classname);
                currentSetting.field("field",field);
            }
            currentSetting.field("description","assigned by "+con.getUser()+" on "+(new Date()));
            currentSetting.field("value",value);
            currentSetting.save();
    	} catch (Exception e) {
            e.printStackTrace();
            return false;
    	}
    	return true;
    }
}


