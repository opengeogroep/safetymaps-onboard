package nl.opengeogroep.safetymaps.onboard;

import fi.iki.elonen.NanoHTTPD;

/**
 *
 * @author Matthijs Laan
 */
public class DelayedRequestForwarder implements Runnable {
    private String dir;
    private String forwardURL;

    private boolean stop = false;

    public DelayedRequestForwarder(String dir, String forwardURL) {
        this.dir = dir;
        this.forwardURL = forwardURL;
    }

    public NanoHTTPD.Response serve(NanoHTTPD.IHTTPSession session) {
        System.out.println("Received delayed request!");
        return new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK, "text/plain; charset=utf-8", "stored");
    }

    @Override
    public void run() {
        System.out.println("Starting forwarding thread");

        while(!stop) {
            forwardRequests();
            try {
                Thread.sleep(15000);
            } catch (InterruptedException ex) {
            }
        }
    }

    private void forwardRequests() {
        // Search for files in dir

        // Forward
    }
}
