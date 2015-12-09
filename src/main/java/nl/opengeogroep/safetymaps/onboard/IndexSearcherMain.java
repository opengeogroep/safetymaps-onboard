package nl.opengeogroep.safetymaps.onboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author Matthijs Laan
 */
public class IndexSearcherMain {
    public static void main(String[] args) throws IOException, ParseException {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get("index"))); // new File("index")));
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = new StandardAnalyzer();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        QueryParser parser = new QueryParser("display_name", analyzer);

        while(true) {
            String line = in.readLine();
            if(line == null || line.length() == 0) {
                break;
            }
            String[] words = line.split("\\s+");
            StringBuilder q = new StringBuilder();
            for(String w: words) {
                q/*.append(w);
                if(!w.contains("~")) {
                    q.append("~");
                }
                q.append(" OR ")*/.append(w).append("*^2 ");
            }
            Query query = parser.parse(q.toString());
            System.out.println("Searching for: " + query.toString("display_name"));

            TopDocs docs = searcher.search(query, 10);
            System.out.println("Hits: " + docs.scoreDocs.length);

            for(int i = 0; i < docs.scoreDocs.length; i++) {
                Document doc = searcher.doc(docs.scoreDocs[i].doc);
                System.out.println(doc.get("display_name") + "; " + doc.getField("lon").numericValue() + "," + doc.getField("lat").numericValue() + ", score="+ docs.scoreDocs[i].score);
            }
        }
        reader.close();
    }
}
