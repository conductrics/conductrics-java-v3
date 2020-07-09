package com.conductrics;

/** A Callback<T> is used to handle an asynchronous result of type T.  */
public interface Callback<T> {
	public void onValue(T value);
}
