package br.com.calunaty.acbremissor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Monta o INI (formato ACBrLibNFe) de uma NF-e (55) ou NFC-e (65) de TESTE em HOMOLOGACAO.
 * Classe pura (sem Android). Emitente, destinatario e responsavel tecnico vem da tela de configuracao.
 * Itens: 5 produtos de exemplo, Simples Nacional (CSOSN 102), CFOP 5102, desconto de 10%, pagamento misto.
 */
public final class NotaTeste {
    public static final String NOME_HOMOLOGACAO = "NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";
    public static final String ITEM1_HOMOLOGACAO = "NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";

    public enum Pagamento { DINHEIRO_CARTAO, DINHEIRO_PIX }

    /** Dados de uma pessoa/empresa da nota. Campos vazios sao omitidos quando opcionais. */
    public static final class Pessoa {
        public String doc = "", nome = "", fantasia = "", ie = "", crt = "1";
        public String logradouro = "", numero = "", bairro = "", codMunicipio = "", municipio = "", uf = "", cep = "", fone = "";
    }

    /** Responsavel tecnico (obrigatorio em algumas UFs). Se o CNPJ ficar vazio, o grupo nao e enviado. */
    public static final class RespTec { public String cnpj = "", contato = "", email = "", fone = ""; }

    public static final class Montada {
        public final String ini; public final BigDecimal vNF; public final BigDecimal vDesc; public final BigDecimal vProd;
        Montada(String ini, BigDecimal vNF, BigDecimal vDesc, BigDecimal vProd) { this.ini = ini; this.vNF = vNF; this.vDesc = vDesc; this.vProd = vProd; }
    }

    private static final Map<String, Integer> CUF = new HashMap<>();
    static {
        String[] t = {"RO11","AC12","AM13","RR14","PA15","AP16","TO17","MA21","PI22","CE23","RN24","PB25","PE26","AL27","SE28","BA29",
                      "MG31","ES32","RJ33","SP35","PR41","SC42","RS43","MS50","MT51","GO52","DF53"};
        for (String s : t) CUF.put(s.substring(0, 2), Integer.parseInt(s.substring(2)));
    }
    public static int codigoUF(String uf) { Integer c = CUF.get(uf == null ? "" : uf.trim().toUpperCase(Locale.ROOT)); if (c == null) throw new IllegalArgumentException("UF invalida: " + uf); return c; }

    private static final String[][] PRODUTOS = {
        // cProd, descricao, NCM, unidade, quantidade, valor unitario
        {"T001", "CAFE TORRADO E MOIDO 500G", "09012100", "UN", "2", "18.90"},
        {"T002", "REFRIGERANTE LATA 350ML",   "22021000", "UN", "6", "4.99"},
        {"T003", "BISCOITO RECHEADO 140G",    "19053100", "UN", "3", "3.20"},
        {"T004", "SABONETE EM BARRA 90G",     "34011190", "UN", "4", "2.75"},
        {"T005", "CADERNO ESPIRAL 96 FOLHAS", "48202000", "UN", "1", "12.00"},
    };

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static BigDecimal r2(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    private static String v(BigDecimal x) { return r2(x).toPlainString(); }
    private static String so(String s) { return s == null ? "" : s.replaceAll("\\D", ""); }
    private static String up(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.ROOT); }

    private static void endereco(StringBuilder s, Pessoa p) {
        s.append("xLgr=").append(up(p.logradouro)).append("\nnro=").append(up(p.numero)).append("\nxBairro=").append(up(p.bairro))
         .append("\ncMun=").append(so(p.codMunicipio)).append("\nxMun=").append(up(p.municipio)).append("\nUF=").append(up(p.uf))
         .append("\nCEP=").append(so(p.cep)).append("\ncPais=1058\nxPais=BRASIL\n");
        if (!so(p.fone).isEmpty()) s.append("Fone=").append(so(p.fone)).append("\n");
    }

    public static Montada montar(int modelo, int serie, int numero, Pagamento pag, Date agora, Pessoa emit, Pessoa dest, RespTec rt) {
        if (modelo != 55 && modelo != 65) throw new IllegalArgumentException("modelo deve ser 55 ou 65");
        boolean nfce = modelo == 65;
        StringBuilder s = new StringBuilder();
        String cNF = String.format(Locale.US, "%08d", new Random().nextInt(99999999));
        String dh = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(agora);
        boolean mesmaUF = up(emit.uf).equals(up(dest.uf));

        s.append("[infNFe]\nVersao=4.00\n");
        s.append("[Identificacao]\n")
         .append("cUF=").append(codigoUF(emit.uf)).append("\ncNF=").append(cNF).append("\nnatOp=VENDA DE MERCADORIA\n")
         .append("Modelo=").append(modelo).append("\nSerie=").append(serie).append("\nnNF=").append(numero).append("\n")
         .append("dhEmi=").append(dh).append("\n")
         .append(nfce ? "" : "dhSaiEnt=" + dh + "\n")
         .append("tpNF=1\nidDest=").append(mesmaUF ? 1 : 2).append("\ncMunFG=").append(so(emit.codMunicipio)).append("\n")
         .append("tpImp=").append(nfce ? 4 : 1).append("\n")
         .append("tpEmis=1\ntpAmb=2\nfinNFe=1\nindFinal=1\nindPres=1\nprocEmi=0\nverProc=ACBrLib Android Ex\n");

        s.append("[Emitente]\nCNPJCPF=").append(so(emit.doc)).append("\nxNome=").append(up(emit.nome)).append("\n");
        if (!emit.fantasia.trim().isEmpty()) s.append("xFant=").append(up(emit.fantasia)).append("\n");
        s.append("IE=").append(so(emit.ie)).append("\nCRT=").append(so(emit.crt).isEmpty() ? "1" : so(emit.crt)).append("\n");
        endereco(s, emit);

        // Em homologacao a SEFAZ exige este texto no nome do destinatario.
        s.append("[Destinatario]\nCNPJCPF=").append(so(dest.doc)).append("\nxNome=").append(NOME_HOMOLOGACAO).append("\nindIEDest=9\n");
        endereco(s, dest);

        BigDecimal totProd = BigDecimal.ZERO, totDesc = BigDecimal.ZERO, totPis = BigDecimal.ZERO, totCof = BigDecimal.ZERO;
        String cfop = mesmaUF ? "5102" : "6102";
        for (int i = 0; i < PRODUTOS.length; i++) {
            String[] p = PRODUTOS[i]; String n = String.format(Locale.US, "%03d", i + 1);
            BigDecimal q = d(p[4]), vu = d(p[5]), vProd = r2(q.multiply(vu)), vDesc = r2(vProd.multiply(d("0.10")));
            BigDecimal base = vProd.subtract(vDesc), vPis = r2(base.multiply(d("0.0065"))), vCof = r2(base.multiply(d("0.03")));
            String xProd = (!nfce && i == 0) ? ITEM1_HOMOLOGACAO : p[1];
            s.append("[Produto").append(n).append("]\n")
             .append("cProd=").append(p[0]).append("\ncEAN=SEM GTIN\nxProd=").append(xProd).append("\nNCM=").append(p[2])
             .append("\nCFOP=").append(cfop).append("\nuCom=").append(p[3]).append("\nqCom=").append(p[4]).append("\nvUnCom=").append(p[5])
             .append("\nvProd=").append(v(vProd)).append("\ncEANTrib=SEM GTIN\nuTrib=").append(p[3]).append("\nqTrib=").append(p[4])
             .append("\nvUnTrib=").append(p[5]).append("\nvDesc=").append(v(vDesc)).append("\nindTot=1\n");
            s.append("[ICMS").append(n).append("]\norig=0\nCSOSN=102\n");
            s.append("[PIS").append(n).append("]\nCST=01\nvBC=").append(v(base)).append("\npPIS=0.65\nvPIS=").append(v(vPis)).append("\n");
            s.append("[COFINS").append(n).append("]\nCST=01\nvBC=").append(v(base)).append("\npCOFINS=3.00\nvCOFINS=").append(v(vCof)).append("\n");
            totProd = totProd.add(vProd); totDesc = totDesc.add(vDesc); totPis = totPis.add(vPis); totCof = totCof.add(vCof);
        }
        BigDecimal vNF = totProd.subtract(totDesc);
        s.append("[Total]\nvBC=0\nvICMS=0\nvProd=").append(v(totProd)).append("\nvDesc=").append(v(totDesc))
         .append("\nvPIS=").append(v(totPis)).append("\nvCOFINS=").append(v(totCof)).append("\nvNF=").append(v(vNF)).append("\n");
        s.append("[Transportador]\nmodFrete=9\n");

        BigDecimal dinheiro = r2(vNF.multiply(d("0.40"))), resto = vNF.subtract(dinheiro);
        s.append("[pag001]\ntPag=01\nvPag=").append(v(dinheiro)).append("\n");
        s.append("[pag002]\ntPag=").append(pag == Pagamento.DINHEIRO_PIX ? "17" : "03").append("\nvPag=").append(v(resto)).append("\ntpIntegra=2\n");

        s.append("[DadosAdicionais]\ninfCpl=DOCUMENTO DE TESTE EMITIDO EM HOMOLOGACAO COM A ACBRLIB ANDROID. SEM VALOR FISCAL.\n");
        if (rt != null && !so(rt.cnpj).isEmpty())
            s.append("[infRespTec]\nCNPJ=").append(so(rt.cnpj)).append("\nxContato=").append(up(rt.contato))
             .append("\nemail=").append(rt.email.trim()).append("\nfone=").append(so(rt.fone)).append("\n");
        return new Montada(s.toString(), r2(vNF), r2(totDesc), r2(totProd));
    }
}
