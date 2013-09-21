package edu.umich.jgracik_zhuwei.eecs441.wewrite;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

public class EditTextCursor extends EditText
{
  public interface onSelectionChangedListener 
  {
    public void onSelectionChanged(int selStart, int selEnd);
  }
  
  onSelectionChangedListener listener;
  
  public EditTextCursor(Context context) 
  {
    super(context);
    listener = null;
  }

  public EditTextCursor(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    listener = null;
  }
  
  public EditTextCursor(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    listener = null;
  }

  public void addOnSelectionChangedListener(onSelectionChangedListener oscl)
  {
    listener = oscl;
  }

  protected void onSelectionChanged(int selStart, int selEnd) 
  {
    if(listener != null) {
      listener.onSelectionChanged(selStart, selEnd);
    }
  }
  
}