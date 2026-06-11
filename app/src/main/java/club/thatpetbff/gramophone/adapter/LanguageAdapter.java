package club.thatpetbff.gramophone.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.adapter.base.MediaEntryViewHolder;
import club.thatpetbff.gramophone.model.Language;
import club.thatpetbff.gramophone.util.MusicUtil;

import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import java.util.List;
import java.util.Locale;

public class LanguageAdapter extends RecyclerView.Adapter<LanguageAdapter.ViewHolder> implements FastScrollRecyclerView.SectionedAdapter {
    private final AppCompatActivity activity;
    private final OnLanguageClickListener listener;
    private List<Language> dataSet;

    public LanguageAdapter(@NonNull AppCompatActivity activity, List<Language> dataSet, OnLanguageClickListener listener) {
        this.activity = activity;
        this.dataSet = dataSet;
        this.listener = listener;
    }

    public List<Language> getDataSet() {
        return dataSet;
    }

    public void swapDataSet(List<Language> dataSet) {
        this.dataSet = dataSet;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return dataSet.get(position).hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(activity).inflate(R.layout.item_list_single_row, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final Language language = dataSet.get(position);

        if (holder.shortSeparator != null) {
            holder.shortSeparator.setVisibility(holder.getBindingAdapterPosition() == getItemCount() - 1 ? View.GONE : View.VISIBLE);
        }

        if (holder.menu != null) {
            holder.menu.setVisibility(View.GONE);
        }

        if (holder.image != null) {
            holder.image.setVisibility(View.GONE);
        }

        if (holder.imageText != null) {
            holder.imageText.setVisibility(View.VISIBLE);
            holder.imageText.setText(language.code.toUpperCase(Locale.US));
        }

        if (holder.title != null) {
            holder.title.setText(language.name);
        }
    }

    @Override
    public int getItemCount() {
        return dataSet.size();
    }

    @NonNull
    @Override
    public String getSectionName(int position) {
        final Language language = dataSet.get(position);
        return MusicUtil.getSectionName(language.name);
    }

    public interface OnLanguageClickListener {
        void onLanguageClick(Language language);
    }

    public class ViewHolder extends MediaEntryViewHolder {
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        @Override
        public void onClick(View view) {
            if (listener != null && getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                listener.onLanguageClick(dataSet.get(getBindingAdapterPosition()));
            }
        }
    }
}
