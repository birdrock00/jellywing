package club.thatpetbff.gramophone.fragments.main;

import androidx.fragment.app.Fragment;

import club.thatpetbff.gramophone.activities.MainActivity;

public abstract class AbsMainActivityFragment extends Fragment {
    public MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }
}
