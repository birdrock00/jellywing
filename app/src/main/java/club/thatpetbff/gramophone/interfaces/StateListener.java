package club.thatpetbff.gramophone.interfaces;

public interface StateListener {
    void onStatePolling();

    void onStateOnline();

    void onStateOffline();
}
