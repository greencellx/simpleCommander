package com.ghostsq.commander.root;

import com.ghostsq.commander.utils.EditPermissions;
import com.ghostsq.commander.utils.Permissions;

public class EditRootPermissions extends EditPermissions {
   
    @Override
    protected void apply( Permissions np ) {
        String cmd = null;
        StringBuilder a = p.generateChownString( np );
        if( a != null && a.length() > 0 ) {
            a.append( " '" ).append( file_path ).append( "'\n" );
            cmd = "chown " + a.toString();
        }
        a = p.generateChmodString( np );
        if( a != null && a.length() > 0 ) {
            a.append( " '" ).append( file_path ).append( "'" );
            if( cmd == null ) cmd = "";
            cmd += "busybox chmod " + a.toString();
        }
        if( cmd != null ) {
            ExecEngine ee = new ExecEngine( this, null, cmd, false, 500 );
            ee.setHandler( new EditPermissions.DoneHandler() );
            ee.start();
        }
    }
}

