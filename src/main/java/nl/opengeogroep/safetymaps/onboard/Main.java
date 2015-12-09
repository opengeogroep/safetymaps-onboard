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
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;

/**
 * Onboard-application for embedded servers connected to a tablet/phone or for
 * on (Java-capable) tablets themselves to provide services to the fullscreen
 * safetymapsDBK viewer. See README.md for details.
 *
 * @author Matthijs Laan
 */
public class Main {
    public static String var;

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption("h", "help", false, "Toon deze help");
        options.addOption("p", "port", true, "Poort om webservice op te starten (standaard 1080)");
        options.addOption("w", "workdir", true, "Werkdirectory (standaard var)");

        LocationSearch.addOptions(options);
        RequestStoreAndForward.addOptions(options);
        return options;
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("safetymaps-onboard", options );
    }

    public static void main(String[] args) throws Exception {

        Options options = buildOptions();
        CommandLine cl = null;
        try {
            CommandLineParser parser = new DefaultParser();

            cl = parser.parse(options, args);
        } catch(ParseException e) {
            System.out.printf("%s\n\n", e.getMessage());

            printHelp(options);
            System.exit(1);
        }

        if(cl.hasOption("h")) {
            printHelp(options);
            System.exit(0);
        }

        var = cl.getOptionValue("var", "var");

        // Set property used in log4j.properties
        System.setProperty("arg.var", var);
        Logger log = Logger.getLogger(Main.class);

        log.info(String.format("%s %s starting (git commit %s, built at %s)",
                Version.getProperty("project.name"),
                Version.getProjectVersion(),
                Version.getProperty("git.commit.id"),
                Version.getProperty("git.build.time")
        ));
        File f = new File(var);

        if(!f.exists()) {
            f.mkdir();
        } else if(!f.isDirectory()) {
            throw new Exception("Cannot create dir: " + f);
        }

        HttpServer server = new HttpServer(var, Integer.parseInt(cl.getOptionValue("port", "1080")), cl);

        WatchService watcher = FileSystems.getDefault().newWatchService();
        Paths.get(".").register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

        System.out.println("Watching for update");
        while(true) {
            WatchKey key = watcher.take();
            for(WatchEvent event: key.pollEvents()) {
                Path p = (Path)event.context();
                if("update.flag".equals(p.toString())) {
                    System.exit(99);
                }
            }
            key.reset();
        }
    }

}
