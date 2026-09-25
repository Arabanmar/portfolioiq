package com.portfolioiq.presentation.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.portfolioiq.R;
import com.portfolioiq.presentation.auth.RegisterActivity;
import com.portfolioiq.presentation.auth.SignInActivity;

/**
 * Welcome / Homepage screen (matches the Sprint Zero mockups). Launcher
 * activity; its only job is routing into Sign In or Create Account, both
 * built in Sprint 1.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button signInButton = findViewById(R.id.signInButton);
        Button createAccountButton = findViewById(R.id.createAccountButton);

        signInButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SignInActivity.class)));
        createAccountButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, RegisterActivity.class)));
    }
}
