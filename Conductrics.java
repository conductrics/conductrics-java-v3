//vim: let g:syntastic_java_javac_classpath="./json-20190722.jar"

package com.conductrics;


import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Iterator;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.MalformedURLException;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.InputStream;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Conductrics {

	public static interface Callback<T> {
		public void onValue(T value);
	}

	private static void log(String line) { System.out.println("Conductrics: " + line); }
	private static void writeAll(String data, OutputStream out) throws IOException {
		DataOutputStream o = new DataOutputStream( out );
		o.writeBytes( data );
		o.flush();
		o.close();
	}
	private static String readAll(InputStream s) {
		if( s == null ) return "";
		java.util.Scanner sc = new java.util.Scanner(s).useDelimiter("\\A");
		return sc.hasNext() ? sc.next() : "";
	}

	private String apiUrl;
	private String apiKey;

	public Conductrics(String apiUrl, String apiKey) {
		this.apiUrl = apiUrl;
		this.apiKey = apiKey;
	}

	public static class RequestOptions {
		private HashMap<String, String> params = new HashMap<String, String>(); // Ultimately, a set of RequestOptions will become parameters to an HTTP request
		private HashMap<String, String> input = new HashMap<String, String>();
		private List<String> _traits = null; // Traits are a special set of parameters that we have to serialize differently
		private int _timeout = 0; // Timeout is just an internal option, and not sent with the params
		private String defaultOption = "A"; // not currently settable
		private HashMap<String, String> defaultOptions = new HashMap<String, String>();
		private HashMap<String, String> forceOptions = new HashMap<String, String>();

		public String getSession() { return params.get("session"); }
		public RequestOptions setSession(String s) {
			params.put("session", s);
			return this;
		}

		public String getUserAgent() { return params.get("ua"); }
		public RequestOptions setUserAgent(String s) {
			params.put("ua", s);
			return this;
		}

		public HashMap<String, String> getInputs() { return input; }
		public String getInput(String key) { return input.get(key); }
		public RequestOptions setInput(String key, String val) {
			input.put(key, val);
			return this;
		}

		public String[] getTraits() { return params.get("traits").split(","); }
		public RequestOptions setTraits(List<String> v) {
			params.put("traits", String.join(",", v));
			return this;
		}
		public RequestOptions setTraits(String ... v) {
			params.put("traits", String.join(",", v));
			return this;
		}

		public Map<String, String> getParams() { return new HashMap<String, String>(params); }
		public RequestOptions setParam(String key, String value) {
			params.put(key, value);
			return this;
		}

		public int getTimeout() { return _timeout; }
		public RequestOptions setTimeout(int ms) {
			_timeout = ms;
			return this;
		}

		public String getDefault(String agentCode) { return defaultOptions.get(agentCode); }
		public RequestOptions setDefault(String agentCode, String optionCode) {
			defaultOptions.put(agentCode, optionCode);
			return this;
		}

		public String getForcedOutcome(String agentCode) { return forceOptions.get(agentCode); }
		public RequestOptions forceOutcome(String agentCode, String optionCode) {
			forceOptions.put(agentCode, optionCode);
			return this;
		}
	}
	private static class HTTP {
		// use a small thread-pool to handle all our HTTP Requests asynchronously
		private static ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 10, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

		private static class RequestRunner implements Runnable {
			private String method;
			private String url;
			private String body;
			private int timeout;
			private Map<String, String> headers;
			private Callback<String> callback;
			public RequestRunner(String method, String url, String body, int timeout, Map<String, String> headers, Callback<String> callback) {
				this.method = method.toUpperCase();
				this.url = url;
				this.body = body;
				this.timeout = timeout;
				this.headers = headers;
				this.callback = callback;
			}

			@Override
			public void run() {
				log(method + ": " + body + " " + url);
				URL u;
				HttpURLConnection conn;
				if( url == null ) {
					log("HTTP RequestRunner url cannot be null, aborting request");
					callback.onValue( null );
					return;
				}

				if( method == "POST" && body == null ) body = "";

				// Try to parse the given URL safely.
				try {
					u = new URL(url);
				} catch( MalformedURLException e ) {
					log("MalformedURLException(url="+url+"): "+e.toString());
					callback.onValue( null );
					return;
				}

				try {
					conn = (HttpURLConnection)u.openConnection();
					conn.setRequestMethod(method);
					if( headers != null ) {
						for( String key : headers.keySet() ) {
							conn.setRequestProperty(key, headers.get(key));
						}
					}
					conn.setConnectTimeout(1000); // we shouldn't have to wait long just to get a socket
					if( timeout > 0 ) {
						conn.setReadTimeout(timeout); // we may have to wait for the server response though, once connected
					}
					conn.setUseCaches( false );
					conn.setDoInput( true );
					if( body != null ) {
						conn.setDoOutput( true );
					}
				} catch( IOException e ) {
					log("IOException("+method+" "+url+"): "+e.toString());
					callback.onValue( null );
					return;
				}

				try {
					if( body != null ) writeAll( body, conn.getOutputStream() );
				} catch( IOException e ) {
					log("IOException("+method+" "+url+"): "+e.toString() + " " + readAll( conn.getErrorStream() ));
					callback.onValue( null );
					return;
				}

				try {
					callback.onValue( readAll( conn.getInputStream() ));
				} catch( IOException e ) {
					log("IOException("+method+" "+url+"): "+e.toString() + readAll( conn.getErrorStream() ));
					callback.onValue( null );
					return;
				}
			}
		}

		public static void request(String method, String url, String body, int timeout, Map<String, String> headers, Callback<String> callback) {
			if( executor.isShutdown() || executor.isTerminated() || executor.isTerminating() ) {
				callback.onValue( null );
			} else {
				executor.execute( new RequestRunner(method, url, body, timeout, headers, callback) );
			}
		}
	}

	public void Exec( RequestOptions opts, JSONArray commands, Callback<ExecResponse> callback) {
		exec(opts, commands, new Callback<JSONObject>() {
			public void onValue(JSONObject data) {
				callback.onValue(new ExecResponse( data ));
			}
		});
	}
	private void exec( RequestOptions opts, JSONArray commands, Callback<JSONObject> callback) {
		try {
			String body = "{ \"commands\": " + commands.toString();
			JSONObject inputs = new JSONObject(opts.getInputs());
			if( inputs.length() > 0 ) {
				body += ", \"inputs\": " + inputs.toString();
			}
			body += " }";

			HashMap<String, String> headers = new HashMap<String, String>();
			headers.put("content-type", "application/json");
			headers.put("content-length", String.format("%d", body.length()));
			String url = apiUrl + "?apikey=" + apiKey;
			try {
				Map<String, String> params = opts.getParams();
				for( String key : params.keySet() ) {
					url += "&"+key+"="+URLEncoder.encode(params.get(key), "utf-8");
				}
			} catch( java.io.UnsupportedEncodingException e) {
				log("Failed to produce a valid API url: " + e.getLocalizedMessage());
				callback.onValue( null );
				return;
			}
			HTTP.request("POST", url, body, opts.getTimeout(), headers, new Callback<String>() {
				public void onValue(String responseBody) {
					if( responseBody == null ) {
						callback.onValue( null );
						return;
					}
					log("POST response: " + responseBody);
					JSONObject result = new JSONObject(responseBody);
					if( result.getInt("status") == 200 ) {
						callback.onValue(result.getJSONObject("data"));
					} else {
						log("exec failed: " + responseBody);
					}
				}
			});
		} catch (JSONException err ) {
			log("JSONException in exec(): " + err.getLocalizedMessage());
		}
	}

	public void Select(RequestOptions opts, String agentCode, Callback<SelectResponse> callback) {
		JSONArray commands = new JSONArray().put(new JSONObject().put("a", agentCode));
		this.Exec( opts, commands, new Callback<ExecResponse>() {
			public void onValue(ExecResponse response) {
				callback.onValue( response == null
					? new SelectResponse(agentCode, opts.getDefault(agentCode), "x")
					: response.getSelection( agentCode, opts.getDefault(agentCode) ));
			}
		});
	}

	public void Reward(RequestOptions opts, String goalCode, Callback<GoalResponse> callback) { Reward( opts, goalCode, 1.0, callback ); }
	public void Reward(RequestOptions opts, String goalCode, Double value, Callback<GoalResponse> callback) {
		JSONArray commands = new JSONArray().put(new JSONObject().put("g", goalCode).put("v", value));
		this.Exec( opts, commands, new Callback<ExecResponse>() {
			public void onValue(ExecResponse response) {
				callback.onValue( response == null
					? new GoalResponse(goalCode)
					: response.getReward( goalCode ));
			}
		});
	}

	public static class ExecResponse {
		private HashMap<String, SelectResponse> sels;
		private HashMap<String, GoalResponse> rewards;
		private List<String> traits;
		private List<String> log;
		public ExecResponse(JSONObject response) {
			sels = new HashMap<>();
			rewards = new HashMap<>();
			traits = new LinkedList<>();
			log = new LinkedList<String>();
			if( response == null ) {
				return;
			}
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
				return;
			}
		}
		public SelectResponse getSelection(String agentCode, String defaultOption) {
			if( sels == null || ! sels.containsKey(agentCode) ) {
				return new SelectResponse(agentCode, defaultOption, "x");
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
		public List<String> getLog() {
			return log;
		}
	}
	public static class SelectResponse {
		private String a;
		private String c;
		private String p;
		private HashMap<String, String> meta;
		private ExecResponse execResponse;
		SelectResponse(String A, String C, String P) {
			meta = new HashMap<>();
			a = A;
			c = C;
			p = P;
		}
		SelectResponse(JSONObject item, ExecResponse source) { // note: this is not the whole response,
			try {
				execResponse = source;
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
				case "x": return "none";
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
			return "{ \"agentCode\": \""+a+"\", \"optionCode\": \""+c+"\", \"policy\": \""+p+"\" }";
		}
		public ExecResponse getExecResponse() {
			return execResponse;
		}
	}
	public static class GoalResponse {
		private String g;
		private HashMap<String, Double> rs = new HashMap<String, Double>();
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
