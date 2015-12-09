package nl.opengeogroep.safetymaps.onboard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * psql bag -c "select openbareruimtenaam || ' ' || COALESCE(CAST(huisnummer as varchar) || ' ','') || COALESCE(CAST(huisletter as varchar) || ' ','') || COALESCE(CAST(huisnummertoevoeging as varchar) || ' ','') || COALESCE(CAST(postcode as varchar) || ' ','') || CASE WHEN lower(woonplaatsnaam) = lower(gemeentenaam) THEN woonplaatsnaam ELSE woonplaatsnaam || ', ' || gemeentenaam END as display_name,
 * st_x(st_transform(geopunt,4326)) as x, st_y(st_transform(geopunt,4326)) as y
 * from bag_actueel.adres where st_contains(st_transform(st_setsrid(st_geomfromtext('MULTIPOLYGON(((4.09204132444052 51.9143105516418,4.08411660696852 52.1873742191799,4.59149658653479 52.1918851784121,4.59633617480214 51.9187859633451,4.09204132444052 51.9143105516418)))'),4326),28992),geopunt)" -Aztq > bag.txt
 *
 * @author Matthijs Laan
 */
public class IndexBuilder {

    public static void main(String[] args) throws IOException, ZipException {

        InputStream in = args.length > 0 ? new FileInputStream(args[0]) : System.in;
        BufferedReader r = new BufferedReader(new InputStreamReader(in));

        FileUtils.deleteQuietly(new File("index"));
        Directory dir = FSDirectory.open(Paths.get("index")); // new File("index"));
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(OpenMode.CREATE);
        IndexWriter writer = new IndexWriter(dir, iwc);

        TextField displayName = new TextField("display_name", "", Field.Store.YES);
        DoubleField x = new DoubleField("lon", 0.0, Field.Store.YES);
        DoubleField y = new DoubleField("lat", 0.0, Field.Store.YES);
        Document doc = new Document();
        doc.add(displayName);
        doc.add(x);
        doc.add(y);

        String s;
        int i = 0;
        while((s = r.readLine()) != null) {
            String[] vals = s.split("\0");

            displayName.setStringValue(vals[0]);
            x.setDoubleValue(Double.parseDouble(vals[1]));
            y.setDoubleValue(Double.parseDouble(vals[2]));

            writer.addDocument(doc);
            i++;

            if(i % 50000 == 0) {
                System.out.println("Indexed " + i + " rows");
            }
        }
        writer.close();
        System.out.println("Complete, indexed " + i + " rows");

        File db = new File("db");
        if(!db.exists()) {
            db.mkdir();
        } else if(!db.isDirectory()) {
            System.out.println("File db in the way, not zipping index");
            return;
        } else {
            for(String f: db.list()) {
                if(f.startsWith("index.z")) {
                    new File(f).delete();
                }
            }
        }
        ZipFile zf = new ZipFile("db" + File.separator + "index.zip");
        ZipParameters params = new ZipParameters();
        params.setCompressionLevel(9);
        zf.createZipFileFromFolder("index", params, true, 10 * 1024 * 1024);
        System.out.println("Created zipfile");
    }
}
