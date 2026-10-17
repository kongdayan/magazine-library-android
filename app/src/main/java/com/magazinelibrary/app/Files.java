package com.magazinelibrary.app;

import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import java.io.*;

final class Files {
    static File part(Context c,long id){File d=new File(c.getFilesDir(),"transfers");d.mkdirs();return new File(d,id+".part");}
    static File cover(Context c,long id){File d=new File(c.getCacheDir(),"covers");d.mkdirs();return new File(d,id+".jpg");}
    static int validate(File file) throws IOException {
        try(InputStream in=new FileInputStream(file)) { byte[] head=new byte[5];if(in.read(head)!=5 || !new String(head,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) throw new IOException("返回内容不是 PDF，请在浏览页完成验证后重试"); }
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd)) {if(pdf.getPageCount()==0) throw new IOException("PDF 没有可读取的页面");return pdf.getPageCount();}
        catch(RuntimeException e){throw new IOException("PDF 无法读取，可能损坏或有密码",e);}
    }
    static Uri publish(Context c,Store.Item i,File source) throws IOException {
        Uri uri=null;
        try {
            ContentResolver r=c.getContentResolver();
            if(!i.tree.isEmpty()) {
                Uri tree=Uri.parse(i.tree);
                Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
                uri=DocumentsContract.createDocument(r,parent,"application/pdf",Names.fileName(i.title));
            } else {
                ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,Names.fileName(i.title));v.put(MediaStore.MediaColumns.MIME_TYPE,"application/pdf");v.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Magazine Library");v.put(MediaStore.MediaColumns.IS_PENDING,1);
                uri=r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            }
            if(uri==null) throw new IOException("无法创建文件，请重新选择下载目录");
            try(InputStream in=new FileInputStream(source);OutputStream out=r.openOutputStream(uri,"w")) {
                if(out==null)throw new IOException("目录不可写");byte[] buf=new byte[64*1024];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
            }
            if(i.tree.isEmpty()){ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.IS_PENDING,0);r.update(uri,v,null,null);}
            return uri;
        } catch(Exception e) {
            if(uri!=null)try {if(i.tree.isEmpty())c.getContentResolver().delete(uri,null,null);else DocumentsContract.deleteDocument(c.getContentResolver(),uri);}catch(Exception ignored){}
            throw new IOException("保存失败：请检查目录授权与可用空间",e);
        }
    }
    static void deleteOutput(Context c,Uri uri)throws IOException {
        try {if(DocumentsContract.isDocumentUri(c,uri)){if(!DocumentsContract.deleteDocument(c.getContentResolver(),uri))throw new IOException("无法清理未完成的输出文件");}else c.getContentResolver().delete(uri,null,null);}
        catch(Exception e){throw new IOException("无法清理未完成的输出文件",e);}
    }
    static void remove(Context c,Store.Item item,boolean deleteFile)throws IOException {
        if(deleteFile&&!item.uri.isEmpty()) {
            Uri u=Uri.parse(item.uri);
            try {if(DocumentsContract.isDocumentUri(c,u)){if(!DocumentsContract.deleteDocument(c.getContentResolver(),u))throw new IOException("文件无法删除");}
                else if(c.getContentResolver().delete(u,null,null)==0)throw new IOException("文件不存在或无法删除");
            }catch(FileNotFoundException ignored){}catch(Exception e){throw new IOException("文件删除失败，请检查目录授权",e);}
        }
        Store.get(c).remove(item.id);part(c,item.id).delete();cover(c,item.id).delete();
    }
    static void rename(Context c,Store.Item item,String title)throws IOException {
        ContentValues values=new ContentValues();String clean=Names.title(title);
        // Imported PDFs retain their original file name; downloaded files belong to this app.
        if(!item.url.isEmpty()&&!item.uri.isEmpty())try {
            Uri u=Uri.parse(item.uri);
            if(DocumentsContract.isDocumentUri(c,u)){Uri renamed=DocumentsContract.renameDocument(c.getContentResolver(),u,Names.fileName(clean));if(renamed==null)throw new IOException("文件无法重命名");values.put("uri",renamed.toString());}
            else {ContentValues name=new ContentValues();name.put(MediaStore.MediaColumns.DISPLAY_NAME,Names.fileName(clean));if(c.getContentResolver().update(u,name,null,null)==0)throw new IOException("文件无法重命名");}
        }catch(Exception e){throw new IOException("文件重命名失败，请检查目录授权",e);}
        values.put("title",clean);values.put("series",Names.series(clean));Store.get(c).update(item.id,values);
    }
    static void makeCover(Context c,long id,File f) {
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd);PdfRenderer.Page p=pdf.openPage(0)) {
            int w=240,h=Math.max(1,w*p.getHeight()/p.getWidth());Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            try(OutputStream out=new FileOutputStream(cover(c,id))){b.compress(Bitmap.CompressFormat.JPEG,85,out);}b.recycle();
        }catch(Exception ignored){}
    }
}
