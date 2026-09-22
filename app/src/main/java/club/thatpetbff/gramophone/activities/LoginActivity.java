package club.thatpetbff.gramophone.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.BuildConfig;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.databinding.ActivityLoginBinding;
import club.thatpetbff.gramophone.activities.base.AbsBaseActivity;
import club.thatpetbff.gramophone.model.User;
import club.thatpetbff.gramophone.util.PreferenceUtil;
import club.thatpetbff.gramophone.util.ServerUrlUtil;

import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.model.system.SystemInfo;
import org.jellyfin.apiclient.model.users.AuthenticationResult;

public class LoginActivity extends AbsBaseActivity implements View.OnClickListener {
    private static final String TAG = "JellywingLogin";
    private ActivityLoginBinding binding;
    private int primaryColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setUpViews();
    }

    @Override
    public void onPause() {
        super.onPause();
        overridePendingTransition(0, R.anim.fade_quick);
    }

    private void setUpViews() {
        primaryColor = PreferenceUtil.getInstance(this).getPrimaryColor();

        setUpToolbar();
        setUpOnClickListeners();

        binding.login.setBackgroundColor(primaryColor);
    }

    private void setUpToolbar() {
        binding.toolbar.setBackgroundColor(primaryColor);
        setSupportActionBar(binding.toolbar);
    }

    private void setUpOnClickListeners() {
        binding.login.setOnClickListener(this);
        binding.select.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == binding.select) {
            startActivity(new Intent(this, SelectActivity.class));
            return;
        }

        String username = binding.username.getText().toString();
        String password = binding.password.getText().toString();
        String server = ServerUrlUtil.normalize(binding.server.getText().toString());
        Context context = this;

        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, getResources().getString(R.string.error_empty_username), Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(server)) {
            Toast.makeText(this, getResources().getString(R.string.error_empty_server), Toast.LENGTH_SHORT).show();
            return;
        }

        binding.login.setEnabled(false);
        binding.login.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));

        // Never log username/password: server URL alone is enough to diagnose routing issues.
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "login attempt to server=" + server);
        }
        App.getApiClient().ChangeServerLocation(server);
        App.getApiClient().AuthenticateUserAsync(username, password, new Response<AuthenticationResult>() {
            @Override
            public void onResponse(AuthenticationResult authenticationResult) {
                if (authenticationResult.getAccessToken() != null) {
                    check(authenticationResult, server);
                }
            }

            @Override
            public void onError(Exception exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "authenticate error: " + exception);
                }
                binding.login.setEnabled(true);
                binding.login.setBackgroundColor(primaryColor);

                String message = exception != null ? exception.getMessage() : null;
                if (message != null && message.contains("AuthFailureError")) {
                    Toast.makeText(context, context.getResources().getString(R.string.error_login_credentials), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, context.getResources().getString(R.string.error_unreachable_server), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void check(AuthenticationResult authenticationResult, String server) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "authenticated, fetching system info from server=" + server);
        }
        App.getApiClient().GetSystemInfoAsync(new Response<SystemInfo>() {
            @Override
            public void onResponse(SystemInfo result) {
                if (result.getVersion().charAt(0) == '1') {
                    User user = new User(authenticationResult, server);

                    App.getDatabase().userDao().insertUser(user);
                    PreferenceUtil.getInstance(LoginActivity.this).setServer(user.server);
                    PreferenceUtil.getInstance(LoginActivity.this).setUser(user.id);

                    Intent intent = new Intent(LoginActivity.this, SplashActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                } else {
                    binding.login.setEnabled(true);
                    binding.login.setBackgroundColor(primaryColor);

                    Toast.makeText(LoginActivity.this, getResources().getString(R.string.error_version), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(Exception exception) {
                // Previously this callback was missing entirely: a failed system-info
                // fetch left the login button permanently disabled with no message.
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "system info error: " + exception);
                }
                binding.login.setEnabled(true);
                binding.login.setBackgroundColor(primaryColor);

                Toast.makeText(LoginActivity.this, getResources().getString(R.string.error_unreachable_server), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
