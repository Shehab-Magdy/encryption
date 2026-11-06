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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Android port of admin_app.py: sign a short hardware code into a short activation code via HMAC. */
public class MainActivity extends Activity {

    // Must match license_core.py exactly.
    private static final String HMAC_KEY_B64 = "HrIJ4BDWQbKGt40uWL6RuBawhB25z7Jm2DhPdmnTdY8=";
    private static final String B32_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int CODE_BYTES = 10; // 80 bits -> 16 base32 chars -> grouped as XXXX-XXXX-XXXX-XXXX

    private static final String DB_FILE = "licenses_db.json";

    private EditText input;
    private TextView output;
    private Button copyButton;

    // ---- Core (mirrors LicenseManager._encode_short_code / _normalize / generate_activation_code) ----

    /** Encode raw bytes as a short, human-readable grouped code (e.g. XK3F-9QPL-7ZRT-M2CD). */
    static String encodeShortCode(byte[] raw) {
        int totalBits = raw.length * 8;
        int numChars = (totalBits + 4) / 5; // ceil(totalBits / 5)
        BigInteger value = new BigInteger(1, raw); // unsigned
        StringBuilder sb = new StringBuilder();
        for (int i = numChars - 1; i >= 0; i--) {
            int idx = value.shiftRight(i * 5).and(BigInteger.valueOf(0x1f)).intValue();
            sb.append(B32_ALPHABET.charAt(idx));
        }
        StringBuilder grouped = new StringBuilder();
        String code = sb.toString();
        for (int i = 0; i < code.length(); i += 4) {
            if (i > 0) grouped.append('-');
            grouped.append(code, i, Math.min(i + 4, code.length()));
        }
        return grouped.toString();
    }

    /** Normalize a user/admin-entered code for comparison (strip separators, uppercase). */
    static String normalize(String code) {
        StringBuilder sb = new StringBuilder();
        for (char c : code.trim().toUpperCase(Locale.US).toCharArray()) {
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** Admin-side: sign a hardware code into a short activation code. */
    static String generateActivationCode(String hardwareId) throws Exception {
        byte[] hmacKey = Base64.decode(HMAC_KEY_B64, Base64.DEFAULT);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
        byte[] sig = mac.doFinal(normalize(hardwareId).getBytes(StandardCharsets.UTF_8));
        return encodeShortCode(Arrays.copyOfRange(sig, 0, CODE_BYTES));
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

    private void saveToDb(String hardwareId, String activationCode) throws Exception {
        JSONArray db = loadDb();
        JSONObject rec = new JSONObject();
        rec.put("ID", db.length() + 1);
        rec.put("HardwareID", hardwareId);
        rec.put("ActivationCode", activationCode);
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
        inLabel.setText("أدخل كود الجهاز (Hardware Code):");
        inLabel.setTextSize(16);
        root.addView(inLabel);

        input = new EditText(this);
        input.setMinLines(1);
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
        String hardwareId = input.getText().toString().trim();
        if (hardwareId.isEmpty()) {
            toast("يرجى إدخال كود الجهاز");
            return;
        }
        try {
            String code = generateActivationCode(hardwareId);
            output.setText(code);
            saveToDb(hardwareId, code);
            copyButton.setVisibility(View.VISIBLE);
            toast("تم إنشاء كود التفعيل وحفظه في قاعدة البيانات");
        } catch (Exception e) {
            toast("فشل في إنشاء الكود: " + e.getMessage());
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
