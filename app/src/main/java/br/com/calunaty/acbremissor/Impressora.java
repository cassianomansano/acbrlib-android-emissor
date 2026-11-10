package br.com.calunaty.acbremissor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Envio de bytes (cupom ESC/POS ou Daruma gerado pela ACBrLib) para impressora termica Bluetooth (perfil SPP).
 *  A ACBrLib do Free Pascal nao tem Bluetooth nativo no Android: ela grava o cupom em arquivo e o app envia. */
final class Impressora {
    static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    static BluetoothAdapter adaptador(Context c) {
        BluetoothManager m = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
        return m == null ? null : m.getAdapter();
    }

    /** Impressoras ja pareadas nas configuracoes do Android: "nome|MAC". */
    static List<String> pareadas(Context c) {
        List<String> r = new ArrayList<>(); BluetoothAdapter a = adaptador(c);
        if (a == null) return r;
        for (BluetoothDevice d : a.getBondedDevices()) r.add((d.getName() == null ? "(sem nome)" : d.getName()) + "|" + d.getAddress());
        return r;
    }

    static void enviar(Context c, String mac, byte[] dados, Emissor.Saida log) throws Exception {
        BluetoothAdapter a = adaptador(c);
        if (a == null || !a.isEnabled()) throw new Exception("Bluetooth desligado");
        // Parar uma busca em andamento exige BLUETOOTH_SCAN (Android 12+); com a impressora ja pareada, nao precisa.
        try { if (a.isDiscovering()) a.cancelDiscovery(); } catch (SecurityException ignored) {}
        BluetoothDevice dev = a.getRemoteDevice(mac);
        BluetoothSocket s = null; Exception ultimo = null;
        for (int tentativa = 0; tentativa < 3 && s == null; tentativa++) {
            BluetoothSocket t = null;
            try {
                if (tentativa == 0) t = dev.createRfcommSocketToServiceRecord(SPP);
                else if (tentativa == 1) t = dev.createInsecureRfcommSocketToServiceRecord(SPP);
                else { Method m = dev.getClass().getMethod("createRfcommSocket", int.class); t = (BluetoothSocket) m.invoke(dev, 1); }
                t.connect(); s = t;
            } catch (Exception e) {
                ultimo = e; try { if (t != null) t.close(); } catch (Exception ignored) {}
                log.msg("Bluetooth: tentativa " + (tentativa + 1) + " falhou (" + e.getMessage() + ")");
            }
        }
        if (s == null) throw new Exception("não conectou na impressora: " + (ultimo == null ? "?" : ultimo.getMessage()));
        try {
            OutputStream o = s.getOutputStream();
            for (int i = 0; i < dados.length; i += 512) { o.write(dados, i, Math.min(512, dados.length - i)); o.flush(); Thread.sleep(25); }
            Thread.sleep(1500);   // deixa a impressora esvaziar o buffer antes de fechar
        } finally { try { s.close(); } catch (Exception ignored) {} }
    }

    /** Pagina de teste simples, sem a ACBrLib, para validar a conexao. */
    static byte[] teste(boolean daruma) {
        String txt = "\n  TESTE DE IMPRESSAO\n  Emissor de testes ACBr Android\n  Calunaty Sistemas\n\n\n\n";
        byte[] corpo = txt.getBytes();
        byte[] ini = {0x1B, 0x40};
        byte[] corte = daruma ? new byte[]{0x1B, 0x6D} : new byte[]{0x1D, 0x56, 0x01};
        byte[] r = new byte[ini.length + corpo.length + corte.length];
        System.arraycopy(ini, 0, r, 0, ini.length); System.arraycopy(corpo, 0, r, ini.length, corpo.length);
        System.arraycopy(corte, 0, r, ini.length + corpo.length, corte.length);
        return r;
    }
}
