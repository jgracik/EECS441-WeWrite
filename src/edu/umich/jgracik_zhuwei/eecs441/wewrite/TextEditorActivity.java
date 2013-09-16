package edu.umich.jgracik_zhuwei.eecs441.wewrite;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import edu.umich.jgracik_zhuwei.eecs441.wewrite.R;

public class TextEditorActivity extends Activity
{
  private UndoableTextEditor undoableWrapper;
  
  @SuppressLint("NewApi")
  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_text_editor);
    // Show the Up button in the action bar.
    setupActionBar();
    
    EditText editor = (EditText) findViewById(R.id.editor_obj);
    editor.setLongClickable(false);
    undoableWrapper = new UndoableTextEditor(editor);
  }

  /**
   * Set up the {@link android.app.ActionBar}, if the API is available.
   */
  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void setupActionBar()
  {
    if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB )
    {
      getActionBar().setDisplayHomeAsUpEnabled(true);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.text_editor, menu);
    return true;
  }


  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    switch ( item.getItemId() )
    {
      case android.R.id.home:
        // This ID represents the Home or Up button. In the case of this
        // activity, the Up button is shown. Use NavUtils to allow users
        // to navigate up one level in the application structure. For
        // more details, see the Navigation pattern on Android Design:
        //
        // http://developer.android.com/design/patterns/navigation.html#up-vs-back
        //
        NavUtils.navigateUpFromSameTask(this);
        return true;
    }
    return super.onOptionsItemSelected(item);
  }
  
  public void undoOperation(View view)
  {
    undoableWrapper.undo();
  }
  
  public void saveFile(View view)
  {
    
  }
  
  public void redoOperation(View view)
  {
    undoableWrapper.redo();
  }

}
