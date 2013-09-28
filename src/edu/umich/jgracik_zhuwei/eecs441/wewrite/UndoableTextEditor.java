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
  
  private boolean undoingOrRedoing;   // avoids adding undo/redo events to history
  private boolean generatingEvent;    // set true when text is being changed
  
  /* Interface for notifying other classes of editor changes
   * TextEditorActivity implements this to know what to broadcast and when
   */
  public interface EditorEventListener {
    public int sendEvent(EditorEvent ee, String type);
    public void triggerSync();
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
    /* TODO need to add offset? */
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
        .setBeginIndex(e.getBeginIndex() + cursorOffset) /* TODO special case for undo, redo */
        .setNewText(csReplace.toString())
        .setOldText(csOther.toString())
        .setNewCursorIdx(editor.getSelectionStart())
        .setUserid(userid)
    .build();
    
    int eventId = eel.sendEvent(ee, "TEXT_CHANGE");
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
    if(pendingEvent.contains(id)) {
      undoHistory.push(ee);
      if(undoHistory.size() > HISTORY_SIZE) {
        undoHistory.remove(0);
      }
      pendingEvent.remove(id);
    } else if(pendingUndo.contains(id)) {
      eel.triggerSync();
      redoHistory.push(ee);
      if(redoHistory.size() > HISTORY_SIZE) {
        redoHistory.remove(0);
      }
      editor.setSelection(ee.getBeginIndex());
      pendingUndo.remove(id);
    } else if(pendingRedo.contains(id)) {
      eel.triggerSync();
      undoHistory.push(ee);
      if(undoHistory.size() > HISTORY_SIZE) {
        undoHistory.remove(0);
      }
      editor.setSelection(ee.getBeginIndex() + ee.getNewText().length());
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
    Log.d(TAG, "needToSync: " + needToSync);
    return needToSync;
  }
  
  public boolean notBusy()
  {
    Log.d(TAG, "syncing: " + syncing + ", undoredo: " + undoingOrRedoing + ", genEvent: " + generatingEvent);
    return !syncing && !undoingOrRedoing && !generatingEvent;
  }
  
  public void sync(String s)
  {
    syncing = true;
    editor.removeTextChangedListener(e_listener);
    
    int origIdx = editor.getSelectionStart() + cursorOffset;
    editor.setText(s);
    
    if(origIdx <= editor.length()) {
      editor.setSelection(origIdx);
    } else {
      editor.setSelection(editor.length());
    }
    cursorOffset = 0;
    
    editor.addTextChangedListener(e_listener);
    needToSync = false;
    syncing = false;
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
      
      // broadcast text change event
      if(connected && !duplicate) {
        int eventId = eel.sendEvent(textChange, "TEXT_CHANGE");
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
        
        /*
         * TODO BROADCAST CURSOR CHANGE EVENT
         * keep track of time last cursor change was sent
         * and only update after some elapsed time threshold
         */
        EditorEvent ee = createCursorEvent(cursorIdx);
        eel.sendEvent(ee, "CURSOR_CHANGE");
      } else {
        generatingEvent = true;
      }
    }
    
  }  
  
}