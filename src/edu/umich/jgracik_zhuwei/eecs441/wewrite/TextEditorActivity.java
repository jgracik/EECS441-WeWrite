package edu.umich.jgracik_zhuwei.eecs441.wewrite;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import com.google.protobuf.InvalidProtocolBufferException;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
  
  // local editor variables
  private UndoableTextEditor undoableWrapper;
  private EditTextCursor editor;
  private int latestSubIdSent;
  
  // collabrify & non-local objects and variables
  private CollabrifyListener collabrifyListener;
  private CollabrifyClient client;
  private EditText serverText;
  private LinkedList<EditorEvent> eventQueue;
  private LinkedList<Integer> eventSubIds;
  private boolean getLatestEvent;
  private int forceNext;
  private long sessId;
  private long orderIdCtr;
  private int latestSubIdApplied;
  
  // objects to implement timer
  private int lastSyncSubId;
  private Handler hSync;
  private Runnable updateTimer = new Runnable()
  {
    public void run()
    {
      Log.i("SYNC", "updateTimer tick");
      Log.d("ORDERING", "latest sub applied: " + latestSubIdApplied + ", latest sent: " + latestSubIdSent);
      if(latestSubIdApplied != latestSubIdSent) {
        applyTextEvent(0, false);
        hSync.postDelayed(this, 1500);
        return;
      } else {
        if(lastSyncSubId < latestSubIdApplied) {
          undoableWrapper.sync(serverText.getText().toString());
          lastSyncSubId = latestSubIdApplied;
          hSync.postDelayed(this, 1500);
          return;
        }
      }
      Log.d("ORDERING", "got past sub id stuff in handler");
      if(undoableWrapper.notBusy() && applyTextEvent(0, false)) {
        Log.i("SYNC", "updateTimer triggering update");
        lastSyncSubId = latestSubIdApplied;
        undoableWrapper.sync(serverText.getText().toString());
      }
      hSync.postDelayed(this, 1500);  // 1500 ms
    }
  };
  
  @SuppressLint("NewApi")
  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_text_editor);
    // Show the Up button in the action bar.
    setupActionBar();
    
    // join parameters stored in intent
    Intent intent = getIntent();
    boolean isJoin = intent.getBooleanExtra(MainActivity.IS_JOIN, false);
    sessId = intent.getLongExtra(MainActivity.SESSION_ID, 0L);
    
    // render editor, but disable until connected
    editor = (EditTextCursor) findViewById(R.id.editor_obj);
    editor.setLongClickable(false);
    editor.setEnabled(false);
    editor.setFocusable(false);
    editor.setHint("Connecting, please wait...");
    undoableWrapper = new UndoableTextEditor(editor);
    undoableWrapper.setEditorEventListener(this);
    latestSubIdSent = -1;
    
    // non-local object setup
    serverText = new EditText(this);
    eventQueue = new LinkedList<EditorEvent>();
    eventSubIds = new LinkedList<Integer>();
    orderIdCtr = 0L; 
    forceNext = 0;
    latestSubIdApplied = -1;

    hSync = new Handler();
    hSync.removeCallbacks(updateTimer);
    lastSyncSubId = 0;
    
    // setup collabrify listener
    collabrifyListener = new CollabrifyAdapter() 
    {      
      @Override
      public void onReceiveEvent(final long orderId, final int subId,
          String eventType, final byte[] data) 
      {
        Log.i(TAG, "collabrifyListener: event received");
        Log.i(TAG, "### orderId: " + orderId + ", subId: " + subId + ", eventType: " + eventType);
        if(orderIdCtr != orderId) {
          return; // collabrify sent out of order or duplicated
        } else {
          orderIdCtr++;
        }

        final EditorEvent fromServer;
        boolean isFromThisUser = false;
        
        if(eventType.equals("TEXT_CHANGE")) 
        {
          try
          {
            fromServer = EditorEvent.parseFrom(data);
            Log.d("ORDERING", "rec event id = " + orderId + ", text = " + fromServer.getNewText());
            isFromThisUser = fromServer.getUserid() == client.currentSessionParticipantId();
            Log.d(TAG, "### is from this user?: " + isFromThisUser);
            eventQueue.add(fromServer);
            eventSubIds.add(subId);
            undoableWrapper.updateUndoRedoStacks(fromServer);
            if(!isFromThisUser) {
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
            isFromThisUser = fromServer.getUserid() == client.currentSessionParticipantId();
            eventQueue.add(fromServer);
            eventSubIds.add(subId);
          }
          catch( InvalidProtocolBufferException e )
          {
            e.printStackTrace();
          }
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
      
    };
    
    getLatestEvent = false;
    
    // init collabrify client
    try {
      client = new CollabrifyClient(this, "user email", "user display name",
          "441fall2013@umich.edu", "XY3721425NoScOpE", getLatestEvent,
          collabrifyListener);
      Log.d(TAG, "client initialized successfully");
    } catch (CollabrifyException ce) {
      Log.e(TAG, "error initializing client");
      ce.printStackTrace();
    }
    
    // if join parameters received, try to join
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
        hSync.removeCallbacks(updateTimer);
        finish();
      }
      catch( CollabrifyException e1 )
      {
        e1.printStackTrace();
        Toast.makeText(this, "Unable to connect to session", Toast.LENGTH_LONG).show();
        hSync.removeCallbacks(updateTimer);
        finish();
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
      case R.id.action_display_session_id:
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, String.valueOf(sessId), Toast.LENGTH_LONG);
        toast.show();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }
  
  // server events only
  public boolean applyTextEvent(int iter, boolean setNeedToSync)
  {
    Log.d(TAG, "in applyTextEvent");
    if(eventQueue.isEmpty()) {
      return setNeedToSync;
    }
    
    EditorEvent ev = eventQueue.poll();
    Integer sub = eventSubIds.poll();
    
    if(!ev.hasBeginIndex()) {
      // is cursor event, ignore
      if(client.currentSessionParticipantId() == ev.getUserid()) {
        latestSubIdApplied = sub.intValue();
      }
      return applyTextEvent(iter, setNeedToSync);
    }
    
    Editable e_text = Editable.Factory.getInstance().newEditable(serverText.getText());
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
    
    Log.d("ORDERING", "applied text event: " + ev.getNewText());
    
    if(client.currentSessionParticipantId() != ev.getUserid()) {
      setNeedToSync = true;
    } else {
      latestSubIdApplied = sub.intValue();
      if(forceNext > 0) {
        setNeedToSync = true;
        forceNext--;
      }
    }
    
    Log.d("ORDERING", "need to sync? " + setNeedToSync);
    
    serverText.setText(e_text);
    if(eventQueue.isEmpty() || iter == 10) {
      return setNeedToSync;
    } else {
      return applyTextEvent(iter + 1, setNeedToSync);
    }
  }
  
  public void undoOperation(View view)
  {
    undoableWrapper.undo();
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
        hSync.removeCallbacks(updateTimer);
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
      hSync.postDelayed(updateTimer, 2000);
      Log.d(TAG, "EDITOR ENABLED, timer started");
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
        hSync.removeCallbacks(updateTimer);
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
        hSync.removeCallbacks(updateTimer);
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
    String serverTextStr = new String(serverText.getText().toString());
    undoableWrapper.sync(serverTextStr);
  }

  @Override
  public void forceNextSync()
  {
    forceNext++;
  }

  @Override
  public void setLatestSubId(int id)
  {
    latestSubIdSent = id;
    
  }

}
