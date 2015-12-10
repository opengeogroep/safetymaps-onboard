package nl.opengeogroep.safetymaps.onboard;

import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;

/**
 *
 * @author Matthijs Laan
 */
public class RequestStoreAndForward extends Thread {
    private static final Logger log = Logger.getLogger(RequestStoreAndForward.class);

    private final String dir;
    private final String forwardURL;

    private boolean stop = false;

    public RequestStoreAndForward(String var, CommandLine cl) {
        this.dir = var + File.separator + "store";
        new File(this.dir).mkdirs();
        this.forwardURL = cl.getOptionValue("forwardurl");

        if(this.forwardURL == null) {
            log.error("store and forward: Geen forwardurl command line optie, forwarden disabled!");
        }

        if(cl.hasOption("save")) {
            new File(this.dir + File.separator + "forwarded").mkdir();
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

        log.info("store and forward: received request for " + session.getMethod() + " " + session.getUri());

        if(!(session.getMethod() == NanoHTTPD.Method.GET || session.getMethod() == NanoHTTPD.Method.POST)) {
            return new NanoHTTPD.Response(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Method not allowed: " + session.getMethod());
        }
        try {
            StoredRequest stored = new StoredRequest(session);

            // Remove /forward/ from URI
            stored.setUriPath(stored.getUriPath().substring("/forward/".length()));

            saveRequest(stored);
        } catch(IOException e) {
            log.error("Error saving request", e);
            return new NanoHTTPD.Response(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "Error saving request: " + e.getClass() + ": " + e.getMessage());
        }

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

    private void saveRequest(StoredRequest request ) throws IOException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-dd-MM_HH:mm:ss.SSS");
        String filename = dir + File.separator + "request_" + df.format(new Date()) + ".dat";
        try(FileOutputStream fos = new FileOutputStream(filename); ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(request);
        }
    }

    private void forwardRequests() {
        // Search for files in dir

        // Forward
    }

    private void forwardRequest() {

    }
}
