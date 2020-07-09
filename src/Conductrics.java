//vim: let g:syntastic_java_javac_classpath="./json-20190722.jar"

package com.conductrics;


import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.Arrays;
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

import com.conductrics.Policy;
import com.conductrics.Status;
import com.conductrics.RequestOptions;
import com.conductrics.Callback;

/** Provides a wrapper around the Conductrics HTTP API. */
public class Conductrics {

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
		if( opts == null || opts.getOffline() ) {
			if( callback != null ) callback.onValue( new ExecResponse( new Exception("offline")));
			return;
		}
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
				if( callback != null ) callback.onValue( new ExecResponse( e ));
				return;
			}
			HTTP.request("POST", url, body, opts.getTimeout(), headers, new CallbackWithError<String>() {
				public void onValue(String responseBody) {
					if( responseBody == null ) {
						if( callback != null ) callback.onValue(new ExecResponse(new Exception("response body is null")));
						return;
					}
					log("POST response: " + responseBody);
					JSONObject result = new JSONObject(responseBody);
					if( result.getInt("status") == 200 ) {
						JSONObject data = result.getJSONObject("data");
						if( data != null ) {
							if( callback != null ) callback.onValue(new ExecResponse(data));
						} else {
							if( callback != null ) callback.onValue(new ExecResponse(new Exception("no 'data' key in JSON response")));
						}
					} else {
						if( callback != null ) callback.onValue(new ExecResponse(new Exception("bad 'status' value in JSON response: " + responseBody)));
					}
				}
				public void onError(Exception err) {
					if( callback != null ) callback.onValue(new ExecResponse(err));
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
		if( opts.getOffline() ) {
			if( callback != null ) callback.onValue( new SelectResponse(agentCode, opts.getDefault(agentCode), "x", new Exception("offline")));
			return;
		}
		JSONArray commands = new JSONArray();
		JSONObject command = new JSONObject().put("a", agentCode);
		if( opts.getProvisional() ) {
			command.put("s", "p");
		} else if( opts.getConfirm() ) {
			command.put("s", "ok");
		}
		List<String> allowed = opts.getAllowedVariants(agentCode);
		if( allowed != null ) {
			command.put("c", new JSONArray(allowed));
		}
		commands.put(command);
		this.exec( opts, commands, new Callback<ExecResponse>() {
			public void onValue(ExecResponse response) {
				if( callback == null ) return;
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
			if( opts.getOffline() ) {
				result.put(agent, new SelectResponse(agent, opts.getDefault(agent), "x", new Exception("offline")));
			} else {
				JSONObject command = new JSONObject().put("a", agent);
				if( opts.getProvisional() ) {
					command.put("s", "p");
				} else if( opts.getConfirm() ) {
					command.put("s", "ok");
				}
				List<String> allowed = opts.getAllowedVariants(agent);
				if( allowed != null ) {
					command.put("c", new JSONArray(allowed));
				}
				commands.put(command);
			}
		}
		if( opts.getOffline() ) {
			if( callback != null ) callback.onValue( result );
		} else {
			this.exec( opts, commands, new Callback<ExecResponse>() {
				public void onValue(ExecResponse response) {
					for( String agent : agentCodes ) {
						if( ! result.containsKey(agent) ) {
							result.put(agent, response.getSelection(agent, opts.getDefault(agent)));
						}
					}
					if( callback != null ) callback.onValue( result );
				}
			});
		}
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
		if( opts.getOffline() ) {
			if( callback != null ) callback.onValue( new GoalResponse(goalCode, new Exception("offline")));
			return;
		}
		JSONArray commands = new JSONArray().put(new JSONObject().put("g", goalCode).put("v", value));
		this.exec( opts, commands, new Callback<ExecResponse>() {
			public void onValue(ExecResponse response) {
				if( callback == null ) return;
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

}
