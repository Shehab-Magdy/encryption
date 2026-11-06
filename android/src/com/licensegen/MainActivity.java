package com.licensegen;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Android port of admin_app.py: Method A (Fernet) -> HID -> Method B (HMAC) activation code. */
public class MainActivity extends Activity {

    // Must match license_core.py exactly.
    private static final String FERNET_KEY_B64 = "NjhyX0tpLTMxVmFjTjB5bmZTTHFYbW1rUXpUVlNUYjlZcFN4dEdOVGxSdz0=";
    private static final String HMAC_KEY_B64 = "HrIJ4BDWQbKGt40uWL6RuBawhB25z7Jm2DhPdmnTdY8=";

    private static final String DB_FILE = "licenses_db.json";

    private EditText input;
    private TextView output;
    private Button copyButton;

    // ---- Core (mirrors LicenseManager.decrypt_A / encrypt_B) ----

    /** Fernet decrypt: token = 0x80 | ts(8) | iv(16) | AES-128-CBC ciphertext | HMAC-SHA256(32). */
    static String decryptA(String token) throws Exception {
        byte[] outer = Base64.decode(FERNET_KEY_B64, Base64.DEFAULT);
        byte[] key = Base64.decode(new String(outer, StandardCharsets.US_ASCII), Base64.URL_SAFE);
        if (key.length != 32) throw new IllegalStateException("Bad Fernet key");
        byte[] signKey = Arrays.copyOfRange(key, 0, 16);
        byte[] encKey = Arrays.copyOfRange(key, 16, 32);

        byte[] data = Base64.decode(token, Base64.URL_SAFE);
        if (data.length < 1 + 8 + 16 + 16 + 32 || data[0] != (byte) 0x80) {
            throw new IllegalArgumentException("Invalid token");
        }
        int macStart = data.length - 32;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signKey, "HmacSHA256"));
        byte[] expected = mac.doFinal(Arrays.copyOfRange(data, 0, macStart));
        if (!MessageDigest.isEqual(expected, Arrays.copyOfRange(data, macStart, data.length))) {
            throw new IllegalArgumentException("Invalid signature");
        }
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encKey, "AES"),
                new IvParameterSpec(Arrays.copyOfRange(data, 9, 25)));
        return new String(c.doFinal(data, 25, macStart - 25), StandardCharsets.UTF_8);
    }

    /** Activation code = "<hid>|<hex hmac-sha256(hid)>". */
    static String encryptB(String hid) throws Exception {
        byte[] hmacKey = Base64.decode(HMAC_KEY_B64, Base64.DEFAULT);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
        byte[] sig = mac.doFinal(hid.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hid).append('|');
        for (byte b : sig) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ---- JSON database (same record shape as admin_app.py) ----

    private File dbFile() {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        return new File(dir, DB_FILE);
    }

    private JSONArray loadDb() {
        File f = dbFile();
        if (!f.exists()) return new JSONArray();
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new JSONArray(bo.toString("UTF-8"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveToDb(String hid, String hidA, String hidB) throws Exception {
        JSONArray db = loadDb();
        JSONObject rec = new JSONObject();
        rec.put("ID", db.length() + 1);
        rec.put("HID_raw", hid);
        rec.put("HID_A", hidA);
        rec.put("HID_B", hidB);
        rec.put("created_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
        db.put(rec);
        try (FileOutputStream out = new FileOutputStream(dbFile())) {
            out.write(db.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---- UI ----

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView inLabel = new TextView(this);
        inLabel.setText("أدخل الكود المشفر (Method A):");
        inLabel.setTextSize(16);
        root.addView(inLabel);

        input = new EditText(this);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setTypeface(Typeface.MONOSPACE);
        root.addView(input);

        Button gen = new Button(this);
        gen.setText("إنشاء كود التفعيل");
        gen.setOnClickListener(v -> generate());
        root.addView(gen);

        TextView outLabel = new TextView(this);
        outLabel.setText("كود التفعيل:");
        outLabel.setTextSize(16);
        outLabel.setPadding(0, dp(16), 0, 0);
        root.addView(outLabel);

        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setTypeface(Typeface.MONOSPACE);
        output.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        output.setPadding(0, dp(8), 0, dp(8));
        root.addView(output);

        copyButton = new Button(this);
        copyButton.setText("نسخ كود التفعيل");
        copyButton.setVisibility(View.GONE);
        copyButton.setOnClickListener(v -> copy());
        root.addView(copyButton);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    private void generate() {
        String encrypted = input.getText().toString().trim();
        if (encrypted.isEmpty()) {
            toast("يرجى إدخال الكود المشفر");
            return;
        }
        try {
            String hid = decryptA(encrypted);
            String code = encryptB(hid);
            output.setText(code);
            saveToDb(hid, encrypted, code);
            copyButton.setVisibility(View.VISIBLE);
            toast("تم إنشاء كود التفعيل وحفظه في قاعدة البيانات");
        } catch (Exception e) {
            toast("فشل في فك التشفير: " + e.getMessage());
        }
    }

    private void copy() {
        String text = output.getText().toString();
        if (text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("activation", text));
        toast("تم نسخ كود التفعيل إلى الحافظة");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
