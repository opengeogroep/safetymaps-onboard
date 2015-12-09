package nl.opengeogroep.safetymaps.onboard;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Paths;
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
public class HttpSearchServer extends NanoHTTPD {

    private Exception initException;

    private IndexReader reader;
    private IndexSearcher searcher;

    private DelayedRequestForwarder forwarder;

    public HttpSearchServer(int port, String index) throws IOException {
        super(port);

        try {
            reader = DirectoryReader.open(FSDirectory.open(Paths.get(index))); // new File("index")));
            searcher = new IndexSearcher(reader);
        } catch(Exception e) {
            this.initException = e;
        }

        start();
        System.out.println("HTTP server started at port " + port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        if(initException != null) {
            StringWriter sw = new StringWriter();
            initException.printStackTrace(new PrintWriter(sw));
            return new Response("Fout in locatiezoeker: " + sw.toString());
        }

        if(!session.getUri().startsWith("/q/")) {
            return new Response(Status.NOT_FOUND, "text/plain", "Not Found: " + session.getUri());
        }

        String p = session.getUri().substring(3);

        if(p == null || p.trim().length() == 0) {
            return new Response(Status.OK, "application/json; charset=utf-8", "[]");
        }

        try {
            String[] words = p.split("\\s+");
            StringBuilder q = new StringBuilder();
            for(String w: words) {
                q.append(w);
                if(!w.contains("~")) {
                    q.append("~");
                }
                q.append(" OR ").append(w).append("*^2 ");
            }
            Analyzer analyzer = new StandardAnalyzer();
            QueryParser parser = new QueryParser("display_name", analyzer);
            Query query = parser.parse(q.toString());
            System.out.println("Searching for: " + query.toString("display_name"));

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
            Response r =  new Response(Status.OK, "application/json; charset=utf-8", results.toString(4));
            r.addHeader("Access-Control-Allow-Origin", "*");
            return r;
        } catch(Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return new Response(Status.INTERNAL_ERROR, "text/plain", "Fout bij zoeken: " + sw.toString());
        }
    }

    public static void main(String[] args) throws IOException {
        new HttpSearchServer(1080, "index");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while(true) {
            String line = in.readLine();
            if(line == null || line.length() == 0) {
                break;
            }
        }
    }
}