package com.conductrics;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;

/** RequestOptions contains the configuration to be used when calling select(), reward(), or exec(). */
public class RequestOptions {
	private HashMap<String, String> params = new HashMap<String, String>(); // Ultimately, a set of RequestOptions will become parameters to an HTTP request
	private HashMap<String, String> input = new HashMap<String, String>();
	private int _timeout = 2000; // Timeout is just an internal option, and not sent with the params
	private String defaultOption = "A";
	private HashMap<String, String> defaultOptions = new HashMap<String, String>();
	private HashMap<String, List<String>> allowed = new HashMap<>();
	private boolean offline = false;
	private boolean provisional = false;
	private boolean shouldConfirm = false;

	/** Construct a new RequestOptions, and set the session identifier at the same time. */
	public RequestOptions(String sessionId) {
		if( sessionId == null || sessionId.length() == 0 ) {
			// Start with a default random session id
			sessionId = java.util.UUID.randomUUID().toString();
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
	public String getDefault(String agentCode) {
		if( defaultOptions.containsKey(agentCode) ) {
			return defaultOptions.get(agentCode);
		} else {
			return "A";
		}
	}
	/** Set the default optionCode.
	 * This value will be used for a SelectResponse.getCode() if any errors occur.
	 * @param agentCode The code of the agent to apply the default to.
	 * @param optionCode The code of the option to use if there is an error, eg "B".
	 */
	public RequestOptions setDefault(String agentCode, String optionCode) {
		defaultOptions.put(agentCode, optionCode);
		return this;
	}

	/** Get the current value of offline mode. */
	public boolean getOffline() { return offline; }
	/** Enable or disable "offline mode".
	 * When in offline mode, no network requests are made.
	 * All calls to select() return default variations.
	 */
	public RequestOptions setOffline(boolean value) {
		offline = value;
		return this;
	}

	/** Get the (limited set of) variants allowed for an agent.
	 * Can return null, which means all possible variants are allowed.
	 */
	public List<String> getAllowedVariants(String agentCode) {
		return allowed.get(agentCode);
	}
	/** Set the allowed variants for an agent.
	 * Will constrain future calls to select(). 
	 */
	public RequestOptions setAllowedVariants(String agentCode, String... variants) {
		return setAllowedVariants(agentCode, Arrays.asList(variants));
	}
	/** Set the allowed variants for an agent.
	 * Will constrain future calls to select(). 
	 */
	public RequestOptions setAllowedVariants(String agentCode, List<String> variants) {
		if( variants.size() > 0 ) {
			allowed.put(agentCode, variants);
		}
		return this;
	}
}
