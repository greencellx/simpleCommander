package com.ghostsq.commander.root;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

class MountsListEngine extends ExecEngine {
    
    public class MountItem {
        private String  dev = "", mntp = "", type = "", opts = "", r1 = "", r2 = "";
        public MountItem( String string ) {
            String[] flds = string.split( " " );
            if( flds.length < 4 ) {
                dev = "???";
            }
            if( flds[1].equals( "on" ) && flds[3].equals( "type" ) ) {
                dev  = flds.length > 0 ? flds[0] : "";
                mntp = flds.length > 2 ? flds[2] : ""; 
                type = flds.length > 4 ? flds[4] : ""; 
                opts = flds.length > 5 ? flds[5] : "";
                
                if( opts.length() > 1 && opts.charAt( 0 ) == '(' && opts.charAt( opts.length()-1 ) == ')' )
                    opts = opts.substring( 1, opts.length()-1 );
            } else {
                dev  = flds.length > 0 ? flds[0] : "";
                mntp = flds.length > 1 ? flds[1] : ""; 
                type = flds.length > 2 ? flds[2] : ""; 
                opts = flds.length > 3 ? flds[3] : ""; 
                r1   = flds.length > 4 ? flds[4] : "";
                r2   = flds.length > 5 ? flds[5] : "";
            }
        }
        public boolean isValid() {
            return dev.length() > 0 && mntp.length() > 0;
        }
        public String getName() {
            return dev + " " + mntp;
        }
        public String getOptions() {
            return opts;
        }
        public String getRest() {
            return type + " " + opts + " " + r1 + " " + r2;
        }
        public String getMountPoint() {
            return mntp;
        }
    }
    
    
    private MountItem[] items_tmp;
    public  String pass_back_on_done;
    MountsListEngine( Context ctx, Handler h, String pass_back_on_done_ ) {
        super( ctx, h );
        pass_back_on_done = pass_back_on_done_;
    }
    public MountItem[] getItems() {
        return items_tmp;
    }       
    @Override
    public void run() {
        try {
            getList();
        }
        catch( Exception e ) {
            sh = "sh";
            // try again
            try {
                getList();
                sendProgress( context.getString( R.string.no_root ), 
                        Commander.OPERATION_COMPLETED, pass_back_on_done );
            }
            catch( Exception e1 ) {
                Log.e( TAG, "Exception even on 'sh' execution", e1 );
                sendProgress( context.getString( R.string.no_root ), 
                        Commander.OPERATION_FAILED, pass_back_on_done );
            }
        }
        finally {
            super.run();
        }
    }
    
    private void getList() throws Exception {
        Process p = Runtime.getRuntime().exec( sh );
        DataOutputStream os = new DataOutputStream( p.getOutputStream() );
        DataInputStream  is = new DataInputStream( p.getInputStream() );
        DataInputStream  es = new DataInputStream( p.getErrorStream() );
        os.writeBytes( "mount\n"); // execute command
        os.flush();
        for( int i=0; i< 10; i++ ) {
            if( isStopReq() ) 
                throw new Exception();
            if( is.available() > 0 ) break;
            Thread.sleep( 50 );
        }
        if( is.available() <= 0 ) // may be an error may be not
            Log.w( TAG, "No output from the executed command" );
        ArrayList<MountItem>  array = new ArrayList<MountItem>();
        while( is.available() > 0 ) {
            if( isStopReq() ) 
                throw new Exception();
            String ln = is.readLine();
            if( ln == null ) break;
            MountItem item = new MountItem( ln );
            if( item.isValid() )
                array.add( item );
        }
        os.writeBytes("exit\n");
        os.flush();
        p.waitFor();
        if( p.exitValue() == 255 )
            Log.e( TAG, "Process.exitValue() returned 255" );
        int sz = array.size();
        items_tmp = new MountItem[sz];
        if( sz > 0 )
            array.toArray( items_tmp );
        String res_s = null;
        if( es.available() > 0 )
            res_s = es.readLine();
        if( res_s != null && res_s.length() > 0 )
           Log.e( TAG, "Error on the 'ls' command: " + res_s );
        sendProgress( res_s, Commander.OPERATION_COMPLETED, pass_back_on_done );
    }        
    
}