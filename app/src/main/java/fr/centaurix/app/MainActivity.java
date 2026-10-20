package fr.centaurix.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.location.LocationManager;
import android.content.Context;
import android.provider.Settings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stand-alone BLE read-only connection test for Centaurus BT200.
 * Built from protocol observations on the user's own Centaurus 1.0.9 APK.
 * No heating, power, settings, OTA, account or network calls.
 */
public class MainActivity extends Activity {
    private static final UUID FFA0 = UUID.fromString("0000ffa0-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final byte[] READ_STATUS = {(byte)0xaa, 0x55, 0x01, 0, 0, 0x7c, 0x14};
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, BluetoothDevice> devices = new LinkedHashMap<>();
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final StringBuilder report = new StringBuilder("Centaurix BT200 BLE Diagnostic 0.4.0\n");
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private TextView state, details, log;
    private LinearLayout deviceItems;
    private Button query;
    private int bleCount, btCount, scanSession;
    private int receivedValid;
    private int requestEpoch;
    private boolean notified, scanning;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(18));
        root.setBackgroundColor(0xff10100f);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        TextView title = text("CENTAURIX / TEST", 27, 0xffe5b85b);
        title.setTypeface(null, 1);
        root.addView(title);
        root.addView(text("BT200 • Diagnostic Bluetooth • v0.4.0 • lecture seule", 13, 0xffcccccc));
        state = text("Prêt à rechercher la BT200", 18, 0xffe5b85b);
        state.setPadding(0, dp(20), 0, dp(12));
        root.addView(state);
        details = text("1. Menu BT200 > Bluetooth > ON, puis APPUI sur le bouton CENTRAL pour confirmer.\n2. Active Bluetooth ET Localisation Android, puis Scanner BLE.\n3. Attends le résultat (10 secondes).", 15, 0xffffffff);
        root.addView(details);
        Button scanButton = button("1 · Scanner les appareils BLE");
        root.addView(scanButton);
        scanButton.setOnClickListener(v -> checkPermissionsAndScan());
        Button loc = button("Ouvrir la localisation Android");
        root.addView(loc);
        loc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        Button paired = button("Afficher les appareils associés (diagnostic)");
        root.addView(paired);
        paired.setOnClickListener(v -> showBonded());
        Button stop = button("Arrêter la recherche");
        root.addView(stop);
        stop.setOnClickListener(v -> stopScan());
        root.addView(text("Appareils détectés", 18, 0xffe5b85b));
        deviceItems = new LinearLayout(this);
        deviceItems.setOrientation(LinearLayout.VERTICAL);
        root.addView(deviceItems);
        query = button("2 · Relancer la requête de lecture");
        query.setEnabled(false);
        root.addView(query);
        query.setOnClickListener(v -> readStatus());
        Button disconnect = button("Déconnecter");
        root.addView(disconnect);
        disconnect.setOnClickListener(v -> { closeGatt(); setState("Déconnecté", false); });
        Button share = button("3 · Partager le journal de diagnostic");
        root.addView(share);
        share.setOnClickListener(v -> shareReport());
        root.addView(text("Journal Bluetooth (sans transmission automatique)", 14, 0xffe5b85b));
        log = text("—", 12, 0xffeeeeee);
        log.setTextIsSelectable(true);
        root.addView(log);
        note("Appli indépendante non affiliée à Lost Vape. Elle n'envoie que la commande officielle AA550100007C14 (demande d'état). Aucune modification des watts ou du firmware.");
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, dp(6), 0, dp(9)); return t;
    }
    private Button button(String label) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(label); return b;
    }
    private void setState(String msg, boolean success) {
        main.post(() -> { state.setText(msg); state.setTextColor(success ? 0xff8fdfa0 : 0xffe5b85b); });
        note("ETAT " + msg);
    }
    private void note(String msg) {
        main.post(() -> {
            String entry = System.currentTimeMillis() + " " + msg;
            report.append(entry).append('\n');
            if (log != null) {
                String s = report.toString();
                log.setText(s.length() > 13000 ? s.substring(s.length() - 13000) : s);
            }
        });
    }
    private boolean locationOn() {
        LocationManager m = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        return m != null && (m.isProviderEnabled(LocationManager.GPS_PROVIDER) || m.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }
    private void showBonded() {
        if (!permitted()) { checkPermissionsAndScan(); return; }
        try {
            BluetoothAdapter a = adapter();
            if (a == null || !a.isEnabled()) return;
            int total=0;
            for (BluetoothDevice d : a.getBondedDevices()) {
                String label=d.getName()==null ? "Sans nom" : d.getName();
                displayDevice(d,label,0,"Associé");
                total++;
            }
            note("Appareils déjà associés = "+total);
        } catch(Exception e) { note("ERREUR appareils associés : "+e); }
    }
    private void displayDevice(BluetoothDevice d,String label,int rssi,String type) {
        String address=d.getAddress();
        main.post(() -> {
            if (devices.containsKey(address)) return;
            devices.put(address,d);
            if ("BLE".equals(type)) bleCount++;
            if (label.toUpperCase(Locale.ROOT).contains("BT200")) btCount++;
            note("DÉCOUVERTE " + type + " : " + label + " RSSI=" + rssi + " MAC=" + address);
            Button b=button((label.toUpperCase(Locale.ROOT).contains("BT200") ? "★ " : "") + label + " • " + type + " (" + rssi + "dBm)\\n"+address);
            deviceItems.addView(b);
            b.setOnClickListener(v -> connect(d,label));
            setState("Détectés "+devices.size()+" dont BT200="+btCount,btCount>0);
        });
    }
    private boolean permitted() {
        if (Build.VERSION.SDK_INT >= 31)
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void checkPermissionsAndScan() {
        if (!permitted()) {
            if (Build.VERSION.SDK_INT >= 31)
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        } else startScan();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == 100 && permitted()) startScan();
        else setState("Autorise les appareils à proximité ET la localisation précise", false);
    }
    private BluetoothAdapter adapter() {
        BluetoothManager m = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        return m == null ? null : m.getAdapter();
    }
    private final ScanCallback scanEvents = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice d = result.getDevice();
            String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
            if (name == null || name.isEmpty()) {
                try { name = d.getName(); } catch (SecurityException ignored) {}
            }
            if (name == null || name.isEmpty()) name = "Sans nom";
            final String label = name;
            final String address = d.getAddress();
            final int rssi = result.getRssi();
            displayDevice(d,label,rssi,"BLE");
        }
        @Override public void onBatchScanResults(java.util.List<ScanResult> batch) { for(ScanResult item:batch) onScanResult(0,item); }
        @Override public void onScanFailed(int error) { note("ERREUR BLE SCAN "+error); setState("Échec du scan BLE : " + error, false); }
    };
    private void startScan() {
        stopScan();
        BluetoothAdapter a = adapter();
        if (a == null || !a.isEnabled()) { setState("Active le Bluetooth du téléphone", false); return; }
        try {
            devices.clear(); bleCount=0; btCount=0; deviceItems.removeAllViews();
            note("Permissions="+permitted()+" | Bluetooth="+a.isEnabled()+" | Localisation="+locationOn());
            if(!locationOn()) note("ALERTE : activer la Localisation Android, nécessaire sur certains appareils.");
            scanner = a.getBluetoothLeScanner();
            if (scanner == null) { setState("Scanner BLE indisponible", false); return; }
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(),scanEvents);
            scanning=true;
            final int session=++scanSession;
            setState("Scan BLE haute sensibilité (20 s)",false);
            note("Scan BLE sans filtre ; session #"+session);
            main.postDelayed(() -> {
                if(session==scanSession && scanning) {
                    stopScan();
                    if(bleCount==0) setState("0 appareil BLE : vérifier localisation et confirmer Bluetooth sur la box",false);
                    else if(btCount==0) setState(bleCount+" appareils BLE trouvés, aucun BT200 : vérifier le menu Bluetooth de la box",false);
                    else setState("BT200 visible : sélectionne-la dans la liste",true);
                    note("BILAN : BLE="+bleCount+" BT200="+btCount+" Localisation="+locationOn());
                }
            },20000);
        } catch (Exception e) { setState("Erreur du scan Bluetooth", false); note(e.toString()); }
    }
    private void stopScan() {
        if (scanning) {
            try { if (scanner != null) scanner.stopScan(scanEvents); } catch (Exception ignored) {}
            scanning = false; scanSession++; scanner = null; note("Recherche BLE arrêtée ; détectés="+bleCount+" BT200="+btCount);
        }
    }
    private void connect(BluetoothDevice d, String label) {
        stopScan(); closeGatt();
        receivedValid = 0; notified = false; buffer.reset();
        final int epoch = ++requestEpoch;
        setState("Connexion : " + label, false);
        note("DEVICE " + label + " " + d.getAddress());
        try {
            gatt = d.connectGatt(this, false, callbacks, BluetoothDevice.TRANSPORT_LE);
            main.postDelayed(() -> {
                if (requestEpoch == epoch && receivedValid == 0)
                    note("Aucune trame BT200 valide après 18 s. Voir détails du journal.");
            }, 18000);
        } catch (Exception e) { setState("Impossible de se connecter", false); note(e.toString()); }
    }
    private final BluetoothGattCallback callbacks = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int next) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setState("Échec connexion GATT : " + status, false); return;
            }
            if (next == BluetoothProfile.STATE_CONNECTED) {
                setState("BLE connecté · recherche du service FFA0", false);
                try { g.discoverServices(); } catch (SecurityException e) { note(e.toString()); }
            } else if (next == BluetoothProfile.STATE_DISCONNECTED) {
                setState("Déconnecté du périphérique", false);
                main.post(() -> query.setEnabled(false));
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            note("GATT découverte status=" + status);
            if (status != BluetoothGatt.GATT_SUCCESS) return;
            for (BluetoothGattService service : g.getServices()) {
                note("SERVICE " + service.getUuid());
                for (BluetoothGattCharacteristic ch : service.getCharacteristics())
                    note("CARACT " + ch.getUuid() + " props=" + ch.getProperties());
            }
            BluetoothGattService svc = g.getService(FFA0);
            if (svc == null) { setState("Connecté, mais service BT200 FFA0 absent", false); return; }
            writeChar = null;
            BluetoothGattCharacteristic notifyChar = null;
            for (BluetoothGattCharacteristic ch : svc.getCharacteristics()) {
                int p = ch.getProperties();
                if (writeChar == null && (p & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0)
                    writeChar = ch;
                if (notifyChar == null && (p & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0)
                    notifyChar = ch;
            }
            if (writeChar == null || notifyChar == null) {
                setState("FFA0 trouvé, caractéristiques WRITE/NOTIFY manquantes", false); return;
            }
            note("FFA0 confirmé • WRITE " + writeChar.getUuid() + " • NOTIFY " + notifyChar.getUuid());
            try {
                boolean enabled = g.setCharacteristicNotification(notifyChar, true);
                BluetoothGattDescriptor cccd = notifyChar.getDescriptor(CCCD);
                if (!enabled || cccd == null) {
                    setState("FFA0 trouvé, mais abonnement aux notifications impossible", false);
                    return;
                }
                byte[] value = (notifyChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                boolean sent;
                if (Build.VERSION.SDK_INT >= 33)
                    sent = g.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS;
                else {
                    cccd.setValue(value);
                    sent = g.writeDescriptor(cccd);
                }
                if (!sent) setState("Échec d'activation des notifications", false);
            } catch (Exception e) { setState("Erreur lors de l'abonnement", false); note(e.toString()); }
        }
        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            note("CCCD résultat=" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notified = true;
                setState("Service BT200 FFA0 prêt • requête de lecture...", false);
                main.post(() -> query.setEnabled(true));
                main.postDelayed(() -> readStatus(), 500);
            } else setState("Notifications non activées : " + status, false);
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch, byte[] value) {
            handleResponse(value);
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            if (Build.VERSION.SDK_INT < 33) handleResponse(ch.getValue());
        }
        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic ch, int status) {
            note("WRITE résultat=" + status);
        }
    };
    private void readStatus() {
        BluetoothGatt g = gatt;
        BluetoothGattCharacteristic ch = writeChar;
        if (g == null || ch == null || !notified) { note("Requête impossible, attendre FFA0 + notifications"); return; }
        int writeType = (ch.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        try {
            boolean ok;
            if (Build.VERSION.SDK_INT >= 33)
                ok = g.writeCharacteristic(ch, READ_STATUS, writeType) == BluetoothStatusCodes.SUCCESS;
            else {
                ch.setWriteType(writeType);
                ch.setValue(READ_STATUS);
                ok = g.writeCharacteristic(ch);
            }
            note("TX " + hex(READ_STATUS) + " writeType=" + writeType + " accepté=" + ok);
            if (!ok) setState("Requête refusée par Android", false);
            else {
                int epoch = requestEpoch;
                main.postDelayed(() -> {
                    if (epoch == requestEpoch && receivedValid == 0)
                        setState("Pas de réponse AA55+CRC après 10 s : exporter le journal", false);
                }, 10000);
            }
        } catch (Exception e) { note(e.toString()); setState("Erreur d'envoi requête", false); }
    }
    private synchronized void handleResponse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        note("RX " + hex(bytes));
        buffer.write(bytes, 0, bytes.length);
        byte[] all = buffer.toByteArray();
        if (all.length > 8192) { buffer.reset(); note("RX buffer reset (trop volumineux)"); return; }
        int pos = 0;
        while (pos + 7 <= all.length) {
            if ((all[pos] & 255) != 0xaa || (all[pos + 1] & 255) != 0x55) { pos++; continue; }
            int length = (all[pos + 3] & 255) | ((all[pos + 4] & 255) << 8);
            if (length > 4096) { pos++; continue; }
            int end = pos + length + 7;
            if (end > all.length) break;
            int expected = (all[end - 2] & 255) | ((all[end - 1] & 255) << 8);
            int real = crc16(all, pos, end - 2);
            if (real != expected) { note("CRC invalide " + expected + " != " + real); pos++; continue; }
            receivedValid++;
            int opcode = all[pos + 2] & 255;
            setState("COMMUNICATION BT200 CONFIRMÉE ✅ (opcode 0x" + Integer.toHexString(opcode).toUpperCase(Locale.ROOT) + ")", true);
            note("RÉPONSE VALIDE AA55+CRC trame #" + receivedValid + " " + hex(Arrays.copyOfRange(all, pos, end)));
            pos = end;
        }
        if (pos > 0) {
            buffer.reset();
            buffer.write(all, pos, all.length - pos);
        }
    }
    private int crc16(byte[] data, int start, int end) {
        int crc = 0xffff;
        for (int i = start; i < end; i++) {
            crc ^= data[i] & 255;
            for (int bit=0; bit<8; bit++) crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0xa001 : (crc >>> 1);
        }
        return crc & 65535;
    }
    private String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append(String.format(Locale.US, "%02X", b & 255));
        return sb.toString();
    }
    private void shareReport() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, report.toString());
        startActivity(Intent.createChooser(share, "Envoyer le diagnostic BT200"));
    }
    private void closeGatt() {
        requestEpoch++; notified = false; writeChar = null;
        main.post(() -> { if (query != null) query.setEnabled(false); });
        BluetoothGatt old = gatt; gatt = null;
        if (old != null) {
            try { old.disconnect(); old.close(); } catch (Exception ignored) {}
        }
    }
    @Override protected void onDestroy() { stopScan(); closeGatt(); super.onDestroy(); }
}