//vim: g:syntastic_java_javac_classpath="./json-20190722.jar"

package com.conductrics.http;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.MalformedURLException;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.InputStream;

public class Conductrics {

	private String apiUrl;
	private String apiKey;
	public Conductrics(String apiUrl, String apiKey) {
		this.apiUrl = apiUrl;
		this.apiKey = apiKey;
	}

	public static class RequestOptions {
		private List<String> _traits = null;
		private Map<String, String> _params = new HashMap<String, String>();

		public String session() { return _params.get("session"); }
		public RequestOptions session(String s) {
			_params.put("session", s);
			return this;
		}

		public String ua() { return _params.get("ua"); }
		public RequestOptions ua(String s) {
			_params.put("ua", s);
			return this;
		}

		public List<String> traits() { return _traits; }
		public RequestOptions traits(List<String> v) {
			_params.put("traits", String.join(",", v));
			return this;
		}
		public RequestOptions traits(String ... v) {
			_params.put("traits", String.join(",", v));
			return this;
		}

		public Map<String, String> getParams() { return _params; }
		public RequestOptions withParam(String key, String value) {
			if( _params == null ) {
				_params = new HashMap<String, String>();
			}
			_params.put(key, value);
			return this;
		}
	}

	protected static void log(String line) { System.out.println("Conductrics: " + line); }

	private String readAll(InputStream s) {
		java.util.Scanner sc = new java.util.Scanner(s).useDelimiter("\\A");
		return sc.hasNext() ? sc.next() : "";
	}
	private void writeAll(String data, OutputStream out) throws IOException {
		DataOutputStream o = new DataOutputStream( out );
		o.writeBytes( data );
		o.flush();
		o.close();
	}

	private String httpPost(String url, String body, Map<String, String> headers) {
		Conductrics.log("POST: " + body + " " + url);
		URL u;
		HttpURLConnection conn;
		try {
			u = new URL(url);
		} catch( MalformedURLException e ) {
			Conductrics.log("MalformedURLException(url="+url+"): "+e.toString());
			return null;
		}
		try {
			conn = (HttpURLConnection)u.openConnection();
			conn.setRequestMethod("POST");
			if( headers != null ) {
				for( String key : headers.keySet() ) {
					conn.setRequestProperty(key, headers.get(key));
				}
			}
			conn.setUseCaches( false );
			conn.setDoInput( true );
			conn.setDoOutput( true );
		} catch( IOException e ) {
			Conductrics.log("IOException(url="+url+"): "+e.toString());
			return null;
		}
		try {
			writeAll( body, conn.getOutputStream() );
			return readAll( conn.getInputStream() );
		} catch( IOException e ) {
			Conductrics.log("IOException(url="+url+"): "+e.toString() + readAll( conn.getErrorStream() ));
			return null;
		}
	}

	public ExecResponse Exec( RequestOptions opts, JSONArray commands) { return new ExecResponse(exec(opts, commands)); }
  private JSONObject exec( RequestOptions opts, JSONArray commands) {
    try {
      String body = "{ \"commands\": " + commands.toString() + " }";
			HashMap<String, String> headers = new HashMap<String, String>();
			headers.put("content-type", "application/json");
			headers.put("content-length", String.format("%d", body.length()));
			String url = apiUrl + "?apikey=" + apiKey;
			Map<String, String> params = opts.getParams();
			if( params != null ) {
				try {
					for( String key : params.keySet() ) {
						url += "&"+key+"="+URLEncoder.encode(params.get(key), "utf-8");
					}
				} catch( java.io.UnsupportedEncodingException e) {
					Conductrics.log("Failed to produce a valid API url: " + e.getLocalizedMessage());
				}
			}
			String responseBody = httpPost(url, body, headers);
			if( responseBody != null ) {
				Conductrics.log("POST response: " + responseBody);
				JSONObject result = new JSONObject(responseBody);
				if( result.getInt("status") != 200 ) {
					Conductrics.log("exec failed: " + responseBody);
					return null;
				} else {
					return result.getJSONObject("data");
				}
			}
    } catch (JSONException err ) {
      Conductrics.log("JSONException in exec(): " + err.getLocalizedMessage());
    }
    return null;
  }

	public SelectResponse Select(RequestOptions opts, String agentCode) {
		JSONArray commands = new JSONArray().put(new JSONObject().put("a", agentCode));
		ExecResponse response = this.Exec( opts, commands );
		return response.getSelection( agentCode );
	}

	public GoalResponse Reward(RequestOptions opts, String goalCode ) {
		return Reward( opts, goalCode, 1.0 );
	}
	public GoalResponse Reward(RequestOptions opts, String goalCode, Double value) {
		JSONArray commands = new JSONArray().put(new JSONObject().put("g", goalCode).put("v", value));
		ExecResponse response = this.Exec( opts, commands );
		return response.getReward( goalCode );
	}

	public static class ExecResponse {
		private HashMap<String, SelectResponse> sels;
		private HashMap<String, GoalResponse> rewards;
		private List<String> traits;
		public ExecResponse(JSONObject response) {
			sels = new HashMap<>();
			rewards = new HashMap<>();
			traits = new ArrayList<>();
			if( response == null ) {
				return;
			}
			try {
				JSONArray items = response.getJSONArray("items");
				for(int i = 0 ; i < items.length(); i++ ) {
					JSONObject item = items.getJSONObject(i);
					if( item.has("a") ) {
						sels.put(item.getString("a"), new SelectResponse(item));
					} else if( item.has( "g") ) {
						rewards.put(item.getString("g"), new GoalResponse(item));
					}
				}
				if( response.has("traits") ) {
					JSONArray t = response.getJSONArray("traits");
					for(int i = 0; i < t.length(); i++ ) {
						traits.add(t.getString(i));
					}
				}
			} catch( JSONException err ) {
				return;
			}
		}
		public SelectResponse getSelection(String agentCode) {
			if( sels == null || ! sels.containsKey(agentCode) ) {
				return null;
			}
			return sels.get(agentCode);
		}
		public GoalResponse getReward(String goalCode) {
			if( rewards == null || ! rewards.containsKey(goalCode) ) {
				return null;
			}
			return rewards.get(goalCode);
		}
		public List<String> getTraits() {
			return traits;
		}
	}
	public static class SelectResponse {
		private String a;
		private String c;
		private String p;
		private HashMap<String, String> meta;
		SelectResponse(String A, String C, String P) {
			meta = new HashMap<>();
			a = A;
			c = C;
			p = P;
		}
		SelectResponse(JSONObject item) { // note: this is not the whole response,
			try {
				c = item.getString("c");
				a = item.getString("a");
				p = item.getString("p");
				meta = new HashMap<>();
				JSONObject md = item.getJSONObject("md");
				Iterator<String> keys = md.keys();
				while(keys.hasNext()) {
					String key = keys.next();
					meta.put(key, md.getString(key));
				}
			} catch( JSONException err ) {
				return;
			}
		}
		public String getAgent() { return a; }
		public String getCode() { return c; }
		public String getPolicy() {
			switch( p ) {
				case "p": return "paused";
				case "r": return "random";
				case "f": return "fixed";
				case "a": return "adaptive";
				case "c": return "control";
				case "s": return "sticky";
				case "b": return "bot";
				default: return p;
			}
		}
		public String getMeta(String key) { return meta.get(key); }
		public String toString() {
			return "{ agentCode: \""+a+"\", optionCode: \""+c+"\", policy: \""+p+"\" }";
		}
	}
	public static class GoalResponse {
		private String g;
		private HashMap<String, Double> rs = new HashMap<String, Double>();
		GoalResponse(JSONObject item) {
			try {
				g = item.getString("g");
				JSONArray a = item.getJSONArray("rs");
				for(int i = 0; i < a.length(); i++ ) {
					JSONObject o = a.getJSONObject(i);
					rs.put(o.getString("a"), o.getDouble("v"));
				}
			} catch( JSONException err ) {
				return;
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

		public String goalCode() { return g; }
		public Double acceptedValue(String agentCode) {
			if( rs == null || ! rs.containsKey(agentCode) ) {
				return 0.0;
			}
			return rs.get(agentCode);
		}
	}
}
