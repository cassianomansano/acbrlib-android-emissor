package br.com.calunaty.acbremissor;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/** Notas autorizadas (uma linha JSON por nota) em files/historico.jsonl. */
final class Historico {
    private final File arq;
    Historico(File dir) { arq = new File(dir, "historico.jsonl"); }

    synchronized void adicionar(JSONObject o) throws Exception {
        try (FileWriter w = new FileWriter(arq, true)) { w.write(o.toString()); w.write('\n'); }
    }

    synchronized List<JSONObject> listar() {
        List<JSONObject> r = new ArrayList<>();
        try { for (String l : Files.readAllLines(arq.toPath())) if (!l.trim().isEmpty()) r.add(new JSONObject(l)); } catch (Exception ignored) {}
        Collections.reverse(r);
        return r;
    }
}
