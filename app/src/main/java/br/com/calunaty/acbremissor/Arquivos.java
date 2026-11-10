package br.com.calunaty.acbremissor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Provedor minimo (sem androidx) que entrega PDF/XML das notas para o "Compartilhar" e para abrir o PDF.
 *  So serve arquivos da pasta docs do app; nao exportado, acesso apenas por permissao concedida na Intent. */
public class Arquivos extends ContentProvider {
    public static final String AUTORIDADE = "br.com.calunaty.acbremissor.arquivos";

    public static Uri uri(File f) { return Uri.parse("content://" + AUTORIDADE + "/" + Uri.encode(f.getName())); }

    private File arquivo(Uri u) throws FileNotFoundException {
        String nome = u.getLastPathSegment();
        if (nome == null || nome.contains("/") || nome.contains("..")) throw new FileNotFoundException("nome invalido");
        File f = new File(new File(getContext().getFilesDir(), "acbr/docs"), nome);
        if (!f.isFile()) throw new FileNotFoundException(nome);
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(arquivo(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        String n = uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment().toLowerCase();
        return n.endsWith(".pdf") ? "application/pdf" : n.endsWith(".xml") ? "text/xml" : "application/octet-stream";
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        try { File f = arquivo(uri); c.addRow(new Object[]{f.getName(), f.length()}); } catch (FileNotFoundException ignored) {}
        return c;
    }

    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
