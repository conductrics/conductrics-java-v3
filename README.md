
Using via Maven
=====

Into your Android project's build.gradle:

> repositories {
>   maven {
>     url "https://conductrics-maven-repo.s3.amazonaws.com/snapshot/"
>   }
> }

In that same file, under `dependencies {`, add:
> implementation('com.conductrics:Conductrics:1.0.5')

Sync the project.

In `AndroidManifest.xml`:
> <uses-permission android:name="android.permission.INTERNET" />

Use the Conductrics Console to get an API URL and an API Key.

In your Android project, eg in MainActivity.java, add some imports:
> import com.conductrics.Conductrics;
> import com.conductrics.RequestOptions;
> import com.conductrics.SelectResponse;
> import com.conductrics.Callback;

Then later, usually in `onCreate`, you can do:
> Conductrics api = new Conductrics( apiURL, apiKey );
> RequestOptions opts = new RequestOptions( sessionID );
> api.select(opts, "agent-code", new Callback<SelectResponse>() {
>   public void onValue(SelectResponse response) {
>			String selected = response.getCode();
>   }
> });


Building
======

> make

Builds the latest `Conductrics-<version>.jar` file.

> make test

Runs all the tests (from `tests/Test.java`).

> make snapshot
> make release

Builds and publishes code to the Maven repo, as either the snapshot or release version.

Prerequisites
=========

Updating the Maven repo requires that the `aws` tool is installed, and you are logged in with proper permissions.
