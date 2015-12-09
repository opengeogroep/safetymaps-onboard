package nl.opengeogroep.safetymaps.onboard;

import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.io.IOException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

/**
 *
 * @author Matthijs Laan
 */
public class RequestStoreAndForward extends Thread {
    private static final Logger log = Logger.getLogger(RequestStoreAndForward.class);

    private String dir;
    private String forwardURL;

    private boolean stop = false;

    public RequestStoreAndForward(String var, CommandLine cl) {
        this.dir = var + File.separator + "store";
        this.forwardURL = cl.getOptionValue("forwardurl");

        if(this.forwardURL == null) {
            try {
                this.forwardURL = FileUtils.readFileToString(new File("forward.txt"), "US-ASCII");
            } catch(IOException e) {
                log.error("store and forward: Geen forwardurl command line optie en geen forward.txt, forwarden disabled!");
            }
        }
    }

    public static void addOptions(Options options) {
        options.addOption("forwardurl", true, "URL van safetymaps server voor forwarden requests (standaard inhoud van forward.txt)");
        options.addOption("save", true, "Bewaar geforwarde requests in forwarded subdirectory");
    }

    public NanoHTTPD.Response serve(NanoHTTPD.IHTTPSession session) {
        if(!session.getUri().startsWith("/forward/")) {
            return null;
        }

        if(this.forwardURL == null) {
            return new NanoHTTPD.Response(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "No forwarding URL configured");
        }

        log.info("store and forward: received request!");

        // TODO store

        return new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK, "text/plain; charset=utf-8", "stored");
    }

    @Override
    public void run() {
        if(this.forwardURL == null) {
            return;
        }

        log.info("store and forward: starting forwarding thread");

        while(!stop) {
            forwardRequests();
            try {
                Thread.sleep(15000);
            } catch (InterruptedException ex) {
            }
        }
    }

    public void shutdown() {
        log.info("store and forward: requesting shutdown");
        this.stop = true;
        interrupt();
        try {
            join();
            log.info("store and forward: thread ended");
        } catch (InterruptedException ex) {
        }
    }

    private void forwardRequests() {
        // Search for files in dir

        // Forward
    }
}
