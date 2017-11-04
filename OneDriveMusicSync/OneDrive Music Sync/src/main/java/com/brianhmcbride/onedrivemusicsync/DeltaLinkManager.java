package com.brianhmcbride.onedrivemusicsync;

import android.content.SharedPreferences;

class DeltaLinkManager {
    private static final String PREFS_NAME = "OneDriveMusicSyncPreferences";

    static final String ODATA_DELTA_LINK = "@odata.deltaLink";

    private static DeltaLinkManager instance = new DeltaLinkManager();

    static DeltaLinkManager getInstance() {
        if (instance == null) {
            instance = getSync();
        }

        return instance;
    }

    private static synchronized DeltaLinkManager getSync() {
        if (instance == null) {
            instance = new DeltaLinkManager();
        }

        return instance;
    }

    private DeltaLinkManager() {}

    String getDeltaLink(){
        SharedPreferences settings = App.get().getSharedPreferences(PREFS_NAME, 0);
        return settings.getString(ODATA_DELTA_LINK, null);
    }

    void setDeltaLink(String deltaLink){
        SharedPreferences settings = App.get().getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(ODATA_DELTA_LINK, deltaLink);
        editor.apply();
    }

    void clearDeltaLink(){
        SharedPreferences settings = App.get().getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.remove(ODATA_DELTA_LINK);
        editor.apply();
    }
}