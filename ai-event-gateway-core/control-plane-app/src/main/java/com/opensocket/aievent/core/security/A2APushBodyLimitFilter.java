package com.opensocket.aievent.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** PC-S4 hard request-body cap for the public remote A2A PUSH callback boundary. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class A2APushBodyLimitFilter extends OncePerRequestFilter {
    private final int maxBodyBytes;
    public A2APushBodyLimitFilter(@Value("${a2a.push.ingress.max-body-bytes:1048576}") int maxBodyBytes){this.maxBodyBytes=Math.max(1024,maxBodyBytes);}

    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !"POST".equalsIgnoreCase(request.getMethod())||!request.getRequestURI().startsWith("/internal/a2a/push/");}

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        long declared=request.getContentLengthLong();
        if(declared>maxBodyBytes){reject(response);return;}
        byte[] body=readBounded(request,maxBodyBytes);
        if(body==null){reject(response);return;}
        chain.doFilter(new CachedBodyRequest(request,body),response);
    }

    private static byte[] readBounded(HttpServletRequest request,int max) throws IOException {
        var in=request.getInputStream();var out=new java.io.ByteArrayOutputStream(Math.min(max,8192));byte[] buf=new byte[8192];int total=0;
        for(int n;(n=in.read(buf))>=0;){total+=n;if(total>max)return null;out.write(buf,0,n);}return out.toByteArray();
    }
    private static void reject(HttpServletResponse response)throws IOException{response.setStatus(413);response.setContentType("application/json");response.getWriter().write("{\"code\":\"A2A_PUSH_BODY_TOO_LARGE\"}");}

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body; CachedBodyRequest(HttpServletRequest request,byte[] body){super(request);this.body=body;}
        @Override public int getContentLength(){return body.length;}
        @Override public long getContentLengthLong(){return body.length;}
        @Override public ServletInputStream getInputStream(){ByteArrayInputStream input=new ByteArrayInputStream(body);return new ServletInputStream(){public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){}public int read(){return input.read();}public int read(byte[] b,int off,int len){return input.read(b,off,len);}};}
        @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    }
}
