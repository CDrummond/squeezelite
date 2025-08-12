package org.lyrion.squeezelite;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.muddz.styleabletoast.StyleableToast;

public class SettingsActivity extends AppCompatActivity {
    private static final int PERMISSION_RECEIVE_BOOT_COMPLETED = 1;
    private static final int PERMISSION_BLUETOOTH_CONNECT_COMPLETED = 2;

    private SettingsFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);

        setContentView(R.layout.settings_activity);

        fragment = new SettingsFragment();
        fragment.setActivity(this);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, fragment)
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
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

                        Preference addressButton = getPreferenceManager().findPreference(Prefs.SERVER_KEY);
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

        private SettingsActivity activity = null;
        private Discovery discovery = null;

        public void setActivity(SettingsActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            Utils.debug("SETUP");

            if (getContext()==null) {
                return;
            }
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            if (sharedPreferences.getBoolean(Prefs.INITIAL_KEY, true)) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(Prefs.INITIAL_KEY, false);
                editor.apply();
            }

            final Preference addressButton = getPreferenceManager().findPreference(Prefs.SERVER_KEY);
            if (addressButton != null) {
                String current = new Discovery.Server(sharedPreferences.getString(Prefs.SERVER_KEY,"")).describe();
                addressButton.setSummary(Utils.isEmpty(current) ? getResources().getString(R.string.blank_server) : current);
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
                            String now = server1.describe();
                            addressButton.setSummary(Utils.isEmpty(now) ? getResources().getString(R.string.blank_server) : now);
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
            final Preference btMacAddresses = getPreferenceManager().findPreference(Prefs.BT_MAC_ADDRESSES_KEY);
            if (null!=btMacAddresses) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    btMacAddresses.setEnabled(false);
                } else {
                    fillBtMacAddressList((MultiSelectListPreference)btMacAddresses, getContext());
                }
            }

            updateSummary(Prefs.PLAYER_NAME_KEY);
            updateSummary(Prefs.VOLUME_CONTROL_KEY);
            updateSummary(Prefs.INITIAL_CONNECTION_TIMEOUT_KEY);
            updateSummary(Prefs.CONNECTION_LOST_TIMEOUT_KEY);
            updateSummary(Prefs.OUTPUT_LIB_KEY);
            updateSummary(Prefs.MAX_BITRATE_KEY);
            updateSummary(Prefs.MAX_BITRATE_WHEN_KEY);
            updateSummary(Prefs.STREAM_BUFFER_KEY);
            updateSummary(Prefs.BT_MAC_ADDRESSES_KEY);
            PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (getContext()!=null) {
                PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Utils.debug(key);
            if (Prefs.START_ON_BOOT_KEY.equals(key)) {
                if (sharedPreferences.getBoolean(key, Prefs.DEFAULT_START_ON_BOOT)) {
                    activity.checkStartOnBootPermission();
                }
            } else if (Prefs.USE_BT_ID_KEY.equals(key)) {
                if (sharedPreferences.getBoolean(Prefs.USE_BT_ID_KEY, false)) {
                    activity.checkBtPermission();
                }
            } else if (Prefs.AUTOSTART_BT_KEY.equals(key)) {
                if (sharedPreferences.getBoolean(Prefs.AUTOSTART_BT_KEY, false)) {
                    activity.checkBtPermission();
                }
            } else {
                updateSummary(key);
            }
        }

        private void updateSummary(String key) {
            Utils.debug(key);
            Preference pref = getPreferenceManager().findPreference(key);
            if (pref != null) {
                if (pref instanceof ListPreference) {
                    ListPreference lp = (ListPreference)pref;
                    if (Prefs.VOLUME_CONTROL_KEY.equals(key)) {
                        String val = lp.getValue();
                        if (Prefs.VOLUME_CONTROL_SEPARATE.equals(val)) {
                            pref.setSummary(R.string.volume_control_separate);
                        } else if (Prefs.VOLUME_CONTROL_DEVICE.equals(val)) {
                            pref.setSummary(R.string.volume_control_device);
                        } else if (Prefs.VOLUME_CONTROL_SYNCHRONIZED.equals(val)) {
                            pref.setSummary(R.string.volume_control_synchronized);
                        } else {
                            pref.setSummary(lp.getEntry());
                        }
                    } else {
                        pref.setSummary(lp.getEntry());
                    }
                } else if (pref instanceof EditTextPreference) {
                    EditTextPreference ep = (EditTextPreference)pref;
                    pref.setSummary(ep.getText());
                } else if (Prefs.BT_MAC_ADDRESSES_KEY.equals(key)) {
                    updateBtDevListSummary(pref);
                }
            }
        }

        private void updateBtDevListSummary(Preference pref) {
            Set<String> macs = Prefs.get(activity).getStringSet(Prefs.BT_MAC_ADDRESSES_KEY, null);
            List<String> names = new LinkedList<>();
            if (null!=macs) {
                for (String mac : macs) {
                    String name = btMacToName.get(mac);
                    names.add(Utils.isEmpty(name) ? mac : name);
                }
                Collections.sort(names);
            }
            if (names.isEmpty()) {
                pref.setSummary(R.string.none);
            } else {
                pref.setSummary(String.join(", ",  names));
            }
        }

        private void unCheckStartOnBoot() {
            if (getContext()==null) {
                return;
            }
            SwitchPreferenceCompat pref = getPreferenceManager().findPreference(Prefs.START_ON_BOOT_KEY);
            if (pref==null || pref.isChecked()) {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(Prefs.START_ON_BOOT_KEY, false);
                editor.apply();
                if (pref != null) {
                    pref.setChecked(false);
                }
            }
        }

        private void disableBt() {
            if (getContext()==null) {
                return;
            }

            SwitchPreferenceCompat useIdPref = getPreferenceManager().findPreference(Prefs.USE_BT_ID_KEY);
            SwitchPreferenceCompat autoStartPref = getPreferenceManager().findPreference(Prefs.AUTOSTART_BT_KEY);

            if (useIdPref==null || useIdPref.isChecked() || autoStartPref==null || autoStartPref.isChecked()) {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(Prefs.USE_BT_ID_KEY, false);
                editor.putBoolean(Prefs.AUTOSTART_BT_KEY, false);
                editor.apply();
                if (useIdPref != null && useIdPref.isChecked()) {
                    useIdPref.setChecked(false);
                }
                if (autoStartPref != null && autoStartPref.isChecked()) {
                    autoStartPref.setChecked(false);
                }
            }
        }
    }

    private static Map<String, String> btMacToName = new HashMap<>();
    private static void fillBtMacAddressList(MultiSelectListPreference btMacAddresses, Context context) {
        Utils.debug("");
        CharSequence[] names = null;
        CharSequence[] macs = null;
        btMacToName = new HashMap<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();

            if (!pairedDevices.isEmpty()) {
                names = new CharSequence[pairedDevices.size()];
                macs = new CharSequence[pairedDevices.size()];
                int idx = 0;
                for (BluetoothDevice bt : pairedDevices) {
                    names[idx]=Utils.getName(bt);
                    macs[idx]=bt.getAddress();
                    idx+=1;
                    btMacToName.put(bt.getAddress(), bt.getName());
                }
            }
        }
        if (null==names) {
            btMacAddresses.setEnabled(false);
        } else {
            btMacAddresses.setEnabled(true);
            btMacAddresses.setEntries(names);
            btMacAddresses.setEntryValues(macs);
        }
    }

    private void checkStartOnBootPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_BOOT_COMPLETED) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_BOOT_COMPLETED}, PERMISSION_RECEIVE_BOOT_COMPLETED);
        }
    }

    private void checkBtPermission() {
        Utils.debug("SDK:"+Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Utils.debug("Req perm");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_BLUETOOTH_CONNECT_COMPLETED);
        } else {
            fillBtMacAddressList(fragment.getPreferenceManager().findPreference(Prefs.BT_MAC_ADDRESSES_KEY), this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_RECEIVE_BOOT_COMPLETED) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                fragment.unCheckStartOnBoot();
            }
            return;
        }
        if (requestCode == PERMISSION_BLUETOOTH_CONNECT_COMPLETED) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fillBtMacAddressList((MultiSelectListPreference)fragment.getPreferenceManager().findPreference(Prefs.BT_MAC_ADDRESSES_KEY), this);
            } else {
                fragment.disableBt();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}