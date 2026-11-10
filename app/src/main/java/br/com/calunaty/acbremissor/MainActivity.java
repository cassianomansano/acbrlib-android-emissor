package br.com.calunaty.acbremissor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.CheckBox;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import br.com.acbr.lib.nfe.ACBrLibNFe;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** Emissor de TESTE em HOMOLOGACAO (NF-e 55 / NFC-e 65) usando a ACBrLib Android (Projeto ACBr) via JNA. */
public class MainActivity extends Activity {
    private static final int ESCOLHER_PFX = 10;
    private SharedPreferences prefs; private Cofre cofre; private Historico historico;
    private File base, docs, pfx;
    private ACBrLibNFe nfe; private final Object trava = new Object();
    private final Emissor emissor = new Emissor(); private volatile boolean emitindo;
    private final LinkedList<String> mensagens = new LinkedList<>();

    private LinearLayout corpo; private TextView tvMsgs, tvCert;
    private EditText edSerie, edUltima, edSenha, edIdCsc, edCsc;
    private final java.util.LinkedHashMap<String, EditText> camposNota = new java.util.LinkedHashMap<>();
    private RadioGroup rgModelo, rgPag; private Button btEmitir, btCancelar;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("emissor", MODE_PRIVATE); cofre = new Cofre(this);
        base = new File(getFilesDir(), "acbr"); docs = new File(base, "docs"); docs.mkdirs(); pfx = new File(base, "certificado.pfx");
        historico = new Historico(getFilesDir());

        LinearLayout raiz = col(); raiz.setPadding(24, 48, 24, 24);
        TextView titulo = txt("Emissor de testes · HOMOLOGAÇÃO", 18); titulo.setTypeface(null, Typeface.BOLD); raiz.addView(titulo);
        raiz.addView(txt("Sem valor fiscal. Ambiente travado em homologação (SEFAZ da UF do emitente).", 12));
        LinearLayout abas = new LinearLayout(this);
        for (String[] a : new String[][]{{"Emitir", "e"}, {"Emitidas", "h"}, {"Configurar", "c"}}) {
            Button bt = new Button(this); bt.setText(a[0]); bt.setAllCaps(false);
            bt.setOnClickListener(v -> mostrar(a[1])); abas.addView(bt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        raiz.addView(abas);
        corpo = col(); ScrollView sv = new ScrollView(this); sv.addView(corpo); raiz.addView(sv);
        setContentView(raiz);
        new Thread(this::prepararSchemas).start();
        mostrar(pfx.isFile() && cofre.tem("senha") ? "e" : "c");
    }

    // ---------------- telas ----------------
    private void mostrar(String aba) {
        corpo.removeAllViews();
        if (aba.equals("c")) telaConfig(); else if (aba.equals("h")) telaHistorico(); else telaEmitir();
    }

    private void telaEmitir() {
        corpo.addView(txt("Modelo", 14));
        rgModelo = radios(new String[]{"NFC-e (65)", "NF-e (55)"}, prefs.getInt("modelo", 65) == 65 ? 0 : 1);
        corpo.addView(rgModelo);
        edSerie = campo("Série", String.valueOf(prefs.getInt("serie", 1)), true);
        edUltima = campo("Última nota emitida (vai tentar a próxima)", String.valueOf(ultima(modeloSel(), prefs.getInt("serie", 1))), true);
        rgModelo.setOnCheckedChangeListener((g, id) -> edUltima.setText(String.valueOf(ultima(modeloSel(), num(edSerie, 1)))));
        corpo.addView(txt("Pagamento (parte em dinheiro)", 14));
        rgPag = radios(new String[]{"Dinheiro + Cartão", "Dinheiro + PIX"}, prefs.getInt("pag", 0)); corpo.addView(rgPag);
        NotaTeste.Pessoa de = pessoa("dest");
        corpo.addView(txt("Destinatário: " + (de.doc.isEmpty() ? "(preencha em Configurar → Dados da nota)" : de.doc + ", " + de.logradouro + " " + de.numero + ", " + de.municipio + "/" + de.uf)
                + ". Em homologação a SEFAZ exige o nome \"" + NotaTeste.NOME_HOMOLOGACAO + "\". 5 produtos de exemplo, desconto de 10%.", 12));
        btEmitir = new Button(this); btEmitir.setText("Emitir nota de teste"); btEmitir.setAllCaps(false);
        btCancelar = new Button(this); btCancelar.setText("Cancelar operação"); btCancelar.setAllCaps(false); btCancelar.setEnabled(emitindo);
        btEmitir.setEnabled(!emitindo);
        btEmitir.setOnClickListener(v -> emitir()); btCancelar.setOnClickListener(v -> { emissor.cancelar = true; msg("Cancelamento pedido — para antes da próxima tentativa."); });
        LinearLayout l = new LinearLayout(this); l.addView(btEmitir, peso()); l.addView(btCancelar, peso()); corpo.addView(l);
        corpo.addView(txt("Últimas mensagens", 14));
        tvMsgs = txt("", 12); tvMsgs.setTypeface(Typeface.MONOSPACE); tvMsgs.setTextIsSelectable(true); corpo.addView(tvMsgs); atualizarMsgs();
    }

    private void telaConfig() {
        tvCert = txt(pfx.isFile() ? "Certificado importado ✔ (" + pfx.length() + " bytes)" : "Nenhum certificado importado.", 14); corpo.addView(tvCert);
        Button imp = new Button(this); imp.setText("Importar certificado .pfx"); imp.setAllCaps(false);
        imp.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, ESCOLHER_PFX); });
        corpo.addView(imp);
        edSenha = campo("Senha do certificado", "", false); edSenha.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edSenha.setHint(cofre.tem("senha") ? "•••••• (já gravada; digite para trocar)" : "");
        edIdCsc = campo("ID do CSC (NFC-e, homologação — ex.: 000001)", cofre.ler("idcsc"), false);
        edCsc = campo("CSC / token (NFC-e, homologação)", "", false); edCsc.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edCsc.setHint(cofre.tem("csc") ? "•••••• (já gravado; digite para trocar)" : "");
        camposNota.clear();
        TextView tn = txt("Dados da nota (ficam só neste aparelho)", 16); tn.setTypeface(null, Typeface.BOLD); corpo.addView(tn);
        corpo.addView(txt("Emitente (precisa ser a empresa do certificado)", 14));
        campos("emit", new String[][]{{"doc","CNPJ"},{"nome","Razão social"},{"fantasia","Nome fantasia (opcional)"},{"ie","Inscrição estadual"},
                {"crt","CRT (1 = Simples Nacional)"},{"logradouro","Logradouro"},{"numero","Número"},{"bairro","Bairro"},
                {"codMunicipio","Código IBGE do município (7 dígitos)"},{"municipio","Município"},{"uf","UF (ex.: SP)"},{"cep","CEP"},{"fone","Telefone (opcional)"}});
        corpo.addView(txt("Destinatário (consumidor de teste)", 14));
        campos("dest", new String[][]{{"doc","CPF ou CNPJ"},{"logradouro","Logradouro"},{"numero","Número"},{"bairro","Bairro"},
                {"codMunicipio","Código IBGE do município"},{"municipio","Município"},{"uf","UF"},{"cep","CEP"}});
        corpo.addView(txt("Responsável técnico (opcional; obrigatório em algumas UFs)", 14));
        campos("rt", new String[][]{{"cnpj","CNPJ"},{"contato","Contato"},{"email","E-mail"},{"fone","Telefone"}});
        Button salvar = new Button(this); salvar.setText("Salvar"); salvar.setAllCaps(false);
        salvar.setOnClickListener(v -> {
            try {
                if (edSenha.getText().length() > 0) cofre.gravar("senha", edSenha.getText().toString());
                cofre.gravar("idcsc", edIdCsc.getText().toString().trim());
                SharedPreferences.Editor ed = prefs.edit();
                for (java.util.Map.Entry<String, EditText> c : camposNota.entrySet()) ed.putString(c.getKey(), c.getValue().getText().toString().trim());
                ed.apply();
                if (edCsc.getText().length() > 0) cofre.gravar("csc", edCsc.getText().toString().trim());
                synchronized (trava) { if (nfe != null) { try { nfe.finalizar(); } catch (Throwable ignored) {} nfe = null; } }
                Toast.makeText(this, "Salvo (cifrado no aparelho)", Toast.LENGTH_SHORT).show(); mostrar("c");
            } catch (Exception e) { Toast.makeText(this, "Erro ao salvar: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        });
        Button teste = new Button(this); teste.setText("Testar SEFAZ (status do serviço)"); teste.setAllCaps(false);
        TextView res = txt("", 12); res.setTypeface(Typeface.MONOSPACE);
        teste.setOnClickListener(v -> { res.setText("Consultando…"); new Thread(() -> {
            String t; try { synchronized (trava) { t = lib(modeloSel()).StatusServico(); } } catch (Throwable e) { t = "Erro: " + e.getMessage(); }
            String f = Emissor.corta(t); runOnUiThread(() -> res.setText(f)); }).start(); });
        corpo.addView(salvar); corpo.addView(teste); corpo.addView(res);

        // ---- impressora Bluetooth (NFC-e) ----
        TextView tit = txt("Impressora Bluetooth (cupom NFC-e)", 16); tit.setTypeface(null, Typeface.BOLD); corpo.addView(tit);
        TextView tvImp = txt(prefs.getString("imp-mac", "").isEmpty() ? "Nenhuma impressora escolhida. Pareie a DR800 nas configurações de Bluetooth do Android antes."
                : "Impressora: " + prefs.getString("imp-nome", "") + " (" + prefs.getString("imp-mac", "") + ")", 14);
        corpo.addView(tvImp);
        Button esc = new Button(this); esc.setText("Escolher impressora pareada"); esc.setAllCaps(false);
        esc.setOnClickListener(v -> { if (permissaoBluetooth()) escolherImpressora(); });
        corpo.addView(esc);
        RadioGroup rgProt = radios(new String[]{"Daruma (DR800 padrão)", "ESC/POS"}, prefs.getInt("imp-modelo", 3) == 3 ? 0 : 1);
        rgProt.setOnCheckedChangeListener((gr, id) -> prefs.edit().putInt("imp-modelo", id == gr.getChildAt(0).getId() ? 3 : 1).apply());
        corpo.addView(rgProt);
        CheckBox auto = new CheckBox(this); auto.setText("Imprimir automaticamente ao autorizar NFC-e"); auto.setChecked(prefs.getBoolean("imp-auto", true));
        auto.setOnCheckedChangeListener((cb, on) -> prefs.edit().putBoolean("imp-auto", on).apply()); corpo.addView(auto);
        Button tst = new Button(this); tst.setText("Imprimir teste"); tst.setAllCaps(false);
        tst.setOnClickListener(v -> {
            if (!permissaoBluetooth()) return;
            String mac = prefs.getString("imp-mac", ""); if (mac.isEmpty()) { Toast.makeText(this, "Escolha a impressora primeiro.", Toast.LENGTH_SHORT).show(); return; }
            Toast.makeText(this, "Enviando teste…", Toast.LENGTH_SHORT).show();
            new Thread(() -> { try { Impressora.enviar(this, mac, Impressora.teste(prefs.getInt("imp-modelo", 3) == 3), this::msg); toast("Teste enviado."); }
                               catch (Exception e) { toast("Falha: " + e.getMessage()); } }).start();
        });
        corpo.addView(tst);
        corpo.addView(txt("O certificado e a senha ficam só neste aparelho (área privada do app; senha e CSC cifrados pelo Keystore do Android).", 12));
    }

    private void telaHistorico() {
        List<JSONObject> l = historico.listar();
        if (l.isEmpty()) { corpo.addView(txt("Nenhuma nota autorizada ainda.", 14)); return; }
        for (JSONObject o : l) {
            LinearLayout card = col(); card.setPadding(8, 16, 8, 16);
            card.addView(txt((o.optInt("modelo") == 65 ? "NFC-e" : "NF-e") + " série " + o.optInt("serie") + " nº " + o.optInt("numero")
                    + " · R$ " + o.optString("vNF") + " · " + o.optString("data"), 14));
            TextView ch = txt("Chave " + o.optString("chave") + "\nProtocolo " + o.optString("protocolo"), 11); ch.setTypeface(Typeface.MONOSPACE); ch.setTextIsSelectable(true); card.addView(ch);
            LinearLayout bts = new LinearLayout(this);
            Button ab = new Button(this); ab.setText("Abrir PDF"); ab.setAllCaps(false); ab.setOnClickListener(v -> abrirPdf(o));
            Button co = new Button(this); co.setText("Compartilhar"); co.setAllCaps(false); co.setOnClickListener(v -> compartilhar(o));
            bts.addView(ab, peso()); bts.addView(co, peso());
            if (o.optInt("modelo") == 65) { Button im = new Button(this); im.setText("Imprimir"); im.setAllCaps(false); im.setOnClickListener(v -> imprimir(o)); bts.addView(im, peso()); }
            card.addView(bts); corpo.addView(card);
        }
    }

    // ---------------- emissao ----------------
    private void emitir() {
        if (!pfx.isFile() || !cofre.tem("senha")) { Toast.makeText(this, "Importe o certificado e grave a senha em Configurar.", Toast.LENGTH_LONG).show(); return; }
        final int modelo = modeloSel(), serie = num(edSerie, 1), proximo = num(edUltima, 0) + 1;
        final NotaTeste.Pagamento pag = rgPag.getCheckedRadioButtonId() == rgPag.getChildAt(1).getId() ? NotaTeste.Pagamento.DINHEIRO_PIX : NotaTeste.Pagamento.DINHEIRO_CARTAO;
        if (serie < 0 || serie > 999) { Toast.makeText(this, "Série deve ser de 0 a 999 (regra da SEFAZ).", Toast.LENGTH_LONG).show(); return; }
        if (proximo < 1 || proximo > 999999999) { Toast.makeText(this, "Número deve ser de 1 a 999.999.999.", Toast.LENGTH_LONG).show(); return; }
        final NotaTeste.Pessoa emit = pessoa("emit"), dest = pessoa("dest"); final NotaTeste.RespTec rt = respTec();
        try { NotaTeste.codigoUF(emit.uf); NotaTeste.codigoUF(dest.uf); } catch (Exception e) { Toast.makeText(this, "Preencha a UF do emitente e do destinatário (Configurar → Dados da nota).", Toast.LENGTH_LONG).show(); return; }
        if (emit.doc.isEmpty() || emit.nome.isEmpty() || emit.ie.isEmpty() || emit.codMunicipio.isEmpty() || dest.doc.isEmpty()) {
            Toast.makeText(this, "Preencha emitente e destinatário em Configurar → Dados da nota.", Toast.LENGTH_LONG).show(); return; }
        if (modelo == 65 && (!cofre.tem("csc") || cofre.ler("idcsc").isEmpty())) { Toast.makeText(this, "NFC-e precisa do ID do CSC e do CSC (Configurar).", Toast.LENGTH_LONG).show(); return; }
        prefs.edit().putInt("modelo", modelo).putInt("serie", serie).putInt("pag", pag == NotaTeste.Pagamento.DINHEIRO_PIX ? 1 : 0).apply();
        emitindo = true; emissor.cancelar = false; btEmitir.setEnabled(false); btCancelar.setEnabled(true);
        msg("Início: " + (modelo == 65 ? "NFC-e" : "NF-e") + " série " + serie + " a partir do nº " + proximo);
        new Thread(() -> {
            try {
                Emissor.Resultado r;
                synchronized (trava) { r = emissor.emitir(lib(modelo), docs, modelo, serie, proximo, pag, emit, dest, rt, this::msg); }
                if (r.autorizada) {
                    prefs.edit().putInt("ultima-" + modelo + "-" + serie, r.numero).apply();
                    JSONObject o = new JSONObject().put("modelo", modelo).put("serie", serie).put("numero", r.numero).put("vNF", r.vNF)
                            .put("chave", r.chave).put("protocolo", r.protocolo).put("data", agora())
                            .put("xml", r.xml == null ? "" : r.xml.getName()).put("pdf", r.pdf == null ? "" : r.pdf.getName());
                    historico.adicionar(o);
                    runOnUiThread(() -> { edUltima.setText(String.valueOf(r.numero)); compartilhar(o); });
                    if (modelo == 65 && prefs.getBoolean("imp-auto", true) && !prefs.getString("imp-mac", "").isEmpty()) imprimirAgora(o);
                }
            } catch (Throwable e) { msg("Erro: " + e.getClass().getSimpleName() + " " + e.getMessage()); }
            finally { emitindo = false; runOnUiThread(() -> { if (btEmitir != null) { btEmitir.setEnabled(true); btCancelar.setEnabled(false); } }); }
        }).start();
    }

    /** Uma instancia da lib (singleton), configurada para HOMOLOGACAO. */
    private ACBrLibNFe lib(int modelo) throws Exception {
        if (nfe == null) {
            nfe = new ACBrLibNFe(new File(base, "acbrlib.ini").getAbsolutePath(), "");
            nfe.inicializar();
            g("Principal", "LogNivel", "0");
            g("DFe", "SSLCryptLib", "1"); g("DFe", "SSLHttpLib", "3"); g("DFe", "SSLXmlSignLib", "4");
            g("DFe", "ArquivoPFX", pfx.getAbsolutePath()); g("DFe", "Senha", cofre.ler("senha")); g("DFe", "UF", pessoa("emit").uf.toUpperCase());
            g("NFe", "Ambiente", "1");  // 1 = HOMOLOGACAO (fixo)
            g("NFe", "PathSchemas", new File(base, "Schemas/NFe").getAbsolutePath());
            g("NFe", "PathSalvar", docs.getAbsolutePath()); g("NFe", "Timeout", "30000"); g("DANFE", "PathPDF", docs.getAbsolutePath());
            if (cofre.tem("csc")) { g("NFe", "IdCSC", cofre.ler("idcsc")); g("NFe", "CSC", cofre.ler("csc")); }
        }
        return nfe;
    }
    private void g(String s, String k, String v) throws Exception { nfe.configGravarValor(s, k, v); }

    // ---------------- impressao Bluetooth ----------------
    private boolean permissaoBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 20);
            Toast.makeText(this, "Permita o acesso ao Bluetooth e toque de novo.", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void escolherImpressora() {
        List<String> l;
        try { l = Impressora.pareadas(this); } catch (SecurityException e) { Toast.makeText(this, "Sem permissão de Bluetooth.", Toast.LENGTH_LONG).show(); return; }
        if (l.isEmpty()) { Toast.makeText(this, "Nenhum aparelho pareado. Pareie a DR800 nas configurações de Bluetooth.", Toast.LENGTH_LONG).show(); return; }
        String[] nomes = new String[l.size()]; for (int i = 0; i < l.size(); i++) nomes[i] = l.get(i).replace("|", "  ·  ");
        new AlertDialog.Builder(this).setTitle("Impressora pareada").setItems(nomes, (d, i) -> {
            String[] p = l.get(i).split("\\|");
            prefs.edit().putString("imp-nome", p[0]).putString("imp-mac", p[1]).apply(); mostrar("c");
        }).show();
    }

    private void imprimir(JSONObject o) {
        if (!permissaoBluetooth()) return;
        if (prefs.getString("imp-mac", "").isEmpty()) { Toast.makeText(this, "Escolha a impressora em Configurar.", Toast.LENGTH_LONG).show(); return; }
        Toast.makeText(this, "Imprimindo…", Toast.LENGTH_SHORT).show();
        new Thread(() -> imprimirAgora(o)).start();
    }

    /** ACBrLib gera o cupom (Daruma/ESC-POS, com QR Code) num arquivo .prn; o app envia os bytes por Bluetooth. */
    private void imprimirAgora(JSONObject o) {
        try {
            File xml = new File(docs, o.optString("xml")), prn = new File(docs, "cupom.prn");
            if (!xml.isFile()) throw new Exception("XML da nota não encontrado");
            prn.delete();
            synchronized (trava) {
                ACBrLibNFe n = lib(65);
                g("DANFENFCe", "TipoRelatorioBobina", "1");                       // ESC/POS (sem Fortes no Android)
                g("PosPrinter", "Modelo", String.valueOf(prefs.getInt("imp-modelo", 3)));  // 3 = Daruma, 1 = ESC/POS
                g("PosPrinter", "Porta", "FILE:" + prn.getAbsolutePath());         // .prn mantem os comandos (.txt viraria texto puro)
                g("PosPrinter", "ColunasFonteNormal", "48"); g("PosPrinter", "PaginaDeCodigo", "2"); g("PosPrinter", "CortaPapel", "1");
                n.LimparLista(); n.CarregarXML(xml.getAbsolutePath());
                n.Imprimir("", 1, "", "False", "", "True", "False");
            }
            if (!prn.isFile() || prn.length() == 0) throw new Exception("cupom não foi gerado");
            byte[] dados = java.nio.file.Files.readAllBytes(prn.toPath());
            msg("Imprimindo nº " + o.optInt("numero") + " (" + dados.length + " bytes) em " + prefs.getString("imp-nome", "") + "…");
            Impressora.enviar(this, prefs.getString("imp-mac", ""), dados, this::msg);
            msg("🖨️ Cupom nº " + o.optInt("numero") + " enviado à impressora.");
            toast("Cupom enviado à impressora.");
        } catch (Throwable e) { msg("Falha ao imprimir: " + e.getMessage()); toast("Falha ao imprimir: " + e.getMessage()); }
    }

    private void toast(String s) { runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show()); }

    // ---------------- arquivos ----------------
    private void compartilhar(JSONObject o) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (String k : new String[]{"pdf", "xml"}) { String n = o.optString(k); if (!n.isEmpty() && new File(docs, n).isFile()) uris.add(Arquivos.uri(new File(docs, n))); }
        if (uris.isEmpty()) { Toast.makeText(this, "Arquivos não encontrados.", Toast.LENGTH_SHORT).show(); return; }
        Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE); i.setType("*/*"); i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        i.putExtra(Intent.EXTRA_TEXT, (o.optInt("modelo") == 65 ? "NFC-e" : "NF-e") + " de TESTE (homologação) série " + o.optInt("serie") + " nº " + o.optInt("numero") + " — protocolo " + o.optString("protocolo"));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Enviar PDF e XML"));
    }
    private void abrirPdf(JSONObject o) {
        File f = new File(docs, o.optString("pdf")); if (!f.isFile()) { Toast.makeText(this, "PDF não encontrado.", Toast.LENGTH_SHORT).show(); return; }
        Intent i = new Intent(Intent.ACTION_VIEW); i.setDataAndType(Arquivos.uri(f), "application/pdf"); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(i); } catch (Exception e) { Toast.makeText(this, "Nenhum leitor de PDF instalado.", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != ESCOLHER_PFX || res != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData()); OutputStream o = new FileOutputStream(pfx)) {
            byte[] buf = new byte[16384]; int n; while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            synchronized (trava) { if (nfe != null) { try { nfe.finalizar(); } catch (Throwable ignored) {} nfe = null; } }
            Toast.makeText(this, "Certificado importado. Agora grave a senha.", Toast.LENGTH_LONG).show(); mostrar("c");
        } catch (Exception e) { Toast.makeText(this, "Falha ao importar: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void prepararSchemas() {
        File marca = new File(base, "Schemas/.ok");
        if (marca.isFile()) return;
        try { copiar("Schemas", new File(base, "Schemas")); marca.createNewFile(); } catch (Exception e) { msg("Falha ao preparar schemas: " + e.getMessage()); }
    }
    private void copiar(String path, File dest) throws Exception {
        AssetManager am = getAssets(); String[] itens = am.list(path);
        if (itens == null || itens.length == 0) {
            dest.getParentFile().mkdirs();
            try (InputStream in = am.open(path); OutputStream o = new FileOutputStream(dest)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) o.write(b, 0, n); }
        } else { dest.mkdirs(); for (String i : itens) copiar(path + "/" + i, new File(dest, i)); }
    }

    // ---------------- dados da nota (configuraveis) ----------------
    private void campos(String prefixo, String[][] defs) {
        for (String[] d : defs) { String k = prefixo + "." + d[0]; EditText e = campo(d[1], prefs.getString(k, ""), false); camposNota.put(k, e); }
    }
    private NotaTeste.Pessoa pessoa(String p) {
        NotaTeste.Pessoa x = new NotaTeste.Pessoa();
        x.doc = prefs.getString(p + ".doc", ""); x.nome = prefs.getString(p + ".nome", ""); x.fantasia = prefs.getString(p + ".fantasia", "");
        x.ie = prefs.getString(p + ".ie", ""); x.crt = prefs.getString(p + ".crt", "1"); x.logradouro = prefs.getString(p + ".logradouro", "");
        x.numero = prefs.getString(p + ".numero", ""); x.bairro = prefs.getString(p + ".bairro", ""); x.codMunicipio = prefs.getString(p + ".codMunicipio", "");
        x.municipio = prefs.getString(p + ".municipio", ""); x.uf = prefs.getString(p + ".uf", ""); x.cep = prefs.getString(p + ".cep", ""); x.fone = prefs.getString(p + ".fone", "");
        return x;
    }
    private NotaTeste.RespTec respTec() {
        NotaTeste.RespTec r = new NotaTeste.RespTec();
        r.cnpj = prefs.getString("rt.cnpj", ""); r.contato = prefs.getString("rt.contato", ""); r.email = prefs.getString("rt.email", ""); r.fone = prefs.getString("rt.fone", "");
        return r;
    }

    // ---------------- utilidades ----------------
    private int ultima(int modelo, int serie) { return prefs.getInt("ultima-" + modelo + "-" + serie, 1); }
    private int modeloSel() {
        if (rgModelo == null) return prefs.getInt("modelo", 65);
        return rgModelo.getCheckedRadioButtonId() == rgModelo.getChildAt(0).getId() ? 65 : 55;
    }
    private static int num(EditText e, int pad) { try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception x) { return pad; } }
    private static String agora() { return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(new Date()); }
    private void msg(String s) {
        String l = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + s;
        synchronized (mensagens) { mensagens.addFirst(l); while (mensagens.size() > 40) mensagens.removeLast(); }
        runOnUiThread(this::atualizarMsgs);
    }
    private void atualizarMsgs() { if (tvMsgs == null) return; StringBuilder b = new StringBuilder(); synchronized (mensagens) { for (String m : mensagens) b.append(m).append('\n'); } tvMsgs.setText(b); }
    private LinearLayout col() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView txt(String s, int sp) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setPadding(0, 8, 0, 8); return t; }
    private LinearLayout.LayoutParams peso() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }
    private EditText campo(String rotulo, String valor, boolean numero) {
        corpo.addView(txt(rotulo, 14)); EditText e = new EditText(this); e.setText(valor);
        if (numero) e.setInputType(InputType.TYPE_CLASS_NUMBER); corpo.addView(e); return e;
    }
    private RadioGroup radios(String[] op, int sel) {
        RadioGroup g = new RadioGroup(this); g.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < op.length; i++) { RadioButton r = new RadioButton(this); r.setId(View.generateViewId()); r.setText(op[i]); g.addView(r); if (i == sel) r.setChecked(true); }
        return g;
    }
}
