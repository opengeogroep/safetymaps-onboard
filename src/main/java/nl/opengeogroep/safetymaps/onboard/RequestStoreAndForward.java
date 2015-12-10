package nl.opengeogroep.safetymaps.onboard;

import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import static org.apache.http.HttpStatus.SC_OK;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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
            return HttpUtil.addCors(new NanoHTTPD.Response(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "No forwarding URL configured"));
        }

        log.info("store and forward: received request for " + session.getMethod() + " " + session.getUri());

        if(!(session.getMethod() == NanoHTTPD.Method.GET || session.getMethod() == NanoHTTPD.Method.POST)) {
            return HttpUtil.addCors(new NanoHTTPD.Response(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Method not allowed: " + session.getMethod()));
        }
        try {
            StoredRequest stored = new StoredRequest(session);

            // Remove /forward/ from URI
            stored.setUriPath(stored.getUriPath().substring("/forward/".length()));

            saveRequest(stored);
        } catch(IOException e) {
            log.error("Error saving request", e);
            return HttpUtil.addCors(new NanoHTTPD.Response(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "Error saving request: " + e.getClass() + ": " + e.getMessage()));
        }

        return HttpUtil.addCors(new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK, "text/plain; charset=utf-8", "stored"));
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
                Thread.sleep(60 * 1000);
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
        // Search for files in dir to forward
        for(File f: new File(dir).listFiles()) {
            if(!f.getName().endsWith(".dat")) {
                log.trace("Ignoring file in store dir: " + f.getAbsolutePath());
                continue;
            }

            try {
                forwardRequest(f);
            } catch(IOException e) {
                // Stop after first IOException

                // Note: this assumes IOException is due to connection problems
                // and all following StoredRequests would fail as well. Because
                // all requests are forwarded to the same forwardURL there is
                // no point in trying to forward the more stored requests.
                return;
            }
        }
    }

    private void forwardRequest(File f) throws IOException {
        StoredRequest request;
        try(FileInputStream fis = new FileInputStream(f); ObjectInputStream ois = new ObjectInputStream(fis)) {
            request = (StoredRequest)ois.readObject();
        } catch(Exception e) {
            log.error("Error reading stored request from " + f.getAbsolutePath(), e);
            return;
        }

        log.info(String.format("Forwarding request from file %s to %s %s%s",
                f.getName(),
                request.getMethod(),
                forwardURL,
                request.getUriPath()));

        try(CloseableHttpClient httpClient = getHttpClient()) {
            String uri = forwardURL + request.getUriPath();
            if(request.getQueryParameters() != null) {
                uri += "?" + request.getQueryParameters();
            }
            RequestBuilder builder = RequestBuilder.create(request.getMethod());
            builder.setUri(uri);
            builder.addHeader("X-Original-Date", HttpUtil.formatDate(request.getReceived()));
            builder.addHeader("X-Original-User-Agent", request.getHeaders().get("user-agent"));
            String contentType = request.getHeaders().get("content-type");
            if(contentType != null) {
                builder.addHeader("Content-Type", contentType);
            }
            if(request.getBody() != null && request.getBody().length > 0) {
                builder.setEntity(new ByteArrayEntity(request.getBody()));
            }
            try(CloseableHttpResponse response = httpClient.execute(builder.build())) {
                if(response.getStatusLine().getStatusCode() == SC_OK) {
                    log.info("Succesfully forwarded: " + response.getStatusLine());
                    f.delete();
                } else {
                    log.warn("Error forwarding request: " + response.getStatusLine());
                    if(log.isDebugEnabled()) {
                        for(Header h: response.getAllHeaders()) {
                            log.debug("Response header: " + h);
                        }
                        try {
                            HttpEntity entity = response.getEntity();
                            if(entity != null) {
                                String content = IOUtils.toString(entity.getContent(), "UTF-8");
                                log.debug("Response content: " + content);
                            }
                        } catch(Exception e) {
                        }
                    }
                }
            } catch(IOException e) {
                log.warn("IOException: " + e.getMessage());
            }
        }
    }

    private CloseableHttpClient getHttpClient() {
        return HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(15 * 1000)
                        .setSocketTimeout(30 * 1000)
                        .build()
                )
                .setUserAgent(Version.getProperty("project.name") + "/" + Version.getProperty("project.version"))
                .build();
    }
}
