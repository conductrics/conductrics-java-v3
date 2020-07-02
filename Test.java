package com.conductrics;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedList;

import com.conductrics.Conductrics.ExecResponse;
import com.conductrics.Conductrics.SelectResponse;
import com.conductrics.Conductrics.GoalResponse;
import com.conductrics.Conductrics.RequestOptions;
import com.conductrics.Conductrics.Callback;
import com.conductrics.Conductrics.Policy;
import com.conductrics.Conductrics.Status;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Test {
	public static void main(String[] args) {
		// run all the tests in a thread pool
		ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 10, 100, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
		// queue them all up, once the queue drains, the process will exit with code 0
		executor.execute(new TestCase());
		executor.execute(new SessionIsSticky());
		executor.execute(new TraitsTest());
		executor.execute(new ParamsTest());
		executor.execute(new DefaultOptionForInvalidAgent());
		executor.execute(new UserAgentTest());
		executor.execute(new InputsTest());
		executor.execute(new OfflineTestExec());
		executor.execute(new OfflineTestSelect());
		executor.execute(new OfflineTestReward());
		executor.execute(new TimeoutTest());
		executor.execute(new SelectMultipleTest());
		executor.execute(new ProvisionalTest());
		executor.execute(new AllowedVariantTest());
		executor.execute(new MetaDataTest());
		executor.execute(new MetaDataNullTest());
		executor.execute(new ReuseOptionTest());
		executor.execute(new RewardTest());
		executor.execute(new DoubleRewardTest());
		executor.execute(new FullSessionOfflineTest());
		executor.execute(new ProvisionalRewardTest());
	}

	static void _assertEqual(String a, String b) throws AssertionError {
		if( a == null && b == null ) return;
		if( a != null && b == null ) assert false : a + " should equal null";
		if( a == null && b != null ) assert false : "null should equal " + b;
		// System.out.println("Assert Equal: " + a + " == " + b + " " + a.equals(b));
		assert a.equals(b) : "'" + a + "' should equal '" + b + "'";
	}
	static void _assertOneOf(String a, String... b) {
		assert a != null;
		for( String s: b ) if( (a == null && s == null) || a.equals(s) ) return;
		assert false : a + " should be one of: " + String.join(",", b);
	}
	static void _assertInList(String a, List<String> b) {
		assert b.contains(a);
	}

	static class TestCase implements Runnable {
		public boolean finished = false;
		protected Conductrics api = new Conductrics(
			"https://api-staging-2020.conductrics.com/owner_jesse/v3/agent-api",
			"api-JQXuiRRrCRkKPXPhChMC"
		);

		public TestCase() {}
		public void finish(Error err) {
			String testName = this.getClass().getName().split("\\$")[1];
			if( ! finished ) {
				finished = true;
				System.out.println(
					(err == null ? "-- PASSED --" : "-! FAILED !-" + err.toString())
					+ " : " + testName
				);
				if( err != null ) {
					System.exit(1);
				}
			} else {
				System.out.println("-! FAILED !- : " + testName + " attempted double-finish");
			}
		}
		public void run() {
			finish(null);
		}
	}

	static class SessionIsSticky extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null);
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse firstOutcome) {
					try {
						_assertEqual( firstOutcome.getAgent(), "a-example");
						_assertOneOf( firstOutcome.getCode(), "A", "B" );
						assert firstOutcome.getPolicy() == Policy.Random : "getPolicy() should be random";
						assert firstOutcome.getError() == null : "getError() should be null";
						api.select( opts, "a-example", new Callback<SelectResponse>() {
							public void onValue(SelectResponse secondOutcome) {
								try {
									_assertEqual( secondOutcome.getCode(), firstOutcome.getCode() );
									assert secondOutcome.getPolicy() == Policy.Sticky : "getPolicy() should be sticky";
									assert secondOutcome.getError() == null : "getError() should be null";
									finish(null);
								} catch( AssertionError err ) {
									finish(err);
								}
							}
						});
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class TraitsTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setTrait("F", "1")
				.setTrait("F", "2");
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						_assertEqual( outcome.getAgent(), "a-example");
						_assertOneOf( outcome.getCode(), "A", "B" );
						assert outcome.getPolicy() == Policy.Random : "getPolicy() should be random";
						String returnedTraits = String.join(",", outcome.getExecResponse().getTraits());
						_assertEqual( returnedTraits, "cust/F:1,cust/F:2");
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class ParamsTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setParam("debug", "true");
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						_assertEqual( outcome.getAgent(), "a-example");
						_assertOneOf( outcome.getCode(), "A", "B" );
						assert outcome.getPolicy() == Policy.Random : "getPolicy() should be random";
						assert outcome.getExecResponse().getLog().size() > 0 : "log should not be empty";
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class DefaultOptionForInvalidAgent extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setDefault("a-invalid", "B");
			api.select( opts, "a-invalid", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-invalid");
						_assertEqual( outcome.getCode(), "B" );
						assert outcome.getPolicy() == Policy.None: "getPolicy() should be none";
						_assertEqual( outcome.getError().getMessage(), "unknown agent" );
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class UserAgentTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setParam("debug", "true")
				.setUserAgent("MAGIC STRING");
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						_assertOneOf( outcome.getCode(), "A", "B" );
						assert outcome.getPolicy() == Policy.Random: "getPolicy() should be random";
						_assertInList( "Added trait 'ua/mo:n' (apply)", outcome.getExecResponse().getLog() );
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class InputsTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setInput("foo", "bar");
			// the a-example agent has been (must be) configured to only return A or B, unless given foo=bar as an input
			// then it will always select C
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						_assertEqual( outcome.getCode(), "C" );
						assert outcome.getPolicy() == Policy.Fixed: "getPolicy() should be fixed";
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class OfflineTestSelect extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setOffline(true)
				.setDefault("a-example", "Z");
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						_assertEqual( outcome.getCode(), "Z" );
						assert outcome.getPolicy() == Policy.None: "getPolicy() should be none";
						_assertEqual( outcome.getError().getMessage(), "offline" );
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}
	static class OfflineTestExec extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setOffline(true);
			api.exec( opts, null, new Callback<ExecResponse>() {
				public void onValue(ExecResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						assert outcome.getJSONObject() == null : "Outcome should have no JSON associated.";
						_assertEqual( outcome.getError().getMessage(), "offline" );
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}
	static class OfflineTestReward extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setOffline(true);
			api.reward( opts, "g-example", new Callback<GoalResponse>() {
				public void onValue(GoalResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getGoalCode(), "g-example");
						assert outcome.getAcceptedValue("a-example") == 0.0 : "Goal should not accept any value";
						_assertEqual( outcome.getError().getMessage(), "offline" );
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}
	static class FullSessionOfflineTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setOffline(true);
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						String variant = outcome.getCode();
						assert outcome.getPolicy() == Policy.None: "getPolicy() should be None";
						api.reward( opts, "g-example", new Callback<GoalResponse>() {
							public void onValue(GoalResponse outcome) {
								try {
									assert outcome != null : "Outcome cannot be null";
									assert "g-example".equals(outcome.getGoalCode()) : "Goal should be g-example";
									assert outcome.getAcceptedValue("a-example") == 0.0 : "Accepted value should be 0.0";
									finish(null);
								} catch( AssertionError err ) {
									finish(err);
								}
							}
						});
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class TimeoutTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setTimeout(1) // after 1 ms, basically instantly
				.setDefault("a-example", "E");
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						_assertEqual( outcome.getCode(), "E" );
						assert outcome.getPolicy() == Policy.None: "getPolicy() should be none";
						_assertEqual( outcome.getError().getMessage(), "Read timed out" );
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class SelectMultipleTest extends TestCase {
		@Override public void run() {
			List<String> agents = Arrays.asList("a-example", "a-example");
			RequestOptions opts = new RequestOptions(null);
			api.select( opts, agents, new Callback<Map<String,SelectResponse>>() {
				public void onValue(Map<String,SelectResponse> outcomes) {
					try {
						SelectResponse outcome = outcomes.get("a-example");
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						assert outcome.getPolicy() == Policy.Sticky: "getPolicy() should be sticky: " + outcome.getPolicy().toString();
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class ProvisionalTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setProvisional(true);
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						assert outcome.getStatus() == Status.Provisional : "getStatus() should be Provisional";
						opts.setConfirm(true);
						api.select(opts, "a-example", new Callback<SelectResponse>() {
							public void onValue(SelectResponse outcome2) {
								try {
									assert outcome2 != null : "Outcome cannot be null";
									_assertEqual( outcome2.getAgent(), "a-example");
									assert outcome2.getStatus() == Status.Confirmed: "getStatus() should be Provisional";
									finish(null);
								} catch( AssertionError err ) {
									finish(err);
								}
							}
						});
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class AllowedVariantTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setAllowedVariants("a-example", "B");
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						_assertEqual( outcome.getCode(), "B");
						assert outcome.getPolicy() == Policy.Random : "getPolicy() should be Random";
						assert outcome.getStatus() == Status.Confirmed : "getStatus() should be Confirmed";
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class MetaDataTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setAllowedVariants("a-example", "A");
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						_assertEqual( outcome.getCode(), "A");
						assert outcome.getPolicy() == Policy.Random : "getPolicy() should be Random";
						assert outcome.getStatus() == Status.Confirmed : "getStatus() should be Confirmed";
						assert "12345".equals(outcome.getMeta("magic")) : "getMeta('magic') should be '12345' got " + outcome.getMeta("magic");
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class MetaDataNullTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setAllowedVariants("a-example", "B");
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						_assertEqual( outcome.getCode(), "B");
						assert outcome.getPolicy() == Policy.Random : "getPolicy() should be Random";
						assert outcome.getStatus() == Status.Confirmed : "getStatus() should be Confirmed";
						assert outcome.getMeta("magic") == null : "getMeta('magic') should be null";
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class ReuseOptionTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setProvisional(true);
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						assert outcome.getPolicy() == Policy.Random : "getPolicy() should be Random";
						assert outcome.getStatus() == Status.Provisional : "getStatus() should be Provisional";
						opts.setConfirm(true);
						api.select( opts, "a-example", new Callback<SelectResponse>() {
							public void onValue(SelectResponse outcome) {
								assert outcome != null : "Outcome cannot be null";
								_assertEqual( outcome.getAgent(), "a-example");
								assert outcome.getPolicy() == Policy.Sticky: "getPolicy() should be Sticky";
								assert outcome.getStatus() == Status.Confirmed : "getStatus() should be Confirmed";
							}
						});
						finish(null);
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class RewardTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null);
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						String variant = outcome.getCode();
						assert outcome.getPolicy() == Policy.Random : "getPolicy() should be Random";
						assert outcome.getStatus() == Status.Confirmed : "getStatus() should be Confirmed";
						api.reward( opts, "g-example", new Callback<GoalResponse>() {
							public void onValue(GoalResponse outcome) {
								try {
									assert outcome != null : "Outcome cannot be null";
									assert "g-example".equals(outcome.getGoalCode()) : "Goal should be g-example";
									assert outcome.getAcceptedValue("a-example") == 1.0 : "Accepted value should be 1.0";
									finish(null);
								} catch( AssertionError err ) {
									finish(err);
								}
							}
						});
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class DoubleRewardTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null);
			api.select( opts, "a-example", new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), "a-example");
						String variant = outcome.getCode();
						assert outcome.getPolicy() == Policy.Random : "getPolicy() should be Random";
						assert outcome.getStatus() == Status.Confirmed : "getStatus() should be Confirmed";
						api.reward( opts, "g-example", new Callback<GoalResponse>() {
							public void onValue(GoalResponse outcome) {
								try {
									assert outcome != null : "Outcome cannot be null";
									assert "g-example".equals(outcome.getGoalCode()) : "Goal should be g-example";
									assert outcome.getAcceptedValue("a-example") == 1.0 : "Accepted value should be 1.0";
									api.reward( opts, "g-example", new Callback<GoalResponse>() {
										public void onValue(GoalResponse outcome) {
											try {
												assert outcome != null : "Outcome cannot be null";
												assert "g-example".equals(outcome.getGoalCode()) : "Goal should be g-example";
												assert outcome.getAcceptedValue("a-example") == 0.0 : "Accepted value should be 0.0, already rewarded.";
												finish(null);
											} catch( AssertionError err ) {
												finish(err);
											}
										}
									});
								} catch( AssertionError err ) {
									finish(err);
								}
							}
						});
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

	static class ProvisionalRewardTest extends TestCase {
		@Override public void run() {
			RequestOptions opts = new RequestOptions(null)
				.setProvisional(true);
			String agentCode = "a-example";
			String goalCode = "g-example";
			api.select( opts, agentCode, new Callback<SelectResponse>() {
				public void onValue(SelectResponse outcome) {
					try {
						assert outcome != null : "Outcome cannot be null";
						_assertEqual( outcome.getAgent(), agentCode);
						assert outcome.getStatus() == Status.Provisional : "getStatus() should be Provisional";
						api.reward(opts, goalCode, new Callback<GoalResponse>() {
							public void onValue(GoalResponse goalResponse) {
								try {
									assert goalResponse != null : "GoalResponse cannot be null";
									_assertEqual( goalResponse.getGoalCode(), goalCode );
									assert goalResponse.getAcceptedValue(agentCode) == 0.0 : "Goals should not be accepted for provisional selections";
									opts.setConfirm(true);
									api.select(opts, agentCode, new Callback<SelectResponse>() {
										public void onValue(SelectResponse outcome2) {
											try {
												assert outcome2 != null : "Outcome cannot be null";
												_assertEqual( outcome2.getAgent(), agentCode);
												assert outcome2.getStatus() == Status.Confirmed: "getStatus() should be Provisional";
												api.reward(opts, goalCode, new Callback<GoalResponse>() {
													public void onValue(GoalResponse goalResponse2) {
														try {
															assert goalResponse2 != null : "GoalResponse (#2) cannot be null";
															_assertEqual( goalResponse2.getGoalCode(), goalCode );
															assert goalResponse2.getAcceptedValue(agentCode) == 1.0 : "Goals should be accepted once a provisional selection is confirmed.";
															finish(null);
														} catch( AssertionError err ) {
															finish(err);
														}
													}
												});
											} catch( AssertionError err ) {
												finish(err);
											}
										}
									});
								} catch( AssertionError err ) {
									finish(err);
								}
							}
						});
					} catch( AssertionError err ) {
						finish(err);
					}
				}
			});
		}
	}

}
