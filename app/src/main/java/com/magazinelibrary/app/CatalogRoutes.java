package com.magazinelibrary.app;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** URLs for a single user-selected catalog request; never enumerates the site. */
final class CatalogRoutes {
    static boolean site(String url) {
        try {URI u=URI.create(url);return Names.https(url)&&("magazinelib.com".equalsIgnoreCase(u.getHost())||"www.magazinelib.com".equalsIgnoreCase(u.getHost()))&&(u.getPort()==-1||u.getPort()==443);}
        catch(Exception e){return false;}
    }
    static String query(String term,String category,String month) {
        if(!site(category))category=MainActivityUrl.HOME;
        String path=URI.create(category).getPath();String base=category.split("[?#]",2)[0];StringBuilder params=new StringBuilder();
        if(!term.trim().isEmpty())param(params,"s",term.trim());
        if(month.matches("\\d{4}/\\d{2}")){param(params,"year",month.substring(0,4));param(params,"monthnum",month.substring(5));}
        if(params.length()==0)return base;
        // WordPress category paths may be hierarchical; use the leaf slug in the query.
        if(!path.equals("/")&&!path.isEmpty()){String clean=path.replaceAll("/+$","");param(params,"category_name",clean.substring(clean.lastIndexOf('/')+1));}
        return MainActivityUrl.HOME+"?"+params;
    }
    private static void param(StringBuilder b,String key,String value){if(b.length()>0)b.append('&');try{b.append(key).append('=').append(URLEncoder.encode(value,"UTF-8"));}catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);}}
    static int number(String url){try{java.util.regex.Matcher m=java.util.regex.Pattern.compile("/page/(\\d+)(?:/|$)").matcher(URI.create(url).getPath());if(m.find())return Integer.parseInt(m.group(1));String q=URI.create(url).getRawQuery();if(q!=null)for(String p:q.split("&"))if(p.startsWith("paged="))return Integer.parseInt(p.substring(6));}catch(Exception ignored){}return 1;}
    static String page(String url,int number){if(!site(url)||number<1||number>100000)throw new IllegalArgumentException("Invalid page");URI u=URI.create(url);String path=u.getRawPath().replaceFirst("/page/\\d+/?$","/");if(!path.endsWith("/"))path+="/";if(number>1)path+="page/"+number+"/";String q=u.getRawQuery();if(q!=null)q=q.replaceAll("(?:^|&)paged=\\d+","").replaceFirst("^&","");return "https://"+u.getHost()+path+(q==null||q.isEmpty()?"":"?"+q);}
    // Keep this class usable by local JVM tests without loading an Android Activity.
    private static final class MainActivityUrl {static final String HOME="https://magazinelib.com/";}
}
