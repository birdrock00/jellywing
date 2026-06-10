package club.thatpetbff.gramophone.interfaces;

public interface MusicServiceEventListener {
    void onServiceConnected();

    void onServiceDisconnected();

    void onQueueChanged();

    void onPlayMetadataChanged();

    void onPlayStateChanged();

    void onRepeatModeChanged();

    void onShuffleModeChanged();
}
