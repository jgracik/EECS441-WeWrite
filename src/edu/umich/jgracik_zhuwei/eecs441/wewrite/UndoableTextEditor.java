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
  
  //private Stack<HistoryEntry> moveEventStack; // needed to help make move atomic
  //private DragListener d_listener;
  //private boolean textMoveEvent = false;    // drag & drop move event occuring
  
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
    
    /*
     * This is specifically to handle move events via drag & drop
     * No longer needed per spec update
     */
    //moveEventStack = new Stack<HistoryEntry>();
    //d_listener = new DragListener();
    //editor.setOnDragListener(d_listener);
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
    //Editable editor_text = editor.getText();
    //int newBeginIndex = e.getBeginIndex();
    //int endIdx = e.getBeginIndex();
    CharSequence csReplace;
    CharSequence csOther;
    
    /*
    if(type == UNDO_OP) {
      csReplace = e.getOldText();
      csOther = e.getNewText();
    } else {
      csReplace = e.getNewText();
      csOther = e.getOldText();
    }
    */
    
    csReplace = e.getOldText();
    csOther = e.getNewText();
    
    /*
    try {
      if(csReplace != null) {
        endIdx += csOther.length();
      }
      
      //TODO editor_text.replace(e.beginIndex, endIdx, csReplace);
      
      Log.d(TAG, "replace okay");
    } catch(IndexOutOfBoundsException ex) {
      // text has been deleted, so endIndex is now outside 
      // the editor's range
      Log.d(TAG, "performEdit exeption: " + ex.toString());
      Log.d(TAG, "beginIdx=" + e.getBeginIndex() + ", endIdx=" + endIdx + ", editor length=" + editor_text.length() + ", replace: [" + csReplace + "]");

      if(editor_text.length() < e.getBeginIndex()) {
        // beginIndex is outside the editor's range: append
        int appendIdx = editor_text.length();
        //TODO editor_text.append(csReplace);
        newBeginIndex = appendIdx; // update new index for future undos or redos

        Log.d(TAG, "appending");
      } else {
        // beginIndex is inside range, end is outside
        //TODO editor_text.insert(e.beginIndex, csReplace);
        
        Log.d(TAG, "inserting");
      }
    }
    */
    
    /* broadcast change event */
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
      pendingUndo.remove(id);
    } else if(pendingRedo.contains(id)) {
      eel.triggerSync();
      undoHistory.push(ee);
      if(undoHistory.size() > HISTORY_SIZE) {
        undoHistory.remove(0);
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
      
      /*
       * This is specifically to handle move events via drag & drop
       * No longer needed per spec update
       */
      /*
      if(textMoveEvent) {
        moveEventStack.push(new HistoryEntry(start, orig, change));
        Log.d(TAG, "pushed event onto moveEventStack, onTextChanged returning");
        return;
      }
      */
      
      /*
      undoHistory.push(new HistoryEntry(start, orig, change));
      
      // swype-like keyboard in android sometimes duplicates events
      // due to text prediction
      if(change.toString().equals(orig.toString())) {
        Log.d(TAG, "duplicate");
        duplicate = true;
        undoHistory.pop();
      }
      
      if(undoHistory.size() > HISTORY_SIZE) {
        undoHistory.remove(0);  // remove from bottom of stack
      }
      */
      
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
  
  
  /*
   * This is specifically to handle move events via drag & drop
   * No longer needed per spec update
   */
  /*
  @SuppressLint("NewApi")
  private class DragListener implements OnDragListener
  {
    public boolean onDrag(View v, DragEvent event)
    {
      if(event.getAction() == DragEvent.ACTION_DROP) {
        // let editorlistener know that a move event is occuring
        textMoveEvent = true;
        
        Log.d(TAG, "drag event ACTION_DROP, clip desc: " + event.getClipDescription().toString());
      }
      else if(event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
        // get rid of android's automatic drag & drop spaces 
        // and insert correct undo history
        
        // stack size should be 3 for move to start or end, 4 for anywhere else
        if(moveEventStack.size() < 3) {
          Log.d(TAG, "move event ended with stack size < 3");
          textMoveEvent = false;
          return false;
        }
        
        undoingOrRedoing = true;
        for(int i = 1; i < moveEventStack.size(); i++) {
          moveEventStack.elementAt(i).cascade = moveEventStack.elementAt(i-1);
        }
        
        undoHistory.push(moveEventStack.peek());
        undoingOrRedoing = false;
        
        moveEventStack.clear(); // clear for next move event
        
        textMoveEvent = false;
        
        Log.d(TAG, "drag event ACTION_ENDED");
      }
      
      return false;
    }
  }
  */
  
  
}