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
package permeagility.util;

import java.util.concurrent.ConcurrentHashMap;

/*
 * A cache of queries - by user id
 */
@SuppressWarnings("serial")
public class QueryCache extends ConcurrentHashMap<String,QueryResult> {
	    	
	public static boolean DEBUG = false;
	
	/*
	 * Get a cached result. If it is not in the cache, it will be added
	 * Note: Cache items are unique to each user
	 */
	public QueryResult getResult(DatabaseConnection con, String query) {
		String user = con.getUser();
		QueryResult cresult = get(user+query);
		if (cresult != null) {
			if (DEBUG) System.out.println("Cache hit found table!");
			return cresult;
		}
		try {
			QueryResult qr = con.query(query);
			query = query.replace('\n', ' ');  // Causes problems in cache keys
			put(user+query, qr);
			if (DEBUG) System.out.println("Added "+user+query+" to cache of "+size());
			return qr;
		} catch (Exception e) {
			System.out.println("Cannot produce query result: "+query+" for user "+user+"\nmessage="+e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
	
	/*
	 * Refresh cache items where the cached query contains the query 
	 * text passed into the function must be exact match
	 */
	public synchronized void refresh(String query) {
		if (containsKey(query)) {
			remove(query);
			System.out.println("Cache entry removed");
		} else {
			if (query.equals("ALL")) {
				System.out.println("Clearing all of queryCache");
				clear();
			} else {
				System.out.println("Could not find cache entry "+query);
			}
		}
	}

	/*
	 * Refresh cache items where the cached query contains the query 
	 * text passed into the function based on a contains partial match
	 * - this is useful for when you have changed a table and want to refresh
	 * all queries that used that table
	 */
	public synchronized void refreshContains(String query) {
		for (String key : keySet()) {
			if (key.toUpperCase().contains(query.toUpperCase())) {
				remove(key);
			}
		}
	}

}


