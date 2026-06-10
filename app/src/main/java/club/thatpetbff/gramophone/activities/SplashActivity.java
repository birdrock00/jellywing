package club.thatpetbff.gramophone.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.activities.base.AbsBaseActivity;
import club.thatpetbff.gramophone.model.User;
import club.thatpetbff.gramophone.service.LoginService;
import club.thatpetbff.gramophone.util.NavigationUtil;
import club.thatpetbff.gramophone.util.PreferenceUtil;

import java.util.List;

public class SplashActivity extends AbsBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);
    }

    @Override
    public void onPause() {
        super.onPause();
        overridePendingTransition(0, R.anim.fade_delay);
    }

    @Override
    protected void onResume() {
        super.onResume();

        User user = App.getDatabase().userDao().getUser(PreferenceUtil.getInstance(this).getUser());
        List<User> available = App.getDatabase().userDao().getUsers();

        if (user == null && available.size() != 0) {
            NavigationUtil.startSelect(this);
        } else if (user == null) {
            NavigationUtil.startLogin(this);
        } else {
            startService(new Intent(this, LoginService.class));
            new Handler().postDelayed(() -> NavigationUtil.startMain(this), 1000);
        }
    }
}
