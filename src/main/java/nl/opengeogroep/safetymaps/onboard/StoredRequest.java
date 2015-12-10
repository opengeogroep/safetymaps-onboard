package nl.opengeogroep.safetymaps.onboard;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Matthijs Laan
 */
public class StoredRequest implements Serializable {
    public static final long serialVersionUID = 1L;

    private String method;
    private String uriPath;
    private String queryParameters;

    private Date received;

    private Map<String, String> headers = new HashMap<>();

    private byte[] body;

    public StoredRequest(NanoHTTPD.IHTTPSession session) throws IOException {
        this.method = session.getMethod().toString();
        this.uriPath = session.getUri();
        this.queryParameters = session.getQueryParameterString();
        this.received = new Date();

        headers.putAll(session.getHeaders());

        if(session.getMethod() == NanoHTTPD.Method.POST) {
            // NanoHTTPD has no support for Transfer-encoding: chunked (issue 211)
            String s = session.getHeaders().get("content-length");
            if(s == null) {
                throw new IOException("Only POST requests with Content-Length headers supported");
            }
            int contentLength = Integer.parseInt(s);
            body = new byte[contentLength];
            IOUtils.readFully(session.getInputStream(), body);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUriPath() {
        return uriPath;
    }

    public void setUriPath(String uriPath) {
        this.uriPath = uriPath;
    }

    public String getQueryParameters() {
        return queryParameters;
    }

    public void setQueryParameters(String queryParameters) {
        this.queryParameters = queryParameters;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public Date getReceived() {
        return received;
    }

    public void setReceived(Date received) {
        this.received = received;
    }
    // </editor-fold>
}
