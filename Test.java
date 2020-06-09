package com.conductrics;

import java.util.List;
import java.util.LinkedList;

import com.conductrics.Conductrics.SelectResponse;
import com.conductrics.Conductrics.GoalResponse;
import com.conductrics.Conductrics.RequestOptions;
import com.conductrics.Conductrics.Callback;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Test {
	public static void main(String[] args) {
		// run all the tests in a thread pool
		ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 2, 100, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
		// queue them all up, once the queue drains, the process will exit with code 0
		// executor.execute(new TestCase());
		// executor.execute(new SessionIsSticky());
		// executor.execute(new TraitsWork());
		// executor.execute(new ParamsWork());
		executor.execute(new DefaultOptionForInvalidAgent());
	}

	public static void _assertEqual(String a, String b) throws AssertionError {
		if( a == null && b == null ) return;
		if( a != null && b == null ) assert false : a + " should equal null";
		if( a == null && b != null ) assert false : "null should equal " + b;
		// System.out.println("Assert Equal: " + a + " == " + b + " " + a.equals(b));
		assert a.equals(b) : a + " should equal " + b;
	}
	public static void _assertOneOf(String a, String... b) {
		// System.out.println("Assert "+a+" is one of: "+String.join(",", b));
		for( String s: b ) if( a.equals(s) ) return;
		assert false : a + " should be one of: " + String.join(",", b);
	}

	public static class TestCase implements Runnable {
		public boolean started = false;
		public boolean finished = false;
		protected Conductrics api = new Conductrics(
			"https://api-staging-2020.conductrics.com/owner_jesse/v3/agent-api",
			"api-JQXuiRRrCRkKPXPhChMC"
		);

		public TestCase() {}
		public void finish(Error optional) {
			String testName = this.getClass().getName().split("\\$")[1];
			if( ! finished ) {
				finished = true;
				System.out.println(testName + ": " +
					(optional == null ? "PASSED" : "FAILED " + optional.toString()));
			} else {
				System.out.println(testName + ": Test is faulty: attempted double-finish");
			}
		}
		public void run() {
			finish(null);
		}
	}

	public static class SessionIsSticky extends TestCase {
		@Override public void run() {
			started = true;
			String sessionId = "s-" + String.format("%f", Math.random()).replace('.','0');
			RequestOptions opts = new RequestOptions()
				.setSession(sessionId);
			api.Select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse firstOutcome) {
					try {
						_assertEqual( firstOutcome.getAgent(), "a-example");
						_assertOneOf( firstOutcome.getCode(), "A", "B" );
						_assertEqual( firstOutcome.getPolicy(), "random");
						api.Select( opts, "a-example", new Callback<SelectResponse>() {
							public void onValue(SelectResponse secondOutcome) {
								try {
									_assertEqual( secondOutcome.getCode(), firstOutcome.getCode() );
									_assertEqual( secondOutcome.getPolicy(), "sticky" );
								} catch( AssertionError err ) {
									finish(err);
									return;
								}
								finish(null);
							}
						});
					} catch( AssertionError err ) {
						finish(err);
						return;
					}
				}
			});
		}
	}

	public static class TraitsWork extends TestCase {
		@Override public void run() {
			started = true;
			String sessionId = "s-" + String.format("%f", Math.random()).replace('.','0');
			RequestOptions opts = new RequestOptions()
				.setSession(sessionId)
				.setTraits("F:1", "F:2");
			api.Select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						_assertEqual( outcome.getAgent(), "a-example");
						_assertOneOf( outcome.getCode(), "A", "B" );
						_assertEqual( outcome.getPolicy(), "random");
						_assertEqual( String.join(",", outcome.getExecResponse().getTraits()), "cust/F:1,cust/F:2");
					} catch( AssertionError err ) {
						finish(err);
						return;
					}
					finish(null);
				}
			});
		}
	}

	public static class ParamsWork extends TestCase {
		@Override public void run() {
			started = true;
			String sessionId = "s-" + String.format("%f", Math.random()).replace('.','0');
			RequestOptions opts = new RequestOptions()
				.setSession(sessionId)
				.setParam("debug", "true");
			api.Select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						_assertEqual( outcome.getAgent(), "a-example");
						_assertOneOf( outcome.getCode(), "A", "B" );
						_assertEqual( outcome.getPolicy(), "random");
						assert outcome.getExecResponse().getLog().size() > 0 : "log should not be empty";
					} catch( AssertionError err ) {
						finish(err);
						return;
					}
					finish(null);
				}
			});
		}
	}

	public static class DefaultOptionForInvalidAgent extends TestCase {
		@Override public void run() {
			started = true;
			RequestOptions opts = new RequestOptions()
				.setSession("1234")
				.setDefault("a-invalid", "B");
			api.Select( opts, "a-invalid", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-invalid");
						_assertEqual( outcome.getCode(), "B" );
						_assertEqual( outcome.getPolicy(), "none");
					} catch( AssertionError err ) {
						finish(err);
						return;
					}
					finish(null);
				}
			});
		}
	}
}
