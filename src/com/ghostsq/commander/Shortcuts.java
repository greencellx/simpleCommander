package com.ghostsq.commander;

import java.lang.String;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.regex.Pattern;

import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;

public class Shortcuts extends BaseAdapter implements Filterable, OnKeyListener, OnClickListener, TextWatcher {
    private final static String TAG = "Shortcuts";
    private final static String old_sep = ",", sep = "`RS`";
	private FileCommander c;
	private Panels        p;
	private int  toChange = -1;
	private View goPanel;
	private ArrayList<Favorite> shortcutsList;
	
	public Shortcuts( FileCommander c_, Panels p_ ) {
		super();
		c = c_;
		p = p_;
		goPanel = c.findViewById( R.id.uri_edit_panel );
		shortcutsList = new ArrayList<Favorite>();
		
        try {
            AutoCompleteTextView textView = (AutoCompleteTextView)goPanel.findViewById( R.id.uri_edit );
            if( textView != null ) {
	            textView.setAdapter( this );
	            textView.setOnKeyListener( this );
	            textView.addTextChangedListener( this );
            }
            Button go = (Button)goPanel.findViewById( R.id.go_button );
            if( go != null )
            	go.setOnClickListener( this );
            View star = goPanel.findViewById( R.id.star );
            if( star != null )
            	star.setOnClickListener( this );
        } catch( Exception e ) {
			c.showMessage( "Exception on setup history dropdown: " + e );
		}
	}

	@Override
	 public Filter getFilter() {
	  Filter nameFilter = new Filter() {
		   @Override
		   public String convertResultToString( Object resultValue ) {
		      return resultValue.toString();
		   }
	
		   @Override
		   protected FilterResults performFiltering(CharSequence constraint) {
			    FilterResults filterResults = new FilterResults();
				if(constraint != null) {
				   filterResults.values = new Object();
				   filterResults.count = 1;
				}
			    return filterResults;
		   }
		   @Override
		   protected void publishResults( CharSequence constraint, FilterResults results ) {
		    if( results != null && results.count > 0 )
		    	notifyDataSetChanged();
		   }
	   };
	   return nameFilter;
	 }

	@Override
	public int getCount() {
		return shortcutsList.size();
	}

	@Override
	public Object getItem( int position ) {
		return shortcutsList.get( position ).getUriString();
	}

	@Override
	public long getItemId( int position ) {
		return position;
	}

	@Override
	public View getView( int position, View convertView, ViewGroup parent ) {
		TextView tv = convertView != null ? (TextView)convertView : new TextView( c );
		tv.setPadding( 4, 4, 4, 4 );
		String screened = Favorite.screenPwd( shortcutsList.get( position ).getUri() );
		tv.setText( screened == null ? "" : screened );
		tv.setTextColor( 0xFF000000 );
		return tv;
	}

	// --- inner functions ---
	
    public final void openGoPanel( int which, String uri ) {
		try {
			goPanel.setVisibility( View.VISIBLE );
			toChange = which;
			AutoCompleteTextView edit = (AutoCompleteTextView)c.findViewById( R.id.uri_edit );
			if( edit != null ) {
				edit.setText( uri );
				edit.showDropDown();
				edit.requestFocus();
			}
			CheckBox star = (CheckBox)c.findViewById( R.id.star );
            if( star != null )
            	star.setChecked( find( uri ) >= 0 );
		}
		catch( Exception e ) {
			c.showMessage( "Error: " + e );
		}
    }
    public final void closeGoPanel() {
		View go_panel = c.findViewById( R.id.uri_edit_panel );
		if( go_panel != null )
			go_panel.setVisibility( View.GONE );
    }
    public final void applyGoPanel() {
    	closeGoPanel();
		TextView edit = (TextView)goPanel.findViewById( R.id.uri_edit );
		String new_dir = edit.getText().toString().trim();
		
		if( toChange >= 0 && new_dir.length() > 0 ) {
//		    new_dir = searchForNotScreenedURI( new_dir );
			if( toChange != p.getCurrent() )
				p.togglePanels( false );
			p.Navigate( toChange, Uri.parse( new_dir ), null );
		}
		toChange = -1;
		p.focus();
    }    
//    private final String searchForNotScreenedURI( String new_dir )
    
    
    public final void addToFavorites( String uri_str ) {
        	removeFromFavorites( uri_str );
        	shortcutsList.add( new Favorite( uri_str, null ) );
			notifyDataSetChanged();
    }
    public final void removeFromFavorites( String uri ) {
		int pos = find( uri );
		if( pos >= 0 )
			shortcutsList.remove( pos );
		notifyDataSetChanged();
    }
    public final int find( String uri ) {
		try {
    		String strip_uri = uri.trim();
    		if( strip_uri.charAt( strip_uri.length()-1 ) != '/' )
    			strip_uri += "/";
	        for( int i = 0; i <= shortcutsList.size(); i++ ) {
	        	String item = shortcutsList.get( i ).getUriString();
	        	if( item != null ) {
	        		String strip_item = item.trim();
	        		if( strip_item.charAt( strip_item.length()-1 ) != '/' )
	        			strip_item += "/";
	        		if( strip_item.compareTo( strip_uri ) == 0 )
	        			return i;
	        	}
	        }
		} catch( Exception e ) {
		}
		return -1;
    }

    public final String getAsString() {
        String s = Utils.join( store(), sep );
        //Log.v( TAG, "Joined favs: " + s );
        return s;
    }
    
    public final void setFromString( String stored ) {
        if( stored == null ) return;
    	shortcutsList.clear();
    	String use_sep = stored.indexOf( sep ) >= 0 ? sep : old_sep;
        String[] favs = stored.split( use_sep );
        try {
            for( int i = 0; i < favs.length; i++ ) {
                String stored_fav = favs[i];
                //Log.v( TAG, "fav: " + stored_fav );
                shortcutsList.add( new Favorite( stored_fav ) );
            }
        } catch( NoSuchElementException e ) {
            c.showError( "Error: " + e );
        }
		if( shortcutsList.isEmpty() )
		    shortcutsList.add( new Favorite( "/sdcard", c.getContext().getString( R.string.default_uri ) ) );
    }

    public final void restore( String[] stored ) {
        for( int i = 0; i < stored.length; i++ ) {
            shortcutsList.add( new Favorite( stored[i] ) );
        }
    }
    
    public final String[] store() {
        int sz = shortcutsList.size();
        String[] ret = new String[sz]; 
        for( int i = 0; i < sz; i++ ) {
            String fav_str = shortcutsList.get( i ).toString();
            //Log.v( TAG, "a fav to store: " + fav_str );
            ret[i] = fav_str;
        }
        return ret;
    }
    
	@Override
	public boolean onKey( View v, int keyCode, KeyEvent event ) {
	    int v_id = v.getId();
	    if( v_id == R.id.uri_edit ) {
	    	switch( keyCode ) {
			case KeyEvent.KEYCODE_BACK:
				closeGoPanel();
	            return true;
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_ENTER:
				try {
					AutoCompleteTextView actv = (AutoCompleteTextView)v;
					if( actv.getListSelection() == ListView.INVALID_POSITION ) { // !actv.isPopupShowing()
						applyGoPanel();
						return true;
					}
				} catch( ClassCastException e ) {
				}
				return false;
/*				
			case KeyEvent.KEYCODE_DPAD_DOWN:
				try {
					AutoCompleteTextView actv = (AutoCompleteTextView)v;
					actv.showDropDown();
				} catch( ClassCastException e ) {
				}
				return false;
*/
			case KeyEvent.KEYCODE_TAB:
				return true;
			}
	    }
		return false;
	}

	@Override
	public void onClick( View v ) {
		switch( v.getId() ) {
		case R.id.star: 
			try {
				if( toChange < 0 ) break;
				TextView edit = (TextView)goPanel.findViewById( R.id.uri_edit );
	            String uri = edit.getText().toString().trim();
	            CheckBox star_cb = (CheckBox)v;
				if( star_cb.isChecked() )
					addToFavorites( uri );
				else 
					removeFromFavorites( uri );
				star_cb.setChecked( find( uri ) >= 0 );
				AutoCompleteTextView actv = (AutoCompleteTextView)goPanel.findViewById( R.id.uri_edit );
				actv.showDropDown();
				actv.requestFocus();
			}
			catch( Exception e ) {
			}
			break;
		case R.id.go_button:
			    applyGoPanel();
			    break;
			}
	}

	// TextWatcher implementation
	
	@Override
	public void afterTextChanged( Editable s ) {
		try {
			TextView edit = (TextView)goPanel.findViewById( R.id.uri_edit );
			CheckBox star = (CheckBox)goPanel.findViewById( R.id.star );
			String   addr = edit.getText().toString().trim();
			star.setChecked( find( addr ) >= 0 );
		}
		catch( Exception e ) {
		}
	}
	@Override
	public void beforeTextChanged( CharSequence s, int start, int count, int after ) {
	}
	@Override
	public void onTextChanged( CharSequence s, int start, int before, int count ) {
	}        
}
