package club.thatpetbff.gramophone.fragments.library;

import club.thatpetbff.gramophone.fragments.AbsMusicServiceFragment;
import club.thatpetbff.gramophone.fragments.main.LibraryFragment;

public class AbsLibraryPagerFragment extends AbsMusicServiceFragment {
    public LibraryFragment getLibraryFragment() {
        return (LibraryFragment) getParentFragment();
    }
}
