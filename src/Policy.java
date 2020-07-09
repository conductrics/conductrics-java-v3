package com.conductrics;

/** Indicates the Policy used to make a selection. @see SelectResponse.getPolicy */
public enum Policy {
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
