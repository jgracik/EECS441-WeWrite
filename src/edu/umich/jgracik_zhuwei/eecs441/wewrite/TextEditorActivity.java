package edu.umich.jgracik_zhuwei.eecs441.wewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

//import edu.umich.imlc.android.common.Utils;
import edu.umich.imlc.collabrify.client.CollabrifyAdapter;
import edu.umich.imlc.collabrify.client.CollabrifyListener;
import edu.umich.imlc.collabrify.client.CollabrifyClient;
import edu.umich.imlc.collabrify.client.CollabrifyParticipant;
import edu.umich.imlc.collabrify.client.CollabrifySession;
import edu.umich.imlc.collabrify.client.exceptions.CollabrifyException;
import edu.umich.imlc.collabrify.client.exceptions.ConnectException;

import edu.umich.jgracik_zhuwei.eecs441.wewrite.EditTextCursor;
import edu.umich.jgracik_zhuwei.eecs441.wewrite.LeaveSessionDialog.LeaveSessionListener;
//import edu.umich.jgracik_zhuwei.eecs441.wewrite.R;

public class TextEditorActivity extends FragmentActivity implements LeaveSessionListener
{
  private static final String TAG = "TextEditorActivity";
  private UndoableTextEditor undoableWrapper;
  private CollabrifyListener collabrifyListener;
  private CollabrifyClient client;
  private EditTextCursor editor;
  private boolean connected;
  private boolean getLatestEvent;
  
  @SuppressLint("NewApi")
  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_text_editor);
    // Show the Up button in the action bar.
    setupActionBar();
    
    Intent intent = getIntent();
    boolean isJoin = intent.getBooleanExtra(MainActivity.IS_JOIN, false);
    long sessId = intent.getLongExtra(MainActivity.SESSION_ID, 0L);
    
    Log.d(TAG, "isJoin: " + isJoin);
    Log.d(TAG, "sessId: " + sessId);
    
    /* TODO
     * ----
     * 
     */
    
    connected = false;
    editor = (EditTextCursor) findViewById(R.id.editor_obj);
    editor.setLongClickable(false);
    editor.setEnabled(false);   // do not allow editing until connection is established
    editor.setFocusable(false); // ^^^
    editor.setHint("Connecting, please wait...");
    undoableWrapper = new UndoableTextEditor(editor);
    
    collabrifyListener = new CollabrifyAdapter() 
    {
      
      @Override
      public void onDisconnect() 
      {
        Log.i(TAG, "collabrifyListener: disconnect");
      }
      
      @Override
      public void onReceiveEvent(final long orderId, int subId,
          String eventType, final byte[] data) 
      {
        Log.i(TAG, "collabrifyListener: event received");
        Log.i(TAG, "### orderId: " + orderId + ", subId: " + subId + ", eventType: " + eventType);
      }
      
      @Override
      public void onReceiveSessionList(final List<CollabrifySession> sessionList) 
      {
        Log.i(TAG, "collabrifyListener: received session list");
        for(int i = 0; i < sessionList.size(); i++) {
          Log.i(TAG, "### " + sessionList.get(i).toString());
        }
      }
      
      @Override
      public void onSessionCreated(long id)
      {
        Log.i(TAG, "collabrifyListener: session created, id = " + id);
        connected = true;
        runOnUiThread(new Runnable()
        {
          @Override
          public void run() {
            enableTextEditor();
          }
        });
      }
      
      @Override
      public void onError(CollabrifyException e)
      {
        Log.e(TAG, "error", e);
      }
      
      @Override
      public void onSessionJoined(long maxOrderId, long baseFileSize)
      {
        Log.i(TAG, "collabrifyListener: sessionJoined successfully");
        try
        {
          Log.i(TAG, "collabrifyListener: session id is: " + client.currentSessionId());
          connected = true;
          runOnUiThread(new Runnable()
          {
            @Override
            public void run() {
              enableTextEditor();
            }
          });
        }
        catch( CollabrifyException e )
        {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      
      @Override
      public void onSessionEnd(long id)
      {
        Log.i(TAG, "collabrify listener: session ended");
        connected = false;
        runOnUiThread(new Runnable()
        {
          @Override
          public void run() {
            showOwnerDisconnectNotice();
          }
        });
      }
      
      @Override
      public byte[] onBaseFileChunkRequested(long currentBaseFileSize)
      {
        Log.i(TAG, "collabrifyListener: base file chunk requested");
        return null;
      }
      
      @Override
      public void onBaseFileChunkReceived(byte[] baseFileChunk)
      {
        Log.i(TAG, "collabrifyListener: base file chunk recvd");
      }
      
      @Override
      public void onBaseFileUploadComplete(long baseFileSize)
      {
        Log.i(TAG, "collabrifyListener: base file upload complete");
      }
      
    };
    
    getLatestEvent = false;
    
    try {
      client = new CollabrifyClient(this, "user email", "user display name",
          "441fall2013@umich.edu", "XY3721425NoScOpE", getLatestEvent,
          collabrifyListener);
      Log.d(TAG, "client initialized successfully");
    } catch (CollabrifyException ce) {
      Log.e(TAG, "error initializing client");
      ce.printStackTrace();
    }
    
    if(isJoin) {
      try
      {
        client.joinSession(sessId, null);
        Toast.makeText(this, "Connecting to session...", Toast.LENGTH_SHORT).show();
      }
      catch( ConnectException e1 )
      {
        // TODO Auto-generated catch block
        e1.printStackTrace();
        Toast.makeText(this, "Unable to connect to session", Toast.LENGTH_LONG).show();
      }
      catch( CollabrifyException e1 )
      {
        // TODO Auto-generated catch block
        e1.printStackTrace();
        Toast.makeText(this, "Unable to connect to session", Toast.LENGTH_LONG).show();
      }
    } else {
      Random rand = new Random();
      String sessName = "editor" + rand.nextInt(90000) + 10000;
      ArrayList<String> tags = new ArrayList<String>();
      tags.add("test_jgracik_zhuwei");
      
      Log.i(TAG, "attempting to create session with name: " + sessName);
      
      try {
        client.createSession(sessName, tags, null, 0);
        Toast createMsg = Toast.makeText(this, "Connecting to new session...", Toast.LENGTH_LONG);
        createMsg.show();
      } catch (ConnectException ce) {
        Log.e(TAG, "ConnectException, unable to create collabrify session");
        ce.printStackTrace();
      } catch (CollabrifyException ce) {
        Log.e(TAG, "CollabrifyException, unable to create collabrify session");
        ce.printStackTrace();
    }
    }
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
  
  public void leaveSession(View view)
  {
    if(!client.inSession()) {
      Toast.makeText(this, "You are not currently connected to a session", Toast.LENGTH_LONG).show();
      return;
    }
    
    long myId = client.currentSessionParticipantId();
    CollabrifyParticipant owner = null; 
    
    try {
      owner = client.currentSessionOwner();
    } catch (CollabrifyException ce) {
      Log.d(TAG, "error getting session owner");
    }
    
    if(owner == null || owner.getId() != myId) {
      try {
        client.leaveSession(false);
        connected = false;
        Toast.makeText(this, "Disconnected", Toast.LENGTH_LONG).show();
        finish();
      } catch (CollabrifyException ce) {
        ce.printStackTrace();
      }
    }
    
    if(owner.getId() == myId) {
      DialogFragment ownerLeaveDialog = new LeaveSessionDialog();
      ownerLeaveDialog.show(getSupportFragmentManager(), "owner_leave_dialog");
    }
  }
  
  protected void enableTextEditor()
  {
    try {
      Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show();
      editor.setEnabled(true);
      editor.setFocusable(true);
      editor.setFocusableInTouchMode(true);
      editor.setHint("Type here");
      editor.requestFocus();
      Log.d(TAG, "EDITOR ENABLED");
    } catch(Exception e) {
      e.printStackTrace();
    }
  }
  
  protected void showOwnerDisconnectNotice()
  {
    Toast.makeText(this, "Disconnected - session deleted by owner", Toast.LENGTH_LONG).show();
  }

  @Override
  public void onDialogPositiveClick(DialogFragment dialog)
  {
    if(client.inSession()) {
      try {
        client.leaveSession(true);
        connected = false;
        Toast.makeText(this, "Disconnected and session deleted", Toast.LENGTH_LONG).show();
        finish();
      } catch (CollabrifyException ce) {
        ce.printStackTrace();
      }
    }
  }

  @Override
  public void onDialogNegativeClick(DialogFragment dialog)
  {
    if(client.inSession()) {
      try {
        client.leaveSession(false);
        Toast.makeText(this, "Disconnected", Toast.LENGTH_LONG).show();
        connected = false;
        finish();
      } catch (CollabrifyException ce) {
        ce.printStackTrace();
      }
    }
  }

}
