package com.opensocket.aievent.core.integration.issue.webhook;
import java.io.*; import jakarta.servlet.*; import jakarta.servlet.http.*;
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
 private final byte[] body; CachedBodyHttpServletRequest(HttpServletRequest request,byte[] body){super(request);this.body=body.clone();}
 byte[] body(){return body.clone();}
 @Override public ServletInputStream getInputStream(){ByteArrayInputStream in=new ByteArrayInputStream(body);return new ServletInputStream(){public boolean isFinished(){return in.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){}public int read(){return in.read();}public int read(byte[] b,int o,int l){return in.read(b,o,l);}};}
 @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
}
