package org.lyrion.squeezelite;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.List;

import io.github.muddz.styleabletoast.StyleableToast;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);

        setContentView(R.layout.settings_activity);

        SettingsFragment fragment = new SettingsFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, fragment)
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private class Discovery extends ServerDiscovery {
            Discovery(Context context) {
                super(context, true);
            }

            public void discoveryFinished(List<Server> servers) {
                Utils.debug("Discovery finished");
                if (getContext()==null) {
                    return;
                }
                if (servers.isEmpty()) {
                    StyleableToast.makeText(getContext(), getResources().getString(R.string.no_servers), Toast.LENGTH_SHORT, R.style.toast).show();
                } else {
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                    Server serverToUse = servers.get(0);
                    Server current = new Server(sharedPreferences.getString(Prefs.SERVER_KEY, null));

                    if (servers.size()>1) {
                        // If more than 1 server found, then select one that is different to the currently selected one.
                        if (!current.isEmpty()) {
                            for (Server server: servers) {
                                if (!server.equals(current)) {
                                    serverToUse = server;
                                    break;
                                }
                            }
                        }
                    }

                    if (current.isEmpty() || !current.equals(serverToUse)) {
                        if (current.isEmpty()) {
                            StyleableToast.makeText(getContext(), getResources().getString(R.string.server_discovered)+"\n\n"+serverToUse.describe(), Toast.LENGTH_SHORT, R.style.toast).show();
                        } else {
                            StyleableToast.makeText(getContext(), getResources().getString(R.string.server_changed)+"\n\n"+serverToUse.describe(), Toast.LENGTH_SHORT, R.style.toast).show();
                        }

                        Preference addressButton = getPreferenceManager().findPreference("server_address");
                        if (addressButton != null) {
                            addressButton.setSummary(serverToUse.describe());
                        }
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(Prefs.SERVER_KEY, serverToUse.encode());
                        editor.apply();
                    } else {
                        StyleableToast.makeText(getContext(), getResources().getString(R.string.no_new_server), Toast.LENGTH_SHORT, R.style.toast).show();
                    }
                }
            }
        }

        private Discovery discovery = null;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            Utils.debug("SETUP");

            if (getContext()==null) {
                return;
            }
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            final Preference addressButton = getPreferenceManager().findPreference(Prefs.SERVER_KEY);
            if (addressButton != null) {
                addressButton.setSummary(new Discovery.Server(sharedPreferences.getString(Prefs.SERVER_KEY,"")).describe());
                addressButton.setOnPreferenceClickListener(arg0 -> {
                    if (getContext()!=null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setTitle(R.string.server_address);
                        SharedPreferences sharedPreferences1 = PreferenceManager.getDefaultSharedPreferences(getContext());
                        Discovery.Server server = new Discovery.Server(sharedPreferences1.getString(Prefs.SERVER_KEY, null));

                        int padding = getResources().getDimensionPixelOffset(R.dimen.dlg_padding);
                        final EditText input = new EditText(getContext());
                        input.setInputType(InputType.TYPE_CLASS_TEXT);
                        input.setText(server.address());
                        LinearLayout layout = new LinearLayout(getContext());
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(padding, padding, padding, padding / 2);
                        layout.addView(input);
                        builder.setView(layout);

                        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            String str = input.getText().toString().replaceAll("\\s+", "");
                            String[] parts = str.split(":");
                            Discovery.Server server1 = new Discovery.Server(parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : Discovery.Server.DEFAULT_PORT, null);
                            SharedPreferences sharedPreferences11 = PreferenceManager.getDefaultSharedPreferences(getContext());
                            SharedPreferences.Editor editor = sharedPreferences11.edit();
                            editor.putString(Prefs.SERVER_KEY, server1.encode());
                            editor.apply();
                            addressButton.setSummary(server1.describe());
                        });
                        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

                        builder.show();
                    }
                    return true;
                });
            }

            Preference discoverButton = getPreferenceManager().findPreference("discover");
            if (discoverButton != null) {
                discoverButton.setOnPreferenceClickListener(arg0 -> {
                    Utils.debug("Discover clicked");
                    if (getContext()!=null) {
                        StyleableToast.makeText(getContext(), getResources().getString(R.string.discovering_server), Toast.LENGTH_SHORT, R.style.toast).show();
                        if (discovery == null) {
                            discovery = new Discovery(getContext().getApplicationContext());
                        }
                        discovery.discover();
                    }
                    return true;
                });
            }

            final Preference playerNameButton = getPreferenceManager().findPreference(Prefs.PLAYER_NAME_KEY);
            if (playerNameButton != null) {
                String playerName = sharedPreferences.getString(Prefs.PLAYER_NAME_KEY, null);
                if (playerName!=null && !playerName.isEmpty()) {
                    playerNameButton.setSummary(playerName);
                }

                playerNameButton.setOnPreferenceClickListener(arg0 -> {
                    if (getContext()!=null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setTitle(R.string.player_name);
                        SharedPreferences sharedPreferences12 = PreferenceManager.getDefaultSharedPreferences(getContext());
                        String value = sharedPreferences12.getString(Prefs.PLAYER_NAME_KEY, null);

                        int padding = getResources().getDimensionPixelOffset(R.dimen.dlg_padding);
                        final EditText input = new EditText(getContext());
                        input.setInputType(InputType.TYPE_CLASS_TEXT);

                        if (null != value) {
                            input.setText(value);
                        }

                        LinearLayout layout = new LinearLayout(getContext());
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(padding, padding, padding, padding / 2);
                        layout.addView(input);
                        builder.setView(layout);

                        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            String str = input.getText().toString().trim();
                            SharedPreferences sharedPreferences121 = PreferenceManager.getDefaultSharedPreferences(getContext());
                            SharedPreferences.Editor editor = sharedPreferences121.edit();
                            editor.putString(Prefs.PLAYER_NAME_KEY, str);
                            editor.apply();
                            playerNameButton.setSummary(str.isEmpty() ? Prefs.DEFAULT_PLAYER_NAME : str);
                        });
                        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

                        builder.show();
                    }
                    return true;
                });
            }
        }
    }
}