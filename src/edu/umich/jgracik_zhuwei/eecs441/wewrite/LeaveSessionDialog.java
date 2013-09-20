package edu.umich.jgracik_zhuwei.eecs441.wewrite;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

/*
 * DialogFragment code based off of Android SDK documentation tutorial code:
 * http://developer.android.com/guide/topics/ui/dialogs.html
 */

public class LeaveSessionDialog extends DialogFragment
{ 
  public interface LeaveSessionListener {
    public void onDialogPositiveClick(DialogFragment dialog);
    public void onDialogNegativeClick(DialogFragment dialog);
  }
  
  LeaveSessionListener listener;
  
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    // Verify that the host activity implements the callback interface
    try {
        listener = (LeaveSessionListener) activity;
    } catch (ClassCastException e) {
        throw new ClassCastException(activity.toString() + " must implement LeaveSessionListener interface");
    }
  }

  
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) 
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setMessage(R.string.dialog_owner_leaving)
      // create action buttons
      .setPositiveButton(R.string.button_leave_only, new DialogInterface.OnClickListener() // create
      {
        @Override
        public void onClick(DialogInterface dialog, int which)
        {
          listener.onDialogPositiveClick(LeaveSessionDialog.this);
        }
      })
      .setNegativeButton(R.string.button_leave_delete, new DialogInterface.OnClickListener() // cancel
      {
        
        @Override
        public void onClick(DialogInterface dialog, int which)
        {
          listener.onDialogNegativeClick(LeaveSessionDialog.this);
        }
      });
    
    return builder.create();
  }
}
