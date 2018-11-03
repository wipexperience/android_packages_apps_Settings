/*
 * Copyright (C) 2018 PixelExperience
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package com.android.settings.custom.ambient.history;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.android.internal.util.custom.ambient.play.AmbientPlayHistoryEntry;
import com.android.internal.util.custom.ambient.play.AmbientPlayHistoryManager;
import com.android.settings.custom.ambient.history.utils.TimeDateUtils;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AmbientPlayHistory extends SettingsPreferenceFragment implements Preference.OnPreferenceClickListener {

    ProgressDialog dlg;
    private Map<String, List<AmbientPlayHistoryPreferenceEntry>> allSongs = new HashMap<>();
    private boolean updateRunning = false;
    private String TAG = "AmbientPlayHistory";
    private BroadcastReceiver onSongMatch = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (intent.getAction().equals(AmbientPlayHistoryManager.INTENT_SONG_MATCH.getAction())) {
                updateList();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.ambient_play_history);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setDivider(null);
    }

    private void updateList() {
        if (updateRunning) {
            return;
        }
        updateRunning = true;
        getPreferenceScreen().removeAll();
        allSongs.clear();
        dlg = new ProgressDialog(getActivity());
        dlg.setCancelable(false);
        dlg.setMessage(getString(R.string.ambient_play_loading_data));
        dlg.show();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                SystemClock.sleep(1000);
                List<AmbientPlayHistoryEntry> songs = AmbientPlayHistoryManager.getSongs(getActivity());
                for (AmbientPlayHistoryEntry entry : songs) {
                    AmbientPlayHistoryPreferenceEntry preference = new AmbientPlayHistoryPreferenceEntry(entry.getSongID(), entry.geMatchTimestamp(), entry.getSongTitle(), entry.getArtistTitle(), getActivity());
                    String matchDate = preference.getFormattedMatchDate();
                    if (allSongs.containsKey(matchDate)) {
                        allSongs.get(matchDate).add(preference);
                    } else {
                        allSongs.put(matchDate, new ArrayList<AmbientPlayHistoryPreferenceEntry>());
                        allSongs.get(matchDate).add(preference);
                    }
                }
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        addIllustration();
                        Map<String, List<AmbientPlayHistoryPreferenceEntry>> treeMap = new TreeMap<>(allSongs);
                        for (String date : treeMap.keySet()) {
                            final List<AmbientPlayHistoryPreferenceEntry> songsByDate = allSongs.get(date);
                            final PreferenceCategory cat = new PreferenceCategory(getActivity());
                            String catTitle = TimeDateUtils.getDaysAgo(getActivity(), songsByDate.get(0).geMatchTimestamp());
                            cat.setTitle(catTitle);
                            cat.setKey(date);
                            getPreferenceScreen().addPreference(cat);
                            for (final AmbientPlayHistoryPreferenceEntry songEntry : songsByDate) {
                                cat.addPreference(songEntry);
                                songEntry.setPreferenceCategory(cat);
                                songEntry.setOnPreferenceClickListener(AmbientPlayHistory.this);
                            }
                        }
                        dlg.dismiss();
                        updateListState();
                        updateRunning = false;
                    }
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(onSongMatch, new IntentFilter(AmbientPlayHistoryManager.INTENT_SONG_MATCH.getAction()));
        updateList();
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(onSongMatch);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CUSTOM_SETTINGS;
    }

    void addIllustration() {
        Preference pref = new Preference(getActivity());
        pref.setLayoutResource(R.layout.ambient_play_illustration);
        pref.setSelectable(false);
        pref.setEnabled(false);
        getPreferenceScreen().addPreference(pref);
    }

    private void updateListState() {
        if (allSongs.size() < 1) {
            Preference pref = new Preference(getActivity());
            pref.setLayoutResource(R.layout.ambient_play_preference_empty_list);
            pref.setTitle(R.string.ambient_play_history_empty);
            pref.setSelectable(false);
            pref.setEnabled(true);
            PreferenceCategory category = new PreferenceCategory(getActivity());
            category.addPreference(pref);
            getPreferenceScreen().addPreference(category);
        }
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.ambient_play_history, menu);
        menu.getItem(0).setVisible(allSongs.size() > 0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_remove_all:
                showRemoveAllDialog();
                return true;
            case R.id.action_add_to_home:
                createShortcut();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showRemoveAllDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(R.string.ambient_play_history_menu_remove_all));
        builder.setMessage(getString(R.string.ambient_play_history_dialog_remove_all_confirmation));
        builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                AmbientPlayHistoryManager.deleteAll(getActivity());
                updateList();
            }
        });
        builder.setNegativeButton(getString(android.R.string.cancel), null);
        builder.show();
    }

    private void createShortcut() {
        try {
            ComponentName cmp = new ComponentName("com.android.settings", "com.android.settings.Settings$AmbientPlayHistoryActivity");
            ShortcutManager shortcutManager = getActivity().getSystemService(ShortcutManager.class);
            ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(getActivity(), "now_playing_history")
                    .setActivity(cmp)
                    .setShortLabel(getString(R.string.ambient_play_history))
                    .setIcon(Icon.createWithResource(getActivity(), R.drawable.ambient_play_launcher))
                    .build();
            shortcutManager.requestPinShortcut(shortcutInfo, null);
        } catch (Exception e) {
            Log.e(TAG, "Error when creating shortcut", e);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference instanceof AmbientPlayHistoryPreferenceEntry) {
            final AmbientPlayHistoryPreferenceEntry entry = (AmbientPlayHistoryPreferenceEntry) preference;
            final AmbientPlayHistoryDialogEntry dialog = new AmbientPlayHistoryDialogEntry(getActivity(), entry);
            dialog.getRemoveButton().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    try {
                        AmbientPlayHistoryManager.deleteSong(entry.getSongID(), getActivity());
                        allSongs.get(entry.getPreferenceCategory().getKey()).remove(entry);
                        entry.removePreference();
                        if (allSongs.get(entry.getPreferenceCategory().getKey()).size() < 1) {
                            allSongs.remove(entry.getPreferenceCategory().getKey());
                            getPreferenceScreen().removePreference(entry.getPreferenceCategory());
                        }
                    } catch (Exception ignored) {
                    }
                    updateListState();
                }
            });
            dialog.show();
        }
        return false;
    }
}
