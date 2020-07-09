package com.conductrics;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.conductrics.SelectResponse;
import com.conductrics.GoalResponse;

/** An ExecReponse describes the response to an API call. */
public class ExecResponse {
	private HashMap<String, SelectResponse> sels = new HashMap<>();
	private HashMap<String, GoalResponse> rewards = new HashMap<>();
	private List<String> traits = new LinkedList<>();
	private List<String> log = new LinkedList<>();
	private JSONObject json;
	public ExecResponse(Exception err) {
		setError( err );
	}
	public ExecResponse(JSONObject response) {
		if( response == null ) {
			return;
		}
		json = response;
		try {
			if( response.has("traits") ) {
				JSONArray t = response.getJSONArray("traits");
				for(int i = 0; i < t.length(); i++ ) {
					traits.add(t.getString(i));
				}
			}
			if( response.has("log") ) {
				JSONArray l = response.getJSONArray("log");
				for(int i = 0; i < l.length(); i++ ) {
					this.log.add(l.getString(i));
				}
			}
			JSONArray items = response.getJSONArray("items");
			for(int i = 0 ; i < items.length(); i++ ) {
				JSONObject item = items.getJSONObject(i);
				if( item.has("a") ) {
					sels.put(item.getString("a"), new SelectResponse(item, this));
				} else if( item.has( "g") ) {
					rewards.put(item.getString("g"), new GoalResponse(item));
				}
			}
		} catch( JSONException err ) {
			setError( err );
			return;
		}
	}
	/** Extract and return a SelectResponse for an agent. */
	public SelectResponse getSelection(String agentCode, String defaultOption) {
		if( sels == null || ! sels.containsKey(agentCode) ) {
			return new SelectResponse(agentCode, defaultOption, "x", new Exception("unknown agent"));
		}
		return sels.get(agentCode);
	}
	/** Extract and return a GoalResponse for a goal. */
	public GoalResponse getReward(String goalCode) {
		if( rewards == null || ! rewards.containsKey(goalCode) ) {
			return new GoalResponse(goalCode); // will have .getAcceptedValue(agentCode) == 0 for all agents
		}
		return rewards.get(goalCode);
	}
	/** Return the list of traits that were applied to this request. */
	public List<String> getTraits() {
		return traits;
	}
	/** Return a list of debug messages from this request.
	 * Populated only if you add .setParam("debug", "true") to the RequestOptions.
	 */
	public List<String> getLog() {
		return log;
	}
	/** Return the underlying JSONObject from which the response was extracted. */
	public JSONObject getJSONObject() {
		return json;
	}
	private Exception error;
	/** Indicate that an error occurred during this request. */
	public void setError(Exception err) { this.error = err; }
	/** Return any error that occurred during this request. */
	public Exception getError() { return error; }
}
