package com.conductrics;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.conductrics.ExecResponse;

/** Describes the result of a goal. */
public class GoalResponse {
	private String g;
	private HashMap<String, Double> rs = new HashMap<String, Double>();
	GoalResponse(String goalCode, Exception err) {
		g = goalCode;
		error = err;
	}
	GoalResponse(String goalCode) {
		g = goalCode;
	}
	GoalResponse(JSONObject item) {
		try {
			g = item.getString("g");
			JSONArray a = item.getJSONArray("rs");
			for(int i = 0; i < a.length(); i++ ) {
				JSONObject o = a.getJSONObject(i);
				rs.put(o.getString("a"), o.getDouble("v"));
			}
		} catch( JSONException err ) {
			setError(new Exception(err.getLocalizedMessage()));
		} catch( Exception err ) {
			setError( err );
		}
	}
	public String toString() {
		String ret = "{ goalCode: '"+g+"', accepted: {";
		for( String agentCode : rs.keySet() ) {
			ret += "\"" + agentCode + "\": " + rs.get(agentCode) + ",";
		}
		ret += "} }";
		return ret;
	}

	/** Return the goal code to which the value was sent. */
	public String getGoalCode() { return g; }
	/** Return the amount of value accepted by a particular agent. */
	public Double getAcceptedValue(String agentCode) {
		if( rs == null || ! rs.containsKey(agentCode) ) {
			return 0.0;
		}
		return rs.get(agentCode);
	}

	private Exception error;
	public void setError(Exception err) { this.error = err; }
	public Exception getError() { return error; }
}
