package nl.opengeogroep.safetymaps.onboard;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Paths;
import net.lingala.zip4j.core.ZipFile;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
public class LocationSearch {
    private static final Logger log = Logger.getLogger(LocationSearch.class);

    private Exception initException;

    private IndexReader reader;
    private IndexSearcher searcher;

    public LocationSearch(String var, CommandLine cl) {
        File indexDir = new File(var + File.separator + "index");
        String db = cl.getOptionValue("searchdb", "db/index.zip");

        try {
            cleanIndexDir(indexDir);
            unpackIndex(new File(var), db);
            log.info("locationsearch: opening Lucene index");
            reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir.toURI())));
            searcher = new IndexSearcher(reader);
        } catch(Exception e) {
            log.error("locationsearch: error opening Lucene index, search disabled!", e);
            this.initException = e;
        }
    }

    private static void cleanIndexDir(File f) {
        if(!f.exists()) {
            f.mkdirs();
        }
        for(String s: f.list()) {
            log.trace("locationsearch: removing " + s);
            FileUtils.deleteQuietly(new File(f + File.separator + s));
        }
    }

    private static void unpackIndex(File f, String zip) throws Exception {
        log.info("locationsearch: extracting index");
        new ZipFile(zip).extractAll(f.getAbsolutePath());
    }

    public static void addOptions(Options options) {
        options.addOption("searchdb", true, "Zoekdatabase ZIP bestand (default db/index.zip)");
    }

    public void shutdown() {
        try {
            reader.close();
        } catch (IOException ex) {
        }
    }

    public Response serve(IHTTPSession session) {
        if(!session.getUri().startsWith("/q/")) {
            return null;
        }

        if(initException != null) {
            StringWriter sw = new StringWriter();
            initException.printStackTrace(new PrintWriter(sw));
            return HttpUtil.addCors(new Response(Response.Status.INTERNAL_ERROR, "text/plain", "Fout in locatiezoeker: " + sw.toString()));
        }

        String p = session.getUri().substring(3);

        if(p == null || p.trim().length() == 0) {
            return HttpUtil.addCors(new Response(Response.Status.OK, "application/json; charset=utf-8", "[]"));
        }
        log.trace("locationsearch: " + p);

        try {
            String[] words = p.split("\\s+");
            StringBuilder q = new StringBuilder();
            for(String w: words) {
                q/*.append(w);
                if(!w.contains("~")) {
                    q.append("~");
                }
                q.append(" OR ")*/.append(w).append("*^2 ");
            }
            Analyzer analyzer = new StandardAnalyzer();
            QueryParser parser = new QueryParser("display_name", analyzer);
            Query query = parser.parse(q.toString());
            log.trace("locationsearch: Lucene query: " + query.toString("display_name"));
            TopDocs docs = searcher.search(query, 10);
            //System.out.println("Hits: " + docs.scoreDocs.length);
            JSONArray results = new JSONArray();

            for(int i = 0; i < docs.scoreDocs.length; i++) {
                Document doc = searcher.doc(docs.scoreDocs[i].doc);

                JSONObject result = new JSONObject();
                for(IndexableField f: doc.getFields()) {
                    Number n = f.numericValue();
                    if(n != null) {
                        result.put(f.name(), n.doubleValue());
                    } else {
                        result.put(f.name(), f.stringValue());
                    }
                }
                results.put(result);

                //String s = doc.get("display_name") + "; " + doc.getField("lon").numericValue() + "," + doc.getField("lat").numericValue() + ", score="+ docs.scoreDocs[i].score;
                //System.out.println(s);
            }
            log.info("locationsearch: " + p + ", hits: " + docs.scoreDocs.length);

            return HttpUtil.addCors(new Response(Response.Status.OK, "application/json; charset=utf-8", results.toString(4)));
        } catch(Exception e) {
            log.error("locationsearch: error searching for " + p, e);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return new Response(Response.Status.INTERNAL_ERROR, "text/plain", "Fout bij zoeken: " + sw.toString());
        }
    }
}
