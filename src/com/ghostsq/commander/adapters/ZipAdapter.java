package com.ghostsq.commander.adapters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.http.util.EncodingUtils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.Panels;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.utils.Utils;

public class ZipAdapter extends CommanderAdapterBase {
    public final static String TAG = "ZipAdapter";
    private static final char   SLC = File.separatorChar;
    private static final String SLS = File.separator;
    protected static final int BLOCK_SIZE = 100000;
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public  Uri          uri = null;
    public  ZipFile      zip = null;
    public  ZipEntry[] items = null;

    public ZipAdapter( Context ctx_ ) {
        super( ctx_ );
        parentLink = PLS;
    }
    @Override
    public int getType() {
        return CA.ZIP;
    }
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            if( tmp_uri != null )
                uri = tmp_uri;
            if( uri == null )
                return false;
            if( reader != null ) { // that's not good.
                if( reader.isAlive() ) {
                    commander.showInfo( ctx.getString( R.string.busy ) );
                    reader.interrupt();
                    Thread.sleep( 500 );      
                    if( reader.isAlive() ) 
                        return false;      
                }
            }
            Log.v( TAG, "reading " + uri );
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            reader = new ListEngine( readerHandler, pass_back_on_done );
            reader.start();
            return true;
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
            e.printStackTrace();
        }
        commander.notifyMe( new Commander.Notify( "Fail", Commander.OPERATION_FAILED ) );
        return false;
    }
    class EnumEngine extends Engine {

        protected EnumEngine( Handler h ) {
            super( h );
        }
        protected final ZipEntry[] GetFolderList( String fld_path ) {
            if( zip == null ) return null;
            if( fld_path == null ) fld_path = ""; 
            else
                if( fld_path.length() > 0 && fld_path.charAt( 0 ) == SLC ) 
                    fld_path = fld_path.substring( 1 );                                 
            int fld_path_len = fld_path.length();
            if( fld_path_len > 0 && fld_path.charAt( fld_path_len - 1 ) != SLC ) { 
                fld_path = fld_path + SLC;
                fld_path_len++;
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            if( entries == null )
                return null;
            ArrayList<ZipEntry> array = new ArrayList<ZipEntry>();
            while( entries.hasMoreElements() ) {
                if( isStopReq() ) return null;
                
                ZipEntry e = entries.nextElement();
                if( e != null ) {
                    String entry_name = fixName( e );
                    //Log.v( TAG, "Found an Entry: " + entry_name );
                    if( entry_name == null || fld_path.compareToIgnoreCase(entry_name) == 0 ) 
                        continue;
                    /* There are at least two kinds of zips - with dedicated folder entry and without one.
                     * The code below should process both.
                     * Do not change until you fully understand how it works.
                     */
                    if( fld_path.regionMatches( true, 0, entry_name, 0, fld_path_len ) ) {
                        int sl_pos = entry_name.indexOf( SLC, fld_path_len );
                        if( sl_pos > 0 ) {
                            String sub_dir = entry_name.substring( fld_path_len, sl_pos );
                            int    sub_dir_len = sub_dir.length();
                            boolean not_yet = true;
                            for( int i = 0; i < array.size(); i++ ) {
                                String a_name = fixName( array.get( i ) );
                                if( a_name.regionMatches( fld_path_len, sub_dir, 0, sub_dir_len ) ) {
                                    not_yet = false;
                                    break;
                                }
                            }
                            if( not_yet ) {  // a folder
                                ZipEntry sur_fld = new ZipEntry( entry_name.substring( 0, sl_pos+1 ) );
                                byte[] eb = { 1, 2 };
                                sur_fld.setExtra( eb );
                                array.add( sur_fld );
                            }
                        }
                        else
                            array.add( e ); // a leaf
                    }
                }
            }
            return array.toArray( new ZipEntry[array.size()] );
        }
    }    
    class ListEngine extends EnumEngine {
        private ZipEntry[] items_tmp = null;
        public  String pass_back_on_done;
        ListEngine( Handler h, String pass_back_on_done_ ) {
        	super( h );
        	pass_back_on_done = pass_back_on_done_;
        }
        public ZipEntry[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            try {
            	if( uri != null ) {
            	    String zip_path = uri.getPath(); 
                	if( zip_path != null ) {
                  	    zip = new ZipFile( zip_path );
                    	String cur_path = null;
                    	try {
                    	    cur_path = uri.getFragment();
                    	}
                    	catch( NullPointerException e ) {
                    	    // it happens only when the Uri is built by Uri.Builder
                    	    Log.e( TAG, "uri.getFragment()", e );
                    	}
                	    items_tmp = GetFolderList( cur_path );
                	    if( items_tmp != null ) { 
                            ZipItemPropComparator comp = new ZipItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
                            Arrays.sort( items_tmp, comp );
                            sendProgress( null, Commander.OPERATION_COMPLETED, pass_back_on_done );
                            return;
                	    }
                	}
                }
            }
            catch( Exception e ) {
                Log.e( TAG, "ListEngine", e );
            }
            finally {
            	super.run();
            }
            sendProgress( "Can't open this ZIP file", Commander.OPERATION_FAILED, pass_back_on_done );
        }
    }
    @Override
    protected void onReadComplete() {
        if( reader instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)reader;
            ZipEntry[] tmp_items = list_engine.getItems();
            if( tmp_items != null && ( mode & MODE_HIDDEN ) == HIDE_MODE ) {
                int cnt = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getName().charAt( 0 ) != '.' )
                        cnt++;
                items = new ZipEntry[cnt];
                int j = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getName().charAt( 0 ) != '.' )
                        items[j++] = tmp_items[i]; 
            }
            else
                items = tmp_items;
            numItems = items != null ? items.length + 1 : 1; 
            notifyDataSetChanged();
        }
    }
    
    @Override
    public String toString() {
        return uri != null ? Uri.decode( uri.toString() ) : "";
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return uri;
    }
    @Override
    public void setUri( Uri uri_ ) {
        uri = uri_;
    }
    
    @Override
    public void setIdentities( String name, String pass ) {
    }
	@Override
	public void reqItemsSize( SparseBooleanArray cis ) {
		commander.notifyMe( new Commander.Notify( "Not supported.", Commander.OPERATION_FAILED ) );
	}
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        try {
            if( zip == null )
                throw new RuntimeException( "Invalid ZIP" );
            ZipEntry[] subItems = bitsToItems( cis );
            if( subItems == null ) 
                throw new RuntimeException( "Nothing to extract" );
            if( !checkReadyness() ) return false;
            File dest = null;
            int rec_h = 0;
            if( to instanceof FSAdapter  ) {
                dest = new File( to.toString() );
                if( !dest.exists() ) dest.mkdirs();
                if( !dest.isDirectory() )
                    throw new RuntimeException( ctx.getString( R.string.dest_exist ) );
            } else {
                dest = new File( createTempDir() );
                rec_h = setRecipient( to ); 
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new CopyFromEngine( workerHandler, subItems, dest, rec_h );
            worker.start();
            return true;
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e.getMessage() );
        }
        return false;
    }

    class CopyFromEngine extends EnumEngine 
    {
	    private File       dest_folder;
	    private ZipEntry[] mList = null;
	    private String     base_pfx;
	    private int        base_len; 
        private int        recipient_hash;
	    CopyFromEngine( Handler h, ZipEntry[] list, File dest, int rec_h ) {
	    	super( h );
	    	mList = list;
	        dest_folder = dest;
	        recipient_hash = rec_h;
            try {
                base_pfx = uri.getFragment();
                if( base_pfx == null )
                    base_pfx = "";
                base_len = base_pfx.length(); 
            }
            catch( NullPointerException e ) {
                Log.e( TAG, "", e );
            }
	    }
	    @Override
	    public void run() {
	    	int total = copyFiles( mList, "" );
            if( recipient_hash != 0 ) {
                sendReceiveReq( recipient_hash, dest_folder );
                return;
            }
			sendResult( Utils.getOpReport( ctx, total, R.string.unpacked ) );
	        super.run();
	    }
	    private final int copyFiles( ZipEntry[] list, String path ) {
	        int counter = 0;
	        try {
	            long dir_size = 0, byte_count = 0;
	            for( int i = 0; i < list.length; i++ ) {
                    ZipEntry f = list[i];	            
                    if( !f.isDirectory() )
                        dir_size += f.getSize();
	            }
	            double conv = 100./(double)dir_size;
	        	for( int i = 0; i < list.length; i++ ) {
	        		ZipEntry entry = list[i];
	        		if( entry == null ) continue;
	        		String entry_name_fixed = fixName( entry );
	        		if( entry_name_fixed == null ) continue;
        		    String file_name = new File( entry_name_fixed ).getName();
        		    File   dest_file = new File( dest_folder, path + file_name );
        			String rel_name = entry_name_fixed.substring( base_len );
        			
        			if( entry.isDirectory() ) {
        				if( !dest_file.mkdir() ) {
        					if( !dest_file.exists() || !dest_file.isDirectory() ) {
	        					errMsg = "Can't create folder \"" + dest_file.getAbsolutePath() + "\"";
	        					break;
        					}
        				}
        				ZipEntry[] subItems = GetFolderList( entry_name_fixed );
	                    if( subItems == null ) {
	                    	errMsg = "Failed to get the file list of the subfolder '" + rel_name + "'.\n";
	                    	break;
	                    }
        				counter += copyFiles( subItems, rel_name );
        				if( errMsg != null ) break;
        			}
        			else {
                        if( dest_file.exists()  ) {
                            int res = askOnFileExist( ctx.getString( R.string.file_exist, dest_file.getAbsolutePath() ), commander );
                            if( res == Commander.ABORT ) break;
                            if( res == Commander.SKIP )  continue;
                            if( res == Commander.REPLACE ) {
                                if( !dest_file.delete() ) {
                                    error( ctx.getString( R.string.cant_del, dest_file.getAbsoluteFile() ) );
                                    break;
                                }
                            }
                        }
        				InputStream in = zip.getInputStream( entry );
        				FileOutputStream out = new FileOutputStream( dest_file );
        	            byte buf[] = new byte[BLOCK_SIZE];
        	            int  n = 0;
        	            int  so_far = (int)(byte_count * conv);
        	            
        	            String unp_msg = ctx.getString( R.string.unpacking, rel_name ); 
        	            while( true ) {
        	                n = in.read( buf );
        	                if( n < 0 ) break;
        	                out.write( buf, 0, n );
        	                byte_count += n;
        	                sendProgress( unp_msg, so_far, (int)(byte_count * conv) );
                            if( stop || isInterrupted() ) {
                                in.close();
                                out.close();
                                dest_file.delete();
                                errMsg = "File '" + dest_file.getName() + "' was not completed, delete.";
                                break;
                            }
        	            }
        			}
                    if( stop || isInterrupted() ) {
                        error( ctx.getString( R.string.canceled ) );
                        break;
                    }
                    if( i >= list.length-1 )
                        sendProgress( ctx.getString( R.string.unpacked_p, rel_name ), (int)(byte_count * conv) );
        			counter++;
	        	}
	    	}
			catch( Exception e ) {
				Log.e( TAG, "copyFiles()", e );
				error( "Exception: " + e.getMessage() );
			}
	        return counter;
	    }
	}
	    
	@Override
	public boolean createFile( String fileURI ) {
		commander.notifyMe( new Commander.Notify( "Operation not supported", Commander.OPERATION_FAILED ) );
		return false;
	}
    @Override
    public void createFolder( String string ) {
        commander.notifyMe( new Commander.Notify( "Not supported", Commander.OPERATION_FAILED ) );
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
        	if( !checkReadyness() ) return false;
        	ZipEntry[] to_delete = bitsToItems( cis );
        	if( to_delete != null && zip != null && uri != null ) {
        	    commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                worker = new DelEngine( workerHandler, new File( uri.getPath() ), to_delete );
                worker.start();
	            return true;
        	}
        }
        catch( Exception e ) {
            Log.e( TAG, "deleteItems()", e );
        }
        commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_FAILED ) );
        return false;
    }

    class DelEngine extends Engine {
        private ZipEntry[] mList = null;
        private File       zipFile;
        DelEngine( Handler h, File zipFile_, ZipEntry[] list ) {
            super( h );
            zipFile = zipFile_;
            mList = list;
        }
        @Override
        public void run() {
            Init( null );
            File old_file = new File( zipFile.getAbsolutePath() + "_tmp_" + (new Date()).getSeconds() + ".zip" );
            try {
                ZipFile zf = new ZipFile( zipFile );
                int  removed = 0, processed = 0, num_entries = zf.size();
                long total_size = zipFile.length(), bytes_saved = 0;
                final String del = ctx.getString( R.string.deleting_a );
                
                if( !zipFile.renameTo(old_file) ) {
                    error("could not rename the file " + zipFile.getAbsolutePath() + " to " + old_file.getAbsolutePath() );
                }
                else {
                    ZipInputStream  zin = new ZipInputStream(  new FileInputStream( old_file ) );
                    ZipOutputStream out = new ZipOutputStream( new FileOutputStream( zipFile ) );
                    
                    byte[] buf = new byte[BLOCK_SIZE];
                    ZipEntry entry = zin.getNextEntry();
                    while( entry != null ) {
                        if( isStopReq() ) break;
                        String name = entry.getName();
                        boolean spare_this = true;
                        for( ZipEntry z : mList ) {
                            if( isStopReq() ) break;
                            String name_to_delete = z.getName();
                            if( name.startsWith( name_to_delete ) ) {
                                spare_this = false;
                                removed++;
                                break;
                            }
                        }
                        if( spare_this ) {
                            int pp = ++processed * 100 / num_entries;
                            // Add ZIP entry to output stream.
                            out.putNextEntry(new ZipEntry( name ));
                            // Transfer bytes from the ZIP file to the output file
                            int len;
                            while( (len = zin.read( buf )) > 0 ) {
                                if( isStopReq() ) break; 
                                out.write(buf, 0, len);
                                bytes_saved += len;
                                sendProgress( del, pp, (int)(bytes_saved * 100 / total_size) );
                            }
                        }
                        entry = zin.getNextEntry();
                    }
                    // Close the streams        
                    zin.close();
                    try {
                        out.close();
                    } catch( Exception e ) {
                        Log.e( TAG, "DelEngine.run()->out.close()", e );
                    }
                    if( isStopReq() ) {
                        zipFile.delete();
                        old_file.renameTo( zipFile );
                        processed = 0;
                        error( s( R.string.interrupted ) );
                    }
                    else {
                        old_file.delete();
                        zip = null;
                        sendResult( Utils.getOpReport( ctx, removed, R.string.deleted ) );
                        return;
                    }
                }
            } catch( Exception e ) {
                error( e.getMessage() );
            }
            sendResult( Utils.getOpReport( ctx, 0, R.string.deleted ) );
            super.run();
        }
    }
    
    @Override
    public String getItemName( int position, boolean full ) {
        if( items != null && position > 0 && position <= items.length ) {
            if( full ) {
                if( uri != null ) {
                    Uri item_uri = uri.buildUpon().encodedFragment( fixName( items[position-1] ) ).build();
                    if( item_uri != null )
                        return item_uri.toString();
                }
                return null;
            }
            return new File( fixName( items[position-1] ) ).getName();
        }
        return null;
    }
    @Override
    public void openItem( int position ) {
        if( position == 0 ) { // ..
            if( uri != null ) {
            	String cur = null; 
            	try {
                    cur = uri.getFragment();
                } catch( Exception e ) {
                }
            	if( cur == null || cur.length() == 0 ||
            	                 ( cur.length() == 1 && cur.charAt( 0 ) == SLC ) ) {
            	    File zip_file = new File( uri.getPath() );
            	    String parent_dir = zip_file.getParent();
            	    commander.Navigate( Uri.parse( parent_dir != null ? parent_dir : Panels.DEFAULT_LOC ), 
            	            zip_file.getName() );
            	}
            	else {
            	    File cur_f = new File( cur );
            	    String parent_dir = cur_f.getParent();
            	    commander.Navigate( uri.buildUpon().fragment( parent_dir != null ? parent_dir : "" ).build(), cur_f.getName() );
            	}
            }
            return;
        }
        if( items == null || position < 0 || position > items.length )
            return;
        ZipEntry item = items[position - 1];
        
        if( item.isDirectory() ) {
            String cur = null;    
            try {
                cur = uri.getFragment();
            }
            catch( NullPointerException e ) {}
        	if( cur == null ) 
        	    cur = "";
        	else
        	    if( cur.length() == 0 || cur.charAt( cur.length()-1 ) != SLC )
        	        cur += SLS;
            commander.Navigate( uri.buildUpon().fragment( fixName( item ) ).build(), null );
        }
    }

    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
    	try {
    		if( !checkReadyness() ) return false;
            if( uris == null || uris.length == 0 ) {
            	commander.notifyMe( new Commander.Notify( s( R.string.copy_err ), Commander.OPERATION_FAILED ) );
            	return false;
            }
            File[] list = Utils.getListOfFiles( uris );
            if( list == null ) {
            	commander.notifyMe( new Commander.Notify( "Something wrong with the files", Commander.OPERATION_FAILED ) );
            	return false;
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            
            zip = null;
            items = null;
            
            worker = new CopyToEngine( workerHandler, list, new File( uri.getPath() ), uri.getFragment(), move_mode );
            worker.start();
            return true;
		} catch( Exception e ) {
			commander.notifyMe( new Commander.Notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED ) );
		}
		return false;
    }

    public boolean createZip( File[] list, String zip_fn ) {
        try {
            if( !checkReadyness() ) return false;
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new CopyToEngine( workerHandler, list, new File( zip_fn ) );
            worker.start();
            return true;
        } catch( Exception e ) {
            commander.notifyMe( new Commander.Notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED ) );
        }
        return false;
    }
    
    class CopyToEngine extends Engine {
        private File[]  topList; 
        private int     basePathLen;
        private File    zipFile;
        private String  destPath;
        private long    totalSize = 0;
        private boolean newZip = false;
        private final String prep = "Preparing...";
        private boolean move = false;
        private boolean del_src_dir = false;
        
        /**
         *  Add files to existing zip 
         */
        CopyToEngine( Handler h, File[] list, File zip_file, String dest_sub, int move_mode_ ) {
            super( h );
            topList = list;
            zipFile = zip_file;
            if( dest_sub != null )
                destPath = dest_sub.endsWith( SLS ) ? dest_sub : dest_sub + SLS;
            else
                destPath = "";
            basePathLen = list.length > 0 ? list[0].getParent().length() + 1 : 0;
            move = ( move_mode_ & MODE_MOVE ) != 0;
            del_src_dir = ( move_mode_ & CommanderAdapter.MODE_DEL_SRC_DIR ) != 0;
        }
        /**
         *  Create a new shiny ZIP 
         */
        CopyToEngine( Handler h, File[] list, File zip_file ) {  
            super( h );
            topList = list;
            zipFile = zip_file;
            destPath = "";
            basePathLen = list.length > 0 ? list[0].getParent().length() + 1 : 0;
            newZip = true;
        }
        @Override
        public void run() {
            int num_files = 0;
            try {
                Init( null );
                sendProgress( prep, 1, 1 );
                ArrayList<File> full_list = new ArrayList<File>( topList.length );
                totalSize = addToList( topList, full_list );
                sendProgress( prep, 2, 2 );
                num_files = addFilesToZip( full_list );
                if( del_src_dir ) {
                    File src_dir = topList[0].getParentFile();
                    if( src_dir != null )
                        src_dir.delete();
                }
            } catch( Exception e ) {
                error( "Exception: " + e.getMessage() );
            }
    		sendResult( Utils.getOpReport( ctx, num_files, R.string.packed ) );
            super.run();
        }
        // adds files to the global full_list, and returns the total size 
        private final long addToList( File[] sub_list, ArrayList<File> full_list ) {
            long total_size = 0;
            try {
                for( int i = 0; i < sub_list.length; i++ ) {
                    if( stop || isInterrupted() ) {
                        errMsg = "Canceled";
                        break;
                    }
                    File f = sub_list[i];
                    if( f != null && f.exists() ) {
                        if( f.isFile() ) {
                            total_size += f.length();
                            full_list.add( f );
                        }
                        else
                        if( f.isDirectory() ) {
                            long dir_sz = addToList( f.listFiles(), full_list );
                            if( errMsg != null ) break;
                            if( dir_sz == 0 )
                                full_list.add( f );
                            else
                                total_size += dir_sz; 
                        }
                    }
                }
            }
            catch( Exception e ) {
                Log.e( TAG, "addToList()", e );
                errMsg = "Exception: " + e.getMessage();
            }
            return total_size;
        }
                
        // the following method was based on the one from http://snippets.dzone.com/posts/show/3468
        private final int addFilesToZip( ArrayList<File> files ) throws IOException {
            File old_file = null;
            try {
                byte[] buf = new byte[BLOCK_SIZE];
                ZipOutputStream out;
                if( newZip ) {
                   out = new ZipOutputStream( new FileOutputStream( zipFile ) );
                }
                else {
                   ZipFile zf = new ZipFile( zipFile );
                   int  num_entries = zf.size();
                   long total_size = zipFile.length(), bytes_saved = 0;

                   old_file = new File( zipFile.getAbsolutePath() + "_tmp_" + (new Date()).getSeconds() + ".zip" );
                   if( !zipFile.renameTo( old_file ) )
                       throw new RuntimeException("could not rename the file " + zipFile.getAbsolutePath() + " to " + old_file.getAbsolutePath() );
                   ZipInputStream  zin = new ZipInputStream(  new FileInputStream( old_file ) );
                   out = new ZipOutputStream( new FileOutputStream( zipFile ) );
                   
                   int e_i = 0, pp;
                   
                   ZipEntry entry = zin.getNextEntry();
                   while( entry != null ) {
                       if( isStopReq() ) break;
                       pp = e_i++ * 100 / num_entries;
                       sendProgress( prep, pp, 0 );
                       String name = entry.getName();  // in this case the name is not corrupted! no need to fix
                       boolean notInFiles = true;
                       for( File f : files ) {
                           if( isStopReq() ) break;
                           String f_path = f.getAbsolutePath();
                           if( f_path.regionMatches( true, basePathLen, name, 0, name.length() ) ) {
                               notInFiles = false;
                               break;
                           }
                       }
                       if( notInFiles ) {
                           // Add ZIP entry to output stream.
                           out.putNextEntry( new ZipEntry( name ) );
                           // Transfer bytes from old ZIP file to the output file
                           int len;
                           while( (len = zin.read( buf )) > 0 ) {
                               if( isStopReq() ) break;
                               out.write(buf, 0, len);
                               bytes_saved += len;
                               sendProgress( prep, pp, (int)(bytes_saved * 100 / total_size) );
                           }
                       }
                       entry = zin.getNextEntry();
                   }
                   // Close the streams        
                   zin.close();

                   if( isStopReq() ) {
                       out.close();
                       zipFile.delete();
                       old_file.renameTo( zipFile );
                       return 0;
                   }
                 }           
                 double conv = 100./(double)totalSize;
                 long   byte_count = 0;
                 // Compress the files
                 int i;
                 for( i = 0; i < files.size(); i++ ) {
                       if( isStopReq() ) break;
                       File f = files.get( i );
                       // Add ZIP entry to output stream.
                       String fn = f.getAbsolutePath();
                       String rfn = destPath + fn.substring( basePathLen );
                       if( f.isDirectory() ) {
                           out.putNextEntry( new ZipEntry( rfn + SLS ) );
                       }
                       else {
                           out.putNextEntry( new ZipEntry( rfn ) );
                           // Transfer bytes from the file to the ZIP file
                           String pack_s = ctx.getString( R.string.packing, fn );
                           InputStream in = new FileInputStream( f );
                           int len;
                           int  so_far = (int)(byte_count * conv);
                           while( (len = in.read( buf )) > 0 ) {
                               if( isStopReq() ) break;
                               out.write(buf, 0, len);
                               byte_count += len;
                               sendProgress( pack_s, so_far, (int)(byte_count * conv) );
                           }
                           // Complete the entry
                           in.close();
                       }
                       out.closeEntry();
                       //Log.v( TAG, "Packed: " + rfn );
                       if( move )
                           f.delete();
                 }
                 // Complete the ZIP file
                 out.close();
                 if( isStopReq() ) {
                       zipFile.delete();
                       if( !newZip ) 
                           old_file.renameTo( zipFile );
                       return 0;
                 }
                 if( !newZip ) 
                      old_file.delete();
                 return i;
            }
            catch( Exception e ) {
                error( e.getMessage() );
                e.printStackTrace();
                if( !newZip ) {
                    zipFile.delete();
                    if( !newZip && old_file != null ) 
                        old_file.renameTo( zipFile );
                }
                return 0;
            }
       }
    }
    @Override
    public boolean renameItem( int position, String newName, boolean c ) {
     // TODO
        return false;
    }

	@Override
	public void prepareToDestroy() {
	    super.prepareToDestroy();
		items = null;
	}

    /*
     * BaseAdapter implementation
     */

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        item.name = "";
        {
            if( position == 0 ) {
                item.name = parentLink;
            }
            else {
                if( items != null && position > 0 && position <= items.length ) {
                    ZipEntry zip_entry = items[position - 1];
                    item.dir = zip_entry.isDirectory();
                    String name = fixName( zip_entry );
                    
                    int lsp = name.lastIndexOf( SLC, item.dir ? name.length() - 2 : name.length() );
                    item.name = lsp > 0 ? name.substring( lsp + 1 ) : name;
                    item.size = zip_entry.getSize();
                    long item_time = zip_entry.getTime();
                    item.date = item_time > 0 ? new Date( item_time ) : null;
                }
            }
        }
        return item;
    }
    
    private final String fixName( ZipEntry entry ) {
        try {
            String entry_name = entry.getName();
            
            if( android.os.Build.VERSION.SDK_INT >= 10 )
                return entry_name; // already fixed?
            
            byte[] ex = entry.getExtra();
            if( ex != null && ex.length == 2 && ex[0] == 1 && ex[1] == 2 ) 
                return entry_name;
            byte bytes[];
/*            
            bytes = EncodingUtils.getAsciiBytes( entry_name );
            bytes = EncodingUtils.getBytes( entry_name, "windows-1250" );
*/            
            bytes = EncodingUtils.getBytes( entry_name, "iso-8859-1" );
            return new String( bytes );
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    private final ZipEntry[] bitsToItems( SparseBooleanArray cis ) {
    	try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            ZipEntry[] subItems = new ZipEntry[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                	subItems[j++] = items[ cis.keyAt( i ) - 1 ];
            return subItems;
		} catch( Exception e ) {
			Log.e( TAG, "", e );
		}
		return null;
    }
    private final boolean checkReadyness()   
    {
        if( worker != null ) {
        	commander.notifyMe( new Commander.Notify( ctx.getString( R.string.busy ), Commander.OPERATION_FAILED ) );
        	return false;
        }
    	return true;
    }
    public class ZipItemPropComparator implements Comparator<ZipEntry> {
        int type;
        boolean case_ignore, ascending;
        
        public ZipItemPropComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            type = type_;
            case_ignore = case_ignore_;
            ascending = ascending_;
        }
		@Override
		public int compare( ZipEntry f1, ZipEntry f2 ) {
            boolean f1IsDir = f1.isDirectory();
            boolean f2IsDir = f2.isDirectory();
            if( f1IsDir != f2IsDir )
                return f1IsDir ? -1 : 1;
            int ext_cmp = 0;
            switch( type ) {
            case SORT_EXT:
                ext_cmp = case_ignore ? 
                        Utils.getFileExt( f1.getName() ).compareToIgnoreCase( Utils.getFileExt( f2.getName() ) ) :
                        Utils.getFileExt( f1.getName() ).compareTo( Utils.getFileExt( f2.getName() ) );
                break;
            case SORT_SIZE:
                ext_cmp = f1.getSize() - f2.getSize() < 0 ? -1 : 1;
                break;
            case SORT_DATE:
                ext_cmp = f1.getTime() - f2.getTime() < 0 ? -1 : 1;
                break;
            }
            if( ext_cmp == 0 )
                ext_cmp = case_ignore ? f1.getName().compareToIgnoreCase( f2.getName() ) : f1.getName().compareTo( f2.getName() );
            return ascending ? ext_cmp : -ext_cmp;
		}
    }

    @Override
    protected void reSort() {
        if( items == null ) return;
        ZipItemPropComparator comp = new ZipItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items, comp );
    }
    @Override
    public CharSequence getFileContent( Uri u ) {
        ZipFile zf = null; 
        try {
            String zip_path = u.getPath();
            String entry_name = u.getFragment();
            if( zip_path != null && entry_name != null ) {
                zf = new ZipFile( zip_path );
                ZipEntry ze = zf.getEntry( entry_name );
                if( ze != null ) {
                    InputStream is = zf.getInputStream( ze );
                    if( is != null ) {
                        int num = is.available();
                        if( num > 0 ) {
                            InputStreamReader isr = new InputStreamReader( is );
                            char[] chars = new char[num];
                            int n = isr.read( chars );
                            isr.close();
                            is.close();
                            if( n >= 0 ) {
                                return CharBuffer.wrap( chars );
                            }
                        }
                        is.close();
                    }
                }
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        } finally {
            try {
                if( zf != null ) zf.close();
            } catch( IOException e ) {}
        }
        return null;
    }
}
