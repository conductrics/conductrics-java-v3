package com.conductrics;

import com.conductrics.Conductrics.SelectResponse;
import com.conductrics.Conductrics.GoalResponse;
import com.conductrics.Conductrics.Callback;

public class Test {
	public static void main(String[] args) {
		Conductrics api = new Conductrics(
			"https://api-staging-v3.conductrics.com/owner_jesse/v3/agent-api",
			"api-JQXuiRRrCRkKPXPhChMC"
		);
		String sessionId = "s-" + String.format("%f", Math.random()).replace('.','0');
		Conductrics.RequestOptions session = new Conductrics.RequestOptions()
			.setSession(sessionId)         // Required
			.setUserAgent("My Custom User-Agent") // Optional
			.setTimeout(2000)              // Optional
			.setTraits("F1", "F2")         // Optional
			.setParam("debug", "true");// Optional, repeatable, adds arbitrary URL params
		api.Select( session, "a-example", new Callback<SelectResponse>() {
			public void onValue(SelectResponse outcome) {
				System.out.println("Outcome: " + outcome.toString());
				api.Reward( session, "g-example", 1.0, new Callback<GoalResponse>() {
					public void onValue(GoalResponse goal) {
						System.out.println("Goal: " + goal.toString());
					}
				});
			}
		});

	}
}
