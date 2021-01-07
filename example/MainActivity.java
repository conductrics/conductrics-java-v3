package com.conductrics.javasdkdemo2;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.conductrics.Conductrics;
import com.conductrics.RequestOptions;
import com.conductrics.SelectResponse;
import com.conductrics.Callback;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Conductrics api = new Conductrics(
                "https://api-staging-2020.conductrics.com/ac-jesse/v3/agent-api",
                "api-cLvdmNRwizzCGwYAeUeR"
        );

        RequestOptions opts = new RequestOptions( getSessionID() );

        api.select(opts, "a-example", new Callback<SelectResponse>() {
            public void onValue(SelectResponse response) {
                Log.d("Conductrics", "Response: " + response.getCode());
            }
        });
    }

    // How to get a session ID that will persist across app launches.
    private String getSessionID() {
        Context context = getApplicationContext();
        SharedPreferences settings = context.getSharedPreferences("Conductrics", Context.MODE_PRIVATE);
        // If an ID is already persisted, use it.
        String id = settings.getString("Session-ID", null);
        if( id == null ) {
            // If we create a new ID, also persist it
            id = UUID.randomUUID().toString();
            settings.edit()
                    .putString("Session-ID", id)
                    .apply();
        }
        return id;
    }
}