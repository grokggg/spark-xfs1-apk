package com.xinghuo.sparkdemo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {

    private static final String APP_KEY_HEX =
            "9f2c41d7a83b06e5c1d94f7b28ae6305d4b81c9f7a2e5d30c6b94f1a8e7d2c05";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private TextView output;
    private SecureRandom rng = new SecureRandom();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 72, 48, 48);
        root.setBackgroundColor(Color.parseColor("#0B1020"));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Spark XFS-1 Demo · self-signed build");
        title.setTextColor(Color.parseColor("#7DE2D1"));
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("算法 XFS-1：SHA-256 派生 + AES-256-GCM 封装 + HMAC-SHA256 绑定");
        sub.setTextColor(Color.parseColor("#9AA7C7"));
        sub.setTextSize(13f);
        sub.setPadding(0, 12, 0, 24);
        root.addView(sub);

        output = new TextView(this);
        output.setTextColor(Color.parseColor("#E8EDF7"));
        output.setTextSize(12f);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setGravity(Gravity.START);
        root.addView(output);

        Button gen = new Button(this);
        gen.setText("生成 XFS-1 令牌");
        gen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runDemo();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 36;
        root.addView(gen, lp);

        setContentView(scroll);
        runDemo();
    }

    private void runDemo() {
        StringBuilder sb = new StringBuilder();
        try {
            String pkg = getPackageName();
            String ver = "1";
            try {
                ver = getPackageManager().getPackageInfo(pkg, 0).versionName;
            } catch (Exception ignored) {
            }
            sb.append("package : ").append(pkg).append('\n');
            sb.append("version : ").append(ver).append('\n');
            sb.append("provider: XFS-1 (self-implemented)\n\n");

            byte[] salt = new byte[16];
            rng.nextBytes(salt);

            byte[] key = deriveKey(pkg, salt);
            sb.append("salt    : ").append(hex(salt)).append('\n');
            sb.append("kdf key : ").append(hex(shorten(key, 8))).append("…\n");

            long ts = System.currentTimeMillis();
            String nonce = hex(randomBytes(8));
            String payload = "{\"app\":\"" + pkg + "\",\"ver\":\"" + ver
                    + "\",\"ts\":" + ts + ",\"nonce\":\"" + nonce + "\"}";

            byte[] token = seal(key, payload.getBytes(UTF8));
            String tokenB64 = Base64.encodeToString(token, Base64.NO_WRAP);
            sb.append("token   : ").append(tokenB64).append('\n');

            // 自检：解回来必须与原文一致
            String back = new String(open(key, token), UTF8);
            boolean ok = payload.equals(back);
            sb.append("decrypt : ").append(ok ? "OK" : "MISMATCH").append('\n');
            sb.append("payload : ").append(back).append('\n');

            // 设备绑定令牌（HMAC）
            byte[] mac = hmac(key, (pkg + "|" + ver + "|" + ts).getBytes(UTF8));
            sb.append("hmac16  : ").append(hex(shorten(mac, 16))).append('\n');

            sb.append("\nXFS-1 self-test: ").append(ok ? "OK" : "FAILED");
        } catch (Throwable t) {
            sb.append("XFS-1 self-test: FAILED\n");
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
        }
        output.setText(sb.toString());
    }

    private byte[] deriveKey(String pkg, byte[] salt) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update("XFS-1".getBytes(UTF8));
        md.update(pkg.getBytes(UTF8));
        md.update(salt);
        return md.digest();
    }

    private byte[] seal(byte[] key, byte[] plain) throws Exception {
        byte[] iv = randomBytes(IV_LEN);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return out;
    }

    private byte[] open(byte[] key, byte[] blob) throws Exception {
        byte[] iv = new byte[IV_LEN];
        System.arraycopy(blob, 0, iv, 0, IV_LEN);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        return c.doFinal(blob, IV_LEN, blob.length - IV_LEN);
    }

    private byte[] hmac(byte[] key, byte[] msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg);
    }

    private byte[] appKey() {
        int n = APP_KEY_HEX.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(APP_KEY_HEX.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        rng.nextBytes(b);
        return b;
    }

    private static byte[] shorten(byte[] in, int n) {
        byte[] out = new byte[Math.min(n, in.length)];
        System.arraycopy(in, 0, out, 0, out.length);
        return out;
    }

    private static String hex(byte[] in) {
        StringBuilder sb = new StringBuilder(in.length * 2);
        for (byte b : in) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
