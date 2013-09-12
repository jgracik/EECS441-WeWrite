package edu.umich.jgracik.eecs441.wewrite;

import java.util.Stack;

import android.annotation.SuppressLint;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.widget.EditText;

public class UndoableTextEditor
{
  private static final String TAG = "UndoableTextEditor";
  private static final int HISTORY_SIZE = 5;
  
  private EditText editor;          // underlying view
  private Stack<HistoryEntry> undoHistory;
  private Stack<HistoryEntry> redoHistory;
  private EditorListener listener;  
  private DragListener d_listener;
  private boolean undoingOrRedoing = false; // avoids adding undo/redo events to history
  
  @SuppressLint("NewApi")
  public UndoableTextEditor(EditText edittext)
  {
    editor = edittext;
    undoHistory = new Stack<HistoryEntry>();
    redoHistory = new Stack<HistoryEntry>();
    listener = new EditorListener();
    d_listener = new DragListener();
    editor.addTextChangedListener(listener);
    editor.setOnDragListener(d_listener);
  }
  
  public void undo()
  {
    if(undoHistory.empty()) {
      Log.d(TAG, "nothing to undo");
      return;
    }
    
    undoingOrRedoing = true;
    
    Editable editor_text = editor.getText();
    HistoryEntry undoEvent = undoHistory.pop();
    
    int endIdx = undoEvent.beginIndex;
    if(undoEvent.newText != null) {
      endIdx += undoEvent.newText.length();
    }
    
    editor_text.replace(undoEvent.beginIndex, endIdx, undoEvent.oldText);
    
    redoHistory.push(undoEvent);
    //undoEvent = null;
    
    undoingOrRedoing = false;
    
    Log.d(TAG, "performed undo operation, returning from undo()");
    
  }
  
  public void redo()
  {
    if(redoHistory.empty()){
      Log.d(TAG, "nothing to redo");
      return;
    }
    
    undoingOrRedoing = true;
    
    Editable editor_text = editor.getText();
    HistoryEntry redoEvent = redoHistory.pop();
    
    int endIdx = redoEvent.beginIndex;
    if(redoEvent.oldText != null) {
      endIdx += redoEvent.oldText.length();
    }
    
    editor_text.replace(redoEvent.beginIndex, endIdx, redoEvent.newText);
    undoHistory.push(redoEvent);
    //redoEvent = null;
    
    undoingOrRedoing = false;
    
    Log.d(TAG, "performed undo operation, returning from undo()");
    
  }
  
  
  /* Class to monitor changes to the EditText field and save last change */
  private class EditorListener implements TextWatcher
  {
    private CharSequence orig, change;
    
    public void beforeTextChanged(CharSequence s, int start, int count, int after)
    {
      // no need to save changes resulting from an undo or redo
      if(undoingOrRedoing) return;
      
      orig = s.subSequence(start, start + count);
      Log.d(TAG, "listener in beforeTextChanged, start: " + start + ", count: " + count + ", orig: [" + orig + "]");
    }
    
    public void onTextChanged(CharSequence s, int start, int before, int count)
    {
      if(undoingOrRedoing) return;
      
      change = s.subSequence(start, start + count);
      Log.d(TAG, "listener in onTextChanged, start: " + start + ", before: " + before + ", change: [" + change + "]");
      
      undoHistory.push(new HistoryEntry(start, orig, change));
      if(undoHistory.size() > HISTORY_SIZE) {
        undoHistory.remove(0);  // remove from bottom of stack
      }
      Log.d(TAG, "listener returning from method onTextChanged, updated lastEvent");
    }

    public void afterTextChanged(Editable s)
    {
      // do nothing
    }
    
  } 
  
  @SuppressLint("NewApi")
  private class DragListener implements OnDragListener
  {
    public boolean onDrag(View v, DragEvent event)
    {
      Log.d(TAG, "onDrag called");
      if(event.getAction() == DragEvent.ACTION_DROP) {
        Log.d(TAG, "drag event ACTION_DROP");
        Log.d(TAG, "drag event clip desc: " + event.getClipDescription().toString());
      }
      else if(event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
        Log.d(TAG, "drag event ACTION_ENDED");
      }
      return false;
    }
  }
  
  /* Undo history object - holds before and after text with index */
  private class HistoryEntry
  {
    int beginIndex;
    CharSequence oldText;
    CharSequence newText;
    
    public HistoryEntry(int idx, CharSequence orig, CharSequence replace)
    {
      beginIndex = idx;
      oldText = orig;
      newText = replace;
    }
  }
  
  
}