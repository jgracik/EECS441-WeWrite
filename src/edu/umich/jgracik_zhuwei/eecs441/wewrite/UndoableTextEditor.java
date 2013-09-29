package edu.umich.jgracik_zhuwei.eecs441.wewrite;

import java.util.Stack;
import java.util.TreeSet;

import edu.umich.jgracik_zhuwei.eecs441.wewrite.EditTextCursor.onSelectionChangedListener;
import edu.umich.jgracik_zhuwei.eecs441.wewrite.EditTextCursor;
import edu.umich.jgracik_zhuwei.eecs441.wewrite.EditorEventProto.EditorEvent;

import android.annotation.SuppressLint;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;

public class UndoableTextEditor
{
  private static final String TAG = "UndoableTextEditor";
  private static final int HISTORY_SIZE = 10;
  private static final int UNDO_OP = 1;
  private static final int REDO_OP = 2;
  
  private EditTextCursor editor;  // underlying view
  
  private Stack<EditorEvent> undoHistory;
  private Stack<EditorEvent> redoHistory;
  private TreeSet<Integer> pendingUndo; // waiting to be received from server
  private TreeSet<Integer> pendingRedo;
  private TreeSet<Integer> pendingEvent;

  private EditorListener e_listener; 
  private long userid;
  private boolean connected;
  private boolean needToSync;
  private boolean syncing;
  private int cursorOffset;
  private int lastCursor;
  
  private boolean undoingOrRedoing;   // avoids adding undo/redo events to history
  private boolean generatingEvent;    // set true when text is being changed
  
  /* Interface for notifying other classes of editor changes
   * TextEditorActivity implements this to know what to broadcast and when
   */
  public interface EditorEventListener {
    public int sendEvent(EditorEvent ee, String type);
    public void triggerSync();
    public void forceNextSync();
    public void setLatestSubId(int id);
  }
  
  private EditorEventListener eel;
  
  @SuppressLint("NewApi")
  public UndoableTextEditor(EditTextCursor edittext)
  {
    undoingOrRedoing = false;
    generatingEvent = false;
    
    editor = edittext;
    connected = false;
    needToSync = false;
    syncing = false;
    cursorOffset = 0;
    lastCursor = 0;
    userid = 0L;
    
    undoHistory = new Stack<EditorEvent>();
    redoHistory = new Stack<EditorEvent>();
    pendingUndo = new TreeSet<Integer>();
    pendingRedo = new TreeSet<Integer>();
    pendingEvent = new TreeSet<Integer>();
    
    e_listener = new EditorListener();
    editor.addTextChangedListener(e_listener);
    editor.addOnSelectionChangedListener(e_listener);
  }
  
  public void setUserID(long id) 
  {
    userid = id;
    connected = true;
  }
  
  public void setEditorEventListener(EditorEventListener e) 
  {
    eel = e;
  }
  
  public EditorEvent createCursorEvent(int cursorIdx)
  {
    EditorEvent ee = EditorEvent.newBuilder()
        .setNewCursorIdx(cursorIdx)
        .setUserid(userid)
    .build();
    
    return ee;
  }
  
  public EditorEvent createUndoRedoEvent(EditorEvent e, int type)
  {
    // swap text ordering
    CharSequence csReplace = e.getOldText();
    CharSequence csOther = e.getNewText();
    
    EditorEvent ee = EditorEvent.newBuilder()
        .setBeginIndex(e.getBeginIndex()) /* TODO special case for undo, redo */
        .setNewText(csReplace.toString())
        .setOldText(csOther.toString())
        .setNewCursorIdx(editor.getSelectionStart())
        .setUserid(userid)
    .build();
    
    lastCursor = editor.getSelectionStart();
    
    int eventId = eel.sendEvent(ee, "TEXT_CHANGE");
    eel.setLatestSubId(eventId);
    if(type == UNDO_OP) {
      pendingUndo.add(eventId);
    } else {
      pendingRedo.add(eventId);
    }
    /* * * */
    
    return ee;
  }
  
  public void confirmEvent(final int id, final EditorEvent ee)
  {
    Log.d("ORDERING", "event confirmed.  id: " + id + ", text: " + ee.getNewText());
    if(pendingEvent.contains(id)) {
      undoHistory.push(ee);
      if(undoHistory.size() > HISTORY_SIZE) {
        undoHistory.remove(0);
      }
      pendingEvent.remove(id);
    } else if(pendingUndo.contains(id)) {
      eel.forceNextSync();
      redoHistory.push(ee);
      if(redoHistory.size() > HISTORY_SIZE) {
        redoHistory.remove(0);
      }
      editor.setSelection(ee.getBeginIndex());
      pendingUndo.remove(id);
    } else if(pendingRedo.contains(id)) {
      eel.forceNextSync();
      undoHistory.push(ee);
      if(undoHistory.size() > HISTORY_SIZE) {
        undoHistory.remove(0);
      }
      try {
        editor.setSelection(ee.getBeginIndex() + ee.getNewText().length());
      } catch(IndexOutOfBoundsException e) {
        Log.e(TAG, "set cursor index out of bounds");
      }
      pendingRedo.remove(id);
    }
  }
  
  public void undo()
  {
    if(undoHistory.empty()) {
      Log.d(TAG, "nothing to undo");
      return;
    }
    
    undoingOrRedoing = true;
    
    EditorEvent undoEvent = undoHistory.pop();
    createUndoRedoEvent(undoEvent, UNDO_OP);
    
    undoingOrRedoing = false;
    
    Log.d(TAG, "performed undo operation, returning from undo()");
  }
  
  public void setNeedToSync(boolean b) 
  {
    needToSync = b;
  }
  
  public boolean needsToSync()
  {
    Log.d("SYNC", "needToSync: " + needToSync);
    return needToSync;
  }
  
  public boolean notBusy()
  {
    Log.d("SYNC", "syncing: " + syncing + ", undoredo: " + undoingOrRedoing + ", genEvent: " + generatingEvent);
    return !syncing && !undoingOrRedoing && !generatingEvent;
  }
  
  public void sync(String s)
  {
    Log.d("SYNC START", "SYNC START");
    syncing = true;
    editor.removeTextChangedListener(e_listener);
    editor.removeOnSelectionChangedListener();
    
    int origIdx = editor.getSelectionStart() + cursorOffset;
    editor.setText(s);
    
    if(origIdx <= editor.length()) {
      editor.setSelection(origIdx);
    } else {
      editor.setSelection(editor.length());
    }
    needToSync = false;
    cursorOffset = 0;
    
    editor.addOnSelectionChangedListener(e_listener);
    editor.addTextChangedListener(e_listener);
    syncing = false;
    Log.d("SYNC FINISH", "SYNC FINISH");
  }
  
  public void updateCursorOffset(EditorEvent ee)
  {
    if(ee.getBeginIndex() > (editor.getSelectionStart() + cursorOffset)) {
      // event occured after current pos with offset, no need to update offset
      return;
    }

    int endIdx = ee.getBeginIndex();
    CharSequence csNew = ee.getNewText();
    CharSequence csOld = ee.getOldText();
    
    if(csNew.length() == 0) {
      endIdx += csOld.length();
    } else if(csOld.length() == 0) {
      endIdx += csNew.length();
    }
    
    cursorOffset += (endIdx - ee.getBeginIndex());
    
    Log.d(TAG, "cursor offset updated, " + (endIdx - ee.getBeginIndex() + " added, new offset: " + cursorOffset));
  }
  
  public void redo()
  {
    if(redoHistory.empty()){
      Log.d(TAG, "nothing to redo");
      return;
    }
    
    undoingOrRedoing = true;

    EditorEvent redoEvent = redoHistory.pop();
    createUndoRedoEvent(redoEvent, REDO_OP);
    
    undoingOrRedoing = false;
    
    Log.d(TAG, "performed redo operation, returning from redo()");
  }
  
  
  /* Class to monitor changes to the EditText field and save last change */
  private class EditorListener implements TextWatcher, onSelectionChangedListener
  {
    private CharSequence orig, change;
    private int beginIdx, cursorIdx;
    private boolean duplicate = false;
    
    public void beforeTextChanged(CharSequence s, int start, int count, int after)
    {
      // no need to save changes resulting from an undo or redo
      if(undoingOrRedoing || syncing) return;
      
      generatingEvent = true;
      
      beginIdx = start;
      orig = s.subSequence(start, start + count);
      Log.d(TAG, "listener in beforeTextChanged, start: " + start + ", count: " + count + ", orig: [" + orig + "]");
    }
    
    public void onTextChanged(CharSequence s, int start, int before, int count)
    {
      if(undoingOrRedoing || syncing) return;
      
      change = s.subSequence(start, start + count);
      Log.d(TAG, "listener in onTextChanged, start: " + start + ", before: " + before + ", change: [" + change + "]");
      
      Log.d(TAG, "listener returning from method onTextChanged");
    }

    public void afterTextChanged(Editable s)
    {
      Log.d(TAG, "afterTextChanged");
      Log.d(TAG, s.toString());
      
      if(undoingOrRedoing || syncing) return;
      
      // create EditorEvent object
      EditorEvent textChange = EditorEvent.newBuilder()
          .setBeginIndex(beginIdx + cursorOffset)
          .setNewText(change.toString())
          .setOldText(orig.toString())
          .setNewCursorIdx(editor.getSelectionStart())
          .setUserid(userid)
      .build();
      
      Log.d("ORDERING", "created event: " + change.toString());
      
      // broadcast text change event
      if(connected && !duplicate) {
        int eventId = eel.sendEvent(textChange, "TEXT_CHANGE");
        eel.setLatestSubId(eventId);
        Log.d("ORDERING", "send event: id = " + eventId);
        pendingEvent.add(eventId);
      }
      
      duplicate = false;
      generatingEvent = false;
    }

    @Override
    public void onSelectionChanged(int selStart, int selEnd)
    {
      /* send cursor change event to server */
      Log.i(TAG, "cursor location changed to " + selStart);
      if(!generatingEvent) {
        Log.i(TAG, "not generating event, sending cursor change");
        cursorIdx = selStart;
        if(cursorIdx == lastCursor) return;
        
        EditorEvent ee = createCursorEvent(cursorIdx);
        eel.setLatestSubId(eel.sendEvent(ee, "CURSOR_CHANGE"));
      } else {
        generatingEvent = true;
      }
    }
    
  }  
  
}