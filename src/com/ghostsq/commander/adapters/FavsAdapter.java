package com.ghostsq.commander.adapters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.favorites.FavDialog;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

public class FavsAdapter extends CommanderAdapterBase {
    private final static String TAG = "FavsAdapter";
    private final static int SCUT_CMD = 26945;
    private ArrayList<Favorite> favs;
    
    public FavsAdapter( Context ctx_ ) {
        super( ctx_, DETAILED_MODE | NARROW_MODE | SHOW_ATTR | ATTR_ONLY );
        numItems = 0;
        favs = null;
        numItems = 1;
    }

    public void setFavorites( ArrayList<Favorite> favs_ ) {
        favs = favs_;
        numItems = favs.size() + 1; 
    }
    
    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & ( MODE_WIDTH | MODE_DETAILS | MODE_ATTR ) ) == 0 )
            super.setMode( mask, val );
        return mode;
    }    
    
    @Override
    public int getType() {
        return CA.FAVS;
    }
    
    @Override
    public String toString() {
        return "favs:";
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return Uri.parse( toString() );
    }
    @Override
    public void setUri( Uri uri ) {
    }

    @Override
    public boolean readSource( Uri tmp_uri, String pbod ) {
        notify( pbod );
        return true;
    }

    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        if( num <= 1 ) {
            menu.add( 0, Commander.OPEN, 0, s( R.string.go_button ) );
            menu.add( 0, R.id.F2,        0, s( R.string.rename_title ) );
            menu.add( 0, R.id.F4,        0, s( R.string.edit_title ) );
            menu.add( 0, R.id.F8,        0, s( R.string.delete_title ) );
            menu.add( 0, SCUT_CMD,       0, s( R.string.shortcut ) );
        }
    }    
    @Override
    public void reqItemsSize( SparseBooleanArray cis ) {
        notErr();
    }
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        return notErr();
    }
        
    @Override
    public boolean createFile( String fileURI ) {
        return notErr();
    }

    @Override
    public void createFolder( String new_name ) {
        notErr();
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        for( int i = 0; i < cis.size(); i++ )
            if( cis.valueAt( i ) ) {
                int k = cis.keyAt( i );
                if( k > 0 ) {
                    favs.remove( k - 1 );
                    numItems--;
                    notifyDataSetChanged();
                    notify( Commander.OPERATION_COMPLETED );
                    return true;
                }
            }
        return false;
    }
    
    @Override
    public void openItem( int position ) {
        if( position == 0 ) {
            commander.Navigate( Uri.parse( "home:" ), null );
            return;
        }
        if( favs == null || position < 0 || position > numItems )
            return;
        commander.Navigate( favs.get( position - 1 ).getUriWithAuth(), null );
    }

    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
        return notErr();
    }

    @Override
    public boolean renameItem( int p, String newName, boolean c  ) {
        if( favs != null && p > 0 && p <= favs.size() ) {
            favs.get( p-1 ).setComment( newName );
            notifyDataSetChanged();
            return true;
        }
        return false;
    }

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        if( SCUT_CMD == command_id ) {
            int k = 0, n = favs.size();
            for( int i = 0; i < cis.size(); i++ ) {
                k = cis.keyAt( i );
                if( cis.valueAt( i ) && k > 0 && k <= n )
                    break;
            }
            if( k > 0 )
                createDesktopShortcut( favs.get( k - 1 ) );
        }
    }
    
    public void editItem( int p ) {
        if( favs != null && p > 0 && p <= favs.size() ) {
            new FavDialog( ctx, favs.get( p-1 ), this );
        }
    }    

    public void invalidate() {
        notifyDataSetChanged();
        notify( Commander.OPERATION_COMPLETED );
    }    

    private final void createDesktopShortcut( Favorite f ) {
        if( f == null ) return;
        Uri uri = f.getUriWithAuth();
        Intent shortcutIntent = new Intent();
        shortcutIntent.setClassName( ctx, commander.getClass().getName() );
        shortcutIntent.setAction( Intent.ACTION_VIEW );
        shortcutIntent.setData( uri );

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        String name = f.getComment();
        if( name == null || name.length() == 0 )
            name = f.getUriString( true );
        intent.putExtra( Intent.EXTRA_SHORTCUT_NAME, name );
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext( ctx, getDrawableIconId( uri ) );
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        intent.setAction( "com.android.launcher.action.INSTALL_SHORTCUT" );
        ctx.sendBroadcast( intent );
    }

    private final int getDrawableIconId( Uri uri ) {
        if( uri != null ) {
            String sch = uri.getScheme();
            if( sch != null && sch.length() != 0 ) {
                int t_id = CA.GetAdapterTypeId( sch );
                if( CA.ZIP  == t_id ) return R.drawable.zip;     else   
                if( CA.FTP  == t_id ) return R.drawable.server;  else   
                if( CA.ROOT == t_id ) return R.drawable.root;    else  
                if( CA.MNT  == t_id ) return R.drawable.mount;   else  
                if( CA.SMB  == t_id ) return R.drawable.smb;     else
                if( CA.HOME == t_id ) return R.drawable.icon;    else
                    return R.drawable.folder;
            }
        }
        return R.drawable.folder;
    }
    
    @Override
    public String getItemName( int p, boolean full ) {
        if( favs != null && p > 0 && p <= favs.size() ) {
            Favorite f = favs.get( p - 1 );
            String comm = f.getComment();
            return comm != null && comm.length() > 0 ? comm : full ? f.getUriString( true ) : "";
        }
        return null;
    }
    

    /*
     * BaseAdapter implementation
     */

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        if( position == 0 ) {
            item = new Item();
            item.name = parentLink;
            item.dir = true;
        }
        else {
            if( favs != null && position > 0 && position <= favs.size() ) {
                Favorite f = favs.get( position - 1 );
                if( f != null ) {
                    item.dir = false;
                    item.name = f.getUriString( true );
                    item.size = -1;
                    item.sel = false;
                    item.date = null;
                    item.attr = f.getComment();
                    item.icon_id = getDrawableIconId( f.getUri() );
                }
            }
        }
        return item;
    }

    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
        Item item = (Item)getItem( position );
        if( item == null ) return null;
        return getView( convertView, parent, item );
    }

    @Override
    protected void reSort() {
        if( favs == null ) return;
        FavoriteComparator comp = new FavoriteComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Collections.sort( favs, comp );
    }
    
    public class FavoriteComparator implements Comparator<Favorite> {
        int     type;
        boolean case_ignore, ascending;

        public FavoriteComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            type = type_;
            case_ignore = case_ignore_;
            ascending = ascending_;
        }
        public int compare( Favorite f1, Favorite f2 ) {
            if( f1 == null || f2 == null ) {
                Log.w( TAG, "a Fav is null!" );
                return 0;
            }
            int ext_cmp = 0;
            switch( type ) { 
            case SORT_EXT: {
                    String c1 = f1.getComment();
                    if( c1 == null ) c1 = "";
                    String c2 = f2.getComment();
                    if( c2 == null ) c2 = "";
                    ext_cmp = c1.compareTo( c2 );
                }
                break;
            case SORT_SIZE: {
                    ext_cmp = getWeight( f1.getUri() ) - getWeight( f2.getUri() ) > 0 ? 1 : -1;
                }
                break;
            case SORT_DATE:
                break;
            }
            if( ext_cmp == 0 ) {
                Uri u1 = f1.getUri();
                Uri u2 = f2.getUri();
                if( u1 != null )
                    ext_cmp = u1.compareTo( u2 );
            }
            return ascending ? ext_cmp : -ext_cmp;
        }
        private int getWeight( Uri u ) {
            int w = 0;
            if( u != null ) {
                w++;
                String s = u.getScheme();
                if( s != null ) {
                    w++;
                    if( "ftp".equals( s ) ) w++; else
                    if( "smb".equals( s ) ) w+=2;
                }
            }
            return w;
        }
    }
}
