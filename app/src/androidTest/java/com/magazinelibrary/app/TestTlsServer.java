package com.magazinelibrary.app;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import java.io.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Loopback HTTPS fixture. Its test certificate and trust exist only in the test APK/process. */
final class TestTlsServer implements AutoCloseable {
    final byte[] pdf;final SSLServerSocket server;final SSLSocketFactory previous;
    volatile boolean cookieSeen,rangeSeen,cookieLeaked,secondCookieSeen,closed;final ExecutorService pool=Executors.newCachedThreadPool();
    TestTlsServer(Context testContext)throws Exception {
        KeyStore keys=KeyStore.getInstance("PKCS12");try(InputStream in=testContext.getAssets().open("fixture.p12")){keys.load(in,"test-only".toCharArray());}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"test-only".toCharArray());TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(keys);
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(km.getKeyManagers(),tm.getTrustManagers(),null);
        previous=HttpsURLConnection.getDefaultSSLSocketFactory();HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
        server=(SSLServerSocket)ssl.getServerSocketFactory().createServerSocket(0,8,java.net.InetAddress.getByName("127.0.0.1"));pdf=makePdf();
        pool.execute(()->{while(!closed)try{SSLSocket socket=(SSLSocket)server.accept();pool.execute(()->serve(socket));}catch(Exception e){if(!closed)throw new RuntimeException(e);}});
    }
    String url(String path){return "https://localhost:"+server.getLocalPort()+path;}
    private byte[] makePdf()throws Exception{PdfDocument d=new PdfDocument();try{for(int n=1;n<=3;n++){PdfDocument.Page p=d.startPage(new PdfDocument.PageInfo.Builder(600,840,n).create());Paint paint=new Paint();p.getCanvas().drawColor(Color.WHITE);paint.setColor(Ui.GREEN);paint.setTextSize(32);p.getCanvas().drawText("Transfer fixture / Page "+n,40,100,paint);for(int row=0;row<40;row++){paint.setTextSize(12);p.getCanvas().drawText("Verified PDF transfer and resume. Line "+row,40,140+row*14,paint);}d.finishPage(p);}ByteArrayOutputStream out=new ByteArrayOutputStream();d.writeTo(out);return out.toByteArray();}finally{d.close();}}
    private void serve(SSLSocket socket){try(socket){
        socket.setSoTimeout(10000);BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.US_ASCII));String request=input.readLine();if(request==null)return;String path=request.split(" ")[1];Map<String,String> headers=new HashMap<>();String line;while((line=input.readLine())!=null&&!line.isEmpty()){int at=line.indexOf(':');if(at>0)headers.put(line.substring(0,at).toLowerCase(Locale.ROOT),line.substring(at+1).trim());}
        OutputStream out=socket.getOutputStream();String cookie=headers.getOrDefault("cookie","");
        if(path.startsWith("/auth")){respond(out,403,"cf-mitigated: challenge\r\nContent-Type: text/html\r\n","<html>Verify your session</html>".getBytes());return;}
        if(path.startsWith("/html")){respond(out,200,"Content-Type: text/html\r\n","<html>This is not a PDF</html>".getBytes());return;}
        if(path.startsWith("/redirect")){respond(out,302,"Location: "+url("/pdf")+"\r\nSet-Cookie: second=ok; Path=/; Secure\r\n",new byte[0]);return;}
        if(path.startsWith("/cross")){respond(out,302,"Location: https://127.0.0.1:"+server.getLocalPort()+"/isolated\r\n",new byte[0]);return;}
        if(path.startsWith("/isolated")){cookieLeaked=cookie.contains("fixtureSession");respond(out,200,"Content-Type: application/pdf\r\nETag: \"fixture-v1\"\r\n",pdf);return;}
        cookieSeen|=cookie.contains("fixtureSession=authorized");secondCookieSeen|=cookie.contains("second=ok");
        if(!cookie.contains("fixtureSession=authorized")){respond(out,403,"Content-Type: text/html\r\n","<html>Cookie required</html>".getBytes());return;}
        int offset=0;if(headers.containsKey("range")){offset=Integer.parseInt(headers.get("range").replace("bytes=","").replace("-",""));rangeSeen=true;}
        String head="HTTP/1.1 "+(offset>0?"206 Partial Content":"200 OK")+"\r\nContent-Type: application/pdf\r\nETag: \"fixture-v1\"\r\nContent-Length: "+(pdf.length-offset)+"\r\nConnection: close\r\n";
        if(offset>0)head+="Content-Range: bytes "+offset+"-"+(pdf.length-1)+"/"+pdf.length+"\r\n";
        out.write((head+"\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.flush();
        for(int at=offset;at<pdf.length;at+=256){out.write(pdf,at,Math.min(256,pdf.length-at));out.flush();if(path.startsWith("/slow"))Thread.sleep(80);}
    }catch(Exception ignored){/* Closing a paused client is expected. */}}
    private void respond(OutputStream out,int code,String headers,byte[] body)throws IOException{out.write(("HTTP/1.1 "+code+" Response\r\n"+headers+"Content-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(body);out.flush();}
    public void close(){closed=true;try{server.close();}catch(Exception ignored){}pool.shutdownNow();HttpsURLConnection.setDefaultSSLSocketFactory(previous);}
}
