package io.adpass.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class PauseReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { AppState.setEnabled(context, false); }
}
