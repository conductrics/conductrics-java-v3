//vim: let g:syntastic_java_javac_classpath="./json-20190722.jar"

package com.conductrics;


import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Iterator;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.InputStream;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Conductrics {

	/** A Callback<T> is used to handle an asynchronous result of type T.
	 */
	public static interface Callback<T> {
		public void onValue(T value);
	}
	/** CallbackWithError<T> is used internally, to propagate possible errors as well as values.
	 * Errors encountered this way are attached to result objects, eg SelectResponse, as getError()
	 */
	private static interface CallbackWithError<T> extends Callback<T> {
		public void onError(Exception err);
	}

	private static void log(String line) {
		System.out.print("Conductrics: ");
		System.out.println(line);
	}
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

	/** Construct an API instance using an API URL and an API Key
	 * @param apiUrl an absolute URL, taken from the Conductrics Console > Developers > API Keys section.
	 * @param apiKey a Conductrics API Key, from the Console.
	 */
	public Conductrics(String apiUrl, String apiKey) {
		this.apiUrl = apiUrl;
		this.apiKey = apiKey;
	}
	private String apiUrl;
	private String apiKey;

	/** RequestOptions contains the configuration to be used when calling select(), reward(), or exec(). */
	public static class RequestOptions {
		private HashMap<String, String> params = new HashMap<String, String>(); // Ultimately, a set of RequestOptions will become parameters to an HTTP request
		private HashMap<String, String> input = new HashMap<String, String>();
		private int _timeout = 2000; // Timeout is just an internal option, and not sent with the params
		private String defaultOption = "A"; // not currently settable
		private HashMap<String, String> defaultOptions = new HashMap<String, String>();
		private HashMap<String, String> forceOptions = new HashMap<String, String>();
		private boolean provisional = false;
		private boolean shouldConfirm = false;

		/** Construct a new RequestOptions, and set the session identifier at the same time. */
		public RequestOptions(String sessionId) {
			if( sessionId == null || sessionId.length() == 0 ) {
				// Start with a default random session id
				sessionId = "s-" + String.format("%f", Math.random()).replace('.','0');
			}
			params.put("session", sessionId);
		}

		/** Return the current provisional status of these requests. */
		public boolean getProvisional() { return provisional; }
		/** Set the current provisional status of these requests.
		 * Requests made with provisional status enabled must later be confirmed, using setConfirm(true).
		 */
		public RequestOptions setProvisional(boolean value) {
			provisional = value;
			if( value ) shouldConfirm = false;
			return this;
		}

		/** Return whether this request will confirm a prior provisional selection. */
		public boolean getConfirm() { return shouldConfirm; }
		/** Set whether this request will confirm a prior provisional selection. */
		public RequestOptions setConfirm(boolean value) {
			shouldConfirm = value;
			if( value ) provisional = false;
			return this;
		}

		/** Return the session identifier for this request. */
		public String getSession() { return params.get("session"); }
		/** Set the session identifier String that will be used for this request. */
		public RequestOptions setSession(String s) {
			params.put("session", s);
			return this;
		}

		/** Return the value to send as the 'User-Agent' of the client. */
		public String getUserAgent() { return params.get("ua"); }
		/** Set the 'User-Agent' of the client. */
		public RequestOptions setUserAgent(String s) {
			params.put("ua", s);
			return this;
		}

		/** Return the set of all "input" values that will be sent with the request.
		 * These "inputs" are used in rules in the Conductrics Console.  */
		public HashMap<String, String> getInputs() { return input; }
		/** Returns one "input" value to be sent with the request.  */
		public String getInput(String key) { return input.get(key); }
		/** Set one "input" value, to be sent along with the request.
		 * These "input" values can be used in Targeting rules, on the Agent screen, of the Console.
		 */
		public RequestOptions setInput(String key, String val) {
			input.put(key, val);
			return this;
		}
		public RequestOptions setInput(String key, double value) {
			input.put(key, String.format("%f", value));
			return this;
		}
		public RequestOptions setInput(String key, long value) {
			input.put(key, String.format("%d", value));
			return this;
		}
		public RequestOptions setInput(String key, boolean value) {
			input.put(key, value ? "true" : "false");
			return this;
		}

		private List<String> traits = new LinkedList<String>();

		/** Return an array of all traits that will be applied to this request. */
		public List<String> getTraits() {
			return traits;
		}
		public RequestOptions setTrait(String group, String trait) {
			traits.add(group + ":" + trait);
			return this;
		}
		public RequestOptions setTrait(String group, long trait) {
			return setTrait(group, String.format("%d", trait));
		}
		public RequestOptions setTrait(String group, double value) {
			return setTrait(group, String.format("%f", value));
		}
		public RequestOptions setTrait(String group, boolean value) {
			return setTrait(group, (value ? "true" : "false"));
		}

		/** Return (a copy of) all the URL parameters to be sent with the request.
		 * Included will be values like "traits", "ua", and "session", which are set elsewhere in RequestOptions.
		 */
		public Map<String, String> getParams() { return new HashMap<String, String>(params); }
		/** Set one custom URL parameter to be sent.
		 * A useful example: setParam("debug", "true"), will cause a resulting ExecResponse.getLog() to be full of messages.
		 * @param key The name of the URL parameter
		 * @param value Any string value, the resulting "&key=value" will be appended to the request.
		 */
		public RequestOptions setParam(String key, String value) {
			params.put(key, value);
			return this;
		}

		/** Return the current timeout, in milliseconds. */
		public int getTimeout() { return _timeout; }
		/** Set the current timeout, in milliseconds. */
		public RequestOptions setTimeout(int ms) {
			_timeout = ms;
			return this;
		}

		/** Return the current default optionCode.
		 * This value will be used for a SelectResponse.getCode() if any errors occur.
		 * Default value is "A", if setDefault() has not called.
		 */
		public String getDefault(String agentCode) { return defaultOptions.get(agentCode); }
		/** Set the default optionCode.
		 * This value will be used for a SelectResponse.getCode() if any errors occur.
		 * @param agentCode The code of the agent to apply the default to.
		 * @param optionCode The code of the option to use if there is an error, eg "B".
		 */
		public RequestOptions setDefault(String agentCode, String optionCode) {
			defaultOptions.put(agentCode, optionCode);
			return this;
		}

		/** Return the current "forced outcome".
		 * If a "forced outcome" is set for an agent, all calls to Select() for that agent, 
		 * with these options, will skip the API request.
		 */
		public String getForcedOutcome(String agentCode) { return forceOptions.get(agentCode); }
		/** Set a "forced outcome" for a particular agent.
		 * Calls to api.Select() that use this RequestOptions will not make a real API request,
		 * but will instead return a SelectResponse with getCode() == the optionCode given here.
		 */
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
			private CallbackWithError<String> callback;
			public RequestRunner(String method, String url, String body, int timeout, Map<String, String> headers, CallbackWithError<String> callback) {
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
					callback.onError( new Exception("invalid cannot be null") );
					return;
				}

				if( method == "POST" && body == null ) body = "";

				// Try to parse the given URL safely.
				try {
					u = new URL(url);
				} catch( MalformedURLException e ) {
					log("MalformedURLException(url="+url+"): "+e.toString());
					callback.onError( e );
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
					if( timeout > 0 ) {
						conn.setConnectTimeout(timeout); // we shouldn't have to wait long just to get a socket
						conn.setReadTimeout(timeout); // we may have to wait for the server response though, once connected
					}
					conn.setUseCaches( false );
					conn.setDoInput( true );
					if( body != null ) {
						conn.setDoOutput( true );
					}
				} catch( IOException e ) {
					log("IOException("+method+" "+url+"): "+e.toString());
					callback.onError( e );
					return;
				}

				try {
					if( body != null ) writeAll( body, conn.getOutputStream() );
				} catch( SocketTimeoutException e ) {
					log("SocketTimeoutException("+method+" "+url+"): "+e.toString());
					callback.onError( e );
				} catch( IOException e ) {
					log("IOException("+method+" "+url+"): "+e.toString());
					callback.onError( e );
					return;
				}

				try {
					callback.onValue( readAll( conn.getInputStream() ));
				} catch( SocketTimeoutException e ) {
					log("SocketTimeoutException("+method+" "+url+"): "+e.toString());
					callback.onError( e );
				} catch( IOException e ) {
					log("IOException("+method+" "+url+"): "+e.toString());
					callback.onError( e );
					return;
				}
			}
		}

		public static void request(String method, String url, String body, int timeout, Map<String, String> headers, CallbackWithError<String> callback) {
			if( executor.isShutdown() || executor.isTerminated() || executor.isTerminating() ) {
				callback.onError( new Exception("threadpool shutdown") );
			} else {
				executor.execute( new RequestRunner(method, url, body, timeout, headers, callback));
			}
		}
	}

	/** Executes any arbitrary API commands, any commands documented in the Runtime API Reference are supported.
	 * The result is given to callback.onValue(), provided by the caller.
	 * @param opts A RequestOptions object that contains the configuration for this request.
	 * @param commands A JSONArray, structrued according to the Runtime API Reference.
	 * @param callback A Callback that will be given an ExecResponse; callback.onValue(ExecResponse)
	 */
	public void exec( RequestOptions opts, JSONArray commands, Callback<ExecResponse> callback) {
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
				params.put("traits", String.join(",", opts.getTraits()));
				for( String key : params.keySet() ) {
					url += "&"+key+"="+URLEncoder.encode(params.get(key), "utf-8");
				}
			} catch( java.io.UnsupportedEncodingException e) {
				log("Failed to produce a valid API url: " + e.getLocalizedMessage());
				callback.onValue( new ExecResponse( e ));
				return;
			}
			HTTP.request("POST", url, body, opts.getTimeout(), headers, new CallbackWithError<String>() {
				public void onValue(String responseBody) {
					if( responseBody == null ) {
						callback.onValue(new ExecResponse(new Exception("response body is null")));
						return;
					}
					log("POST response: " + responseBody);
					JSONObject result = new JSONObject(responseBody);
					if( result.getInt("status") == 200 ) {
						JSONObject data = result.getJSONObject("data");
						if( data != null ) {
							callback.onValue(new ExecResponse(data));
						} else {
							callback.onValue(new ExecResponse(new Exception("no 'data' key in JSON response")));
						}
					} else {
						callback.onValue(new ExecResponse(new Exception("bad 'status' value in JSON response: " + responseBody)));
					}
				}
				public void onError(Exception err) {
					callback.onValue(new ExecResponse(err));
				}
			});
		} catch (JSONException err ) {
			log("JSONException in exec(): " + err.getLocalizedMessage());
		}
	}

	/** Request a selection from a specified agent.
	 * @param opts A RequestOptions object that contains the configuration for this request.
	 * @param agentCode A String that specifies which agent to use.
	 * @param callback A Callback that will be given a SelectResponse; callback.onValue(SelectResponse)
	 */
	public void select(RequestOptions opts, String agentCode, Callback<SelectResponse> callback) {
		String forced = opts.getForcedOutcome(agentCode);
		if( forced != null ) {
			callback.onValue( new SelectResponse(agentCode, forced, "x", new Exception("forced")) );
			return;
		}
		JSONArray commands = new JSONArray();
		JSONObject command = new JSONObject().put("a", agentCode);
		if( opts.getProvisional() ) {
			command.put("s", "p");
		} else if( opts.getConfirm() ) {
			command.put("s", "ok");
		}
		commands.put(command);
		this.exec( opts, commands, new Callback<ExecResponse>() {
			public void onValue(ExecResponse response) {
				if( response == null ) {
					callback.onValue( new SelectResponse(agentCode, opts.getDefault(agentCode), "x", new Exception("null response")) );
				} else if( response.getError() != null ) {
					callback.onValue( new SelectResponse(agentCode, opts.getDefault(agentCode), "x", response.getError()));
				} else {
					callback.onValue( response.getSelection( agentCode, opts.getDefault(agentCode) ));
				}
			}
		});
	}

	/** Request multiple selections at the same time. Can be used to interact with an MVT agent.
	 * @param opts A RequestOptions object that contains the configuration for this request.
	 * @param agentCodes A list of agent codes.
	 * @param callback A Callback that will be given a list of SelectResponse objects.
	 */
	public void select(RequestOptions opts, List<String> agentCodes, Callback<Map<String, SelectResponse>> callback) {
		Map<String, SelectResponse> result = new HashMap<>();
		JSONArray commands = new JSONArray();
		for( String agent : agentCodes ) {
			JSONObject command = new JSONObject().put("a", agent);
			if( opts.getProvisional() ) {
				command.put("s", "p");
			} else if( opts.getConfirm() ) {
				command.put("s", "ok");
			}
			String forced = opts.getForcedOutcome(agent);
			if( forced != null ) {
				result.put(agent, new SelectResponse(agent, forced, "x", new Exception("forced")));
			} else {
				commands.put(command);
			}
		}
		this.exec( opts, commands, new Callback<ExecResponse>() {
			public void onValue(ExecResponse response) {
				for( String agent : agentCodes ) {
					result.put(agent, response.getSelection(agent, opts.getDefault(agent)));
				}
				callback.onValue( result );
			}
		});
	}

	private Callback<GoalResponse> emptyCallback = new Callback<GoalResponse>() {
		public void onValue(GoalResponse response) { }
	};

	/** Notify Conductrics that some Reward value should be registered.
	 * @param opts A RequestOptions object that contains the configuration for this request.
	 * @param goalCode A String that specifies which goal should get the value.
	 * @param callback A Callback that will be given a GoalResponse; callback.onValue(GoalResponse)
	 */
	public void reward(RequestOptions opts, String goalCode, Callback<GoalResponse> callback) { reward( opts, goalCode, 1.0, callback ); }
	/** Notify Conductrics that some Reward value should be registered.
	 * @param opts A RequestOptions object that contains the configuration for this request.
	 * @param goalCode A String that specifies which goal should get the value.
	 */
	public void reward(RequestOptions opts, String goalCode) { reward( opts, goalCode, 1.0, emptyCallback ); }
	/** Notify Conductrics that some Reward value should be registered.
	 * @param opts A RequestOptions object that contains the configuration for this request.
	 * @param goalCode A String that specifies which goal should get the value.
	 * @param value A Double that indicate how much value to register for the goal; if not given, defaults to 1.0
	 */
	public void reward(RequestOptions opts, String goalCode, Double value) { reward( opts, goalCode, value, emptyCallback ); }
	/** Notify Conductrics that some Reward value should be registered.
	 * @param opts A RequestOptions object that contains the configuration for this request.
	 * @param goalCode A String that specifies which goal should get the value.
	 * @param value A Double that indicate how much value to register for the goal; if not given, defaults to 1.0
	 * @param callback A Callback that will be given a GoalResponse; callback.onValue(GoalResponse)
	 */
	public void reward(RequestOptions opts, String goalCode, Double value, Callback<GoalResponse> callback) {
		JSONArray commands = new JSONArray().put(new JSONObject().put("g", goalCode).put("v", value));
		this.exec( opts, commands, new Callback<ExecResponse>() {
			public void onValue(ExecResponse response) {
				if( response == null ) {
					callback.onValue( new GoalResponse(goalCode, new Exception("response is null")) );
				} else if( response.getError() != null ) {
					callback.onValue( new GoalResponse(goalCode, response.getError()) );
				} else {
					callback.onValue( response.getReward( goalCode ));
				}
			}
		});
	}

	/** An ExecReponse describes the response to an API call. */
	public static class ExecResponse {
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
	/** Indicates the Policy used to make a selection. @see SelectResponse.getPolicy */
	public static enum Policy {
		None,
		Paused,
		Random,
		Fixed,
		Adaptive,
		Control,
		Sticky,
		Bot,
		Unknown
	}
	public static enum Status {
		Confirmed,
		Provisional,
		Unknown
	}
	/** Describes the result of a selection. */
	public static class SelectResponse {
		private String a;
		private String c;
		private String p;
		private Status s = Status.Unknown;
		private HashMap<String, String> meta;
		private ExecResponse execResponse;
		SelectResponse(String A, String C, String P) {
			meta = new HashMap<>();
			a = A;
			c = C;
			p = P;
		}
		SelectResponse(String A, String C, String P, Exception err) {
			meta = new HashMap<>();
			a = A; c = C; p = P;
			setError(err);
		}
		SelectResponse(JSONObject item, ExecResponse source) { // note: this is not the whole response,
			try {
				execResponse = source;
				a = item.getString("a");
				c = item.getString("c");
				p = item.getString("p");
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
		/** Return the policy used to make this selection. */
		public Policy getPolicy() {
			switch( p ) {
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
	/** Describes the result of a goal. */
	public static class GoalResponse {
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

}
