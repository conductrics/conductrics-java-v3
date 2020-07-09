package com.conductrics;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.conductrics.ExecResponse;

/** Describes the result of a selection. */
public class SelectResponse {
	private String a;
	private String c;
	private Policy p = Policy.Unknown;
	private Status s = Status.Unknown;
	private HashMap<String, String> meta;
	private ExecResponse execResponse;
	SelectResponse(String A, String C, String P) {
		meta = new HashMap<>();
		a = A;
		c = C;
		p = getPolicy(P);
	}
	SelectResponse(String A, String C, String P, Exception err) {
		meta = new HashMap<>();
		a = A;
		c = C;
		p = getPolicy(P);
		setError(err);
	}
	SelectResponse(JSONObject item, ExecResponse source, String A, String C) { // note: this is not the whole response,
		try {
			execResponse = source;
			a = item.getString("a");
			if( a == null ) a = A;
			c = item.getString("c");
			if( c == null ) c = C;
			String _p = item.getString("p");
			if( _p == null ) _p = "x";
			p = getPolicy(_p);
			meta = new HashMap<>();
			JSONObject md = item.getJSONObject("md");
			Iterator<String> keys = md.keys();
			while(keys.hasNext()) {
				String key = keys.next();
				meta.put(key, md.getString(key));
			}
			switch( item.getString("s") ) {
				case "ok": s = Status.Confirmed; break;
				case "p": s = Status.Provisional; break;
				default: s = Status.Unknown; break;
			}
			return;
		} catch( JSONException err ) {
			setError(new Exception(err.getLocalizedMessage()));
		} catch( Exception err ) {
			setError( err );
		}
	}
	/** Return the agent code that made this selection. */
	public String getAgent() { return a; }
	/** Return the option code selected by this agent. */
	public String getCode() { return c; }

	// how to map the internal api response codes to our enum
	private static Policy getPolicy(String s) {
		switch( s ) {
			case "x": return Policy.None;
			case "p": return Policy.Paused;
			case "r": return Policy.Random;
			case "f": return Policy.Fixed;
			case "a": return Policy.Adaptive;
			case "c": return Policy.Control;
			case "s": return Policy.Sticky;
			case "b": return Policy.Bot;
			default: return Policy.Unknown;
		}
	}

	/** Return the policy used to make this selection. */
	public Policy getPolicy() { return p; }

	/** Return any meta-data associated with this selection.
	 * Meta data is configured in the Console.
	 */
	public String getMeta(String key) { return meta.get(key); }

	/** Return the status of the request. */
	public Status getStatus() { return s; }

	/** Return a JSON-compatible description of this selection (without meta-data). */
	public String toString() { return "{ \"agentCode\": \""+a+"\", \"optionCode\": \""+c+"\", \"policy\": \""+p+"\" }"; }

	/** Return the underlying ExecResponse from which this SelectResponse was extracted. */
	public ExecResponse getExecResponse() { return execResponse; }

	private Exception error;
	/** Indicate that an error occurred during this request. */
	public void setError(Exception err) { this.error = err; }
	/** Return any error that occurred during this request. */
	public Exception getError() { return error; }
}
