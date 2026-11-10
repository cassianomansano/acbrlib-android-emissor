package br.com.calunaty.acbremissor;

import br.com.acbr.lib.nfe.ACBrLibNFe;
import java.io.File;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Laco de emissao em HOMOLOGACAO (validado contra a SEFAZ em homologacao):
 *  CarregarINI -> Assinar -> Validar -> Enviar; duplicidade (204/539) -> numero+1 em 1 s; rede -> repete em 1 s. */
final class Emissor {
    interface Saida { void msg(String s); }

    static final class Resultado {
        final boolean autorizada; final int numero; final String chave, protocolo, motivo; final File xml, pdf; final String vNF;
        Resultado(boolean a, int n, String c, String p, String m, File x, File pdf, String v) { autorizada = a; numero = n; chave = c; protocolo = p; motivo = m; xml = x; this.pdf = pdf; vNF = v; }
    }

    private static final Pattern CSTAT = Pattern.compile("(?i)cStat=(\\d+)");
    private static final Pattern PROT = Pattern.compile("(?i)nProt=(\\d+)");
    private static final Pattern MOTIVO = Pattern.compile("(?i)xMotivo=([^\\r\\n]+)");
    private static final Pattern CHAVE = Pattern.compile("Id=\"NFe(\\d{44})\"");
    private static final Pattern PDF = Pattern.compile("(?i)Msg=([^\\r\\n]+\\.pdf)");

    volatile boolean cancelar;

    static Set<String> cstats(String s) { Set<String> r = new LinkedHashSet<>(); Matcher m = CSTAT.matcher(s); while (m.find()) r.add(m.group(1)); return r; }
    private static String achar(Pattern p, String s) { Matcher m = p.matcher(s); return m.find() ? m.group(1).trim() : ""; }

    Resultado emitir(ACBrLibNFe nfe, File docs, int modelo, int serie, int numero, NotaTeste.Pagamento pag,
                     NotaTeste.Pessoa emit, NotaTeste.Pessoa dest, NotaTeste.RespTec rt, Saida out) throws Exception {
        int n = numero;
        for (int tentativa = 1; ; tentativa++) {
            if (cancelar) { out.msg("Operação cancelada antes da tentativa " + tentativa + "."); return new Resultado(false, n, "", "", "cancelada", null, null, ""); }
            NotaTeste.Montada m = NotaTeste.montar(modelo, serie, n, pag, new Date(), emit, dest, rt);
            nfe.LimparLista();
            String resp;
            try {
                nfe.CarregarINI(m.ini);
                nfe.Assinar();
                nfe.Validar();
                out.msg("[" + tentativa + "] série " + serie + " nº " + n + ": enviando à SEFAZ-" + emit.uf.toUpperCase() + " (homologação)…");
                resp = nfe.Enviar(1, false, true, false);
            } catch (Exception e) {
                resp = String.valueOf(e.getMessage());
                try { resp += " " + nfe.ultimoRetorno(); } catch (Throwable ignored) {}
            }
            Set<String> cs = cstats(resp);
            String motivo = achar(MOTIVO, resp);
            if (cs.contains("100")) {
                String xmlTxt = nfe.ObterXml(0);
                String chave = achar(CHAVE, xmlTxt), prot = achar(PROT, resp);
                nfe.GravarXml(0, chave + "-nfe.xml", docs.getAbsolutePath());
                File xml = new File(docs, chave + "-nfe.xml");
                File pdf = null;
                try { nfe.ImprimirPDF(); String p = achar(PDF, nfe.ultimoRetorno()); if (!p.isEmpty()) pdf = new File(p); } catch (Exception e) { out.msg("PDF não gerado: " + e.getMessage()); }
                if (pdf == null || !pdf.isFile()) { File alt = new File(docs, chave + "-nfe.pdf"); pdf = alt.isFile() ? alt : null; }
                out.msg("✅ AUTORIZADA série " + serie + " nº " + n + " — protocolo " + prot);
                return new Resultado(true, n, chave, prot, motivo, xml, pdf, m.vNF.toPlainString());
            }
            if (cs.contains("204") || cs.contains("539") || resp.contains("Duplicidade")) {
                out.msg("[" + tentativa + "] nº " + n + ": duplicidade (" + String.join("/", cs) + ") → tentando nº " + (n + 1) + " em 1 s");
                n++; Thread.sleep(1000); continue;
            }
            boolean rede = cs.isEmpty() && (resp.contains("Timeout") || resp.contains("HTTP") || resp.contains("Socket") || resp.contains("host") || resp.contains("conex"));
            if (rede) { out.msg("[" + tentativa + "] nº " + n + ": falha de comunicação, repetindo em 1 s — " + corta(resp)); Thread.sleep(1000); continue; }
            out.msg("⛔ Parou no nº " + n + ": " + (motivo.isEmpty() ? corta(resp) : String.join("/", cs) + " " + motivo));
            return new Resultado(false, n, "", "", motivo.isEmpty() ? corta(resp) : motivo, null, null, m.vNF.toPlainString());
        }
    }

    static String corta(String s) { s = s.replace('\n', ' ').replace('\r', ' '); return s.length() > 300 ? s.substring(0, 300) + "…" : s; }
}
