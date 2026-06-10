package club.thatpetbff.gramophone.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.drawerlayout.widget.DrawerLayout;

import com.afollestad.materialcab.attached.AttachedCab;
import com.afollestad.materialcab.attached.AttachedCabKt;
import club.thatpetbff.gramophone.activities.base.AbsMusicContentActivity;
import club.thatpetbff.gramophone.interfaces.CabHolder;
import club.thatpetbff.gramophone.util.NavigationUtil;
import club.thatpetbff.gramophone.util.PreferenceUtil;
import club.thatpetbff.gramophone.util.ThemeUtil;
import club.thatpetbff.gramophone.databinding.ActivityMainContentBinding;
import club.thatpetbff.gramophone.databinding.ActivityMainDrawerLayoutBinding;
import club.thatpetbff.gramophone.databinding.NavigationDrawerHeaderBinding;
import club.thatpetbff.gramophone.dialogs.ConfirmLogoutDialog;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.glide.CustomGlideRequest;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.fragments.main.LibraryFragment;
import club.thatpetbff.gramophone.util.MusicUtil;
import club.thatpetbff.gramophone.util.QueryUtil;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.jellyfin.apiclient.model.dto.BaseItemDto;

import java.util.List;

public class MainActivity extends AbsMusicContentActivity implements CabHolder {
    private ActivityMainDrawerLayoutBinding binding;
    private ActivityMainContentBinding contentBinding;
    private NavigationDrawerHeaderBinding navigationBinding;
    private boolean onLogout;

    @Nullable
    private AttachedCab cab;

    @Nullable
    private List<BaseItemDto> libraries;

    @Nullable
    private Bundle state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        state = savedInstanceState;

        setColor(PreferenceUtil.getInstance(this).getPrimaryColor());
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            binding.navigationView.setFitsSystemWindows(false);
        }
    }

    @Override
    public void onStateOnline() {
        Menu menu = binding.navigationView.getMenu();
        QueryUtil.getLibraries(media -> {
            libraries = media;
            menu.clear();

            for (BaseItemDto itemDto : libraries) {
                if (menu.size() == 0) {
                    QueryUtil.currentLibrary = itemDto;
                }

                if (itemDto.getCollectionType() == null || !itemDto.getCollectionType().equals("music")) continue;
                menu.add(R.id.navigation_drawer_menu_category_sections, itemDto.getId().hashCode(), menu.size(), itemDto.getName());
                menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_album_white_24dp);
            }

            menu.add(R.id.navigation_drawer_menu_category_other, R.id.nav_settings, menu.size(), R.string.action_settings);
            menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_settings_white_24dp);
            menu.add(R.id.navigation_drawer_menu_category_other, R.id.nav_about, menu.size(), R.string.action_about);
            menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_info_outline_white_24dp);
            menu.add(R.id.navigation_drawer_menu_category_other, R.id.nav_logout, menu.size(), R.string.logout);
            menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_exit_to_app_white_48dp);

            setUpDrawerLayout();

            menu.getItem(0).setChecked(true);
            if (state == null) {
                setCurrentFragment(LibraryFragment.newInstance());
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();

        // only override when logout selected
        if (onLogout) {
            overridePendingTransition(0, R.anim.fade_quick);
            onLogout = false;
        }
    }

    private void setCurrentFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment, null).commit();
    }

    @Override
    protected View createContentView() {
        binding = ActivityMainDrawerLayoutBinding.inflate(getLayoutInflater());
        contentBinding = ActivityMainContentBinding.inflate(getLayoutInflater());

        ViewGroup drawerContent = binding.drawerContentContainer;
        drawerContent.addView(wrapSlidingMusicPanel(contentBinding.getRoot()));

        return binding.getRoot();
    }

    private void setUpNavigationView() {
        int normalColor = ThemeUtil.getColorResource(this, android.R.attr.textColorPrimary);
        int activeColor = PreferenceUtil.getInstance(this).getAccentColor();

        binding.navigationView.setItemIconTintList(ThemeUtil.getColorStateList(normalColor, activeColor));
        binding.navigationView.setItemTextColor(ThemeUtil.getColorStateList(normalColor, activeColor));

        binding.navigationView.setNavigationItemSelectedListener(menuItem -> {
            binding.drawerLayout.closeDrawers();
            int menuItemId = menuItem.getItemId();
            if (menuItemId == R.id.nav_settings) {
                new Handler().postDelayed(() -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)), 200);
            } else if (menuItemId == R.id.nav_about) {
                new Handler().postDelayed(() -> startActivity(new Intent(MainActivity.this, AboutActivity.class)), 200);
            } else if (menuItemId == R.id.nav_logout) {
                onLogout = true;
                ConfirmLogoutDialog.create().show(getSupportFragmentManager(), ConfirmLogoutDialog.TAG);
            }

            // only run the following code when a new library has been selected
            if (menuItemId == QueryUtil.currentLibrary.getId().hashCode()) return true;

            for (BaseItemDto itemDto : libraries) {
                if (menuItemId == itemDto.getId().hashCode()) {
                    QueryUtil.currentLibrary = itemDto;
                    setCurrentFragment(LibraryFragment.newInstance());
                    break;
                }
            }

            // setCheckable must be applied to the items on creation
            // it also applies a tacky background color for the checked item
            // this is a hack to check the current item without that
            if (menuItem.getGroupId() != R.id.navigation_drawer_menu_category_sections) return true;

            for (int i = 0; i < binding.navigationView.getMenu().size(); i++) {
                MenuItem item = binding.navigationView.getMenu().getItem(i);

                // ignore items that open new activities since the navigation view is hidden
                item.setChecked(item == menuItem);
            }

            return true;
        });
    }

    private void setUpDrawerLayout() {
        setUpNavigationView();
    }

    private void updateNavigationDrawerHeader() {
        if (!MusicPlayerRemote.getPlayingQueue().isEmpty()) {
            Song song = MusicPlayerRemote.getCurrentSong();
            if (navigationBinding == null) {
                navigationBinding = NavigationDrawerHeaderBinding.inflate(getLayoutInflater());

                binding.navigationView.addHeaderView(navigationBinding.getRoot());
                navigationBinding.getRoot().setOnClickListener(v -> {
                    binding.drawerLayout.closeDrawers();
                    if (getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
                        expandPanel();
                    }
                });
            }

            navigationBinding.title.setText(song.title);
            navigationBinding.text.setText(MusicUtil.getSongInfoString(song));

            CustomGlideRequest.Builder
                .from(this, song.getArtworkItemId(), song.blurHash)
                .build().centerInside().into(navigationBinding.image);
        } else if (binding.navigationView.getHeaderCount() != 0) {
            binding.navigationView.removeHeaderView(navigationBinding.getRoot());
            navigationBinding = null;
        }
    }

    @Override
    public void onPlayMetadataChanged() {
        super.onPlayMetadataChanged();
        updateNavigationDrawerHeader();
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        updateNavigationDrawerHeader();
    }

    @Override
    public void onCreateCab(AttachedCab cab) {
        this.cab = cab;
    }

    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(binding.navigationView)) {
            binding.drawerLayout.closeDrawers();
        } else if (cab != null && AttachedCabKt.isActive(cab)) {
            AttachedCabKt.destroy(cab);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (binding.drawerLayout.isDrawerOpen(binding.navigationView)) {
                binding.drawerLayout.closeDrawer(binding.navigationView);
            } else {
                binding.drawerLayout.openDrawer(binding.navigationView);
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPanelExpanded(View view) {
        super.onPanelExpanded(view);
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    @Override
    public void onPanelCollapsed(View view) {
        super.onPanelCollapsed(view);
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }
}
