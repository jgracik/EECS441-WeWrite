package edu.umich.jgracik_zhuwei.eecs441.wewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.protobuf.InvalidProtocolBufferException;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.text.Editable;
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
import edu.umich.jgracik_zhuwei.eecs441.wewrite.EditorEventProto.EditorEvent;
import edu.umich.jgracik_zhuwei.eecs441.wewrite.LeaveSessionDialog.LeaveSessionListener;
import edu.umich.jgracik_zhuwei.eecs441.wewrite.UndoableTextEditor.EditorEventListener;
//import edu.umich.jgracik_zhuwei.eecs441.wewrite.R;

public class TextEditorActivity extends FragmentActivity implements LeaveSessionListener, EditorEventListener
{
  private static final String TAG = "TextEditorActivity";
  private UndoableTextEditor undoableWrapper;
  private CollabrifyListener collabrifyListener;
  private CollabrifyClient client;
  private EditTextCursor editor;
  private EditText serverText;
  private boolean getLatestEvent;
  private long sessId;
  
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
    sessId = intent.getLongExtra(MainActivity.SESSION_ID, 0L);
    
    Log.d(TAG, "isJoin: " + isJoin);
    Log.d(TAG, "sessId: " + sessId);
    
    editor = (EditTextCursor) findViewById(R.id.editor_obj);
    editor.setLongClickable(false);
    editor.setEnabled(false);   // do not allow editing until connection is established
    editor.setFocusable(false); // ^^^
    editor.setHint("Connecting, please wait...");
    undoableWrapper = new UndoableTextEditor(editor);
    undoableWrapper.setEditorEventListener(this);
    
    serverText = new EditText(this);
    
    collabrifyListener = new CollabrifyAdapter() 
    {
      
      @Override
      public void onDisconnect() 
      {
        Log.i(TAG, "collabrifyListener: disconnect");
      }
      
      @Override
      public void onReceiveEvent(final long orderId, final int subId,
          String eventType, final byte[] data) 
      {
        Log.i(TAG, "collabrifyListener: event received");
        Log.i(TAG, "### orderId: " + orderId + ", subId: " + subId + ", eventType: " + eventType);
        final EditorEvent fromServer;
        
        if(eventType.equals("TEXT_CHANGE")) {
          try
          {
            fromServer = EditorEvent.parseFrom(data);
            applyTextEvent(fromServer);
            if(fromServer.getUserid() != client.currentSessionParticipantId()) {
              undoableWrapper.setNeedToSync(true);
              undoableWrapper.updateCursorOffset(fromServer);
            } else {
              runOnUiThread(new Runnable() {
                @Override
                public void run()
                {
                  undoableWrapper.confirmEvent(subId, fromServer);              
                }
                
              }); 
            }
          }
          catch( InvalidProtocolBufferException e )
          {
            e.printStackTrace();
          }
        } else if(eventType.equals("CURSOR_CHANGE")) {
          try
          {
            fromServer = EditorEvent.parseFrom(data);
            /* TODO update cursor position hash map */
          }
          catch( InvalidProtocolBufferException e )
          {
            e.printStackTrace();
          }
        }
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
        sessId = id;
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
          e.printStackTrace();
        }
      }
      
      @Override
      public void onSessionEnd(long id)
      {
        Log.i(TAG, "collabrify listener: session ended");
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
        e1.printStackTrace();
        Toast.makeText(this, "Unable to connect to session", Toast.LENGTH_LONG).show();
      }
      catch( CollabrifyException e1 )
      {
        e1.printStackTrace();
        Toast.makeText(this, "Unable to connect to session", Toast.LENGTH_LONG).show();
      }
    } else {
      Random rand = new Random();
      String sessName = "editor" + (rand.nextInt(90000) + 10000);
      ArrayList<String> tags = new ArrayList<String>();
      tags.add("test_jgracik_zhuwei");
      
      Log.i(TAG, "attempting to create session with name: " + sessName);
      
      try {
        client.createSession(sessName, tags, null, 0);
        Toast createMsg = Toast.makeText(this, "Connecting to new session...", Toast.LENGTH_SHORT);
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
  
  // server events only
  public void applyTextEvent(EditorEvent ev)
  {
    Log.d(TAG, "in applyTextEvent");
    Editable e_text = serverText.getText();
    int endIdx = ev.getBeginIndex();
    CharSequence csReplace = ev.getNewText();
    CharSequence csOther = ev.getOldText();
    
    try {
      if(csReplace != null) {
        endIdx += csOther.length();
      }
      
      e_text.replace(ev.getBeginIndex(), endIdx, csReplace);
      
      Log.d("SERVERTEXT " + TAG, "replace okay, beginIdx=" + ev.getBeginIndex() + ", endIdx = " + endIdx + ", csReplace = " + csReplace);
      Log.d("SERVERTEXT " + TAG, "server's text is : " + e_text.toString());
    } catch(IndexOutOfBoundsException ex) {
      // text has been deleted, so endIndex is now outside 
      // the editor's range
      Log.d("SERVERTEXT " + TAG, "performEdit exeption: " + ex.toString());
      Log.d("SERVERTEXT " + TAG, "beginIdx=" + ev.getBeginIndex() + ", endIdx=" + endIdx + ", editor length=" + e_text.length() + ", replace: [" + csReplace + "]");

      if(e_text.length() < ev.getBeginIndex()) {
        // beginIndex is outside the editor's range: append
        //int appendIdx = e_text.length();
        e_text.append(csReplace);

        Log.d("SERVERTEXT " + TAG, "appending");
      } else {
        // beginIndex is inside range, end is outside
        e_text.insert(ev.getBeginIndex(), csReplace);
        
        Log.d("SERVERTEXT " + TAG, "inserting");
      }
    }
  }
  
  public void undoOperation(View view)
  {
    undoableWrapper.undo();
  }
  
  public void saveFile(View view)
  {
    /* TODO 
     * TEMPORARY - force the local editor to sync with the server-only editor
     * for preliminary testing
     */
    undoableWrapper.sync(serverText.getText().toString());
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
      Toast.makeText(this, "Connected! Session id is " + sessId, Toast.LENGTH_LONG).show();
      undoableWrapper.setUserID(client.currentSessionParticipantId());
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
      try 
      {
        client.leaveSession(false);
        Toast.makeText(this, "Disconnected", Toast.LENGTH_LONG).show();
        finish();
      } catch (CollabrifyException ce) {
        ce.printStackTrace();
      }
    }
  }

  @Override
  public int sendEvent(EditorEvent ee, String type)
  {
    Log.d(TAG, "SENDING BUFFER\n" + ee.toString());
    if(client.inSession()) {
      try
      {
        return client.broadcast(ee.toByteArray(), type);
      }
      catch( CollabrifyException e )
      {
        e.printStackTrace();
      }
    }
    
    return Integer.MIN_VALUE;
    
  }
  
  @Override
  public void triggerSync()
  {
    undoableWrapper.sync(serverText.getText().toString());
  }

}
