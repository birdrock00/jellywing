package club.thatpetbff.gramophone.activities.details;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialcab.attached.AttachedCab;
import com.afollestad.materialcab.attached.AttachedCabKt;
import club.thatpetbff.gramophone.BuildConfig;
import club.thatpetbff.gramophone.activities.base.AbsMusicContentActivity;
import club.thatpetbff.gramophone.databinding.ActivityGenreDetailBinding;
import club.thatpetbff.gramophone.util.NavigationUtil;
import club.thatpetbff.gramophone.util.PreferenceUtil;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.adapter.song.SongAdapter;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.interfaces.CabHolder;
import club.thatpetbff.gramophone.model.Genre;
import club.thatpetbff.gramophone.util.QueryUtil;
import club.thatpetbff.gramophone.util.ViewUtil;

import org.jellyfin.apiclient.model.querying.ItemQuery;

import java.util.ArrayList;

public class GenreDetailActivity extends AbsMusicContentActivity implements CabHolder {
    public static final String EXTRA_GENRE = BuildConfig.APPLICATION_ID + ".extra.genre";

    private ActivityGenreDetailBinding binding;

    private Genre genre;

    private AttachedCab cab;
    private SongAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        genre = getIntent().getParcelableExtra(EXTRA_GENRE);

        super.onCreate(savedInstanceState);

        setUpRecyclerView();
        setUpToolBar();
    }

    @Override
    public void onStateOnline() {
        ItemQuery query = new ItemQuery();
        query.setGenreIds(new String[]{genre.id});

        QueryUtil.getSongs(query, media -> {
            adapter.getDataSet().addAll(media);
            adapter.notifyDataSetChanged();
        });
    }

    @Override
    protected View createContentView() {
        binding = ActivityGenreDetailBinding.inflate(getLayoutInflater());

        return wrapSlidingMusicPanel(binding.getRoot());
    }

    private void setUpRecyclerView() {
        ViewUtil.setUpFastScrollRecyclerViewColor(this, binding.recyclerView, PreferenceUtil.getInstance(this).getAccentColor());
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SongAdapter(this, new ArrayList<>(), R.layout.item_list, false, this);
        binding.recyclerView.setAdapter(adapter);

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                checkIsEmpty();
            }
        });
    }

    private void setUpToolBar() {
        binding.toolbar.setBackgroundColor(PreferenceUtil.getInstance(this).getPrimaryColor());
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setTitle(genre.name);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detail_genre, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_shuffle_genre) {
            MusicPlayerRemote.openAndShuffleQueue(adapter.getDataSet(), true);
            return true;
        } else if (id == R.id.action_download) {
            NavigationUtil.startDownload(this, adapter.getDataSet());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateCab(AttachedCab cab) {
        this.cab = cab;
    }

    @Override
    public void onBackPressed() {
        if (cab != null && AttachedCabKt.isActive(cab)) {
            AttachedCabKt.destroy(cab);
        } else {
            binding.recyclerView.stopScroll();
            super.onBackPressed();
        }
    }

    private void checkIsEmpty() {
        binding.empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        binding.recyclerView.setAdapter(null);

        adapter = null;
        super.onDestroy();
    }
}
