package com.ghostsq.commander.toolbuttons;

import java.util.ArrayList;

import com.ghostsq.commander.R;

import android.content.Context;
import android.content.SharedPreferences;

public class ToolButtons extends ArrayList<ToolButton> 
{
    private static final long serialVersionUID = 1L;
    private static final String pref_key = "tool_buttons"; 
    public final String TAG = getClass().getName();
    
    public final void restore( SharedPreferences shared_pref, Context context ) {
        String bcns = shared_pref.getString( pref_key, null );
        if( bcns != null && bcns.length() > 0 ) {
            // add new introduced buttons also here like below:
            //if( bcns.indexOf( "enter" ) >= 0 ) bcns += ",enter";
            String[] bcna = bcns.split( "," );
            for( String bcn : bcna ) {
                int bi = ToolButton.getId( bcn );
                if( bi == 0 ) continue;
                ToolButton tb = new ToolButton( bi );
                tb.restore( shared_pref, context );
                add( tb );
            }
        }
        else{
            int[] bia = { 
                 R.id.F1,      
                 R.id.F2,      
                 R.id.F4,      
                 R.id.SF4,     
                 R.id.F5,      
                 R.id.F6,      
                 R.id.F7,      
                 R.id.F8,      
                 R.id.F9,      
                 R.id.F10,     
                 R.id.sz,      
                 R.id.eq,      
                 R.id.tgl,     
                 R.id.enter,   
                 R.id.refresh,
                 R.id.by_name, 
                 R.id.by_ext,  
                 R.id.by_size, 
                 R.id.by_date, 
                 R.id.hidden,
                 R.id.sel_all, 
                 R.id.uns_all, 
                 R.id.add_fav,  
                 R.id.remount,
                 R.id.home,    
                 R.id.favs,    
                 R.id.sdcard,  
                 R.id.root,   
                 R.id.mount,   
                 R.id.softkbd
            };
            for( int bi : bia ) {
                ToolButton tb = new ToolButton( bi );
                tb.restore( shared_pref, context );
                add( tb );
            }
        }
    }
    public final void store( SharedPreferences.Editor editor ) {
        StringBuffer bicsb = new StringBuffer();
        for( int i = 0; i < size(); i++ ) {
            ToolButton tb = get( i );
            if( i > 0 ) bicsb.append( "," );
            bicsb.append( tb.getCodeName() );
            tb.store( editor );
        }
        editor.putString( pref_key, bicsb.toString() );
    }
}
