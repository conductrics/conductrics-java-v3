package com.conductrics.http;

public class ConductricsTest {
	public static void main(String[] args) {
		Conductrics api = new Conductrics(
			"https://api-staging-v3.conductrics.com/owner_jesse/v3/agent-api",
			"api-JQXuiRRrCRkKPXPhChMC"
		);
		String sessionId = "s-" + String.format("%f", Math.random()).replace('.','0');
		Conductrics.RequestOptions session = new Conductrics.RequestOptions()
			.session(sessionId)         // Required
			.ua("My Custom User-Agent") // Optional
			.timeout(2000)              // Optional
			.traits("F1", "F2")         // Optional
			.withParam("debug", "true");// Optional, repeatable, adds arbitrary URL params
		Conductrics.SelectResponse outcome = api.Select( session, "a-example" );
		Conductrics.GoalResponse goal = api.Reward( session, "g-example", 1.0 );
	}
}
