package br.com.calunaty.acbremissor;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Guarda senha do certificado e CSC cifrados com AES-GCM; a chave fica no Android Keystore (nao sai do aparelho). */
final class Cofre {
    private static final String ALIAS = "acbremissor-cofre";
    private final SharedPreferences prefs;

    Cofre(Context c) { prefs = c.getSharedPreferences("cofre", Context.MODE_PRIVATE); }

    private SecretKey chave() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return g.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    void gravar(String nome, String valor) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, chave());
        byte[] iv = c.getIV(), ct = c.doFinal(valor.getBytes("UTF-8"));
        ByteBuffer b = ByteBuffer.allocate(1 + iv.length + ct.length); b.put((byte) iv.length).put(iv).put(ct);
        prefs.edit().putString(nome, Base64.encodeToString(b.array(), Base64.NO_WRAP)).apply();
    }

    String ler(String nome) {
        String s = prefs.getString(nome, null); if (s == null) return "";
        try {
            ByteBuffer b = ByteBuffer.wrap(Base64.decode(s, Base64.NO_WRAP)); byte[] iv = new byte[b.get()]; b.get(iv);
            byte[] ct = new byte[b.remaining()]; b.get(ct);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, chave(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), "UTF-8");
        } catch (Exception e) { return ""; }
    }

    boolean tem(String nome) { return prefs.contains(nome); }
}
