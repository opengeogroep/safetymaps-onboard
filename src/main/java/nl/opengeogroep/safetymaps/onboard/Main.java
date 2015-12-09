package nl.opengeogroep.safetymaps.onboard;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import net.lingala.zip4j.core.ZipFile;
import org.apache.commons.io.FileUtils;

/**
 * Onboard-application for embedded servers connected to a tablet/phone or for
 * on (Windows)tablets themselves to provide services to the fullscreen
 * safetymapsDBK viewer.
 * 
 * @author Matthijs Laan
 */
public class Main {

    public static void main(String[] args) throws Exception {

        if(args.length == 0) {
            System.out.println("Argument missing: index zipfile");
            System.exit(1);
        }

        System.out.println("locationsearch starting");
        File f = new File("var");

        if(!f.exists()) {
            f.mkdir();
        } else if(!f.isDirectory()) {
            throw new Exception("Cannot create dir: " + f);
        }
        cleanVarDir(f);
        unpackIndex(f, args[0]);

        HttpSearchServer server = new HttpSearchServer(1080, f + File.separator + "index");

        WatchService watcher = FileSystems.getDefault().newWatchService();
        Paths.get(".").register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

        System.out.println("Watching for update");
        while(true) {
            WatchKey key = watcher.take();
            for(WatchEvent event: key.pollEvents()) {
                Path p = (Path)event.context();
                if("locationupdate.flag".equals(p.toString())) {
                    System.exit(99);
                }
            }
            key.reset();
        }
    }

    private static void cleanVarDir(File f) {
        for(String s: f.list()) {
            System.out.println("Cleaning: " + s);
            FileUtils.deleteQuietly(new File(f + File.separator + s));
        }
    }

    private static void unpackIndex(File f, String zip) throws Exception {
        System.out.println("Extracting index");
        new ZipFile(zip).extractAll(f.getAbsolutePath());
    }
}
