package nl.opengeogroep.safetymaps.onboard;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.io.IOException;
import org.apache.commons.cli.CommandLine;
import org.apache.log4j.Logger;

/**
 *
 * @author Matthijs Laan
 */
public class HttpServer extends NanoHTTPD {
    private static final Logger log = Logger.getLogger(HttpServer.class);

    private final RequestStoreAndForward forwarder;
    private final LocationSearch search;

    public HttpServer(String var, int port, CommandLine cl) throws IOException {
        super(port);

        forwarder = new RequestStoreAndForward(var, cl);
        forwarder.start();

        search = new LocationSearch(var, cl);

        start();
        log.info("HTTP server started at port " + port);
    }

    public void shutdown() {
        search.shutdown();
        forwarder.shutdown();
    }

    @Override
    public Response serve(IHTTPSession session) {

        Response r = forwarder.serve(session);
        if(r != null) {
            return r;
        }

        r = search.serve(session);
        if(r != null) {
            return r;
        }

        return HttpUtil.addCors(new Response(Status.NOT_FOUND, "text/plain", "Not Found: " + session.getUri()));
    }
}