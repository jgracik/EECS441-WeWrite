package edu.umich.jgracik_zhuwei.eecs441.wewrite;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.View;
//import edu.umich.jgracik_zhuwei.eecs441.wewrite.R;

public class MainActivity extends FragmentActivity
{
  public static final String IS_JOIN = "edu.umich.jgracik_zhuwei.eecs441.wewrite.IS_JOIN";
  public static final String SESSION_ID = "edu.umich.jgracik_zhuwei.eecs441.wewrite.SESSION_ID";

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }
  
  //method for new file
  public void mainCreateFile(View view) 
  {
    DialogFragment filePopup = new NewFileDialogFragment();
    filePopup.show(getSupportFragmentManager(), "newfile");
  }
  
  //method for new collaboration session
  public void mainCreateSession(View view) 
  {
    Intent newSessionIntent = new Intent(this, TextEditorActivity.class);
    newSessionIntent.putExtra(IS_JOIN, false);
    newSessionIntent.putExtra(SESSION_ID, 0L);
    startActivity(newSessionIntent);
  }
  
  //method for joining an existing collab session
  public void mainJoinSession(View view) 
  {
    DialogFragment joinSessionPopup = new SessionDialogFragment();
    joinSessionPopup.show(getSupportFragmentManager(), "joinsession");
  }

}


