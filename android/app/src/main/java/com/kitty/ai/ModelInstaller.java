package com.kitty.ai;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ModelInstaller {
    public static File model(Context c){return new File(c.getFilesDir(),"vosk-model");}
    public static boolean installed(Context c){return new File(model(c),"am/final.mdl").isFile()&&new File(model(c),"conf/model.conf").isFile();}
    public static synchronized void install(Context c,Uri uri) throws Exception {
        File temp=new File(c.getFilesDir(),"model-import");delete(temp);if(!temp.mkdirs())throw new Exception("Cannot create model directory");
        try(InputStream source=c.getContentResolver().openInputStream(uri);ZipInputStream zip=new ZipInputStream(source)){
            ZipEntry entry;long total=0;int count=0;byte[] data=new byte[16384];
            while((entry=zip.getNextEntry())!=null){
                if(++count>5000)throw new Exception("Too many files in model ZIP");
                File dest=new File(temp,entry.getName());
                if(!dest.getCanonicalPath().startsWith(temp.getCanonicalPath()+File.separator))throw new Exception("Invalid model ZIP path");
                if(entry.isDirectory()){dest.mkdirs();continue;}
                dest.getParentFile().mkdirs();
                try(FileOutputStream out=new FileOutputStream(dest)){
                    int n;while((n=zip.read(data))!=-1){total+=n;if(total>350_000_000)throw new Exception("Use a small Vosk model under 350 MB extracted");out.write(data,0,n);}
                }
            }
            File found=find(temp,0);
            if(found==null)throw new Exception("No Vosk model found. Import the original small English Vosk ZIP.");
            try(org.vosk.Model validated=new org.vosk.Model(found.getAbsolutePath())){ /* Verify before replacing the working model. */ }
            File previous=new File(c.getFilesDir(),"vosk-model-previous");delete(previous);
            File target=model(c);
            if(target.exists()&&!target.renameTo(previous))throw new Exception("Cannot move previous model");
            if(!found.renameTo(target)){previous.renameTo(target);throw new Exception("Cannot install model");}
            delete(previous);
        } finally {delete(temp);}
    }
    private static File find(File dir,int depth){
        if(new File(dir,"am/final.mdl").isFile()&&new File(dir,"conf/model.conf").isFile())return dir;
        if(depth>=2)return null;
        File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.isDirectory()){File found=find(f,depth+1);if(found!=null)return found;}
        return null;
    }
    private static void delete(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)delete(child);}if(f.exists())f.delete();}
}
