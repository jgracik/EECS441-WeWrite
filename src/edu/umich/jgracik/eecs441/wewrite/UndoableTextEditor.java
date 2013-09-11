package edu.umich.jgracik.eecs441.wewrite;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

public class UndoableTextEditor
{
  private static final String TAG = "UndoableTextEditor";
  
  private EditText editor;          // underlying view
  private HistoryEntry undoEvent;   // holds most recent change from listener
  private HistoryEntry redoEvent;   // holds most recent undone op from listener
  private EditorListener listener;  
  private boolean undoingOrRedoing = false; // acts as "lock" for undoEvent and redoEvent
  
  public UndoableTextEditor(EditText edittext)
  {
    editor = edittext;
    undoEvent = null;
    listener = new EditorListener();
    editor.addTextChangedListener(listener);
  }
  
  public void undo()
  {
    if(undoEvent == null) {
      Log.d(TAG, "nothing to undo");
      return;
    }
    
    undoingOrRedoing = true;
    
    Editable editor_text = editor.getText();
    
    int endIdx = undoEvent.beginIndex;
    if(undoEvent.newText != null) {
      endIdx += undoEvent.newText.length();
    }
    
    editor_text.replace(undoEvent.beginIndex, endIdx, undoEvent.oldText);
    
    redoEvent = undoEvent;
    undoEvent = null;
    
    undoingOrRedoing = false;
    
    Log.d(TAG, "performed undo operation, returning from undo()");
    
  }
  
  public void redo()
  {
    if(redoEvent == null) {
      Log.d(TAG, "nothing to redo");
      return;
    }
    
    undoingOrRedoing = true;
    
    Editable editor_text = editor.getText();
    
    int endIdx = redoEvent.beginIndex;
    if(redoEvent.oldText != null) {
      endIdx += redoEvent.oldText.length();
    }
    
    editor_text.replace(redoEvent.beginIndex, endIdx, redoEvent.newText);
    undoEvent = redoEvent;
    redoEvent = null;
    
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
      Log.d(TAG, "listener returning from method beforeTextChanged, orig set to: " + orig);
    }
    
    public void onTextChanged(CharSequence s, int start, int before, int count)
    {
      if(undoingOrRedoing) return;
      
      change = s.subSequence(start, start + count);
      Log.d(TAG, "listener in method onTextChanged, change set to: " + change);
      
      undoEvent = new HistoryEntry(start, orig, change);
      Log.d(TAG, "listener returning from method onTextChanged, updated lastEvent");
    }

    public void afterTextChanged(Editable s)
    {
      // do nothing
      Log.d(TAG, "listener entered method afterTextChanged");
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